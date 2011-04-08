// Copyright (c) 2010 - 2011 -- Clark & Parsia, LLC. <http://www.clarkparsia.com>
// For more information about licensing and copyright of this software, please contact
// inquiries@clarkparsia.com or visit http://stardog.com

package com.clarkparsia.openrdf.query.util;

import org.openrdf.query.algebra.helpers.QueryModelVisitorBase;
import org.openrdf.query.algebra.Filter;
import org.openrdf.query.algebra.ValueConstant;
import org.openrdf.query.algebra.SameTerm;
import org.openrdf.query.algebra.Var;
import org.openrdf.query.algebra.Projection;
import org.openrdf.query.algebra.MultiProjection;
import org.openrdf.query.algebra.Extension;
import org.openrdf.query.algebra.StatementPattern;
import org.openrdf.query.algebra.ProjectionElemList;
import org.openrdf.query.algebra.ProjectionElem;
import org.openrdf.query.algebra.ExtensionElem;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.Join;
import org.openrdf.query.algebra.Union;
import org.openrdf.query.algebra.BinaryTupleOperator;
import org.openrdf.query.algebra.SingletonSet;
import org.openrdf.query.algebra.LeftJoin;
import org.openrdf.query.algebra.Or;
import org.openrdf.query.algebra.And;
import org.openrdf.query.algebra.Bound;
import org.openrdf.query.algebra.ValueExpr;
import org.openrdf.query.algebra.UnaryTupleOperator;
import org.openrdf.model.Value;
import org.openrdf.model.Literal;
import org.openrdf.model.impl.BooleanLiteralImpl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

/**
 * <p>Implementation of a Algebra visitor that will re-write describe queries that are normally generated by Sesame to a far simpler form which is easier to evaluate.</p>
 *
 * @author Michael Grove
 * @since 0.3
 * @version 0.3
*/
public final class DescribeRewriter extends QueryModelVisitorBase<Exception> {
	private Collection<String> mVars = new HashSet<String>();
	private Collection<Value> mValues = new HashSet<Value>();

	/**
	 * @inheritDoc
	 */
	@Override
	public void meet(final Filter theFilter) throws Exception {
		super.meet(theFilter);

		rewriteUnary(theFilter);

		theFilter.visit(new ConstantVisitor());

		if (theFilter.getCondition() instanceof ValueConstant
			&& ((ValueConstant)theFilter.getCondition()).getValue().equals(BooleanLiteralImpl.TRUE)) {

			theFilter.replaceWith(theFilter.getArg());
		}
	}

	/**
	 * @inheritDoc
	 */
	@Override
	protected void meetUnaryTupleOperator(final UnaryTupleOperator theUnaryTupleOperator) throws Exception {
		super.meetUnaryTupleOperator(theUnaryTupleOperator);
		rewriteUnary(theUnaryTupleOperator);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void meet(final SameTerm theSameTerm) throws Exception {
		super.meet(theSameTerm);

		boolean remove = false;

		if (theSameTerm.getLeftArg() instanceof Var && ((Var)theSameTerm.getLeftArg()).getName().startsWith("-descr-")) {
			if (theSameTerm.getRightArg() instanceof Var) {
				mVars.add(((Var)theSameTerm.getRightArg()).getName());
			}
			else if (theSameTerm.getRightArg() instanceof ValueConstant) {
				mValues.add(((ValueConstant)theSameTerm.getRightArg()).getValue());
			}

			remove = true;
		}

		if (theSameTerm.getRightArg() instanceof Var && ((Var)theSameTerm.getRightArg()).getName().startsWith("-descr-")) {
			if (theSameTerm.getLeftArg() instanceof Var) {
				mVars.add(((Var)theSameTerm.getLeftArg()).getName());
			}
			else if (theSameTerm.getLeftArg() instanceof ValueConstant) {
				mValues.add(((ValueConstant)theSameTerm.getLeftArg()).getValue());
			}

			remove = true;
		}

		if (remove) {
			theSameTerm.replaceWith(new ValueConstant(BooleanLiteralImpl.TRUE));
		}
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void meet(final Projection theProj) throws Exception {
		super.meet(theProj);

		// probably not a describe, so don't muck with the query algebra
		if (mValues.isEmpty() && mVars.isEmpty()) {
			return;
		}

		// TODO: scoping!
		MultiProjection aNewProj = new MultiProjection();
		Extension aExt = null;
		List<StatementPattern> aNewPatterns = new ArrayList<StatementPattern>();

		int count = 0;
		for (String aVar : mVars) {
			ProjectionElemList pel = new ProjectionElemList();
			pel.addElement(new ProjectionElem(aVar, "subject"));
			pel.addElement(new ProjectionElem("proj" + (count+1), "predicate"));
			pel.addElement(new ProjectionElem("proj" + (count+2), "object"));

			aNewPatterns.add(new StatementPattern(new Var(aVar), new Var("proj" + (count+1)), new Var("proj" + (count+2))));

			count +=2 ;
			aNewProj.addProjection(pel);
		}

		for (Value aVal : mValues) {
			String name = "proj" + (count++);

			ProjectionElemList pel = new ProjectionElemList();
			pel.addElement(new ProjectionElem(name, "subject"));
			pel.addElement(new ProjectionElem("proj" + (count+1), "predicate"));
			pel.addElement(new ProjectionElem("proj" + (count+2), "object"));

			if (aExt == null) aExt = new Extension();
			aExt.addElement(new ExtensionElem(new ValueConstant(aVal), name));

			aNewPatterns.add(new StatementPattern(new Var(name, aVal), new Var("proj" + (count+1)), new Var("proj" + (count+2))));

			count +=2 ;
			aNewProj.addProjection(pel);
		}

		TupleExpr aExpr = theProj.getArg();
		if (!aNewPatterns.isEmpty()) {
			aExpr = new Join(aNewPatterns.get(0), aExpr);

			for (int i = 1; i < aNewPatterns.size(); i++) {
				aExpr = new Join(aExpr, aNewPatterns.get(i));
			}
		}

		if (aExt != null) {
			aExt.setArg(aExpr);
			aExpr = aExt;
		}

		aNewProj.setArg(aExpr);

		theProj.replaceWith(aNewProj);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void meet(final Join theJoin) throws Exception {
		super.meet(theJoin);
		rewriteBinary(theJoin);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void meet(final Union theUnion) throws Exception {
		super.meet(theUnion);
		rewriteBinary(theUnion);
	}

	@Override
	public void meet(final StatementPattern thePattern) throws Exception {
		super.meet(thePattern);
		if (isDescribeOnlyPattern(thePattern)) {
			thePattern.replaceWith(new SingletonSet());
		}
	}

	private void rewriteBinary(BinaryTupleOperator theOp) {
		boolean removeLeft = false;
		boolean removeRight = false;

		if (isDescribeOnlyPattern(theOp.getLeftArg())) {
			removeLeft = true;
		}

		if (isDescribeOnlyPattern(theOp.getRightArg())) {
			removeRight = true;
		}

		if (removeLeft && removeRight) {
			theOp.replaceWith(new SingletonSet());
		}
		else if (removeLeft) {
			theOp.replaceWith(theOp.getRightArg());
		}
		else if (removeRight) {
			theOp.replaceWith(theOp.getLeftArg());
		}
	}

	private void rewriteUnary(UnaryTupleOperator theOp) {
		if (isDescribeOnlyPattern(theOp.getArg())) {
			theOp.getArg().replaceWith(new SingletonSet());
		}
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void meet(final LeftJoin theLeftJoin) throws Exception {
		super.meet(theLeftJoin);

		boolean removeLeft = false;
		boolean removeRight = false;

		if (isDescribeOnlyPattern(theLeftJoin.getLeftArg())) {
			removeLeft = true;
		}

		if (isDescribeOnlyPattern(theLeftJoin.getRightArg())) {
			removeRight = true;
		}

		if (removeLeft && removeRight) {
			theLeftJoin.replaceWith(new SingletonSet());
		}
		else if (removeLeft) {
			if (theLeftJoin.getCondition() != null) {
				theLeftJoin.replaceWith(new Filter(theLeftJoin.getRightArg(), theLeftJoin.getCondition()));
			}
			else {
				theLeftJoin.replaceWith(theLeftJoin.getRightArg());
			}
		}
		else if (removeRight) {
			if (theLeftJoin.getCondition() != null) {
				theLeftJoin.replaceWith(new Filter(theLeftJoin.getLeftArg(), theLeftJoin.getCondition()));
			}
			else {
				theLeftJoin.replaceWith(theLeftJoin.getLeftArg());
			}
		}
	}

	private boolean isDescribeOnlyPattern(final TupleExpr theExpr) {
		return theExpr instanceof StatementPattern
			   && ((StatementPattern)theExpr).getSubjectVar().getName().equals("-descr-subj")
			   && ((StatementPattern)theExpr).getPredicateVar().getName().equals("-descr-pred")
			   && ((StatementPattern)theExpr).getObjectVar().getName().equals("-descr-obj");
	}

	/**
	 * Adapted from the Sesame visitor of the same name in the ConstantOptimizer.  Primary change is that it does not require an evaluation strategy to complete the work.
	 */
	private static class ConstantVisitor extends QueryModelVisitorBase<Exception> {

		@Override
		public void meet(Or or) throws Exception {
			or.visitChildren(this);

			if (isConstant(or.getLeftArg()) && isConstant(or.getRightArg())) {
				boolean value = isTrue(or.getLeftArg()) && isTrue(or.getRightArg());
				or.replaceWith(new ValueConstant(BooleanLiteralImpl.valueOf(value)));
			}
			else if (isConstant(or.getLeftArg())) {
				boolean leftIsTrue = isTrue(or.getLeftArg());
				if (leftIsTrue) {
					or.replaceWith(new ValueConstant(BooleanLiteralImpl.TRUE));
				}
				else {
					or.replaceWith(or.getRightArg());
				}
			}
			else if (isConstant(or.getRightArg())) {
				boolean rightIsTrue = isTrue(or.getRightArg());
				if (rightIsTrue) {
					or.replaceWith(new ValueConstant(BooleanLiteralImpl.TRUE));
				}
				else {
					or.replaceWith(or.getLeftArg());
				}
			}
		}

		@Override
		public void meet(And and) throws Exception {
			and.visitChildren(this);

				if (isConstant(and.getLeftArg()) && isConstant(and.getRightArg())) {
					boolean value = isTrue(and.getLeftArg()) && isTrue(and.getRightArg());
					and.replaceWith(new ValueConstant(BooleanLiteralImpl.valueOf(value)));
				}
				else if (isConstant(and.getLeftArg())) {
					boolean leftIsTrue = isTrue(and.getLeftArg());
					if (leftIsTrue) {
						and.replaceWith(and.getRightArg());
					}
					else {
						and.replaceWith(new ValueConstant(BooleanLiteralImpl.FALSE));
					}
				}
				else if (isConstant(and.getRightArg())) {
					boolean rightIsTrue = isTrue(and.getRightArg());
					if (rightIsTrue) {
						and.replaceWith(and.getLeftArg());
					}
					else {
						and.replaceWith(new ValueConstant(BooleanLiteralImpl.FALSE));
					}
				}
		}

		@Override
		public void meet(Bound bound) throws Exception {
			super.meet(bound);

			if (bound.getArg().hasValue()) {
				// variable is always bound
				bound.replaceWith(new ValueConstant(BooleanLiteralImpl.TRUE));
			}
		}

		private boolean isTrue(final ValueExpr theValue) {
			return ((ValueConstant)theValue).getValue() instanceof Literal && ((Literal)((ValueConstant)theValue).getValue()).booleanValue();
		}

		private boolean isTrue(final Value theValue) {
			return theValue instanceof Literal && ((Literal)theValue).booleanValue();
		}

		private boolean isConstant(ValueExpr expr) {
			return expr instanceof ValueConstant || expr instanceof Var && ((Var) expr).hasValue();
		}
	}

	public static class Clean extends QueryModelVisitorBase<Exception> {

		@Override
		protected void meetBinaryTupleOperator(final BinaryTupleOperator theBinaryTupleOperator) throws Exception {
			super.meetBinaryTupleOperator(theBinaryTupleOperator);

			boolean removeLeft = theBinaryTupleOperator.getLeftArg() instanceof SingletonSet;
			boolean removeRight = theBinaryTupleOperator.getRightArg() instanceof SingletonSet;

			if (removeLeft && removeRight) {
				theBinaryTupleOperator.replaceWith(new SingletonSet());
			}
			else if (removeLeft) {
				theBinaryTupleOperator.replaceWith(theBinaryTupleOperator.getRightArg());
			}
			else if (removeRight) {
				theBinaryTupleOperator.replaceWith(theBinaryTupleOperator.getLeftArg());
			}
		}
	}
}
