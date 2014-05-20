/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

/**
 * A convenient base class for a {@code ResponseBodyInterceptor} to instruct the
 * {@link org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
 * MappingJackson2HttpMessageConverter} to serialize with JSONP formatting.
 *
 * <p>Sub-classes must specify the query parameter name(s) to check for the name
 * of the JSONP callback function.
 *
 * <p>Sub-classes are likely to be annotated with the {@code @ControllerAdvice}
 * annotation and auto-detected or otherwise must be registered directly with the
 * {@code RequestMappingHandlerAdapter} and {@code ExceptionHandlerExceptionResolver}.
 *
 * @author Rossen Stoyanchev
 * @since 4.1
 */
public abstract class AbstractJsonpResponseBodyInterceptor extends AbstractMappingJacksonResponseBodyInterceptor {

	private final String[] jsonpQueryParamNames;


	protected AbstractJsonpResponseBodyInterceptor(Collection<String> queryParamNames) {
		Assert.isTrue(!CollectionUtils.isEmpty(queryParamNames), "At least one query param name is required");
		this.jsonpQueryParamNames = queryParamNames.toArray(new String[queryParamNames.size()]);
	}


	@Override
	protected void beforeBodyWriteInternal(MappingJacksonValue bodyContainer, MediaType contentType,
			MethodParameter returnType, ServerHttpRequest request, ServerHttpResponse response) {

		HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

		for (String name : this.jsonpQueryParamNames) {
			String value = servletRequest.getParameter(name);
			if (value != null) {
				MediaType contentTypeToUse = getContentType(contentType, request, response);
				response.getHeaders().setContentType(contentTypeToUse);
				bodyContainer.setJsonpFunction(value);
				return;
			}
		}
	}

	/**
	 * Return the content type to set the response to.
	 * This implementation always returns "application/javascript".
	 * @param contentType the content type selected through content negotiation
	 * @param request the current request
	 * @param response the current response
	 * @return the content type to set the response to
	 */
	protected MediaType getContentType(MediaType contentType, ServerHttpRequest request, ServerHttpResponse response) {
		return new MediaType("application", "javascript");
	}

}
