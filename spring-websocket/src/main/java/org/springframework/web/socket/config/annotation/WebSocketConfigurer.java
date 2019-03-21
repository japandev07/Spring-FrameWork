/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.socket.config.annotation;

import org.springframework.web.socket.WebSocketHandler;

/**
 * Defines callback methods to configure the WebSocket request handling
 * via {@link org.springframework.web.socket.config.annotation.EnableWebSocket @EnableWebSocket}.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public interface WebSocketConfigurer {

	/**
	 * Register {@link WebSocketHandler WebSocketHandlers} including SockJS fallback options if desired.
	 */
	void registerWebSocketHandlers(WebSocketHandlerRegistry registry);

}
