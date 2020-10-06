/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.web.reactive.result.view.freemarker;

import org.springframework.web.reactive.result.view.AbstractUrlBasedView;
import org.springframework.web.reactive.result.view.UrlBasedViewResolver;

/**
 * A {@code ViewResolver} for resolving {@link FreeMarkerView} instances, i.e.
 * FreeMarker templates and custom subclasses of it.
 *
 * <p>The view class for all views generated by this resolver can be specified
 * via the "viewClass" property. See {@link UrlBasedViewResolver} for details.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class FreeMarkerViewResolver extends UrlBasedViewResolver {

	/**
	 * Simple constructor.
	 */
	public FreeMarkerViewResolver() {
		setViewClass(requiredViewClass());
	}

	/**
	 * Convenience constructor with a prefix and suffix.
	 * @param suffix the suffix to prepend view names with
	 * @param prefix the prefix to prepend view names with
	 */
	public FreeMarkerViewResolver(String prefix, String suffix) {
		setViewClass(requiredViewClass());
		setPrefix(prefix);
		setSuffix(suffix);
	}


	/**
	 * Requires {@link FreeMarkerView}.
	 */
	@Override
	protected Class<?> requiredViewClass() {
		return FreeMarkerView.class;
	}

	@Override
	protected AbstractUrlBasedView instantiateView() {
		return (getViewClass() == FreeMarkerView.class ? new FreeMarkerView() : super.instantiateView());
	}

}
