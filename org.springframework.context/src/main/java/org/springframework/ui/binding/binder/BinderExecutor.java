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

import org.springframework.ui.binding.config.BindingRuleConfiguration;

/**
 * A SPI interface that lets you configure a model binder, then execute it.
 * Hides details about the source of binder source values.
 * @author Keith Donald
 * @since 3.0
 * @param <M> the type of model to bind to
 */
public interface BinderExecutor<M> {
	
	/**
	 * Configure the model object to bind to.
	 * @param model the model
	 */
	void setModel(M model);
	
	/**
	 * Add a binding rule for the model property.
	 * @param property the model property
	 * @return a builder API for configuring the rule
	 */
	BindingRuleConfiguration bindingRule(String property);

	// TODO allow injection of pre-created BindingRules

	/**
	 * Execute the bind operation.
	 * @return the binding results
	 */
	BindingResults bind();

	// TODO return validation results
	void validate();
	
	/**
	 * The model that was bound to.
	 */
	M getModel();
	
}
