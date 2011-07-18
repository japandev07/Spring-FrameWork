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

package org.springframework.web.method.annotation.support;

import java.beans.PropertyEditor;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.WebUtils;

/**
 * Resolves method arguments annotated with @{@link RequestParam}, arguments of 
 * type {@link MultipartFile} in conjunction with Spring's {@link MultipartResolver} 
 * abstraction, and arguments of type {@code javax.servlet.http.Part} in conjunction 
 * with Servlet 3.0 multipart requests. This resolver can also be created in default 
 * resolution mode in which simple types (int, long, etc.) not annotated 
 * with @{@link RequestParam} are also treated as request parameters with the 
 * parameter name derived from the argument name.
 * 
 * <p>If the method parameter type is {@link Map}, the request parameter name is used to 
 * resolve the request parameter String value. The value is then converted to a {@link Map} 
 * via type conversion assuming a suitable {@link Converter} or {@link PropertyEditor} has 
 * been registered. If a request parameter name is not specified with a {@link Map} method 
 * parameter type, the {@link RequestParamMapMethodArgumentResolver} is used instead 
 * providing access to all request parameters in the form of a map.
 * 
 * <p>A {@link WebDataBinder} is invoked to apply type conversion to resolved request header values that 
 * don't yet match the method parameter type.
 * 
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 * @see RequestParamMapMethodArgumentResolver
 */
public class RequestParamMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver {

	private final boolean useDefaultResolution;

	/**
	 * @param beanFactory a bean factory to use for resolving  ${...} placeholder and #{...} SpEL expressions 
	 * in default values, or {@code null} if default values are not expected to contain expressions
	 * @param useDefaultResolution in default resolution mode a method argument that is a simple type, as
	 * defined in {@link BeanUtils#isSimpleProperty(Class)}, is treated as a request parameter even if it doesn't have
	 * an @{@link RequestParam} annotation, the request parameter name is derived from the method parameter name.
	 */
	public RequestParamMethodArgumentResolver(ConfigurableBeanFactory beanFactory, 
											  boolean useDefaultResolution) {
		super(beanFactory);
		this.useDefaultResolution = useDefaultResolution;
	}

	/**
	 * Supports the following:
	 * <ul>
	 * 	<li>@RequestParam method arguments. This excludes the case where a parameter is of type 
	 * 		{@link Map} and the annotation does not specify a request parameter name. See 
	 * 		{@link RequestParamMapMethodArgumentResolver} instead for such parameters.
	 * 	<li>Arguments of type {@link MultipartFile} even if not annotated.
	 * 	<li>Arguments of type {@code javax.servlet.http.Part} even if not annotated.
	 * </ul>
	 * 
	 * <p>In default resolution mode, simple type arguments not annotated with @RequestParam are also supported.
	 */
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> paramType = parameter.getParameterType();
		RequestParam requestParamAnnot = parameter.getParameterAnnotation(RequestParam.class);
		if (requestParamAnnot != null) {
			if (Map.class.isAssignableFrom(paramType)) {
				return StringUtils.hasText(requestParamAnnot.value());
			}
			return true;
		}
		else if (MultipartFile.class.equals(paramType)) {
			return true;
		}
		else if ("javax.servlet.http.Part".equals(parameter.getParameterType().getName())) {
			return true;
		}
		else if (this.useDefaultResolution) {
			return BeanUtils.isSimpleProperty(paramType);
		}
		else {
			return false;
		}
	}

	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
		return (annotation != null) ? 
				new RequestParamNamedValueInfo(annotation) : 
				new RequestParamNamedValueInfo();
	}

	@Override
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest webRequest) throws Exception {
		
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		MultipartHttpServletRequest multipartRequest = 
			WebUtils.getNativeRequest(servletRequest, MultipartHttpServletRequest.class);

		if (multipartRequest != null) {
			List<MultipartFile> files = multipartRequest.getFiles(name);
			if (!files.isEmpty()) {
				return (files.size() == 1 ? files.get(0) : files);
			}
		}
		
		if ("javax.servlet.http.Part".equals(parameter.getParameterType().getName())) {
			return servletRequest.getPart(name);
		}
		
		String[] paramValues = webRequest.getParameterValues(name);
		if (paramValues != null) {
			return paramValues.length == 1 ? paramValues[0] : paramValues;
		}
		else {
			return null;
		}
	}

	@Override
	protected void handleMissingValue(String paramName, MethodParameter parameter) throws ServletException {
		throw new MissingServletRequestParameterException(paramName, parameter.getParameterType().getSimpleName());
	}

	private class RequestParamNamedValueInfo extends NamedValueInfo {

		private RequestParamNamedValueInfo() {
			super("", true, ValueConstants.DEFAULT_NONE);
		}
		
		private RequestParamNamedValueInfo(RequestParam annotation) {
			super(annotation.value(), annotation.required(), annotation.defaultValue());
		}
	}
}