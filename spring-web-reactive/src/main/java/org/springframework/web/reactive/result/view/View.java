/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.reactive.result.view;

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.server.ServerWebExchange;

/**
 * Contract to render {@link HandlerResult} to the HTTP response.
 *
 * <p>In contrast to an {@link org.springframework.core.codec.Encoder Encoder}
 * which is a singleton and encodes any object of a given type, a {@code View}
 * is typically selected by name and resolved using a {@link ViewResolver}
 * which may for example match it to an HTML template. Furthermore a {@code View}
 * may render based on multiple attributes contained in the model.
 *
 * <p>A {@code View} can also choose to select an attribute from the model use
 * any existing {@code Encoder} to render alternate media types.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface View {

	/**
	 * Return the list of media types this View supports, or an empty list.
	 */
	List<MediaType> getSupportedMediaTypes();

	/**
	 * Render the view based on the given {@link HandlerResult}. Implementations
	 * can access and use the model or only a specific attribute in it.
	 * @param model Map with name Strings as keys and corresponding model
	 * objects as values (Map can also be {@code null} in case of empty model)
	 * @param contentType the content type selected to render with which should
	 * match one of the {@link #getSupportedMediaTypes() supported media types}.
	 * @param exchange the current exchange
	 * @return {@code Mono} to represent when and if rendering succeeds
	 */
	Mono<Void> render(Map<String, ?> model, MediaType contentType, ServerWebExchange exchange);

}
