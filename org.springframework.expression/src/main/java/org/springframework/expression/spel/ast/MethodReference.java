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

import java.util.List;

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;

/**
 * @author Andy Clement
 * @author Juergen Hoeller
 * @since 3.0
 */
public class MethodReference extends SpelNodeImpl {

	private final String name;

	private volatile MethodExecutor cachedExecutor;
	private final boolean nullSafe;

	public MethodReference(boolean nullSafe, String methodName, int pos, SpelNodeImpl... arguments) {
		super(pos,arguments);
		name = methodName;
		this.nullSafe = nullSafe;
	}


	@Override
	public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
		TypedValue currentContext = state.getActiveContextObject();
		Object[] arguments = new Object[getChildCount()];
		for (int i = 0; i < arguments.length; i++) {
			// Make the root object the active context again for evaluating the parameter
			// expressions
			try {
				state.pushActiveContextObject(state.getRootContextObject());
				arguments[i] = children[i].getValueInternal(state).getValue();
			} finally {
				state.popActiveContextObject();	
			}
		}
		if (currentContext.getValue() == null) {
			if (nullSafe) {
				return TypedValue.NULL;
			} else {
				throw new SpelEvaluationException(getStartPosition(), SpelMessage.METHOD_CALL_ON_NULL_OBJECT_NOT_ALLOWED,
						FormatHelper.formatMethodForMessage(name, getTypes(arguments)));
			}
		}

		MethodExecutor executorToUse = this.cachedExecutor;
		if (executorToUse != null) {
			try {
				return executorToUse.execute(
						state.getEvaluationContext(), state.getActiveContextObject().getValue(), arguments);
			}
			catch (AccessException ae) {
				// Two reasons this can occur:
				// 1. the method invoked actually threw a real exception
				// 2. the method invoked was not passed the arguments it expected and has become 'stale'
				
				// In the first case we should not retry, in the second case we should see if there is a 
				// better suited method.
				
				// To determine which situation it is, the AccessException will contain a cause - this
				// will be the exception thrown by the reflective invocation.  Inside this exception there
				// may or may not be a root cause.  If there is a root cause it is a user created exception.
				// If there is no root cause it was a reflective invocation problem.
				
				Throwable causeOfAccessException = ae.getCause();
				Throwable rootCause = (causeOfAccessException==null?null:causeOfAccessException.getCause());
				if (rootCause!=null) {
					// User exception was the root cause - exit now
					throw new SpelEvaluationException( getStartPosition(), rootCause, SpelMessage.EXCEPTION_DURING_METHOD_INVOCATION,
							this.name, state.getActiveContextObject().getValue().getClass().getName(), rootCause.getMessage());
				}
				
				// at this point we know it wasn't a user problem so worth a retry if a better candidate can be found
				this.cachedExecutor = null;
			}
		}

		// either there was no accessor or it no longer existed
		executorToUse = findAccessorForMethod(this.name, getTypes(arguments), state);
		this.cachedExecutor = executorToUse;
		try {
			return executorToUse.execute(
					state.getEvaluationContext(), state.getActiveContextObject().getValue(), arguments);
		} catch (AccessException ae) {
			throw new SpelEvaluationException( getStartPosition(), ae, SpelMessage.EXCEPTION_DURING_METHOD_INVOCATION,
					this.name, state.getActiveContextObject().getValue().getClass().getName(), ae.getMessage());
		}
	}

	private Class<?>[] getTypes(Object... arguments) {
		Class<?>[] argumentTypes = new Class[arguments.length];
		for (int i = 0; i < arguments.length; i++) {
			argumentTypes[i] = (arguments[i]==null?null:arguments[i].getClass());
		}
		return argumentTypes;
	}

	@Override
	public String toStringAST() {
		StringBuilder sb = new StringBuilder();
		sb.append(name).append("(");
		for (int i = 0; i < getChildCount(); i++) {
			if (i > 0)
				sb.append(",");
			sb.append(getChild(i).toStringAST());
		}
		sb.append(")");
		return sb.toString();
	}

	private MethodExecutor findAccessorForMethod(String name, Class<?>[] argumentTypes, ExpressionState state)
			throws SpelEvaluationException {

		TypedValue context = state.getActiveContextObject();
		Object contextObject = context.getValue();
		EvaluationContext eContext = state.getEvaluationContext();

		List<MethodResolver> mResolvers = eContext.getMethodResolvers();
		if (mResolvers != null) {
			for (MethodResolver methodResolver : mResolvers) {
				try {
					MethodExecutor cEx = methodResolver.resolve(
							state.getEvaluationContext(), contextObject, name, argumentTypes);
					if (cEx != null) {
						return cEx;
					}
				}
				catch (AccessException ex) {
					throw new SpelEvaluationException(getStartPosition(),ex, SpelMessage.PROBLEM_LOCATING_METHOD, name, contextObject.getClass());
				}
			}
		}
		throw new SpelEvaluationException(getStartPosition(),SpelMessage.METHOD_NOT_FOUND, FormatHelper.formatMethodForMessage(name, argumentTypes),
				FormatHelper.formatClassNameForMessage(contextObject instanceof Class ? ((Class<?>) contextObject) : contextObject.getClass()));
	}

}
