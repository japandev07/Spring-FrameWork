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

package org.springframework.messaging.handler.annotation.support;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;

/**
 * Exception that indicates that a method argument has not the expected type.
 *
 * @author Stephane Nicoll
 * @since 4.0.3
 */
@SuppressWarnings({"serial", "deprecation"})
public class MethodArgumentTypeMismatchException extends AbstractMethodArgumentResolutionException {

	public MethodArgumentTypeMismatchException(Message<?> message, MethodParameter parameter, String description) {
		super(message, parameter, description);
	}

}
