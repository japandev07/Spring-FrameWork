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

package org.springframework.sockjs;

import org.springframework.websocket.CloseStatus;


/**
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class SockJsHandlerAdapter implements SockJsHandler {

	@Override
	public void afterConnectionEstablished(SockJsSession session) throws Exception {
	}

	@Override
	public void handleMessage(String message, SockJsSession session) throws Exception {
	}

	@Override
	public void handleError(Throwable exception, SockJsSession session) {
	}

	@Override
	public void afterConnectionClosed(CloseStatus status, SockJsSession session) {
	}

}
