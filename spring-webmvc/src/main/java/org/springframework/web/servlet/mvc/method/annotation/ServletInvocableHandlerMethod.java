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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import org.springframework.http.HttpStatus;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.View;
import org.springframework.web.util.NestedServletException;

/**
 * Extends {@link InvocableHandlerMethod} with the ability to handle return
 * values through a registered {@link HandlerMethodReturnValueHandler} and
 * also supports setting the response status based on a method-level
 * {@code @ResponseStatus} annotation.
 *
 * <p>A {@code null} return value (including void) may be interpreted as the
 * end of request processing in combination with a {@code @ResponseStatus}
 * annotation, a not-modified check condition
 * (see {@link ServletWebRequest#checkNotModified(long)}), or
 * a method argument that provides access to the response stream.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ServletInvocableHandlerMethod extends InvocableHandlerMethod {

	private HttpStatus responseStatus;

	private String responseReason;

	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;


	/**
	 * Creates an instance from the given handler and method.
	 */
	public ServletInvocableHandlerMethod(Object handler, Method method) {
		super(handler, method);
		initResponseStatus();
	}

	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public ServletInvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
		initResponseStatus();
	}

	private void initResponseStatus() {
		ResponseStatus annot = getMethodAnnotation(ResponseStatus.class);
		if (annot != null) {
			this.responseStatus = annot.value();
			this.responseReason = annot.reason();
		}
	}

	/**
	 * Register {@link HandlerMethodReturnValueHandler} instances to use to
	 * handle return values.
	 */
	public void setHandlerMethodReturnValueHandlers(HandlerMethodReturnValueHandlerComposite returnValueHandlers) {
		this.returnValueHandlers = returnValueHandlers;
	}

	/**
	 * Invokes the method and handles the return value through a registered
	 * {@link HandlerMethodReturnValueHandler}.
	 *
	 * @param webRequest the current request
	 * @param mavContainer the ModelAndViewContainer for this request
	 * @param providedArgs "given" arguments matched by type, not resolved
	 */
	public final void invokeAndHandle(ServletWebRequest webRequest,
			ModelAndViewContainer mavContainer, Object... providedArgs) throws Exception {

		Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);

		setResponseStatus(webRequest);

		if (returnValue == null) {
			if (isRequestNotModified(webRequest) || hasResponseStatus() || mavContainer.isRequestHandled()) {
				mavContainer.setRequestHandled(true);
				return;
			}
		}
		else if (StringUtils.hasText(this.responseReason)) {
			mavContainer.setRequestHandled(true);
			return;
		}

		mavContainer.setRequestHandled(false);

		try {
			this.returnValueHandlers.handleReturnValue(returnValue, getReturnValueType(returnValue), mavContainer, webRequest);
		}
		catch (Exception ex) {
			if (logger.isTraceEnabled()) {
				logger.trace(getReturnValueHandlingErrorMessage("Error handling return value", returnValue), ex);
			}
			throw ex;
		}
	}

	/**
	 * Set the response status according to the {@link ResponseStatus} annotation.
	 */
	private void setResponseStatus(ServletWebRequest webRequest) throws IOException {
		if (this.responseStatus == null) {
			return;
		}

		if (StringUtils.hasText(this.responseReason)) {
			webRequest.getResponse().sendError(this.responseStatus.value(), this.responseReason);
		}
		else {
			webRequest.getResponse().setStatus(this.responseStatus.value());
		}

		// to be picked up by the RedirectView
		webRequest.getRequest().setAttribute(View.RESPONSE_STATUS_ATTRIBUTE, this.responseStatus);
	}

	/**
	 * Does the given request qualify as "not modified"?
	 * @see ServletWebRequest#checkNotModified(long)
	 * @see ServletWebRequest#checkNotModified(String)
	 */
	private boolean isRequestNotModified(ServletWebRequest webRequest) {
		return webRequest.isNotModified();
	}

	/**
	 * Does this method have the response status instruction?
	 */
	private boolean hasResponseStatus() {
		return responseStatus != null;
	}

	private String getReturnValueHandlingErrorMessage(String message, Object returnValue) {
		StringBuilder sb = new StringBuilder(message);
		if (returnValue != null) {
			sb.append(" [type=" + returnValue.getClass().getName() + "] ");
		}
		sb.append("[value=" + returnValue + "]");
		return getDetailedErrorMessage(sb.toString());
	}

	/**
	 * Create a ServletInvocableHandlerMethod that will return the given value from an
	 * async operation instead of invoking the controller method again. The async result
	 * value is then either processed as if the controller method returned it or an
	 * exception is raised if the async result value itself is an Exception.
	 */
	ServletInvocableHandlerMethod wrapConcurrentResult(final Object result) {

		return new CallableHandlerMethod(new Callable<Object>() {

			@Override
			public Object call() throws Exception {
				if (result instanceof Exception) {
					throw (Exception) result;
				}
				else if (result instanceof Throwable) {
					throw new NestedServletException("Async processing failed", (Throwable) result);
				}
				return result;
			}
		});
	}


	/**
	 * A sub-class of {@link HandlerMethod} that invokes the given {@link Callable}
	 * instead of the target controller method. This is useful for resuming processing
	 * with the result of an async operation. The goal is to process the value returned
	 * from the Callable as if it was returned by the target controller method, i.e.
	 * taking into consideration both method and type-level controller annotations (e.g.
	 * {@code @ResponseBody}, {@code @ResponseStatus}, etc).
	 */
	private class CallableHandlerMethod extends ServletInvocableHandlerMethod {

		public CallableHandlerMethod(Callable<?> callable) {
			super(callable, ClassUtils.getMethod(callable.getClass(), "call"));
			this.setHandlerMethodReturnValueHandlers(ServletInvocableHandlerMethod.this.returnValueHandlers);
		}

		/**
		 * Bridge to type-level annotations of the target controller method.
		 */
		@Override
		public Class<?> getBeanType() {
			return ServletInvocableHandlerMethod.this.getBeanType();
		}

		/**
		 * Bridge to method-level annotations of the target controller method.
		 */
		@Override
		public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.getMethodAnnotation(annotationType);
		}
	}

}
