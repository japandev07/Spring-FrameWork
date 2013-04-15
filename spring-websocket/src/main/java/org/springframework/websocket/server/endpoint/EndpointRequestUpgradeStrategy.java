/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.websocket.server.endpoint;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;


/**
 * A strategy for performing the actual request upgrade after the handshake checks have
 * passed, encapsulating runtime-specific steps of the handshake.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public interface EndpointRequestUpgradeStrategy {

	void upgrade(ServerHttpRequest request, ServerHttpResponse response, String protocol,
			EndpointRegistration registration) throws Exception;

}
