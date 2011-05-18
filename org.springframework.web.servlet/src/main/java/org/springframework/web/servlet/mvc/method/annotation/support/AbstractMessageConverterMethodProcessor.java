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

package org.springframework.web.servlet.mvc.method.annotation.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.HandlerMapping;

/**
 * A base class for resolving method argument values by reading from the body of a request with {@link
 * HttpMessageConverter}s and for handling method return values by writing to the response with {@link
 * HttpMessageConverter}s.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public abstract class AbstractMessageConverterMethodProcessor
		implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {

	private static final MediaType MEDIA_TYPE_APPLICATION = new MediaType("application");

	protected final Log logger = LogFactory.getLog(getClass());

	private final List<HttpMessageConverter<?>> messageConverters;

	private final List<MediaType> allSupportedMediaTypes;

	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> messageConverters) {
		Assert.notEmpty(messageConverters, "'messageConverters' must not be empty");
		this.messageConverters = messageConverters;
		this.allSupportedMediaTypes = getAllSupportedMediaTypes(messageConverters);
	}

	private static List<MediaType> getAllSupportedMediaTypes(List<HttpMessageConverter<?>> messageConverters) {
		Set<MediaType> allSupportedMediaTypes = new HashSet<MediaType>();
		for (HttpMessageConverter<?> messageConverter : messageConverters) {
			allSupportedMediaTypes.addAll(messageConverter.getSupportedMediaTypes());
		}
		List<MediaType> result = new ArrayList<MediaType>(allSupportedMediaTypes);
		MediaType.sortBySpecificity(result);
		return Collections.unmodifiableList(result);
	}

	@SuppressWarnings("unchecked")
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest,
												   MethodParameter methodParam,
												   Class<T> paramType)
			throws IOException, HttpMediaTypeNotSupportedException {

		HttpInputMessage inputMessage = createInputMessage(webRequest);

		MediaType contentType = inputMessage.getHeaders().getContentType();
		if (contentType == null) {
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}

		for (HttpMessageConverter<?> messageConverter : this.messageConverters) {
			if (messageConverter.canRead(paramType, contentType)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Reading [" + paramType.getName() + "] as \"" + contentType + "\" using [" +
							messageConverter + "]");
				}
				return ((HttpMessageConverter<T>) messageConverter).read(paramType, inputMessage);
			}
		}

		throw new HttpMediaTypeNotSupportedException(contentType, allSupportedMediaTypes);
	}

	/**
	 * Creates a new {@link HttpInputMessage} from the given {@link NativeWebRequest}.
	 *
	 * @param webRequest the web request to create an input message from
	 * @return the input message
	 */
	protected ServletServerHttpRequest createInputMessage(NativeWebRequest webRequest) {
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		return new ServletServerHttpRequest(servletRequest);
	}

	/**
	 * Creates a new {@link HttpOutputMessage} from the given {@link NativeWebRequest}.
	 *
	 * @param webRequest the web request to create an output message from
	 * @return the output message
	 */
	protected ServletServerHttpResponse createOutputMessage(NativeWebRequest webRequest) {
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		return new ServletServerHttpResponse(response);
	}

	/**
	 * Writes the given return value to the given web request. Delegates to
	 * {@link #writeWithMessageConverters(Object, MethodParameter, ServletServerHttpRequest, ServletServerHttpResponse)}
	 */
	protected <T> void writeWithMessageConverters(T returnValue,
												  MethodParameter returnType,
												  NativeWebRequest webRequest)
			throws IOException, HttpMediaTypeNotAcceptableException {
		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);
		writeWithMessageConverters(returnValue, returnType, inputMessage, outputMessage);
	}

	/**
	 * Writes the given return type to the given output message.
	 *
	 * @param returnValue the value to write to the output message
	 * @param returnType the type of the value
	 * @param inputMessage the input messages. Used to inspect the {@code Accept} header.
	 * @param outputMessage the output message to write to
	 * @throws IOException thrown in case of I/O errors
	 * @throws HttpMediaTypeNotAcceptableException thrown when the conditions indicated by {@code Accept} header on
	 * the request cannot be met by the message converters
	 */
	@SuppressWarnings("unchecked")
	protected <T> void writeWithMessageConverters(T returnValue,
												  MethodParameter returnType,
												  ServletServerHttpRequest inputMessage,
												  ServletServerHttpResponse outputMessage)
			throws IOException, HttpMediaTypeNotAcceptableException {


		Set<MediaType> acceptableMediaTypes = getAcceptableMediaTypes(inputMessage);
		Set<MediaType> producibleMediaTypes = getProducibleMediaTypes(inputMessage.getServletRequest());

		List<MediaType> mediaTypes = new ArrayList<MediaType>();
		for (MediaType acceptableMediaType : acceptableMediaTypes) {
			for (MediaType producibleMediaType : producibleMediaTypes) {
				if (acceptableMediaType.isCompatibleWith(producibleMediaType)) {
					mediaTypes.add(getMostSpecificMediaType(acceptableMediaType, producibleMediaType));
				}
			}
		}
		if (mediaTypes.isEmpty()) {
			throw new HttpMediaTypeNotAcceptableException(allSupportedMediaTypes);
		}
		MediaType.sortBySpecificity(mediaTypes);
		MediaType selectedMediaType = null;
		for (MediaType mediaType : mediaTypes) {
			if (mediaType.isConcrete()) {
				selectedMediaType = mediaType;
				break;
			}
			else if (mediaType.equals(MediaType.ALL) || mediaType.equals(MEDIA_TYPE_APPLICATION)) {
				selectedMediaType = MediaType.APPLICATION_OCTET_STREAM;
				break;
			}
		}
		if (selectedMediaType != null) {
			for (HttpMessageConverter<?> messageConverter : messageConverters) {
				if (messageConverter.canWrite(returnValue.getClass(), selectedMediaType)) {
					((HttpMessageConverter<T>) messageConverter).write(returnValue, selectedMediaType, outputMessage);
					if (logger.isDebugEnabled()) {
						logger.debug("Written [" + returnValue + "] as \"" + selectedMediaType + "\" using [" +
								messageConverter + "]");
					}
					return;
				}
			}
		}
		throw new HttpMediaTypeNotAcceptableException(allSupportedMediaTypes);
	}

	/**
	 * Returns the media types that can be produced:
	 * <ul>
	 * 	<li>The set of producible media types specified in the request mappings, or
	 * 	<li>The set of supported media types by all configured message converters, or
	 * 	<li>{@link MediaType#ALL}
	 * </ul>
	 */
	@SuppressWarnings("unchecked")
	protected Set<MediaType> getProducibleMediaTypes(HttpServletRequest request) {
		Set<MediaType> mediaTypes = (Set<MediaType>) request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
		if (!CollectionUtils.isEmpty(mediaTypes)) {
			return mediaTypes;
		}
		else if (!allSupportedMediaTypes.isEmpty()) {
			return new HashSet<MediaType>(allSupportedMediaTypes);
		}
		else {
			return Collections.singleton(MediaType.ALL);
		}

	}

	private Set<MediaType> getAcceptableMediaTypes(HttpInputMessage inputMessage) {
		Set<MediaType> result = new HashSet<MediaType>(inputMessage.getHeaders().getAccept());
		if (result.isEmpty()) {
			result.add(MediaType.ALL);
		}
		return result;
	}
	
	private MediaType getMostSpecificMediaType(MediaType type1, MediaType type2) {
		return MediaType.SPECIFICITY_COMPARATOR.compare(type1, type2) < 0 ? type1 : type2;
	}

}