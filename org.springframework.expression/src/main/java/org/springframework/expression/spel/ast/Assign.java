/*
 * Copyright 2002-2009 the original author or authors.
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
import org.springframework.expression.spel.ExpressionState;

/**
 * Represents assignment. An alternative to calling setValue() for an expression is to use an assign.
 *
 * <p>Example: 'someNumberProperty=42'
 *
 * @author Andy Clement
 * @since 3.0
 */
public class Assign extends SpelNodeImpl {

	public Assign(int pos,SpelNodeImpl... operands) {
		super(pos,operands);
	}

	@Override
	public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
		TypedValue newValue = children[1].getValueInternal(state);
		getChild(0).setValue(state, newValue.getValue());
		return newValue;
	}

	@Override
	public String toStringAST() {
		return new StringBuilder().append(getChild(0).toStringAST()).append("=").append(getChild(1).toStringAST())
				.toString();
	}

}
