/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.oxm;

/**
 * Exception thrown on marshalling validation failure.
 *
 * @author Arjen Poutsma
 * @since 3.0
 */
@SuppressWarnings("serial")
public class ValidationFailureException extends XmlMappingException {

	/**
	 * Construct a {@code ValidationFailureException} with the specified detail message.
	 * @param msg the detail message
	 */
	public ValidationFailureException(String msg) {
		super(msg);
	}

	/**
	 * Construct a {@code ValidationFailureException} with the specified detail message
	 * and nested exception.
	 * @param msg the detail message
	 * @param cause the nested exception
	 */
	public ValidationFailureException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
