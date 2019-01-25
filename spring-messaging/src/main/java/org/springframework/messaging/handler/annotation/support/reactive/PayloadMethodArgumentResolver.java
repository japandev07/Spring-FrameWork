/*
 * Copyright 2002-2019 the original author or authors.
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
package org.springframework.messaging.handler.annotation.support.reactive;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

/**
 * A resolver to extract and decode the payload of a message using a
 * {@link Decoder}, where the payload is expected to be a {@link Publisher} of
 * {@link DataBuffer DataBuffer}.
 *
 * <p>Validation is applied if the method argument is annotated with
 * {@code @javax.validation.Valid} or
 * {@link org.springframework.validation.annotation.Validated}. Validation
 * failure results in an {@link MethodArgumentNotValidException}.
 *
 * <p>This resolver should be ordered last if {@link #useDefaultResolution} is
 * set to {@code true} since in that case it supports all types and does not
 * require the presence of {@link Payload}.
 *
 * @author Rossen Stoyanchev
 * @since 5.2
 */
public class PayloadMethodArgumentResolver implements HandlerMethodArgumentResolver {

	protected final Log logger = LogFactory.getLog(getClass());


	private final List<Decoder<?>> decoders;

	@Nullable
	private final Validator validator;

	private final ReactiveAdapterRegistry adapterRegistry;

	private final boolean useDefaultResolution;


	public PayloadMethodArgumentResolver(List<? extends Decoder<?>> decoders, @Nullable Validator validator,
			@Nullable ReactiveAdapterRegistry registry, boolean useDefaultResolution) {

		Assert.isTrue(!CollectionUtils.isEmpty(decoders), "At least one Decoder is required.");
		this.decoders = Collections.unmodifiableList(new ArrayList<>(decoders));
		this.validator = validator;
		this.adapterRegistry = registry != null ? registry : ReactiveAdapterRegistry.getSharedInstance();
		this.useDefaultResolution = useDefaultResolution;
	}


	/**
	 * Return a read-only list of the configured decoders.
	 */
	public List<Decoder<?>> getDecoders() {
		return this.decoders;
	}

	/**
	 * Return the configured validator, if any.
	 */
	@Nullable
	public Validator getValidator() {
		return this.validator;
	}

	/**
	 * Return the configured {@link ReactiveAdapterRegistry}.
	 */
	public ReactiveAdapterRegistry getAdapterRegistry() {
		return this.adapterRegistry;
	}

	/**
	 * Whether this resolver is configured to use default resolution, i.e.
	 * works for any argument type regardless of whether {@code @Payload} is
	 * present or not.
	 */
	public boolean isUseDefaultResolution() {
		return this.useDefaultResolution;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(Payload.class) || this.useDefaultResolution;
	}


	/**
	 * Decode the content of the given message payload through a compatible
	 * {@link Decoder}.
	 *
	 * <p>Validation is applied if the method argument is annotated with
	 * {@code @javax.validation.Valid} or
	 * {@link org.springframework.validation.annotation.Validated}. Validation
	 * failure results in an {@link MethodArgumentNotValidException}.
	 *
	 * @param parameter the target method argument that we are decoding to
	 * @param message the message from which the content was extracted
	 * @return a Mono with the result of argument resolution
	 *
	 * @see #extractPayloadContent(MethodParameter, Message)
	 * @see #getMimeType(Message)
	 */
	@Override
	public final Mono<Object> resolveArgument(MethodParameter parameter, Message<?> message) {
		Payload ann = parameter.getParameterAnnotation(Payload.class);
		if (ann != null && StringUtils.hasText(ann.expression())) {
			throw new IllegalStateException("@Payload SpEL expressions not supported by this resolver");
		}
		Publisher<DataBuffer> content = extractPayloadContent(parameter, message);
		return decodeContent(parameter, message, ann == null || ann.required(), content, getMimeType(message));
	}

	/**
	 * Extract the content to decode from the message. By default, the message
	 * payload is expected to be {@code Publisher<DataBuffer>}. Sub-classes can
	 * override this method to change that assumption.
	 * @param parameter the target method parameter we're decoding to
	 * @param message the input message with the content
	 * @return the content to decode
	 */
	@SuppressWarnings("unchecked")
	protected Publisher<DataBuffer> extractPayloadContent(MethodParameter parameter, Message<?> message) {
		Publisher<DataBuffer> content;
		try {
			content = (Publisher<DataBuffer>) message.getPayload();
		}
		catch (ClassCastException ex) {
			throw new MethodArgumentResolutionException(
					message, parameter, "Expected Publisher<DataBuffer> payload", ex);
		}
		return content;
	}

	/**
	 * Return the mime type for the content. By default this method checks the
	 * {@link MessageHeaders#CONTENT_TYPE} header expecting to find a
	 * {@link MimeType} value or a String to parse to a {@link MimeType}.
	 * @param message the input message
	 */
	@Nullable
	protected MimeType getMimeType(Message<?> message) {
		Object headerValue = message.getHeaders().get(MessageHeaders.CONTENT_TYPE);
		if (headerValue == null) {
			return null;
		}
		else if (headerValue instanceof String) {
			return MimeTypeUtils.parseMimeType((String) headerValue);
		}
		else if (headerValue instanceof MimeType) {
			return (MimeType) headerValue;
		}
		else {
			throw new IllegalArgumentException("Unexpected MimeType value: " + headerValue);
		}
	}

	private Mono<Object> decodeContent(MethodParameter parameter, Message<?> message,
			boolean isContentRequired, Publisher<DataBuffer> content, @Nullable MimeType mimeType) {

		ResolvableType targetType = ResolvableType.forMethodParameter(parameter);
		Class<?> resolvedType = targetType.resolve();
		ReactiveAdapter adapter = (resolvedType != null ? getAdapterRegistry().getAdapter(resolvedType) : null);
		ResolvableType elementType = (adapter != null ? targetType.getGeneric() : targetType);
		isContentRequired = isContentRequired || (adapter != null && !adapter.supportsEmpty());
		Consumer<Object> validator = getValidator(message, parameter);

		if (logger.isDebugEnabled()) {
			logger.debug("Mime type:" + mimeType);
		}
		mimeType = mimeType != null ? mimeType : MimeTypeUtils.APPLICATION_OCTET_STREAM;

		for (Decoder<?> decoder : this.decoders) {
			if (decoder.canDecode(elementType, mimeType)) {
				if (adapter != null && adapter.isMultiValue()) {
					if (logger.isDebugEnabled()) {
						logger.debug("0..N [" + elementType + "]");
					}
					Flux<?> flux = decoder.decode(content, elementType, mimeType, Collections.emptyMap());
					flux = flux.onErrorResume(ex -> Flux.error(handleReadError(parameter, message, ex)));
					if (isContentRequired) {
						flux = flux.switchIfEmpty(Flux.error(() -> handleMissingBody(parameter, message)));
					}
					if (validator != null) {
						flux = flux.doOnNext(validator::accept);
					}
					return Mono.just(adapter.fromPublisher(flux));
				}
				else {
					if (logger.isDebugEnabled()) {
						logger.debug("0..1 [" + elementType + "]");
					}
					// Single-value (with or without reactive type wrapper)
					Mono<?> mono = decoder.decodeToMono(content, targetType, mimeType, Collections.emptyMap());
					mono = mono.onErrorResume(ex -> Mono.error(handleReadError(parameter, message, ex)));
					if (isContentRequired) {
						mono = mono.switchIfEmpty(Mono.error(() -> handleMissingBody(parameter, message)));
					}
					if (validator != null) {
						mono = mono.doOnNext(validator::accept);
					}
					return (adapter != null ? Mono.just(adapter.fromPublisher(mono)) : Mono.from(mono));
				}
			}
		}

		return Mono.error(new MethodArgumentResolutionException(
				message, parameter, "Cannot decode to [" + targetType + "]" + message));
	}

	private Throwable handleReadError(MethodParameter parameter, Message<?> message, Throwable ex) {
		return ex instanceof DecodingException ?
				new MethodArgumentResolutionException(message, parameter, "Failed to read HTTP message", ex) : ex;
	}

	private MethodArgumentResolutionException handleMissingBody(MethodParameter param, Message<?> message) {
		return new MethodArgumentResolutionException(message, param,
				"Payload content is missing: " + param.getExecutable().toGenericString());
	}

	@Nullable
	private Consumer<Object> getValidator(Message<?> message, MethodParameter parameter) {
		if (this.validator == null) {
			return null;
		}
		for (Annotation ann : parameter.getParameterAnnotations()) {
			Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
			if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
				Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
				Object[] validationHints = (hints instanceof Object[] ? (Object[]) hints : new Object[] {hints});
				String name = Conventions.getVariableNameForParameter(parameter);
				return target -> {
					BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, name);
					if (!ObjectUtils.isEmpty(validationHints) && this.validator instanceof SmartValidator) {
						((SmartValidator) this.validator).validate(target, bindingResult, validationHints);
					}
					else {
						this.validator.validate(target, bindingResult);
					}
					if (bindingResult.hasErrors()) {
						throw new MethodArgumentNotValidException(message, parameter, bindingResult);
					}
				};
			}
		}
		return null;
	}

}
