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
package org.springframework.web.reactive.result.method.annotation;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.reactive.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

/**
 * Abstract base class for argument resolvers that resolve method arguments
 * by reading the request body with an {@link HttpMessageReader}.
 *
 * <p>Applies validation if the method argument is annotated with
 * {@code @javax.validation.Valid} or
 * {@link org.springframework.validation.annotation.Validated}. Validation
 * failure results in an {@link ServerWebInputException}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public abstract class AbstractMessageReaderArgumentResolver {

	private static final TypeDescriptor MONO_TYPE = TypeDescriptor.valueOf(Mono.class);

	private static final TypeDescriptor FLUX_TYPE = TypeDescriptor.valueOf(Flux.class);


	private final List<HttpMessageReader<?>> messageReaders;

	private final ConversionService conversionService;

	private final Validator validator;

	private final List<MediaType> supportedMediaTypes;


	/**
	 * Constructor with message converters and a ConversionService.
	 * @param messageReaders readers to convert from the request body
	 * @param service for converting to other reactive types from Flux and Mono
	 * @param validator validator to validate decoded objects with
	 */
	protected AbstractMessageReaderArgumentResolver(List<HttpMessageReader<?>> messageReaders,
			ConversionService service, Validator validator) {

		Assert.notEmpty(messageReaders, "At least one message reader is required.");
		Assert.notNull(service, "'conversionService' is required.");
		this.messageReaders = messageReaders;
		this.conversionService = service;
		this.validator = validator;
		this.supportedMediaTypes = messageReaders.stream()
				.flatMap(converter -> converter.getReadableMediaTypes().stream())
				.collect(Collectors.toList());
	}


	/**
	 * Return the configured message converters.
	 */
	public List<HttpMessageReader<?>> getMessageReaders() {
		return this.messageReaders;
	}

	/**
	 * Return the configured {@link ConversionService}.
	 */
	public ConversionService getConversionService() {
		return this.conversionService;
	}


	protected Mono<Object> readBody(MethodParameter bodyParameter, boolean isBodyRequired,
			ServerWebExchange exchange) {

		TypeDescriptor typeDescriptor = new TypeDescriptor(bodyParameter);
		boolean convertFromMono = getConversionService().canConvert(MONO_TYPE, typeDescriptor);
		boolean convertFromFlux = getConversionService().canConvert(FLUX_TYPE, typeDescriptor);

		ResolvableType elementType = ResolvableType.forMethodParameter(bodyParameter);
		if (convertFromMono || convertFromFlux) {
			elementType = elementType.getGeneric(0);
		}

		ServerHttpRequest request = exchange.getRequest();
		MediaType mediaType = request.getHeaders().getContentType();
		if (mediaType == null) {
			mediaType = MediaType.APPLICATION_OCTET_STREAM;
		}

		for (HttpMessageReader<?> reader : getMessageReaders()) {
			if (reader.canRead(elementType, mediaType)) {
				if (convertFromFlux) {
					Flux<?> flux = reader.read(elementType, request)
							.onErrorResumeWith(ex -> Flux.error(getReadError(ex, bodyParameter)));
					if (checkRequired(bodyParameter, isBodyRequired)) {
						flux = flux.switchIfEmpty(Flux.error(getRequiredBodyError(bodyParameter)));
					}
					if (this.validator != null) {
						flux = flux.map(applyValidationIfApplicable(bodyParameter));
					}
					return Mono.just(getConversionService().convert(flux, FLUX_TYPE, typeDescriptor));
				}
				else {
					Mono<?> mono = reader.readMono(elementType, request)
							.otherwise(ex -> Mono.error(getReadError(ex, bodyParameter)));
					if (checkRequired(bodyParameter, isBodyRequired)) {
						mono = mono.otherwiseIfEmpty(Mono.error(getRequiredBodyError(bodyParameter)));
					}
					if (this.validator != null) {
						mono = mono.map(applyValidationIfApplicable(bodyParameter));
					}
					if (convertFromMono) {
						return Mono.just(getConversionService().convert(mono, MONO_TYPE, typeDescriptor));
					}
					else {
						return Mono.from(mono);
					}
				}
			}
		}

		return Mono.error(new UnsupportedMediaTypeStatusException(mediaType, this.supportedMediaTypes));
	}

	protected boolean checkRequired(MethodParameter bodyParameter, boolean isBodyRequired) {
		if ("rx.Single".equals(bodyParameter.getNestedParameterType().getName())) {
			return true;
		}
		return isBodyRequired;
	}

	protected ServerWebInputException getReadError(Throwable ex, MethodParameter parameter) {
		return new ServerWebInputException("Failed to read HTTP message", parameter, ex);
	}

	protected ServerWebInputException getRequiredBodyError(MethodParameter parameter) {
		return new ServerWebInputException("Required request body is missing: " +
				parameter.getMethod().toGenericString());
	}

	protected <T> Function<T, T> applyValidationIfApplicable(MethodParameter methodParam) {
		Annotation[] annotations = methodParam.getParameterAnnotations();
		for (Annotation ann : annotations) {
			Validated validAnnot = AnnotationUtils.getAnnotation(ann, Validated.class);
			if (validAnnot != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
				Object hints = (validAnnot != null ? validAnnot.value() : AnnotationUtils.getValue(ann));
				Object[] validHints = (hints instanceof Object[] ? (Object[]) hints : new Object[] {hints});
				return element -> {
					doValidate(element, validHints, methodParam);
					return element;
				};
			}
		}
		return element -> element;
	}

	/**
	 * TODO: replace with use of DataBinder
	 */
	private void doValidate(Object target, Object[] validationHints, MethodParameter methodParam) {
		String name = Conventions.getVariableNameForParameter(methodParam);
		Errors errors = new BeanPropertyBindingResult(target, name);
		if (!ObjectUtils.isEmpty(validationHints) && this.validator instanceof SmartValidator) {
			((SmartValidator) this.validator).validate(target, errors, validationHints);
		}
		else if (this.validator != null) {
			this.validator.validate(target, errors);
		}
		if (errors.hasErrors()) {
			throw new ServerWebInputException("Validation failed", methodParam);
		}
	}

}
