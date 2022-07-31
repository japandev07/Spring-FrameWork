/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.method.annotation;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Handler for return values of type {@link DeferredResult},
 * {@link org.springframework.util.concurrent.ListenableFuture}, and
 * {@link CompletionStage}.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public class DeferredResultMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	@SuppressWarnings("deprecation")
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		Class<?> type = returnType.getParameterType();
		return (DeferredResult.class.isAssignableFrom(type) ||
				org.springframework.util.concurrent.ListenableFuture.class.isAssignableFrom(type) ||
				CompletionStage.class.isAssignableFrom(type));
	}

	@SuppressWarnings("deprecation")
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		if (returnValue == null) {
			mavContainer.setRequestHandled(true);
			return;
		}

		DeferredResult<?> result;

		if (returnValue instanceof DeferredResult<?> deferredResult) {
			result = deferredResult;
		}
		else if (returnValue instanceof org.springframework.util.concurrent.ListenableFuture<?> listenableFuture) {
			result = adaptListenableFuture(listenableFuture);
		}
		else if (returnValue instanceof CompletionStage<?> completionStage) {
			result = adaptCompletionStage(completionStage);
		}
		else {
			// Should not happen...
			throw new IllegalStateException("Unexpected return value type: " + returnValue);
		}

		WebAsyncUtils.getAsyncManager(webRequest).startDeferredResultProcessing(result, mavContainer);
	}

	@SuppressWarnings("deprecation")
	private DeferredResult<Object> adaptListenableFuture(org.springframework.util.concurrent.ListenableFuture<?> future) {
		DeferredResult<Object> result = new DeferredResult<>();
		future.addCallback(new org.springframework.util.concurrent.ListenableFutureCallback<Object>() {
			@Override
			public void onSuccess(@Nullable Object value) {
				result.setResult(value);
			}
			@Override
			public void onFailure(Throwable ex) {
				result.setErrorResult(ex);
			}
		});
		return result;
	}

	private DeferredResult<Object> adaptCompletionStage(CompletionStage<?> future) {
		DeferredResult<Object> result = new DeferredResult<>();
		future.whenComplete((value, ex) -> {
			if (ex != null) {
				if (ex instanceof CompletionException && ex.getCause() != null) {
					ex = ex.getCause();
				}
				result.setErrorResult(ex);
			}
			else {
				result.setResult(value);
			}
		});
		return result;
	}

}
