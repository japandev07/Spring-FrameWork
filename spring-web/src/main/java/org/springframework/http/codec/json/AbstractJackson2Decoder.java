/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.http.codec.json;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.codec.HttpMessageDecoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * Abstract base class for Jackson 2.9 decoding, leveraging non-blocking parsing.
 *
 * <p>Compatible with Jackson 2.9.7 and higher.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @author Arjen Poutsma
 * @since 5.0
 * @see <a href="https://github.com/FasterXML/jackson-core/issues/57" target="_blank">Add support for non-blocking ("async") JSON parsing</a>
 */
public abstract class AbstractJackson2Decoder extends Jackson2CodecSupport implements HttpMessageDecoder<Object> {

	private int maxInMemorySize = 256 * 1024;


	/**
	 * Constructor with a Jackson {@link ObjectMapper} to use.
	 */
	protected AbstractJackson2Decoder(ObjectMapper mapper, MimeType... mimeTypes) {
		super(mapper, mimeTypes);
	}


	/**
	 * Set the max number of bytes that can be buffered by this decoder. This
	 * is either the size of the entire input when decoding as a whole, or the
	 * size of one top-level JSON object within a JSON stream. When the limit
	 * is exceeded, {@link DataBufferLimitException} is raised.
	 * <p>By default this is set to 256K.
	 * @param byteCount the max number of bytes to buffer, or -1 for unlimited
	 * @since 5.1.11
	 */
	public void setMaxInMemorySize(int byteCount) {
		this.maxInMemorySize = byteCount;
	}

	/**
	 * Return the {@link #setMaxInMemorySize configured} byte count limit.
	 * @since 5.1.11
	 */
	public int getMaxInMemorySize() {
		return this.maxInMemorySize;
	}


	@Override
	public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
		ObjectMapper mapper = selectObjectMapper(elementType, mimeType);
		if (mapper == null) {
			return false;
		}
		JavaType javaType = mapper.constructType(elementType.getType());
		// Skip String: CharSequenceDecoder + "*/*" comes after
		if (CharSequence.class.isAssignableFrom(elementType.toClass()) || !supportsMimeType(mimeType)) {
			return false;
		}
		if (!logger.isDebugEnabled()) {
			return mapper.canDeserialize(javaType);
		}
		else {
			AtomicReference<Throwable> causeRef = new AtomicReference<>();
			if (mapper.canDeserialize(javaType, causeRef)) {
				return true;
			}
			logWarningIfNecessary(javaType, causeRef.get());
			return false;
		}
	}

	@Override
	public Flux<Object> decode(Publisher<DataBuffer> input, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		ObjectMapper mapper = selectObjectMapper(elementType, mimeType);
		if (mapper == null) {
			return Flux.error(new IllegalStateException("No ObjectMapper for " + elementType));
		}

		boolean forceUseOfBigDecimal = mapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
		if (BigDecimal.class.equals(elementType.getType())) {
			forceUseOfBigDecimal = true;
		}

		Flux<DataBuffer> processed = processInput(input, elementType, mimeType, hints);
		Flux<TokenBuffer> tokens = Jackson2Tokenizer.tokenize(processed, mapper.getFactory(), mapper,
				true, forceUseOfBigDecimal, getMaxInMemorySize());

		ObjectReader objectReader = getObjectReader(mapper, elementType, hints);

		return customizeReaderFromStream(objectReader, mimeType, elementType, hints)
				.flatMapMany(reader -> tokens.handle((tokenBuffer, sink) -> {
						try {
							Object value = reader.readValue(tokenBuffer.asParser(mapper));
							logValue(value, hints);
							if (value != null) {
								sink.next(value);
							}
						}
						catch (IOException ex) {
							sink.error(processException(ex));
						}
					})
				);
	}

	/**
	 * Process the input publisher into a flux. Default implementation returns
	 * {@link Flux#from(Publisher)}, but subclasses can choose to customize
	 * this behavior.
	 * @param input the {@code DataBuffer} input stream to process
	 * @param elementType the expected type of elements in the output stream
	 * @param mimeType the MIME type associated with the input stream (optional)
	 * @param hints additional information about how to do encode
	 * @return the processed flux
	 * @since 5.1.14
	 */
	protected Flux<DataBuffer> processInput(Publisher<DataBuffer> input, ResolvableType elementType,
				@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		return Flux.from(input);
	}

	@Override
	public Mono<Object> decodeToMono(Publisher<DataBuffer> input, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
		return DataBufferUtils.join(input, this.maxInMemorySize)
				.flatMap(dataBuffer -> {
							try {
								ObjectReader objectReader = getObjectReader(elementType, mimeType, hints);
								return customizeReaderFromStream(objectReader, mimeType, elementType, hints)
										.flatMap(reader -> {
											try {
												return Mono.justOrEmpty(decode(dataBuffer, reader, hints));
											}
											catch (DecodingException ex) {
												return Mono.error(ex);
											}
										});
							}
							catch (IllegalStateException ex) {
								return Mono.error(ex);
							}
						});
	}

	@Override
	public Object decode(DataBuffer dataBuffer, ResolvableType targetType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {
		ObjectReader reader = getObjectReader(targetType, mimeType, hints);
		reader = customizeReader(reader, mimeType, targetType, hints);
		return decode(dataBuffer, reader, hints);
	}

	private Object decode(@NonNull DataBuffer dataBuffer, @NonNull ObjectReader objectReader,
			@Nullable Map<String, Object> hints) throws DecodingException {
		try {
			Object value = objectReader.readValue(dataBuffer.asInputStream());
			logValue(value, hints);
			return value;
		}
		catch (IOException ex) {
			throw processException(ex);
		}
		finally {
			DataBufferUtils.release(dataBuffer);
		}
	}

	private ObjectReader getObjectReader(ResolvableType targetType, @Nullable MimeType mimeType,
			@Nullable Map<String, Object> hints) {
		ObjectMapper mapper = selectObjectMapper(targetType, mimeType);
		if (mapper == null) {
			throw new IllegalStateException("No ObjectMapper for " + targetType);
		}
		return getObjectReader(mapper, targetType, hints);
	}

	private ObjectReader getObjectReader(
			ObjectMapper mapper, ResolvableType elementType, @Nullable Map<String, Object> hints) {

		Assert.notNull(elementType, "'elementType' must not be null");
		Class<?> contextClass = getContextClass(elementType);
		if (contextClass == null && hints != null) {
			contextClass = getContextClass((ResolvableType) hints.get(ACTUAL_TYPE_HINT));
		}
		JavaType javaType = getJavaType(elementType.getType(), contextClass);
		Class<?> jsonView = (hints != null ? (Class<?>) hints.get(Jackson2CodecSupport.JSON_VIEW_HINT) : null);
		return jsonView != null ?
				mapper.readerWithView(jsonView).forType(javaType) :
				mapper.readerFor(javaType);
	}

	/**
	 * Provides the ability for subclasses to customize the {@link ObjectReader} for deserialization from a stream.
	 * @param reader the {@link ObjectReader} available for customization
	 * @param mimeType the MIME type associated with the input stream
	 * @param elementType the expected type of elements in the output stream
	 * @param hints additional information about how to do encode
	 * @return the customized {@link ObjectReader}
	 */
	protected Mono<ObjectReader> customizeReaderFromStream(@NonNull ObjectReader reader, @Nullable MimeType mimeType,
			ResolvableType elementType, @Nullable Map<String, Object> hints) {
		return Mono.just(customizeReader(reader, mimeType, elementType, hints));
	}

	/**
	 * Provides the ability for subclasses to customize the {@link ObjectReader} for deserialization.
	 * @param reader the {@link ObjectReader} available for customization
	 * @param mimeType the MIME type associated with the input stream
	 * @param elementType the expected type of elements in the output stream
	 * @param hints additional information about how to do encode
	 * @return the customized {@link ObjectReader}
	 */
	protected ObjectReader customizeReader(@NonNull ObjectReader reader, @Nullable MimeType mimeType,
			ResolvableType elementType, @Nullable Map<String, Object> hints) {
		return reader;
	}

	@Nullable
	private Class<?> getContextClass(@Nullable ResolvableType elementType) {
		MethodParameter param = (elementType != null ? getParameter(elementType)  : null);
		return (param != null ? param.getContainingClass() : null);
	}

	private void logValue(@Nullable Object value, @Nullable Map<String, Object> hints) {
		if (!Hints.isLoggingSuppressed(hints)) {
			LogFormatUtils.traceDebug(logger, traceOn -> {
				String formatted = LogFormatUtils.formatValue(value, !traceOn);
				return Hints.getLogPrefix(hints) + "Decoded [" + formatted + "]";
			});
		}
	}

	private CodecException processException(IOException ex) {
		if (ex instanceof InvalidDefinitionException) {
			JavaType type = ((InvalidDefinitionException) ex).getType();
			return new CodecException("Type definition error: " + type, ex);
		}
		if (ex instanceof JsonProcessingException) {
			String originalMessage = ((JsonProcessingException) ex).getOriginalMessage();
			return new DecodingException("JSON decoding error: " + originalMessage, ex);
		}
		return new DecodingException("I/O error while parsing input stream", ex);
	}


	// HttpMessageDecoder

	@Override
	public Map<String, Object> getDecodeHints(ResolvableType actualType, ResolvableType elementType,
			ServerHttpRequest request, ServerHttpResponse response) {

		return getHints(actualType);
	}

	@Override
	public List<MimeType> getDecodableMimeTypes() {
		return getMimeTypes();
	}

	@Override
	public List<MimeType> getDecodableMimeTypes(ResolvableType targetType) {
		return getMimeTypes(targetType);
	}

	// Jackson2CodecSupport

	@Override
	protected <A extends Annotation> A getAnnotation(MethodParameter parameter, Class<A> annotType) {
		return parameter.getParameterAnnotation(annotType);
	}

}
