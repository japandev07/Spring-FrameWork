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
import org.springframework.util.Assert;

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

	public final int configuration;

	/**
	 * Construct an expression, only used by the parser.
	 */
	public SpelExpression(String expression, SpelNodeImpl ast, int configuration) {
		this.expression = expression;
		this.ast = ast;
		this.configuration = configuration;
	}

	// implementing Expression
	
	public Object getValue() throws EvaluationException {
		ExpressionState expressionState = new ExpressionState(new StandardEvaluationContext(), configuration);
		return ast.getValue(expressionState);
	}
	
	public <T> T getValue(Class<T> expectedResultType) throws EvaluationException {
		ExpressionState expressionState = new ExpressionState(new StandardEvaluationContext(), configuration);
		Object result = ast.getValue(expressionState);
		return ExpressionUtils.convert(expressionState.getEvaluationContext(), result, expectedResultType);
	}
	
	public Object getValue(EvaluationContext context) throws EvaluationException {
		Assert.notNull(context, "The EvaluationContext is required");
		return ast.getValue(new ExpressionState(context, configuration));
	}

	@SuppressWarnings("unchecked")
	public <T> T getValue(EvaluationContext context, Class<T> expectedResultType) throws EvaluationException {
		Object result = ast.getValue(new ExpressionState(context, configuration));
		if (result != null && expectedResultType != null) {
			Class<?> resultType = result.getClass();
			if (!expectedResultType.isAssignableFrom(resultType)) {
				// Attempt conversion to the requested type, may throw an exception
				result = context.getTypeConverter().convertValue(result, expectedResultType);
			}
		}
		return (T) result;
	}

	public Class getValueType() throws EvaluationException {
		return ast.getValueInternal(new ExpressionState(new StandardEvaluationContext(), configuration)).getTypeDescriptor().getType();
	}

	public Class getValueType(EvaluationContext context) throws EvaluationException {
		Assert.notNull(context, "The EvaluationContext is required");
		ExpressionState eState = new ExpressionState(context, configuration);
		TypeDescriptor typeDescriptor = ast.getValueInternal(eState).getTypeDescriptor();
		return typeDescriptor.getType();
	}

	public TypeDescriptor getValueTypeDescriptor() throws EvaluationException {
		return ast.getValueInternal(new ExpressionState(new StandardEvaluationContext(), configuration)).getTypeDescriptor();
	}
	
	public TypeDescriptor getValueTypeDescriptor(EvaluationContext context) throws EvaluationException {
		Assert.notNull(context, "The EvaluationContext is required");
		ExpressionState eState = new ExpressionState(context, configuration);
		TypeDescriptor typeDescriptor = ast.getValueInternal(eState).getTypeDescriptor();
		return typeDescriptor;
	}
	
	public String getExpressionString() {
		return expression;
	}

	public boolean isWritable(EvaluationContext context) throws EvaluationException {
		Assert.notNull(context, "The EvaluationContext is required");		
		return ast.isWritable(new ExpressionState(context, configuration));
	}

	public void setValue(EvaluationContext context, Object value) throws EvaluationException {
		Assert.notNull(context, "The EvaluationContext is required");		
		ast.setValue(new ExpressionState(context, configuration), value);
	}
	
	// impl only

	/**
	 * @return return the Abstract Syntax Tree for the expression
	 */
	public SpelNode getAST() {
		return ast;
	}

	/**
	 * Produce a string representation of the Abstract Syntax Tree for the expression, this should ideally look like the
	 * input expression, but properly formatted since any unnecessary whitespace will have been discarded during the
	 * parse of the expression.
	 * 
	 * @return the string representation of the AST
	 */
	public String toStringAST() {
		return ast.toStringAST();
	}

}
