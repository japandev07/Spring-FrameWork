/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.web.method.support;

import java.util.Map;

import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.support.SimpleSessionStatus;

/**
 * Records model and view related decisions made by
 * {@link HandlerMethodArgumentResolver}s and
 * {@link HandlerMethodReturnValueHandler}s during the course of invocation of
 * a controller method.
 *
 * <p>The {@link #setRequestHandled} flag can be used to indicate the request
 * has been handled directly and view resolution is not required.
 *
 * <p>A default {@link Model} is automatically created at instantiation.
 * An alternate model instance may be provided via {@link #setRedirectModel}
 * for use in a redirect scenario. When {@link #setRedirectModelScenario} is set
 * to {@code true} signalling a redirect scenario, the {@link #getModel()}
 * returns the redirect model instead of the default model.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ModelAndViewContainer {

	private boolean ignoreDefaultModelOnRedirect = false;

	private Object view;

	private final ModelMap defaultModel = new BindingAwareModelMap();

	private ModelMap redirectModel;

	private boolean redirectModelScenario = false;

	private final SessionStatus sessionStatus = new SimpleSessionStatus();

	private boolean requestHandled = false;


	/**
	 * By default the content of the "default" model is used both during
	 * rendering and redirect scenarios. Alternatively controller methods
	 * can declare an argument of type {@code RedirectAttributes} and use
	 * it to provide attributes to prepare the redirect URL.
	 * <p>Setting this flag to {@code true} guarantees the "default" model is
	 * never used in a redirect scenario even if a RedirectAttributes argument
	 * is not declared. Setting it to {@code false} means the "default" model
	 * may be used in a redirect if the controller method doesn't declare a
	 * RedirectAttributes argument.
	 * <p>The default setting is {@code false}.
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Set a view name to be resolved by the DispatcherServlet via a ViewResolver.
	 * Will override any pre-existing view name or View.
	 */
	public void setViewName(String viewName) {
		this.view = viewName;
	}

	/**
	 * Return the view name to be resolved by the DispatcherServlet via a
	 * ViewResolver, or {@code null} if a View object is set.
	 */
	public String getViewName() {
		return (this.view instanceof String ? (String) this.view : null);
	}

	/**
	 * Set a View object to be used by the DispatcherServlet.
	 * Will override any pre-existing view name or View.
	 */
	public void setView(Object view) {
		this.view = view;
	}

	/**
	 * Return the View object, or {@code null} if we using a view name
	 * to be resolved by the DispatcherServlet via a ViewResolver.
	 */
	public Object getView() {
		return this.view;
	}

	/**
	 * Whether the view is a view reference specified via a name to be
	 * resolved by the DispatcherServlet via a ViewResolver.
	 */
	public boolean isViewReference() {
		return (this.view instanceof String);
	}

	/**
	 * Return the model to use -- either the "default" or the "redirect" model.
	 * The default model is used if {@code redirectModelScenario=false} or
	 * there is no redirect model (i.e. RedirectAttributes was not declared as
	 * a method argument) and {@code ignoreDefaultModelOnRedirect=false}.
	 */
	public ModelMap getModel() {
		if (useDefaultModel()) {
			return this.defaultModel;
		}
		else {
			return (this.redirectModel != null) ? this.redirectModel : new ModelMap();
		}
	}

	/**
	 * Whether to use the default model or the redirect model.
	 */
	private boolean useDefaultModel() {
		return (!this.redirectModelScenario || (this.redirectModel == null && !this.ignoreDefaultModelOnRedirect));
	}

	/**
	 * Return the "default" model created at instantiation.
	 * <p>In general it is recommended to use {@link #getModel()} instead which
	 * returns either the "default" model (template rendering) or the "redirect"
	 * model (redirect URL preparation). Use of this method may be needed for
	 * advanced cases when access to the "default" model is needed regardless,
	 * e.g. to save model attributes specified via {@code @SessionAttributes}.
	 * @return the default model, never {@code null}
	 */
	public ModelMap getDefaultModel() {
		return this.defaultModel;
	}

	/**
	 * Provide a separate model instance to use in a redirect scenario.
	 * The provided additional model however is not used used unless
	 * {@link #setRedirectModelScenario(boolean)} gets set to {@code true} to signal
	 * a redirect scenario.
	 */
	public void setRedirectModel(ModelMap redirectModel) {
		this.redirectModel = redirectModel;
	}

	/**
	 * Whether the controller has returned a redirect instruction, e.g. a
	 * "redirect:" prefixed view name, a RedirectView instance, etc.
	 */
	public void setRedirectModelScenario(boolean redirectModelScenario) {
		this.redirectModelScenario = redirectModelScenario;
	}

	/**
	 * Return the {@link SessionStatus} instance to use that can be used to
	 * signal that session processing is complete.
	 */
	public SessionStatus getSessionStatus() {
		return this.sessionStatus;
	}

	/**
	 * Whether the request has been handled fully within the handler, e.g.
	 * {@code @ResponseBody} method, and therefore view resolution is not
	 * necessary. This flag can also be set when controller methods declare an
	 * argument of type {@code ServletResponse} or {@code OutputStream}).
	 * <p>The default value is {@code false}.
	 */
	public void setRequestHandled(boolean requestHandled) {
		this.requestHandled = requestHandled;
	}

	/**
	 * Whether the request has been handled fully within the handler.
	 */
	public boolean isRequestHandled() {
		return this.requestHandled;
	}

	/**
	 * Add the supplied attribute to the underlying model.
	 * A shortcut for {@code getModel().addAttribute(String, Object)}.
	 */
	public ModelAndViewContainer addAttribute(String name, Object value) {
		getModel().addAttribute(name, value);
		return this;
	}

	/**
	 * Add the supplied attribute to the underlying model.
	 * A shortcut for {@code getModel().addAttribute(Object)}.
	 */
	public ModelAndViewContainer addAttribute(Object value) {
		getModel().addAttribute(value);
		return this;
	}

	/**
	 * Copy all attributes to the underlying model.
	 * A shortcut for {@code getModel().addAllAttributes(Map)}.
	 */
	public ModelAndViewContainer addAllAttributes(Map<String, ?> attributes) {
		getModel().addAllAttributes(attributes);
		return this;
	}

	/**
	 * Copy attributes in the supplied {@code Map} with existing objects of
	 * the same name taking precedence (i.e. not getting replaced).
	 * A shortcut for {@code getModel().mergeAttributes(Map<String, ?>)}.
	 */
	public ModelAndViewContainer mergeAttributes(Map<String, ?> attributes) {
		getModel().mergeAttributes(attributes);
		return this;
	}

	/**
	 * Remove the given attributes from the model.
	 */
	public ModelAndViewContainer removeAttributes(Map<String, ?> attributes) {
		if (attributes != null) {
			for (String key : attributes.keySet()) {
				getModel().remove(key);
			}
		}
		return this;
	}

	/**
	 * Whether the underlying model contains the given attribute name.
	 * A shortcut for {@code getModel().containsAttribute(String)}.
	 */
	public boolean containsAttribute(String name) {
		return getModel().containsAttribute(name);
	}


	/**
	 * Return diagnostic information.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ModelAndViewContainer: ");
		if (!isRequestHandled()) {
			if (isViewReference()) {
				sb.append("reference to view with name '").append(this.view).append("'");
			}
			else {
				sb.append("View is [").append(this.view).append(']');
			}
			if (useDefaultModel()) {
				sb.append("; default model ");
			}
			else {
				sb.append("; redirect model ");
			}
			sb.append(getModel());
		}
		else {
			sb.append("Request handled directly");
		}
		return sb.toString();
	}

}
