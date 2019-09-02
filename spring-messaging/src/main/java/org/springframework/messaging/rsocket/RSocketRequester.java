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

import java.net.URI;
import java.util.function.Consumer;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.codec.Decoder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.util.MimeType;

/**
 * A thin wrapper around a sending {@link RSocket} with a fluent API accepting
 * and returning higher level Objects for input and for output, along with
 * methods to prepare routing and other metadata.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 5.2
 */
public interface RSocketRequester {

	/**
	 * Return the underlying sending RSocket.
	 */
	RSocket rsocket();

	/**
	 * Return the data {@code MimeType} selected for the underlying RSocket
	 * at connection time. On the client side this is configured via
	 * {@link RSocketRequester.Builder#dataMimeType(MimeType)} while on the
	 * server side it's obtained from the {@link ConnectionSetupPayload}.
	 */
	MimeType dataMimeType();

	/**
	 * Return the metadata {@code MimeType} selected for the underlying RSocket
	 * at connection time. On the client side this is configured via
	 * {@link RSocketRequester.Builder#metadataMimeType(MimeType)} while on the
	 * server side it's obtained from the {@link ConnectionSetupPayload}.
	 */
	MimeType metadataMimeType();

	/**
	 * Begin to specify a new request with the given route to a remote handler.
	 * <p>The route can be a template with placeholders, e.g.
	 * {@code "flight.{code}"} in which case the supplied route variables are
	 * formatted via {@code toString()} and expanded into the template.
	 * If a formatted variable contains a "." it is replaced with the escape
	 * sequence "%2E" to avoid treating it as separator by the responder .
	 * <p>If the connection is set to use composite metadata, the route is
	 * encoded as {@code "message/x.rsocket.routing.v0"}. Otherwise the route
	 * is encoded according to the mime type for the connection.
	 * @param route the route expressing a remote handler mapping
	 * @param routeVars variables to be expanded into the route template
	 * @return a spec for further defining and executing the request
	 */
	RequestSpec route(String route, Object... routeVars);

	/**
	 * Begin to specify a new request with the given metadata value.
	 * @param metadata the metadata value to encode
	 * @param mimeType the mime type that describes the metadata;
	 * This is required for connection using composite metadata. Otherwise the
	 * value is encoded according to the mime type for the connection and this
	 * argument may be left as {@code null}.
	 */
	RequestSpec metadata(Object metadata, @Nullable MimeType mimeType);


	/**
	 * Obtain a builder to create a client {@link RSocketRequester} by connecting
	 * to an RSocket server.
	 */
	static RSocketRequester.Builder builder() {
		return new DefaultRSocketRequesterBuilder();
	}

	/**
	 * Wrap an existing {@link RSocket}. Typically used in client or server
	 * responders to wrap the {@code RSocket} for the remote side.
	 */
	static RSocketRequester wrap(
			RSocket rsocket, MimeType dataMimeType, MimeType metadataMimeType,
			RSocketStrategies strategies) {

		return new DefaultRSocketRequester(rsocket, dataMimeType, metadataMimeType, strategies);
	}


	/**
	 * Builder to create a requester by connecting to a server.
	 */
	interface Builder {

		/**
		 * Configure the payload data MimeType to specify on the {@code SETUP}
		 * frame that applies to the whole connection.
		 * <p>If not set, this will be initialized to the MimeType of the first
		 * {@link RSocketStrategies.Builder#decoder(Decoder[])  non-default}
		 * {@code Decoder}, or otherwise the MimeType of the first decoder.
		 */
		RSocketRequester.Builder dataMimeType(@Nullable MimeType mimeType);

		/**
		 * Configure the payload metadata MimeType to specify on the {@code SETUP}
		 * frame that applies to the whole connection.
		 * <p>By default this is set to
		 * {@code "message/x.rsocket.composite-metadata.v0"} in which case the
		 * route, if provided, is encoded as a {@code "message/x.rsocket.routing.v0"}
		 * composite metadata entry. If this is set to any other MimeType, it is
		 * assumed that's the MimeType for the route, if provided.
		 */
		RSocketRequester.Builder metadataMimeType(MimeType mimeType);

		/**
		 * Set the data for the setup payload. The data will be encoded
		 * according to the configured {@link #dataMimeType(MimeType)}.
		 * <p>By default this is not set.
		 */
		RSocketRequester.Builder setupData(Object data);

		/**
		 * Set the route for the setup payload. The rules for formatting and
		 * encoding the route are the same as those for a request route as
		 * described in {@link #route(String, Object...)}.
		 * <p>By default this is not set.
		 */
		RSocketRequester.Builder setupRoute(String route, Object... routeVars);

		/**
		 * Add metadata entry to the setup payload. Composite metadata must be
		 * in use if this is called more than once or in addition to
		 * {@link #setupRoute(String, Object...)}.
		 */
		RSocketRequester.Builder setupMetadata(Object value, @Nullable MimeType mimeType);

		/**
		 * Provide {@link RSocketStrategies} to use.
		 * <p>By default this is based on default settings of
		 * {@link RSocketStrategies.Builder} but may be further customized via
		 * {@link #rsocketStrategies(Consumer)}.
		 */
		RSocketRequester.Builder rsocketStrategies(@Nullable RSocketStrategies strategies);

		/**
		 * Customize the {@link RSocketStrategies}.
		 * <p>By default this starts out as {@link RSocketStrategies#builder()}.
		 * However if strategies were {@link #rsocketStrategies(RSocketStrategies) set}
		 * explicitly, then they are {@link RSocketStrategies#mutate() mutated}.
		 */
		RSocketRequester.Builder rsocketStrategies(Consumer<RSocketStrategies.Builder> configurer);

		/**
		 * Callback to configure the {@code ClientRSocketFactory} directly.
		 * <ul>
		 * <li>The data and metadata mime types cannot be set directly
		 * on the {@code ClientRSocketFactory} and will be overridden. Use the
		 * shortcuts {@link #dataMimeType(MimeType)} and
		 * {@link #metadataMimeType(MimeType)} on this builder instead.
		 * <li>The frame decoder also cannot be set directly and instead is set
		 * to match the configured {@code DataBufferFactory}.
		 * <li>For the
		 * {@link io.rsocket.RSocketFactory.ClientRSocketFactory#setupPayload(Payload)
		 * setupPayload}, consider using methods on this builder to specify the
		 * route, other metadata, and data as Object values to be encoded.
		 * <li>To configure client side responding, see
		 * {@link RSocketMessageHandler#clientResponder(RSocketStrategies, Object...)}.
		 * </ul>
		 */
		RSocketRequester.Builder rsocketFactory(ClientRSocketFactoryConfigurer configurer);

		/**
		 * Configure this builder through a {@code Consumer}. This enables
		 * libraries such as Spring Security to provide shortcuts for applying
		 * a set of related customizations.
		 * @param configurer the configurer to apply
		 */
		RSocketRequester.Builder apply(Consumer<RSocketRequester.Builder> configurer);

		/**
		 * Connect to the server over TCP.
		 * @param host the server host
		 * @param port the server port
		 * @return an {@code RSocketRequester} for the connection
		 * @see TcpClientTransport
		 */
		Mono<RSocketRequester> connectTcp(String host, int port);

		/**
		 * Connect to the server over WebSocket.
		 * @param uri the RSocket server endpoint URI
		 * @return an {@code RSocketRequester} for the connection
		 * @see WebsocketClientTransport
		 */
		Mono<RSocketRequester> connectWebSocket(URI uri);

		/**
		 * Connect to the server with the given {@code ClientTransport}.
		 * @param transport the client transport to use
		 * @return an {@code RSocketRequester} for the connection
		 */
		Mono<RSocketRequester> connect(ClientTransport transport);

	}


	/**
	 * Spec for providing input data for an RSocket request.
	 */
	interface RequestSpec {

		/**
		 * Use this to append additional metadata entries when using composite
		 * metadata. An {@link IllegalArgumentException} is raised if this
		 * method is used when not using composite metadata.
		 * @param metadata an Object to be encoded with a suitable
		 * {@link org.springframework.core.codec.Encoder Encoder}, or a
		 * {@link org.springframework.core.io.buffer.DataBuffer DataBuffer}
		 * @param mimeType the mime type that describes the metadata
		 */
		RequestSpec metadata(Object metadata, MimeType mimeType);

		/**
		 * Append additional metadata entries through a {@code Consumer}.
		 * This enables libraries such as Spring Security to provide shortcuts
		 * for applying a set of customizations.
		 * @param configurer the configurer to apply
		 * @throws IllegalArgumentException if not using composite metadata.
		 */
		RequestSpec metadata(Consumer<RequestSpec> configurer);

		/**
		 * Provide payload data for the request. This can be one of:
		 * <ul>
		 * <li>Concrete value
		 * <li>{@link Publisher} of value(s)
		 * <li>Any other producer of value(s) that can be adapted to a
		 * {@link Publisher} via {@link ReactiveAdapterRegistry}
		 * </ul>
		 * @param data the Object value for the payload data
		 * @return spec to declare the expected response
		 */
		ResponseSpec data(Object data);

		/**
		 * Variant of {@link #data(Object)} that also accepts a hint for the
		 * types of values that will be produced. The class hint is used to
		 * find a compatible {@code Encoder} once, up front vs per value.
		 * @param producer the source of payload data value(s). This must be a
		 * {@link Publisher} or another producer adaptable to a
		 * {@code Publisher} via {@link ReactiveAdapterRegistry}
		 * @param elementClass the type of values to be produced
		 * @return spec to declare the expected response
		 */
		ResponseSpec data(Object producer, Class<?> elementClass);

		/**
		 * Variant of {@link #data(Object, Class)} for when the type hint has
		 * to have a generic type. See {@link ParameterizedTypeReference}.
		 * @param producer the source of payload data value(s). This must be a
		 * {@link Publisher} or another producer adaptable to a
		 * {@code Publisher} via {@link ReactiveAdapterRegistry}
		 * @param elementTypeRef the type of values to be produced
		 * @return spec to declare the expected response
		 */
		ResponseSpec data(Object producer, ParameterizedTypeReference<?> elementTypeRef);
	}


	/**
	 * Spect to declare the type of request and expected response.
	 */
	interface ResponseSpec {

		/**
		 * Perform a {@link RSocket#fireAndForget fireAndForget}.
		 */
		Mono<Void> send();

		/**
		 * Perform a {@link RSocket#requestResponse requestResponse} exchange.
		 * <p>If the return type is {@code Mono<Void>}, the {@code Mono} will
		 * complete after all data is consumed.
		 * <p><strong>Note:</strong> This method will raise an error if
		 * the request payload is a multi-valued {@link Publisher} as there is
		 * no many-to-one RSocket interaction.
		 * @param dataType the expected data type for the response
		 * @param <T> parameter for the expected data type
		 * @return the decoded response
		 */
		<T> Mono<T> retrieveMono(Class<T> dataType);

		/**
		 * Variant of {@link #retrieveMono(Class)} for when the dataType has
		 * to have a generic type. See {@link ParameterizedTypeReference}.
		 */
		<T> Mono<T> retrieveMono(ParameterizedTypeReference<T> dataTypeRef);

		/**
		 * Perform an {@link RSocket#requestStream requestStream} or a
		 * {@link RSocket#requestChannel requestChannel} exchange depending on
		 * whether the request input is single or multi-payload.
		 * <p>If the return type is {@code Flux<Void>}, the {@code Flux} will
		 * complete after all data is consumed.
		 * @param dataType the expected type for values in the response
		 * @param <T> parameterize the expected type of values
		 * @return the decoded response
		 */
		<T> Flux<T> retrieveFlux(Class<T> dataType);

		/**
		 * Variant of {@link #retrieveFlux(Class)} for when the dataType has
		 * to have a generic type. See {@link ParameterizedTypeReference}.
		 */
		<T> Flux<T> retrieveFlux(ParameterizedTypeReference<T> dataTypeRef);
	}

}
