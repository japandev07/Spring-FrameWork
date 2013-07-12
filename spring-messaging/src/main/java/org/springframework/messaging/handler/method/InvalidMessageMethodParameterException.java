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

package org.springframework.messaging.handler.method;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;


/**
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class InvalidMessageMethodParameterException extends MessagingException {

	private static final long serialVersionUID = -6905878930083523161L;

	private final MethodParameter parameter;


	public InvalidMessageMethodParameterException(Message<?> message, String description,
			MethodParameter parameter, Throwable cause) {
		super(message, description, cause);
		this.parameter = parameter;
	}

	public InvalidMessageMethodParameterException(Message<?> message, String description,
			MethodParameter parameter) {

		super(message, description);
		this.parameter = parameter;
	}


	public MethodParameter getParameter() {
		return this.parameter;
	}

}
