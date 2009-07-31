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

package org.springframework.web.bind.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for handling exceptions in specific handler classes and/or
 * handler methods. Provides consistent style between Servlet and Portlet
 * environments, with the semantics adapting to the concrete environment.
 *
 * <p>Handler methods which are annotated with this annotation are allowed
 * to have very flexible signatures. They may have arguments of the following
 * types, in arbitrary order:
 * <ul>
 * <li>Request and/or response objects (Servlet API or Portlet API).
 * You may choose any specific request/response type, e.g.
 * {@link javax.servlet.ServletRequest} / {@link javax.servlet.http.HttpServletRequest}
 * or {@link javax.portlet.PortletRequest} / {@link javax.portlet.ActionRequest} /
 * {@link javax.portlet.RenderRequest}. Note that in the Portlet case,
 * an explicitly declared action/render argument is also used for mapping
 * specific request types onto a handler method (in case of no other
 * information given that differentiates between action and render requests).
 * <li>Session object (Servlet API or Portlet API): either
 * {@link javax.servlet.http.HttpSession} or {@link javax.portlet.PortletSession}.
 * An argument of this type will enforce the presence of a corresponding session.
 * As a consequence, such an argument will never be <code>null</code>.
 * <i>Note that session access may not be thread-safe, in particular in a
 * Servlet environment: Consider switching the
 * {@link org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter#setSynchronizeOnSession "synchronizeOnSession"}
 * flag to "true" if multiple requests are allowed to access a session concurrently.</i>
 * <li>{@link org.springframework.web.context.request.WebRequest} or
 * {@link org.springframework.web.context.request.NativeWebRequest}.
 * Allows for generic request parameter access as well as request/session
 * attribute access, without ties to the native Servlet/Portlet API.
 * <li>{@link java.util.Locale} for the current request locale
 * (determined by the most specific locale resolver available,
 * i.e. the configured {@link org.springframework.web.servlet.LocaleResolver}
 * in a Servlet environment and the portal locale in a Portlet environment).
 * <li>{@link java.io.InputStream} / {@link java.io.Reader} for access
 * to the request's content. This will be the raw InputStream/Reader as
 * exposed by the Servlet/Portlet API.
 * <li>{@link java.io.OutputStream} / {@link java.io.Writer} for generating
 * the response's content. This will be the raw OutputStream/Writer as
 * exposed by the Servlet/Portlet API.
 * </ul>
 *
 * <p>The following return types are supported for handler methods:
 * <ul>
 * <li>A <code>ModelAndView</code> object (Servlet MVC or Portlet MVC).
 * <li>A {@link org.springframework.ui.Model Model} object, with the view name
 * implicitly determined through a {@link org.springframework.web.servlet.RequestToViewNameTranslator}.
 * <li>A {@link java.util.Map} object for exposing a model,
 * with the view name implicitly determined through a
 * {@link org.springframework.web.servlet.RequestToViewNameTranslator}.
 * <li>A {@link org.springframework.web.servlet.View} object.
 * <li>A {@link java.lang.String} value which is interpreted as view name.
 * <li><code>void</code> if the method handles the response itself (by
 * writing the response content directly, declaring an argument of type
 * {@link javax.servlet.ServletResponse} / {@link javax.servlet.http.HttpServletResponse}
 * / {@link javax.portlet.RenderResponse} for that purpose)
 * or if the view name is supposed to be implicitly determined through a
 * {@link org.springframework.web.servlet.RequestToViewNameTranslator}
 * (not declaring a response argument in the handler method signature;
 * only applicable in a Servlet environment).
 * </ul>
 *
 * <p><b>NOTE: <code>@RequestMapping</code> will only be processed if a
 * corresponding <code>HandlerMapping</code> (for type level annotations)
 * and/or <code>HandlerAdapter</code> (for method level annotations) is
 * present in the dispatcher.</b> This is the case by default in both
 * <code>DispatcherServlet</code> and <code>DispatcherPortlet</code>.
 * However, if you are defining custom <code>HandlerMappings</code> or
 * <code>HandlerAdapters</code>, then you need to make sure that a
 * corresponding custom <code>DefaultAnnotationHandlerMapping</code>
 * and/or <code>AnnotationMethodHandlerAdapter</code> is defined as well
 * - provided that you intend to use <code>@RequestMapping</code>.
 *
 * @author Arjen Poutsma
 * @see org.springframework.web.context.request.WebRequest
 * @see org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerExceptionResolver
 * @since 3.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExceptionHandler {

	/**
	 * Exceptions handled by the annotation method. If empty, will default to any exceptions listed in the method
	 * argument list.
	 */
	Class<? extends Throwable>[] value() default {};

}
