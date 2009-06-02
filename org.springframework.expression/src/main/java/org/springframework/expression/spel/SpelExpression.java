/*
 * Copyright 2004-2008 the original author or authors.
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
package org.springframework.expression.spel;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.common.ExpressionUtils;
import org.springframework.expression.spel.ast.SpelNodeImpl;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * A SpelExpressions represents a parsed (valid) expression that is ready to be evaluated in a specified context. An
 * expression can be evaluated standalone or in a specified context. During expression evaluation the context may be
 * asked to resolve references to types, beans, properties, methods.
 * 
 * @author Andy Clement
 * @since 3.0
 */
public class SpelExpression implements Expression {
	
	private final String expression;

	public final SpelNodeImpl ast;


	/**
	 * Construct an expression, only used by the parser.
	 */
	public SpelExpression(String expression, SpelNodeImpl ast) {
		this.expression = expression;
		this.ast = ast;
	}

	/**
	 * @return the expression string that was parsed to create this expression instance
	 */
	public String getExpressionString() {
		return this.expression;
	}

	/**
	 * {@inheritDoc}
	 */
	public Object getValue() throws EvaluationException {
		ExpressionState expressionState = new ExpressionState(new StandardEvaluationContext());
		return this.ast.getValue(expressionState);
	}

	/**
	 * {@inheritDoc}
	 */
	public Object getValue(EvaluationContext context) throws EvaluationException {
		return this.ast.getValue(new ExpressionState(context));
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public <T> T getValue(EvaluationContext context, Class<T> expectedResultType) throws EvaluationException {
		Object result = ast.getValue(new ExpressionState(context));

		if (result != null && expectedResultType != null) {
			Class<?> resultType = result.getClass();
			if (!expectedResultType.isAssignableFrom(resultType)) {
				// Attempt conversion to the requested type, may throw an exception
				result = context.getTypeConverter().convertValue(result, expectedResultType);
			}
		}
		return (T) result;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setValue(EvaluationContext context, Object value) throws EvaluationException {
		this.ast.setValue(new ExpressionState(context), value);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isWritable(EvaluationContext context) throws EvaluationException {
		return this.ast.isWritable(new ExpressionState(context));
	}

	/**
	 * @return return the Abstract Syntax Tree for the expression
	 */
	public SpelNode getAST() {
		return this.ast;
	}

	/**
	 * Produce a string representation of the Abstract Syntax Tree for the expression, this should ideally look like the
	 * input expression, but properly formatted since any unnecessary whitespace will have been discarded during the
	 * parse of the expression.
	 * 
	 * @return the string representation of the AST
	 */
	public String toStringAST() {
		return this.ast.toStringAST();
	}

	/**
	 * {@inheritDoc}
	 */
	public Class getValueType(EvaluationContext context) throws EvaluationException {
		ExpressionState eState = new ExpressionState(context);
		TypeDescriptor typeDescriptor = this.ast.getValueInternal(eState).getTypeDescriptor();
		return typeDescriptor.getType();
	}

	/**
	 * {@inheritDoc}
	 */
	public TypeDescriptor getValueTypeDescriptor(EvaluationContext context) throws EvaluationException {
		ExpressionState eState = new ExpressionState(context);
		TypeDescriptor typeDescriptor = this.ast.getValueInternal(eState).getTypeDescriptor();
		return typeDescriptor;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Class getValueType() throws EvaluationException {
		return this.ast.getValueInternal(new ExpressionState(new StandardEvaluationContext())).getTypeDescriptor().getType();
	}

	/**
	 * {@inheritDoc}
	 */
	public TypeDescriptor getValueTypeDescriptor() throws EvaluationException {
		return this.ast.getValueInternal(new ExpressionState(new StandardEvaluationContext())).getTypeDescriptor();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public <T> T getValue(Class<T> expectedResultType) throws EvaluationException {
		ExpressionState expressionState = new ExpressionState(new StandardEvaluationContext());
		Object result = this.ast.getValue(expressionState);
		return ExpressionUtils.convert(expressionState.getEvaluationContext(), result, expectedResultType);
	}

}
