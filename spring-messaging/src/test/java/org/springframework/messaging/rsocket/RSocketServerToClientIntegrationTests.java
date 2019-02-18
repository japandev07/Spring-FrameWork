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
package org.springframework.messaging.rsocket;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import io.rsocket.Closeable;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.messaging.ReactiveMessageChannel;
import org.springframework.messaging.ReactiveSubscribableChannel;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.support.DefaultReactiveMessageChannel;
import org.springframework.stereotype.Controller;

/**
 * Client-side handling of requests initiated from the server side.
 *
 *  @author Rossen Stoyanchev
 */
public class RSocketServerToClientIntegrationTests {

	private static AnnotationConfigApplicationContext context;

	private static Closeable server;

	private static MessagingAcceptor clientAcceptor;


	@BeforeClass
	@SuppressWarnings("ConstantConditions")
	public static void setupOnce() {

		context = new AnnotationConfigApplicationContext(ServerConfig.class);

		ReactiveMessageChannel messageChannel = context.getBean("serverChannel", ReactiveMessageChannel.class);
		RSocketStrategies rsocketStrategies = context.getBean(RSocketStrategies.class);

		clientAcceptor = new MessagingAcceptor(
				context.getBean("clientChannel", ReactiveMessageChannel.class));

		server = RSocketFactory.receive()
				.acceptor(new MessagingAcceptor(messageChannel, rsocketStrategies))
				.transport(TcpServerTransport.create("localhost", 7000))
				.start()
				.block();
	}

	@AfterClass
	public static void tearDownOnce() {
		server.dispose();
	}


	@Test
	public void echo() {
		connectAndVerify("connect.echo");
	}

	@Test
	public void echoAsync() {
		connectAndVerify("connect.echo-async");
	}

	@Test
	public void echoStream() {
		connectAndVerify("connect.echo-stream");
	}

	@Test
	public void echoChannel() {
		connectAndVerify("connect.echo-channel");
	}


	private static void connectAndVerify(String destination) {

		ServerController serverController = context.getBean(ServerController.class);
		serverController.reset();

		RSocket rsocket = null;
		try {
			rsocket = RSocketFactory.connect()
					.setupPayload(DefaultPayload.create("", destination))
					.dataMimeType("text/plain")
					.acceptor(clientAcceptor)
					.transport(TcpClientTransport.create("localhost", 7000))
					.start()
					.block();

			serverController.await(Duration.ofSeconds(5));
		}
		finally {
			if (rsocket != null) {
				rsocket.dispose();
			}
		}
	}


	@Controller
	@SuppressWarnings({"unused", "NullableProblems"})
	static class ServerController {

		// Must be initialized by @Test method...
		volatile MonoProcessor<Void> result;


		@MessageMapping("connect.echo")
		void echo(RSocketRequester requester) {
			runTest(() -> {
				Flux<String> result = Flux.range(1, 3).concatMap(i ->
						requester.route("echo").data("Hello " + i).retrieveMono(String.class));

				StepVerifier.create(result)
						.expectNext("Hello 1")
						.expectNext("Hello 2")
						.expectNext("Hello 3")
						.verifyComplete();
			});
		}

		@MessageMapping("connect.echo-async")
		void echoAsync(RSocketRequester requester) {
			runTest(() -> {
				Flux<String> result = Flux.range(1, 3).concatMap(i ->
						requester.route("echo-async").data("Hello " + i).retrieveMono(String.class));

				StepVerifier.create(result)
						.expectNext("Hello 1 async")
						.expectNext("Hello 2 async")
						.expectNext("Hello 3 async")
						.verifyComplete();
			});
		}

		@MessageMapping("connect.echo-stream")
		void echoStream(RSocketRequester requester) {
			runTest(() -> {
				Flux<String> result = requester.route("echo-stream").data("Hello").retrieveFlux(String.class);

				StepVerifier.create(result)
						.expectNext("Hello 0")
						.expectNextCount(5)
						.expectNext("Hello 6")
						.expectNext("Hello 7")
						.thenCancel()
						.verify();
			});
		}

		@MessageMapping("connect.echo-channel")
		void echoChannel(RSocketRequester requester) {
			runTest(() -> {
				Flux<String> result = requester.route("echo-channel")
						.data(Flux.range(1, 10).map(i -> "Hello " + i), String.class)
						.retrieveFlux(String.class);

				StepVerifier.create(result)
						.expectNext("Hello 1 async")
						.expectNextCount(7)
						.expectNext("Hello 9 async")
						.expectNext("Hello 10 async")
						.verifyComplete();
			});
		}


		private void runTest(Runnable testEcho) {
			Mono.fromRunnable(testEcho)
					.doOnError(ex -> result.onError(ex))
					.doOnSuccess(o -> result.onComplete())
					.subscribeOn(Schedulers.elastic())
					.subscribe();
		}

		private static Payload payload(String destination, String data) {
			return DefaultPayload.create(data, destination);
		}


		public void reset() {
			this.result = MonoProcessor.create();
		}

		public void await(Duration duration) {
			this.result.block(duration);
		}
	}


	private static class ClientController {

		final ReplayProcessor<String> fireForgetPayloads = ReplayProcessor.create();


		@MessageMapping("receive")
		void receive(String payload) {
			this.fireForgetPayloads.onNext(payload);
		}

		@MessageMapping("echo")
		String echo(String payload) {
			return payload;
		}

		@MessageMapping("echo-async")
		Mono<String> echoAsync(String payload) {
			return Mono.delay(Duration.ofMillis(10)).map(aLong -> payload + " async");
		}

		@MessageMapping("echo-stream")
		Flux<String> echoStream(String payload) {
			return Flux.interval(Duration.ofMillis(10)).map(aLong -> payload + " " + aLong);
		}

		@MessageMapping("echo-channel")
		Flux<String> echoChannel(Flux<String> payloads) {
			return payloads.delayElements(Duration.ofMillis(10)).map(payload -> payload + " async");
		}
	}


	@Configuration
	static class ServerConfig {

		@Bean
		public ClientController clientController() {
			return new ClientController();
		}

		@Bean
		public ServerController serverController() {
			return new ServerController();
		}

		@Bean
		public ReactiveSubscribableChannel clientChannel() {
			return new DefaultReactiveMessageChannel();
		}

		@Bean
		public ReactiveSubscribableChannel serverChannel() {
			return new DefaultReactiveMessageChannel();
		}

		@Bean
		public RSocketMessageHandler clientMessageHandler() {
			List<Object> handlers = Collections.singletonList(clientController());
			RSocketMessageHandler handler = new RSocketMessageHandler(clientChannel(), handlers);
			handler.setRSocketStrategies(rsocketStrategies());
			return handler;
		}

		@Bean
		public RSocketMessageHandler serverMessageHandler() {
			RSocketMessageHandler handler = new RSocketMessageHandler(serverChannel());
			handler.setRSocketStrategies(rsocketStrategies());
			return handler;
		}

		@Bean
		public RSocketStrategies rsocketStrategies() {
			return RSocketStrategies.builder()
					.decoder(StringDecoder.allMimeTypes())
					.encoder(CharSequenceEncoder.allMimeTypes())
					.build();
		}
	}

}
