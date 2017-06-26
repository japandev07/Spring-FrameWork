/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.context.request.async;

import org.springframework.web.context.request.NativeWebRequest;

/**
 * Registered at the end, after all other interceptors and
 * therefore invoked only if no other interceptor handles the error.
 *
 * @author Violeta Georgieva
 * @since 5.0
 */
public class ErrorDeferredResultProcessingInterceptor implements DeferredResultProcessingInterceptor {

	@Override
	public <T> boolean handleError(NativeWebRequest request, DeferredResult<T> result, Throwable t)
			throws Exception {

		result.setErrorResult(t);
		return false;
	}

}
