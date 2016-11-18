/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.reactive.result.view;

import java.util.Locale;
import java.util.function.Function;

import reactor.core.publisher.Mono;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.PatternMatchUtils;


/**
 * A {@link ViewResolver} that allow direct resolution of symbolic view names
 * to URLs without explicit mapping definition. This is useful if symbolic names
 * match the names of view resources in a straightforward manner (i.e. the
 * symbolic name is the unique part of the resource's filename), without the need
 * for a dedicated mapping to be defined for each view.
 *
 * <p>Supports {@link AbstractUrlBasedView} subclasses like
 * {@link org.springframework.web.reactive.result.view.freemarker.FreeMarkerView}.
 * The view class for all views generated by this resolver can be specified
 * via the "viewClass" property.
 *
 * <p>View names can either be resource URLs themselves, or get augmented by a
 * specified prefix and/or suffix. Exporting an attribute that holds the
 * RequestContext to all views is explicitly supported.
 *
 * <p>Example: prefix="templates/", suffix=".ftl", viewname="test" ->
 * "templates/test.ftl"
 *
 * <p>As a special feature, redirect URLs can be specified via the "redirect:"
 * prefix. E.g.: "redirect:myAction" will trigger a redirect to the given
 * URL, rather than resolution as standard view name. This is typically used
 * for redirecting to a controller URL after finishing a form workflow.
 *
 * <p>Note: This class does not support localized resolution, i.e. resolving
 * a symbolic view name to different resources depending on the current locale.
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class UrlBasedViewResolver extends ViewResolverSupport implements ViewResolver, InitializingBean {

	/**
	 * Prefix for special view names that specify a redirect URL (usually
	 * to a controller after a form has been submitted and processed).
	 * Such view names will not be resolved in the configured default
	 * way but rather be treated as special shortcut.
	 */
	public static final String REDIRECT_URL_PREFIX = "redirect:";


	private Class<?> viewClass;

	private String prefix = "";

	private String suffix = "";

	private String[] viewNames;

	private Function<String, RedirectView> redirectViewProvider = url -> new RedirectView(url);


	/**
	 * Set the view class to instantiate through {@link #createUrlBasedView(String)}.
	 * @param viewClass a class that is assignable to the required view class
	 * which by default is AbstractUrlBasedView.
	 */
	public void setViewClass(Class<?> viewClass) {
		if (viewClass == null || !requiredViewClass().isAssignableFrom(viewClass)) {
			String name = (viewClass != null ? viewClass.getName() : null);
			throw new IllegalArgumentException("Given view class [" + name + "] " +
					"is not of type [" + requiredViewClass().getName() + "]");
		}
		this.viewClass = viewClass;
	}

	/**
	 * Return the view class to be used to create views.
	 */
	protected Class<?> getViewClass() {
		return this.viewClass;
	}

	/**
	 * Return the required type of view for this resolver.
	 * This implementation returns {@link AbstractUrlBasedView}.
	 * @see AbstractUrlBasedView
	 */
	protected Class<?> requiredViewClass() {
		return AbstractUrlBasedView.class;
	}

	/**
	 * Set the prefix that gets prepended to view names when building a URL.
	 */
	public void setPrefix(String prefix) {
		this.prefix = (prefix != null ? prefix : "");
	}

	/**
	 * Return the prefix that gets prepended to view names when building a URL.
	 */
	protected String getPrefix() {
		return this.prefix;
	}

	/**
	 * Set the suffix that gets appended to view names when building a URL.
	 */
	public void setSuffix(String suffix) {
		this.suffix = (suffix != null ? suffix : "");
	}

	/**
	 * Return the suffix that gets appended to view names when building a URL.
	 */
	protected String getSuffix() {
		return this.suffix;
	}

	/**
	 * Set the view names (or name patterns) that can be handled by this
	 * {@link ViewResolver}. View names can contain simple wildcards such that
	 * 'my*', '*Report' and '*Repo*' will all match the view name 'myReport'.
	 * @see #canHandle
	 */
	public void setViewNames(String... viewNames) {
		this.viewNames = viewNames;
	}

	/**
	 * Return the view names (or name patterns) that can be handled by this
	 * {@link ViewResolver}.
	 */
	protected String[] getViewNames() {
		return this.viewNames;
	}

	/**
	 * URL based {@link RedirectView} provider which can be used to provide, for example,
	 * redirect views with a custom default status code.
	 */
	public void setRedirectViewProvider(Function<String, RedirectView> redirectViewProvider) {
		this.redirectViewProvider = redirectViewProvider;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (getViewClass() == null) {
			throw new IllegalArgumentException("Property 'viewClass' is required");
		}
	}

	@Override
	public Mono<View> resolveViewName(String viewName, Locale locale) {
		if (!canHandle(viewName, locale)) {
			return Mono.empty();
		}
		AbstractUrlBasedView urlBasedView;
		if (viewName.startsWith(REDIRECT_URL_PREFIX)) {
			String redirectUrl = viewName.substring(REDIRECT_URL_PREFIX.length());
			urlBasedView = this.redirectViewProvider.apply(redirectUrl);
		}
		else {
			urlBasedView = createUrlBasedView(viewName);
		}
		View view = applyLifecycleMethods(viewName, urlBasedView);
		try {
			return (urlBasedView.checkResourceExists(locale) ? Mono.just(view) : Mono.empty());
		}
		catch (Exception ex) {
			return Mono.error(ex);
		}
	}

	/**
	 * Indicates whether or not this {@link ViewResolver} can handle the
	 * supplied view name. If not, an empty result is returned. The default
	 * implementation checks against the configured {@link #setViewNames
	 * view names}.
	 * @param viewName the name of the view to retrieve
	 * @param locale the Locale to retrieve the view for
	 * @return whether this resolver applies to the specified view
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	protected boolean canHandle(String viewName, Locale locale) {
		String[] viewNames = getViewNames();
		return (viewNames == null || PatternMatchUtils.simpleMatch(viewNames, viewName));
	}

	/**
	 * Creates a new View instance of the specified view class and configures it.
	 * Does <i>not</i> perform any lookup for pre-defined View instances.
	 * <p>Spring lifecycle methods as defined by the bean container do not have to
	 * be called here; those will be applied by the {@code loadView} method
	 * after this method returns.
	 * <p>Subclasses will typically call {@code super.buildView(viewName)}
	 * first, before setting further properties themselves. {@code loadView}
	 * will then apply Spring lifecycle methods at the end of this process.
	 * @param viewName the name of the view to build
	 * @return the View instance
	 */
	protected AbstractUrlBasedView createUrlBasedView(String viewName) {
		AbstractUrlBasedView view = (AbstractUrlBasedView) BeanUtils.instantiateClass(getViewClass());
		view.setSupportedMediaTypes(getSupportedMediaTypes());
		view.setDefaultCharset(getDefaultCharset());
		view.setUrl(getPrefix() + viewName + getSuffix());
		return view;
	}

	private View applyLifecycleMethods(String viewName, AbstractView view) {
		return (View) getApplicationContext().getAutowireCapableBeanFactory().initializeBean(view, viewName);
	}

}
