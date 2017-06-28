/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.http.codec.json;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.eclipse.jetty.io.RuntimeIOException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.HttpMessageDecoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * Decode a byte stream into JSON and convert to Object's with Jackson 2.9.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 5.0
 * @see Jackson2JsonEncoder
 */
public class Jackson2JsonDecoder extends Jackson2CodecSupport implements HttpMessageDecoder<Object> {

	public Jackson2JsonDecoder() {
		super(Jackson2ObjectMapperBuilder.json().build());
	}

	public Jackson2JsonDecoder(ObjectMapper mapper, MimeType... mimeTypes) {
		super(mapper, mimeTypes);
	}

	@Override
	public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
		JavaType javaType = objectMapper().getTypeFactory().constructType(elementType.getType());
		// Skip String: CharSequenceDecoder + "*/*" comes after
		return (!CharSequence.class.isAssignableFrom(elementType.resolve(Object.class)) &&
				objectMapper().canDeserialize(javaType) && supportsMimeType(mimeType));
	}

	@Override
	public List<MimeType> getDecodableMimeTypes() {
		return JSON_MIME_TYPES;
	}

	@Override
	public Flux<Object> decode(Publisher<DataBuffer> input, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		Flux<TokenBuffer> tokens = Flux.from(input)
				.flatMap(new Jackson2Tokenizer(nonBlockingParser(), true));

		return decodeInternal(tokens, elementType, mimeType, hints);
	}

	@Override
	public Mono<Object> decodeToMono(Publisher<DataBuffer> input, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		Flux<TokenBuffer> tokens = Flux.from(input)
				.flatMap(new Jackson2Tokenizer(nonBlockingParser(), false));

		return decodeInternal(tokens, elementType, mimeType, hints).singleOrEmpty();
	}

	private Flux<Object> decodeInternal(Flux<TokenBuffer> tokens,
			ResolvableType elementType, @Nullable MimeType mimeType,
			@Nullable Map<String, Object> hints) {

		Assert.notNull(tokens, "'tokens' must not be null");
		Assert.notNull(elementType, "'elementType' must not be null");

		Class<?> contextClass = getParameter(elementType).map(MethodParameter::getContainingClass).orElse(null);
		JavaType javaType = getJavaType(elementType.getType(), contextClass);
		Class<?> jsonView = (hints != null ? (Class<?>) hints.get(Jackson2CodecSupport.JSON_VIEW_HINT) : null);

		ObjectReader reader = (jsonView != null ?
				objectMapper().readerWithView(jsonView).forType(javaType) :
				objectMapper().readerFor(javaType));

		return tokens.flatMap(tokenBuffer -> {
			try {
				Object value = reader.readValue(tokenBuffer.asParser());
				return Mono.just(value);
			}
			catch (InvalidDefinitionException ex) {
				return Mono.error(new CodecException("Type definition error: " + ex.getType(), ex));
			}
			catch (JsonProcessingException ex) {
				return Mono.error(new DecodingException("JSON decoding error: " + ex.getOriginalMessage(), ex));
			}
			catch (IOException ex) {
				return Mono.error(new DecodingException("I/O error while parsing input stream", ex));
			}
		});
	}


	// HttpMessageDecoder...

	@Override
	public Map<String, Object> getDecodeHints(ResolvableType actualType, ResolvableType elementType,
			ServerHttpRequest request, ServerHttpResponse response) {

		return getHints(actualType);
	}

	@Override
	protected <A extends Annotation> A getAnnotation(MethodParameter parameter, Class<A> annotType) {
		return parameter.getParameterAnnotation(annotType);
	}

	private JsonParser nonBlockingParser() {
		try {
			JsonFactory factory = this.objectMapper().getFactory();
			return factory.createNonBlockingByteArrayParser();
		}
		catch (IOException ex) {
			throw new RuntimeIOException(ex);
		}
	}
}
