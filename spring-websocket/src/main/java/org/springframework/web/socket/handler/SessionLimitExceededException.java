/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.socket.handler;

import org.springframework.lang.Nullable;
import org.springframework.web.socket.CloseStatus;

/**
 * Raised when a WebSocket session has exceeded limits it has been configured
 * for, e.g. timeout, buffer size, etc.
 *
 * @author Rossen Stoyanchev
 * @since 4.0.3
 */
@SuppressWarnings("serial")
public class SessionLimitExceededException extends RuntimeException {

	private final CloseStatus status;


	public SessionLimitExceededException(String message, @Nullable CloseStatus status) {
		super(message);
		this.status = (status != null ? status : CloseStatus.NO_STATUS_CODE);
	}


	public CloseStatus getStatus() {
		return this.status;
	}

}
