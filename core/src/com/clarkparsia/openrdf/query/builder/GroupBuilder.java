/*
 * Copyright (c) 2009-2010 Clark & Parsia, LLC. <http://www.clarkparsia.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clarkparsia.openrdf.query.builder;

import org.openrdf.query.algebra.StatementPattern;
import org.openrdf.query.algebra.ValueExpr;
import org.openrdf.query.algebra.Var;
import org.openrdf.query.algebra.Compare;
import org.openrdf.query.algebra.ValueConstant;

import org.openrdf.model.Value;

import com.clarkparsia.utils.BasicUtils;

import static com.clarkparsia.utils.collections.CollectionUtil.set;
import com.clarkparsia.openrdf.query.builder.UnionBuilder;
import com.clarkparsia.openrdf.query.builder.BasicGroup;

import org.openrdf.query.parser.ParsedQuery;

import java.util.Arrays;

import java.util.Set;

/**
 * <p>Builder for creating a grouped set of query atoms and filters in a query.</p>
 *
 * @author Michael Grove
 * @since 0.2
 * @version 0.2.2
 */
public class GroupBuilder<T extends ParsedQuery, E extends SupportsGroups> {
	private E mBuilder;
	private BasicGroup mGroup;

	private StatementPattern.Scope mScope = StatementPattern.Scope.DEFAULT_CONTEXTS;
	private Var mContext = null;

	public GroupBuilder(final E theBuilder) {
		this(theBuilder, false, null);
	}

	public GroupBuilder(final E theBuilder, boolean theOptional) {
		this(theBuilder, theOptional, null);
	}

	public GroupBuilder(final E theBuilder, boolean theOptional, BasicGroup theParent) {
		mBuilder = theBuilder;
		mGroup = new BasicGroup(theOptional);

		if (theParent == null) {
			if (mBuilder != null) {
				mBuilder.addGroup(mGroup);
			}
		}
		else {
			theParent.addChild(mGroup);
		}
	}

    public Group getGroup() {
		return mGroup;
	}

	public GroupBuilder<T,E> group() {
		return new GroupBuilder<T,E>(mBuilder, false, mGroup);
	}

	public GroupBuilder<T,E> optional() {
		return new GroupBuilder<T,E>(mBuilder, true, mGroup);
	}

	public E closeGroup() {
		return mBuilder;
	}

	public UnionBuilder<T> union() {
		UnionBuilder<T> aBuilder = new UnionBuilder<T>(this);
		
		mGroup.addChild(aBuilder);

		return aBuilder;
	}

	public GroupBuilder setScope(StatementPattern.Scope theScope) {
		mScope = theScope;

		for (StatementPattern aPattern : mGroup.getPatterns()) {
			aPattern.setScope(mScope);
		}

		return this;
	}

	public GroupBuilder setContext(String theContextVar) {
		mContext = new Var(theContextVar);
		return this;
	}

	public GroupBuilder setContext(Value theContextValue) {
		mContext = valueToVar(theContextValue);

		for (StatementPattern aPattern : mGroup.getPatterns()) {
			aPattern.setContextVar(mContext);
		}

		return this;
	}

	public FilterBuilder<T, E> filter() {
		return new FilterBuilder<T, E>(this);
	}

    public GroupBuilder<T,E> filter(ValueExpr theExpr) {
        mGroup.addFilter(theExpr);

        return this;
    }

	public GroupBuilder<T,E> filter(String theVar, Compare.CompareOp theOp, Value theValue) {
		Compare aComp = new Compare(new Var(theVar), new ValueConstant(theValue), theOp);
		mGroup.addFilter(aComp);

		return this;
	}

    public GroupBuilder<T,E> atom(StatementPattern thePattern) {
        return addPattern(thePattern);
    }

    public GroupBuilder<T,E> atom(StatementPattern... thePatterns) {
        return atoms(set(Arrays.asList(thePatterns)));
    }

    public GroupBuilder<T,E> atoms(Set<StatementPattern> thePatterns) {
		for (StatementPattern aPattern : thePatterns) {
			aPattern.setContextVar(mContext);
			aPattern.setScope(mScope);
		}

        mGroup.addAll(thePatterns);

        return this;
    }

	public GroupBuilder<T,E> atom(String theSubjVar, String thePredVar, String theObjVar) {
		return addPattern(newPattern(new Var(theSubjVar), new Var(thePredVar), new Var(theObjVar)));
	}

	public GroupBuilder<T,E> atom(String theSubjVar, String thePredVar, Value theObj) {
		return addPattern(newPattern(new Var(theSubjVar), new Var(thePredVar), valueToVar(theObj)));
	}

	public GroupBuilder<T,E> atom(String theSubjVar, Value thePredVar, String theObj) {
		return addPattern(newPattern(new Var(theSubjVar), valueToVar(thePredVar), new Var(theObj)));
	}

	public GroupBuilder<T,E> atom(String theSubjVar, Value thePred, Value theObj) {
		return addPattern(newPattern(new Var(theSubjVar), valueToVar(thePred), valueToVar(theObj)));
	}

	public GroupBuilder<T,E> atom(Value theSubjVar, Value thePredVar, Value theObj) {
		return addPattern(newPattern(valueToVar(theSubjVar), valueToVar(thePredVar), valueToVar(theObj)));
	}

	public GroupBuilder<T,E> atom(Value theSubjVar, Value thePredVar, String theObj) {
		return addPattern(newPattern(valueToVar(theSubjVar), valueToVar(thePredVar), new Var(theObj)));
	}

	public GroupBuilder<T,E> atom(Value theSubjVar, String thePredVar, String theObj) {
		return addPattern(newPattern(valueToVar(theSubjVar), new Var(thePredVar), new Var(theObj)));
	}

	private GroupBuilder<T,E> addPattern(StatementPattern thePattern) {
		thePattern.setContextVar(mContext);
		thePattern.setScope(mScope);

		mGroup.add(thePattern);

		return this;
	}

	private StatementPattern newPattern(Var theSubj, Var thePred, Var theObj) {
		return new StatementPattern(mScope, theSubj, thePred, theObj, mContext);
	}

	public static Var valueToVar(Value theValue) {
		Var aVar = new Var(BasicUtils.getRandomString(4), theValue);
		aVar.setAnonymous(true);

		return aVar;
	}
}
