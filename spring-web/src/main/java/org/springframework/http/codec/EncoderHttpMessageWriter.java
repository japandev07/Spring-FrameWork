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

package org.springframework.http.codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;

/**
 * {@code HttpMessageWriter} that wraps and delegates to a {@link Encoder}.
 *
 * <p>Also a {@code ServerHttpMessageWriter} that pre-resolves encoding hints
 * from the extra information available on the server side such as the request
 * or controller method annotations.
 *
 * @author Arjen Poutsma
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class EncoderHttpMessageWriter<T> implements ServerHttpMessageWriter<T> {

	/**
	 * Default list of media types that signify a "streaming" scenario such that
	 * there may be a time lag between items written and hence requires flushing.
	 */
	public static final List<MediaType> DEFAULT_STREAMING_MEDIA_TYPES =
			Collections.singletonList(MediaType.APPLICATION_STREAM_JSON);


	private final Encoder<T> encoder;

	private final List<MediaType> mediaTypes;

	private final MediaType defaultMediaType;

	private final List<MediaType> streamingMediaTypes = new ArrayList<>(1);


	/**
	 * Create an instance wrapping the given {@link Encoder}.
	 */
	public EncoderHttpMessageWriter(Encoder<T> encoder) {
		Assert.notNull(encoder, "Encoder is required");
		this.encoder = encoder;
		this.mediaTypes = MediaType.asMediaTypes(encoder.getEncodableMimeTypes());
		this.defaultMediaType = initDefaultMediaType(this.mediaTypes);
		this.streamingMediaTypes.addAll(DEFAULT_STREAMING_MEDIA_TYPES);
	}

	private static MediaType initDefaultMediaType(List<MediaType> mediaTypes) {
		return mediaTypes.stream().filter(MediaType::isConcrete).findFirst().orElse(null);
	}


	/**
	 * Return the {@code Encoder} of this writer.
	 */
	public Encoder<T> getEncoder() {
		return this.encoder;
	}

	@Override
	public List<MediaType> getWritableMediaTypes() {
		return this.mediaTypes;
	}

	/**
	 * Configure "streaming" media types for which flushing should be performed
	 * automatically vs at the end of the input stream.
	 * <p>By default this is set to {@link #DEFAULT_STREAMING_MEDIA_TYPES}.
	 * @param mediaTypes one or more media types to add to the list
	 */
	public void setStreamingMediaTypes(List<MediaType> mediaTypes) {
		this.streamingMediaTypes.addAll(mediaTypes);
	}

	/**
	 * Return the configured list of "streaming" media types.
	 */
	public List<MediaType> getStreamingMediaTypes() {
		return Collections.unmodifiableList(this.streamingMediaTypes);
	}


	@Override
	public boolean canWrite(ResolvableType elementType, MediaType mediaType) {
		return this.encoder.canEncode(elementType, mediaType);
	}

	@Override
	public Mono<Void> write(Publisher<? extends T> inputStream, ResolvableType elementType,
			MediaType mediaType, ReactiveHttpOutputMessage message,
			Map<String, Object> hints) {

		HttpHeaders headers = message.getHeaders();

		if (headers.getContentType() == null) {
			MediaType fallback = this.defaultMediaType;
			mediaType = useFallback(mediaType, fallback) ? fallback : mediaType;
			if (mediaType != null) {
				mediaType = addDefaultCharset(mediaType, fallback);
				headers.setContentType(mediaType);
			}
		}

		Flux<DataBuffer> body = this.encoder.encode(inputStream,
				message.bufferFactory(), elementType, headers.getContentType(), hints);

		return isStreamingMediaType(headers.getContentType()) ?
				message.writeAndFlushWith(body.map(Flux::just)) :
				message.writeWith(body);
	}

	private static boolean useFallback(MediaType main, MediaType fallback) {
		return main == null || !main.isConcrete() ||
				main.equals(MediaType.APPLICATION_OCTET_STREAM) && fallback != null;
	}

	private static MediaType addDefaultCharset(MediaType main, MediaType defaultType) {
		if (main.getCharset() == null && defaultType != null && defaultType.getCharset() != null) {
			return new MediaType(main, defaultType.getCharset());
		}
		return main;
	}

	private boolean isStreamingMediaType(MediaType contentType) {
		return this.streamingMediaTypes.stream().anyMatch(contentType::isCompatibleWith);
	}


	// ServerHttpMessageWriter...

	@Override
	public Mono<Void> write(Publisher<? extends T> inputStream, ResolvableType actualType,
			ResolvableType elementType, MediaType mediaType, ServerHttpRequest request,
			ServerHttpResponse response, Map<String, Object> hints) {

		Map<String, Object> allHints = new HashMap<>();
		allHints.putAll(getWriteHints(actualType, elementType, mediaType, request, response));
		allHints.putAll(hints);

		return write(inputStream, elementType, mediaType, response, allHints);
	}

	/**
	 * Get additional hints for encoding for example based on the server request
	 * or annotations from controller method parameters. By default, delegate to
	 * the encoder if it is an instance of {@link ServerHttpEncoder}.
	 */
	protected Map<String, Object> getWriteHints(ResolvableType streamType, ResolvableType elementType,
			MediaType mediaType, ServerHttpRequest request, ServerHttpResponse response) {

		if (this.encoder instanceof ServerHttpEncoder) {
			ServerHttpEncoder<?> httpEncoder = (ServerHttpEncoder<?>) this.encoder;
			return httpEncoder.getEncodeHints(streamType, elementType, mediaType, request, response);
		}
		return Collections.emptyMap();
	}

}
