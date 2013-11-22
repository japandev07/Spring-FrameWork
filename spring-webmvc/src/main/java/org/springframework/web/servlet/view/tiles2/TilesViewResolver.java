/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.web.servlet.view.tiles2;

import org.springframework.web.servlet.view.UrlBasedViewResolver;

/**
 * Convenience subclass of {@link org.springframework.web.servlet.view.UrlBasedViewResolver}
 * that supports {@link TilesView} (i.e. Tiles definitions) and custom subclasses of it.
 *
 * <p>The view class for all views generated by this resolver can be specified
 * via the "viewClass" property. See UrlBasedViewResolver's javadoc for details.
 *
 * <p><b>Note:</b> When chaining ViewResolvers, a TilesViewResolver will
 * check for the existence of the specified template resources and only return
 * a non-null View object if the template was actually found.
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see #setViewClass
 * @see #setPrefix
 * @see #setSuffix
 * @see #setRequestContextAttribute
 * @see TilesView
 */
public class TilesViewResolver extends UrlBasedViewResolver {

	public TilesViewResolver() {
		setViewClass(requiredViewClass());
	}

	/**
	 * Requires {@link TilesView}.
	 */
	@Override
	protected Class<?> requiredViewClass() {
		return TilesView.class;
	}

}
