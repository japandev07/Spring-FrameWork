/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import java.util.List;
import java.util.Map;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.annotation.InitBinderMethodDataBinderFactory;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * An {@link InitBinderMethodDataBinderFactory} that creates a {@link ServletRequestDataBinder}. 
 * 
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ServletInitBinderMethodDataBinderFactory extends InitBinderMethodDataBinderFactory {

	/**
	 * Create an {@link ServletInitBinderMethodDataBinderFactory} instance.
	 * @param initBinderMethods init binder methods to use to initialize new data binders.  
	 * @param bindingInitializer a WebBindingInitializer to use to initialize created data binder instances.
	 */
	public ServletInitBinderMethodDataBinderFactory(List<InvocableHandlerMethod> initBinderMethods,
													WebBindingInitializer bindingInitializer) {
		super(initBinderMethods, bindingInitializer);
	}

	/**
	 * Creates a Servlet data binder.
	 */
	@Override
	protected WebDataBinder createBinderInstance(Object target, String objectName) {
		return new ServletRequestPathVarDataBinder(target, objectName);
	}

	/**
	 * Adds URI template variables to the map of request values used to do data binding. 
	 */
	private static class ServletRequestPathVarDataBinder extends ServletRequestDataBinder {

		public ServletRequestPathVarDataBinder(Object target, String objectName) {
			super(target, objectName);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void doBind(MutablePropertyValues mpvs) {
			RequestAttributes requestAttrs = RequestContextHolder.getRequestAttributes();
			if (requestAttrs != null) {
				String key = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
				int scope = RequestAttributes.SCOPE_REQUEST;
				Map<String, String> uriTemplateVars = (Map<String, String>) requestAttrs.getAttribute(key, scope);
				mpvs.addPropertyValues(uriTemplateVars);
			}
			super.doBind(mpvs);
		}
	}
	
}