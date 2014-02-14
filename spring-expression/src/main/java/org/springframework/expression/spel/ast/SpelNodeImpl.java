/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.expression.spel.ast;

import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.ExpressionUtils;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;

/**
 * The common supertype of all AST nodes in a parsed Spring Expression Language format
 * expression.
 *
 * @author Andy Clement
 * @since 3.0
 */
public abstract class SpelNodeImpl implements SpelNode {

	private static SpelNodeImpl[] NO_CHILDREN = new SpelNodeImpl[0];


	protected int pos; // start = top 16bits, end = bottom 16bits

	protected SpelNodeImpl[] children = SpelNodeImpl.NO_CHILDREN;

	private SpelNodeImpl parent;


	public SpelNodeImpl(int pos, SpelNodeImpl... operands) {
		this.pos = pos;
		// pos combines start and end so can never be zero because tokens cannot be zero length
		Assert.isTrue(pos != 0);
		if (operands != null && operands.length > 0) {
			this.children = operands;
			for (SpelNodeImpl childnode : operands) {
				childnode.parent = this;
			}
		}
	}


	protected SpelNodeImpl getPreviousChild() {
		SpelNodeImpl result = null;
		if (this.parent != null) {
			for (SpelNodeImpl child : this.parent.children) {
				if (this==child) {
					break;
				}
				result = child;
			}
		}
		return result;
	}

	/**
     * @return true if the next child is one of the specified classes
     */
	protected boolean nextChildIs(Class<?>... clazzes) {
		if (this.parent != null) {
			SpelNodeImpl[] peers = this.parent.children;
			for (int i = 0, max = peers.length; i < max; i++) {
				if (peers[i] == this) {
					if ((i + 1) >= max) {
						return false;
					}

					Class<?> clazz = peers[i + 1].getClass();
					for (Class<?> desiredClazz : clazzes) {
						if (clazz.equals(desiredClazz)) {
							return true;
						}
					}

					return false;
				}
			}
		}
		return false;
	}

	@Override
	public final Object getValue(ExpressionState expressionState) throws EvaluationException {
		if (expressionState != null) {
			return getValueInternal(expressionState).getValue();
		}
		else {
			// configuration not set - does that matter?
			return getValue(new ExpressionState(new StandardEvaluationContext()));
		}
	}

	@Override
	public final TypedValue getTypedValue(ExpressionState expressionState) throws EvaluationException {
		if (expressionState != null) {
			return getValueInternal(expressionState);
		}
		else {
			// configuration not set - does that matter?
			return getTypedValue(new ExpressionState(new StandardEvaluationContext()));
		}
	}

	// by default Ast nodes are not writable
	@Override
	public boolean isWritable(ExpressionState expressionState) throws EvaluationException {
		return false;
	}

	@Override
	public void setValue(ExpressionState expressionState, Object newValue) throws EvaluationException {
		throw new SpelEvaluationException(getStartPosition(),
				SpelMessage.SETVALUE_NOT_SUPPORTED, getClass());
	}

	@Override
	public SpelNode getChild(int index) {
		return this.children[index];
	}

	@Override
	public int getChildCount() {
		return this.children.length;
	}

	@Override
	public Class<?> getObjectClass(Object obj) {
		if (obj == null) {
			return null;
		}
		return (obj instanceof Class ? ((Class<?>) obj) : obj.getClass());
	}

	protected final <T> T getValue(ExpressionState state, Class<T> desiredReturnType) throws EvaluationException {
		return ExpressionUtils.convertTypedValue(state.getEvaluationContext(), getValueInternal(state), desiredReturnType);
	}

	@Override
	public int getStartPosition() {
		return (this.pos >> 16);
	}

	@Override
	public int getEndPosition() {
		return (this.pos & 0xffff);
	}

	protected ValueRef getValueRef(ExpressionState state) throws EvaluationException {
		throw new SpelEvaluationException(this.pos, SpelMessage.NOT_ASSIGNABLE, toStringAST());
	}


	public abstract TypedValue getValueInternal(ExpressionState expressionState) throws EvaluationException;

}
