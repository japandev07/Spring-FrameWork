/*
 * Copyright 2002-2005 the original author or authors.
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

package org.springframework.jca.cci;

import javax.resource.ResourceException;

import org.springframework.dao.InvalidDataAccessResourceUsageException;

/**
 * Exception thrown when the connector doesn't support a specific CCI operation.
 *
 * @author Juergen Hoeller
 * @since 1.2
 */
@SuppressWarnings("serial")
public class CciOperationNotSupportedException extends InvalidDataAccessResourceUsageException {

	/**
	 * Constructor for CciOperationNotSupportedException.
	 * @param msg message
	 * @param ex ResourceException root cause
	 */
	public CciOperationNotSupportedException(String msg, ResourceException ex) {
		super(msg, ex);
	}

}
