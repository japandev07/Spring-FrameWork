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
package org.springframework.ui.binding;

import org.springframework.ui.alert.Alert;
import org.springframework.ui.alert.Severity;

/**
 * A binding between a source element and a model property.
 * @author Keith Donald
 * @since 3.0
 */
public interface Binding {

	/**
	 * The value to display in the UI.
	 * Is the formatted model value if {@link BindingStatus#CLEAN} or {@link BindingStatus#COMMITTED}.
	 * Is the formatted buffered value if {@link BindingStatus#DIRTY} or {@link BindingStatus#COMMIT_FAILURE}.
	 * Is the source value if {@link BindingStatus#INVALID_SOURCE_VALUE}.
	 */
	Object getValue();

	/**
	 * If this Binding is read-only.
	 * A read-only Binding cannot have source values applied and cannot be committed.
	 */
	boolean isReadOnly();
	
	/**
	 * Apply the source value to this binding.
	 * The source value is parsed and stored in the binding's value buffer.
	 * Sets to {@link BindingStatus#DIRTY} if succeeds.
	 * Sets to {@link BindingStatus#INVALID_SOURCE_VALUE} if fails.
	 * @param sourceValue
	 * @throws IllegalStateException if read only
	 */
	void applySourceValue(Object sourceValue);
	
	/**
	 * The current binding status.
	 * Initially {@link BindingStatus#CLEAN clean}.
	 * Is {@link BindingStatus#DIRTY} after applying a source value to the value buffer.
	 * Is {@link BindingStatus#COMMITTED} after successfully committing the buffered value.
	 * Is {@link BindingStatus#INVALID_SOURCE_VALUE} if a source value could not be applied.
	 * Is {@link BindingStatus#COMMIT_FAILURE} if a buffered value could not be committed.
	 */
	BindingStatus getStatus();
	
	/**
	 * An alert that communicates the details of a BindingStatus change to the user.
	 * Returns <code>null</code> if the BindingStatus has never changed.
	 * Returns a {@link Severity#INFO} Alert with code <code>bindSuccess</code> after a successful commit.
	 * Returns a {@link Severity#ERROR} Alert with code <code>typeMismatch</code> if the source value could not be converted to type required by the Model.
	 * Returns a {@link Severity#FATAL} Alert with code <code>internalError</code> if the buffered value could not be committed due to a unexpected runtime exception.
	 */
	Alert getStatusAlert();
	
	/**
	 * Commit the buffered value to the model.
	 * Sets to {@link BindingStatus#CLEAN} if succeeds.
	 * Sets to {@link BindingStatus#COMMIT_FAILURE} if fails.
	 * @throws IllegalStateException if not {@link BindingStatus#DIRTY} or read-only
	 */
	void commit();
	
	/**
	 * Access raw model values.
	 */
	Model getModel();
	
	/**
	 * Get a Binding to a nested property value.
	 * @param nestedProperty the nested property name, such as "foo"; should not be a property path like "foo.bar"
	 * @return the binding to the nested property
	 */
	Binding getBinding(String nestedProperty);

	/**
	 * If bound to an indexable Collection, either a {@link java.util.List} or an array.
	 */
	boolean isIndexable();

	/**
	 * If a List, get a Binding to a element in the List.
	 * @param index the element index
	 * @return the indexed binding
	 */
	Binding getIndexedBinding(int index);

	/**
	 * If bound to a {@link java.util.Map}.
	 */
	boolean isMap();

	/**
	 * If a Map, get a Binding to a value in the Map.
	 * @param key the map key
	 * @return the keyed binding
	 */
	Binding getKeyedBinding(Object key);

	/**
	 * Format a potential model value for display.
	 * If an indexable binding, expects the model value to be a potential collection element & uses the configured element formatter.
	 * If a map binding, expects the model value to be a potential map value & uses the configured map value formatter.
	 * @param potentialValue the potential value
	 * @return the formatted string
	 */
	String formatValue(Object potentialModelValue);
	
	/**
	 * For accessing the raw bound model object.
	 * @author Keith Donald
	 */
	public interface Model {
		
		/**
		 * Read the raw model value.
		 */
		Object getValue();
		
		/**
		 * The model value type.
		 */
		Class<?> getValueType();		

		/**
		 * Set the model value.
		 * @throws IllegalStateException if this binding is read-only
		 */
		void setValue(Object value);
	}
	
	/**
	 * The states of a Binding.
	 * @author Keith Donald
	 */
	public enum BindingStatus {
		
		/**
		 * Initial state: No value is buffered, and there is a direct channel to the model value.
		 */
		CLEAN,
		
		/**
		 * An invalid source value is applied.
		 */
		INVALID_SOURCE_VALUE,
		
		/**
		 * The binding buffer contains a valid value that has not been committed.
		 */
		DIRTY,

		/**
		 * The buffered value has been committed.
		 */
		COMMITTED,
		
		/**
		 * The buffered value failed to commit.
		 */
		COMMIT_FAILURE
	}
}