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
package org.springframework.ui.binding.support;

import org.springframework.ui.format.Formatter;

/**
 * A fluent interface for configuring a newly added binding to a property path.
 * @author Keith Donald
 * @see GenericBinder#addBinding(String)
 */
public interface BindingConfiguration {
	
	/**
	 * Set the Formatter to use to format bound property values.
	 * If a collection property, the formatter is used to format collection element values.
	 * Default is null.
	 */
	BindingConfiguration formatWith(Formatter<?> formatter);

	/**
	 * Mark the binding as required.
	 * A required binding will generate an exception if no sourceValue is provided to a bind invocation.
	 * This attribute is used to detect client configuration errors.
	 * It is not intended to be used as a user data validation constraint.
	 * Examples:
	 * <pre>
	 * name=required - will generate an exception if 'name' is not contained in the source values map.
	 * addresses=required - will generate an exception if 'addresses[n]' is not contained in the source value map for at least one n.
	 * addresses.city=required - will generate an exception if 'addresses[n].city' is not present in the source values map, for every address[n].
	 * </pre>
	 */
	BindingConfiguration required();
}