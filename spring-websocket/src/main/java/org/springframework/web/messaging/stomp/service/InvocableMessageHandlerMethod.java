/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.messaging.stomp.service;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import org.springframework.core.GenericTypeResolver;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.messaging.stomp.StompMessage;
import org.springframework.web.method.HandlerMethod;

/**
 * Invokes the handler method for a given message after resolving
 * its method argument values through registered {@link MessageMethodArgumentResolver}s.
 * <p>
 * Argument resolution often requires a {@link WebDataBinder} for data binding or for type
 * conversion. Use the {@link #setDataBinderFactory(WebDataBinderFactory)} property to
 * supply a binder factory to pass to argument resolvers.
 * <p>
 * Use {@link #setMessageMethodArgumentResolvers(MessageMethodArgumentResolverComposite)}
 * to customize the list of argument resolvers.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class InvocableMessageHandlerMethod extends HandlerMethod {

	private MessageMethodArgumentResolverComposite argumentResolvers = new MessageMethodArgumentResolverComposite();

	private ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();


	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public InvocableMessageHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}

	/**
	 * Constructs a new handler method with the given bean instance, method name and
	 * parameters.
	 *
	 * @param bean the object bean
	 * @param methodName the method name
	 * @param parameterTypes the method parameter types
	 * @throws NoSuchMethodException when the method cannot be found
	 */
	public InvocableMessageHandlerMethod(
			Object bean, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		super(bean, methodName, parameterTypes);
	}

	/**
	 * Set {@link MessageMethodArgumentResolver}s to use to use for resolving method
	 * argument values.
	 */
	public void setMessageMethodArgumentResolvers(MessageMethodArgumentResolverComposite argumentResolvers) {
		this.argumentResolvers = argumentResolvers;
	}

	/**
	 * Set the ParameterNameDiscoverer for resolving parameter names when needed (e.g.
	 * default request attribute name).
	 * <p>
	 * Default is an
	 * {@link org.springframework.core.LocalVariableTableParameterNameDiscoverer}
	 * instance.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * TODO
	 *
	 * @exception Exception raised if no suitable argument resolver can be found, or the
	 *            method raised an exception
	 */
	public final Object invoke(StompMessage message, Object replyTo) throws Exception {

		Object[] args = getMethodArgumentValues(message, replyTo);

		if (logger.isTraceEnabled()) {
			StringBuilder builder = new StringBuilder("Invoking [");
			builder.append(this.getMethod().getName()).append("] method with arguments ");
			builder.append(Arrays.asList(args));
			logger.trace(builder.toString());
		}

		Object returnValue = invoke(args);

		if (logger.isTraceEnabled()) {
			logger.trace("Method [" + this.getMethod().getName() + "] returned [" + returnValue + "]");
		}

		return returnValue;
	}

	/**
	 * Get the method argument values for the current request.
	 */
	private Object[] getMethodArgumentValues(StompMessage message, Object replyTo) throws Exception {

		MethodParameter[] parameters = getMethodParameters();
		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter parameter = parameters[i];
			parameter.initParameterNameDiscovery(parameterNameDiscoverer);
			GenericTypeResolver.resolveParameterType(parameter, getBean().getClass());

			args[i] = resolveProvidedArgument(parameter);
			if (args[i] != null) {
				continue;
			}

			if (this.argumentResolvers.supportsParameter(parameter)) {
				try {
					args[i] = this.argumentResolvers.resolveArgument(parameter, message, replyTo);
					continue;
				} catch (Exception ex) {
					if (logger.isTraceEnabled()) {
						logger.trace(getArgumentResolutionErrorMessage("Error resolving argument", i), ex);
					}
					throw ex;
				}
			}

			if (args[i] == null) {
				String msg = getArgumentResolutionErrorMessage("No suitable resolver for argument", i);
				throw new IllegalStateException(msg);
			}
		}
		return args;
	}

	private String getArgumentResolutionErrorMessage(String message, int index) {
		MethodParameter param = getMethodParameters()[index];
		message += " [" + index + "] [type=" + param.getParameterType().getName() + "]";
		return getDetailedErrorMessage(message);
	}

	/**
	 * Adds HandlerMethod details such as the controller type and method signature to the given error message.
	 * @param message error message to append the HandlerMethod details to
	 */
	protected String getDetailedErrorMessage(String message) {
		StringBuilder sb = new StringBuilder(message).append("\n");
		sb.append("HandlerMethod details: \n");
		sb.append("Controller [").append(getBeanType().getName()).append("]\n");
		sb.append("Method [").append(getBridgedMethod().toGenericString()).append("]\n");
		return sb.toString();
	}

	/**
	 * Attempt to resolve a method parameter from the list of provided argument values.
	 */
	private Object resolveProvidedArgument(MethodParameter parameter, Object... providedArgs) {
		if (providedArgs == null) {
			return null;
		}
		for (Object providedArg : providedArgs) {
			if (parameter.getParameterType().isInstance(providedArg)) {
				return providedArg;
			}
		}
		return null;
	}

	/**
	 * Invoke the handler method with the given argument values.
	 */
	private Object invoke(Object... args) throws Exception {
		ReflectionUtils.makeAccessible(this.getBridgedMethod());
		try {
			return getBridgedMethod().invoke(getBean(), args);
		}
		catch (IllegalArgumentException e) {
			String msg = getInvocationErrorMessage(e.getMessage(), args);
			throw new IllegalArgumentException(msg, e);
		}
		catch (InvocationTargetException e) {
			// Unwrap for HandlerExceptionResolvers ...
			Throwable targetException = e.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			}
			else if (targetException instanceof Error) {
				throw (Error) targetException;
			}
			else if (targetException instanceof Exception) {
				throw (Exception) targetException;
			}
			else {
				String msg = getInvocationErrorMessage("Failed to invoke controller method", args);
				throw new IllegalStateException(msg, targetException);
			}
		}
	}

	private String getInvocationErrorMessage(String message, Object[] resolvedArgs) {
		StringBuilder sb = new StringBuilder(getDetailedErrorMessage(message));
		sb.append("Resolved arguments: \n");
		for (int i=0; i < resolvedArgs.length; i++) {
			sb.append("[").append(i).append("] ");
			if (resolvedArgs[i] == null) {
				sb.append("[null] \n");
			}
			else {
				sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
				sb.append("[value=").append(resolvedArgs[i]).append("]\n");
			}
		}
		return sb.toString();
	}

}
