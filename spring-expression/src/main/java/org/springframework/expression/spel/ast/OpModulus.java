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

import java.math.BigDecimal;

import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Operation;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.util.NumberUtils;

/**
 * Implements the modulus operator.
 *
 * @author Andy Clement
 * @author Giovanni Dall'Oglio Risso
 * @since 3.0
 */
public class OpModulus extends Operator {


	public OpModulus(int pos, SpelNodeImpl... operands) {
		super("%", pos, operands);
	}


	@Override
	public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
		Object leftOperand = getLeftOperand().getValueInternal(state).getValue();
		Object rightOperand = getRightOperand().getValueInternal(state).getValue();
		if (leftOperand instanceof Number && rightOperand instanceof Number) {
			Number leftNumber = (Number) leftOperand;
			Number rightNumber = (Number) rightOperand;

			if (leftNumber instanceof BigDecimal || rightNumber instanceof BigDecimal) {
				BigDecimal leftBigDecimal = NumberUtils.convertNumberToTargetClass(leftNumber, BigDecimal.class);
				BigDecimal rightBigDecimal = NumberUtils.convertNumberToTargetClass(rightNumber, BigDecimal.class);
				return new TypedValue(leftBigDecimal.remainder(rightBigDecimal));
			}

			if (leftNumber instanceof Double || rightNumber instanceof Double) {
				if (leftNumber instanceof Double && rightNumber instanceof Double) {
					this.exitTypeDescriptor = "D";
				}
				return new TypedValue(leftNumber.doubleValue() % rightNumber.doubleValue());
			}

			if (leftNumber instanceof Float || rightNumber instanceof Float) {
				if (leftNumber instanceof Float && rightNumber instanceof Float) {
					this.exitTypeDescriptor = "F";
				}
				return new TypedValue(leftNumber.floatValue() % rightNumber.floatValue());
			}

			if (leftNumber instanceof Long || rightNumber instanceof Long) {
				if (leftNumber instanceof Long && rightNumber instanceof Long) {
					this.exitTypeDescriptor = "J";
				}
				return new TypedValue(leftNumber.longValue() % rightNumber.longValue());
			}

			if (leftNumber instanceof Integer && rightNumber instanceof Integer) {
				this.exitTypeDescriptor = "I";
			}
			return new TypedValue(leftNumber.intValue() % rightNumber.intValue());
		}

		return state.operate(Operation.MODULUS, leftOperand, rightOperand);
	}
	
	@Override
	public boolean isCompilable() {
		if (!getLeftOperand().isCompilable()) {
			return false;
		}
		if (this.children.length>1) {
			 if (!getRightOperand().isCompilable()) {
				 return false;
			 }
		}
		return this.exitTypeDescriptor!=null;
	}
	
	@Override
	public void generateCode(MethodVisitor mv, CodeFlow codeflow) {
		getLeftOperand().generateCode(mv, codeflow);
		String leftdesc = getLeftOperand().getExitDescriptor();
		if (!CodeFlow.isPrimitive(leftdesc)) {
			CodeFlow.insertUnboxInsns(mv, this.exitTypeDescriptor.charAt(0), leftdesc);
		}
		if (this.children.length > 1) {
			codeflow.enterCompilationScope();
			getRightOperand().generateCode(mv, codeflow);
			String rightdesc = getRightOperand().getExitDescriptor();
			codeflow.exitCompilationScope();
			if (!CodeFlow.isPrimitive(rightdesc)) {
				CodeFlow.insertUnboxInsns(mv, this.exitTypeDescriptor.charAt(0), rightdesc);
			}
			switch (this.exitTypeDescriptor.charAt(0)) {
				case 'I':
					mv.visitInsn(IREM);
					break;
				case 'J':
					mv.visitInsn(LREM);
					break;
				case 'F': 
					mv.visitInsn(FREM);
					break;
				case 'D':
					mv.visitInsn(DREM);
					break;				
				default:
					throw new IllegalStateException("Unrecognized exit descriptor: '"+this.exitTypeDescriptor+"'");			
			}
		}
		codeflow.pushDescriptor(this.exitTypeDescriptor);
	}

}
