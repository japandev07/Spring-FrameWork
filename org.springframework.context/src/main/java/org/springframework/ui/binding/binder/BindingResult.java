/*
 * Copyright 2004-2009 the original author or authors.
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
package org.springframework.ui.binding.binder;

import org.springframework.ui.alert.Alert;

/**
 * The result of a bind operation.
 * @author Keith Donald
 * @since 3.0
 * @see Binder#bind(java.util.Map)
 */
public interface BindingResult {

	/**
	 * The name of the field this binding result is for.
	 * @see Binder#getNested(String)
	 */
	String getFieldName();

	/**
	 * The raw submitted value for which binding was attempted.
	 * If not a failure, this value was successfully bound to the model.
	 * @see #isFailure()
	 */
	Object getSubmittedValue();
	
	/**
	 * Indicates if the binding failed.
	 */
	boolean isFailure();

	/**
	 * Gets the alert for this binding result, appropriate for rendering the result to the user.
	 * An alert describing a successful binding will have info severity.
	 * An alert describing a failed binding will have either warning, error, or fatal severity.
	 */
	Alert getAlert();

}