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

package org.springframework.web.socket.handler;

/**
 * Raised when a WebSocket session has exceeded limits it has been configured
 * for, e.g. timeout, buffer size, etc.
 *
 * @author Rossen Stoyanchev
 * @since 3.0.4
 */
@SuppressWarnings("serial")
public class SessionLimitExceededException extends RuntimeException {


	public SessionLimitExceededException(String message) {
		super(message);
	}

}
