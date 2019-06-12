/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.messaging.rsocket;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.metadata.CompositeMetadataFlyweight;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * Default, package-private {@link RSocketRequester} implementation.
 *
 * @author Rossen Stoyanchev
 * @since 5.2
 */
final class DefaultRSocketRequester implements RSocketRequester {

	static final MimeType COMPOSITE_METADATA = new MimeType("message", "x.rsocket.composite-metadata.v0");

	static final MimeType ROUTING = new MimeType("message", "x.rsocket.routing.v0");

	static final List<MimeType> METADATA_MIME_TYPES = Arrays.asList(COMPOSITE_METADATA, ROUTING);


	private static final Map<String, Object> EMPTY_HINTS = Collections.emptyMap();


	private final RSocket rsocket;

	private final MimeType dataMimeType;

	private final MimeType metadataMimeType;

	private final RSocketStrategies strategies;

	private final DataBuffer emptyDataBuffer;


	DefaultRSocketRequester(
			RSocket rsocket, MimeType dataMimeType, MimeType metadataMimeType,
			RSocketStrategies strategies) {

		Assert.notNull(rsocket, "RSocket is required");
		Assert.notNull(dataMimeType, "'dataMimeType' is required");
		Assert.notNull(metadataMimeType, "'metadataMimeType' is required");
		Assert.notNull(strategies, "RSocketStrategies is required");

		Assert.isTrue(METADATA_MIME_TYPES.contains(metadataMimeType),
				() -> "Unexpected metadatata mime type: '" + metadataMimeType + "'");

		this.rsocket = rsocket;
		this.dataMimeType = dataMimeType;
		this.metadataMimeType = metadataMimeType;
		this.strategies = strategies;
		this.emptyDataBuffer = this.strategies.dataBufferFactory().wrap(new byte[0]);
	}


	@Override
	public RSocket rsocket() {
		return this.rsocket;
	}

	@Override
	public MimeType dataMimeType() {
		return this.dataMimeType;
	}

	@Override
	public MimeType metadataMimeType() {
		return this.metadataMimeType;
	}

	@Override
	public RequestSpec route(String route) {
		return new DefaultRequestSpec(route);
	}


	private static boolean isVoid(ResolvableType elementType) {
		return (Void.class.equals(elementType.resolve()) || void.class.equals(elementType.resolve()));
	}

	private DataBufferFactory bufferFactory() {
		return this.strategies.dataBufferFactory();
	}


	private class DefaultRequestSpec implements RequestSpec {

		private final Map<Object, MimeType> metadata = new LinkedHashMap<>(4);


		public DefaultRequestSpec(String route) {
			Assert.notNull(route, "'route' is required");
			metadata(route, ROUTING);
		}


		@Override
		public RequestSpec metadata(Object metadata, MimeType mimeType) {
			Assert.isTrue(this.metadata.isEmpty() || metadataMimeType().equals(COMPOSITE_METADATA),
					"Additional metadata entries supported only with composite metadata");
			this.metadata.put(metadata, mimeType);
			return this;
		}

		@Override
		public ResponseSpec data(Object data) {
			Assert.notNull(data, "'data' must not be null");
			return toResponseSpec(data, ResolvableType.NONE);
		}

		@Override
		public <T, P extends Publisher<T>> ResponseSpec data(P publisher, Class<T> dataType) {
			Assert.notNull(publisher, "'publisher' must not be null");
			Assert.notNull(dataType, "'dataType' must not be null");
			return toResponseSpec(publisher, ResolvableType.forClass(dataType));
		}

		@Override
		public <T, P extends Publisher<T>> ResponseSpec data(P publisher, ParameterizedTypeReference<T> dataTypeRef) {
			Assert.notNull(publisher, "'publisher' must not be null");
			Assert.notNull(dataTypeRef, "'dataTypeRef' must not be null");
			return toResponseSpec(publisher, ResolvableType.forType(dataTypeRef));
		}

		private ResponseSpec toResponseSpec(Object input, ResolvableType dataType) {
			ReactiveAdapter adapter = strategies.reactiveAdapterRegistry().getAdapter(input.getClass());
			Publisher<?> publisher;
			if (input instanceof Publisher) {
				publisher = (Publisher<?>) input;
			}
			else if (adapter != null) {
				publisher = adapter.toPublisher(input);
			}
			else {
				Mono<Payload> payloadMono = Mono
						.fromCallable(() -> encodeData(input, ResolvableType.forInstance(input), null))
						.map(this::firstPayload)
						.doOnDiscard(Payload.class, Payload::release)
						.switchIfEmpty(emptyPayload());
				return new DefaultResponseSpec(payloadMono);
			}

			if (isVoid(dataType) || (adapter != null && adapter.isNoValue())) {
				Mono<Payload> payloadMono = Mono.when(publisher).then(emptyPayload());
				return new DefaultResponseSpec(payloadMono);
			}

			Encoder<?> encoder = dataType != ResolvableType.NONE && !Object.class.equals(dataType.resolve()) ?
					strategies.encoder(dataType, dataMimeType) : null;

			if (adapter != null && !adapter.isMultiValue()) {
				Mono<Payload> payloadMono = Mono.from(publisher)
						.map(value -> encodeData(value, dataType, encoder))
						.map(this::firstPayload)
						.switchIfEmpty(emptyPayload());
				return new DefaultResponseSpec(payloadMono);
			}

			Flux<Payload> payloadFlux = Flux.from(publisher)
					.map(value -> encodeData(value, dataType, encoder))
					.switchOnFirst((signal, inner) -> {
						DataBuffer data = signal.get();
						if (data != null) {
							return Mono.fromCallable(() -> firstPayload(data))
									.concatWith(inner.skip(1).map(PayloadUtils::createPayload));
						}
						else {
							return inner.map(PayloadUtils::createPayload);
						}
					})
					.doOnDiscard(Payload.class, Payload::release)
					.switchIfEmpty(emptyPayload());
			return new DefaultResponseSpec(payloadFlux);
		}

		@SuppressWarnings("unchecked")
		private <T> DataBuffer encodeData(T value, ResolvableType valueType, @Nullable Encoder<?> encoder) {
			if (value instanceof DataBuffer) {
				return (DataBuffer) value;
			}
			if (encoder == null) {
				valueType = ResolvableType.forInstance(value);
				encoder = strategies.encoder(valueType, dataMimeType);
			}
			return ((Encoder<T>) encoder).encodeValue(
					value, bufferFactory(), valueType, dataMimeType, EMPTY_HINTS);
		}

		private Payload firstPayload(DataBuffer data) {
			DataBuffer metadata;
			try {
				metadata = getMetadata();
				return PayloadUtils.createPayload(metadata, data);
			}
			catch (Throwable ex) {
				DataBufferUtils.release(data);
				throw ex;
			}
		}

		private Mono<Payload> emptyPayload() {
			return Mono.fromCallable(() -> firstPayload(emptyDataBuffer));
		}

		private DataBuffer getMetadata() {
			if (metadataMimeType().equals(COMPOSITE_METADATA)) {
				CompositeByteBuf metadata = getAllocator().compositeBuffer();
				this.metadata.forEach((key, value) -> {
					DataBuffer dataBuffer = encodeMetadata(key, value);
					CompositeMetadataFlyweight.encodeAndAddMetadata(metadata, getAllocator(), value.toString(),
							dataBuffer instanceof NettyDataBuffer ?
									((NettyDataBuffer) dataBuffer).getNativeBuffer() :
									Unpooled.wrappedBuffer(dataBuffer.asByteBuffer()));

				});
				return asDataBuffer(metadata);
			}
			Assert.isTrue(this.metadata.size() < 2, "Composite metadata required for multiple entries");
			Map.Entry<Object, MimeType> entry = this.metadata.entrySet().iterator().next();
			Assert.isTrue(metadataMimeType().equals(entry.getValue()),
					() -> "Expected metadata MimeType '" + metadataMimeType() + "', actual " + this.metadata);
			return encodeMetadata(entry.getKey(), entry.getValue());
		}

		@SuppressWarnings("unchecked")
		private <T> DataBuffer encodeMetadata(Object metadata, MimeType mimeType) {
			if (metadata instanceof DataBuffer) {
				return (DataBuffer) metadata;
			}
			ResolvableType type = ResolvableType.forInstance(metadata);
			Encoder<T> encoder = strategies.encoder(type, mimeType);
			Assert.notNull(encoder, () -> "No encoder for metadata " + metadata + ", mimeType '" + mimeType + "'");
			return encoder.encodeValue((T) metadata, bufferFactory(), type, mimeType, EMPTY_HINTS);
		}

		private ByteBufAllocator getAllocator() {
			return bufferFactory() instanceof NettyDataBufferFactory ?
					((NettyDataBufferFactory) bufferFactory()).getByteBufAllocator() :
					ByteBufAllocator.DEFAULT;
		}

		private DataBuffer asDataBuffer(ByteBuf byteBuf) {
			if (bufferFactory() instanceof NettyDataBufferFactory) {
				return ((NettyDataBufferFactory) bufferFactory()).wrap(byteBuf);
			}
			else {
				DataBuffer dataBuffer = bufferFactory().wrap(byteBuf.nioBuffer());
				byteBuf.release();
				return dataBuffer;
			}
		}
	}


	private class DefaultResponseSpec implements ResponseSpec {

		@Nullable
		private final Mono<Payload> payloadMono;

		@Nullable
		private final Flux<Payload> payloadFlux;

		DefaultResponseSpec(Mono<Payload> payloadMono) {
			this.payloadMono = payloadMono;
			this.payloadFlux = null;
		}

		DefaultResponseSpec(Flux<Payload> payloadFlux) {
			this.payloadMono = null;
			this.payloadFlux = payloadFlux;
		}

		@Override
		public Mono<Void> send() {
			Assert.state(this.payloadMono != null, "No RSocket interaction model for one-way send with Flux");
			return this.payloadMono.flatMap(rsocket::fireAndForget);
		}

		@Override
		public <T> Mono<T> retrieveMono(Class<T> dataType) {
			return retrieveMono(ResolvableType.forClass(dataType));
		}

		@Override
		public <T> Mono<T> retrieveMono(ParameterizedTypeReference<T> dataTypeRef) {
			return retrieveMono(ResolvableType.forType(dataTypeRef));
		}

		@Override
		public <T> Flux<T> retrieveFlux(Class<T> dataType) {
			return retrieveFlux(ResolvableType.forClass(dataType));
		}

		@Override
		public <T> Flux<T> retrieveFlux(ParameterizedTypeReference<T> dataTypeRef) {
			return retrieveFlux(ResolvableType.forType(dataTypeRef));
		}

		@SuppressWarnings("unchecked")
		private <T> Mono<T> retrieveMono(ResolvableType elementType) {
			Assert.notNull(this.payloadMono, "No RSocket interaction model for Flux request to Mono response.");
			Mono<Payload> payloadMono = this.payloadMono.flatMap(rsocket::requestResponse);

			if (isVoid(elementType)) {
				return (Mono<T>) payloadMono.then();
			}

			Decoder<?> decoder = strategies.decoder(elementType, dataMimeType);
			return (Mono<T>) payloadMono.map(this::retainDataAndReleasePayload)
					.map(dataBuffer -> decoder.decode(dataBuffer, elementType, dataMimeType, EMPTY_HINTS));
		}

		@SuppressWarnings("unchecked")
		private <T> Flux<T> retrieveFlux(ResolvableType elementType) {
			Flux<Payload> payloadFlux = this.payloadMono != null ?
					this.payloadMono.flatMapMany(rsocket::requestStream) :
					rsocket.requestChannel(this.payloadFlux);

			if (isVoid(elementType)) {
				return payloadFlux.thenMany(Flux.empty());
			}

			Decoder<?> decoder = strategies.decoder(elementType, dataMimeType);
			return payloadFlux.map(this::retainDataAndReleasePayload).map(dataBuffer ->
					(T) decoder.decode(dataBuffer, elementType, dataMimeType, EMPTY_HINTS));
		}

		private DataBuffer retainDataAndReleasePayload(Payload payload) {
			return PayloadUtils.retainDataAndReleasePayload(payload, bufferFactory());
		}
	}

}
