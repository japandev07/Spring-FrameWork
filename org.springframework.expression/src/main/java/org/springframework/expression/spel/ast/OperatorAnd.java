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
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.support.BooleanTypedValue;

/**
 * Represents the boolean AND operation.
 *
 * @author Andy Clement
 * @since 3.0
 */
public class OperatorAnd extends Operator {

	public OperatorAnd(int pos, SpelNodeImpl... operands) {
		super("and", pos, operands);
	}

	@Override
	public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
		boolean leftValue;
		boolean rightValue;

		try {
			leftValue = (Boolean)state.convertValue(getLeftOperand().getValueInternal(state), BOOLEAN_TYPE_DESCRIPTOR);
		}
		catch (SpelEvaluationException ee) {
			ee.setPosition(getLeftOperand().getStartPosition());
			throw ee;
		}

		if (leftValue == false) {
			return BooleanTypedValue.forValue(false); // no need to evaluate right operand
		}

		try {
			rightValue = (Boolean)state.convertValue(getRightOperand().getValueInternal(state), BOOLEAN_TYPE_DESCRIPTOR);
		}
		catch (SpelEvaluationException ee) {
			ee.setPosition(getRightOperand().getStartPosition());
			throw ee;
		}

		return /* leftValue && */BooleanTypedValue.forValue(rightValue);
	}

}
