/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.messaging.tcp.reactor;

import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.tcp.ReconnectStrategy;
import org.springframework.messaging.tcp.TcpConnectionHandler;
import org.springframework.messaging.tcp.TcpOperations;
import org.springframework.util.Assert;
import org.springframework.util.concurrent.ListenableFuture;

import reactor.core.Environment;
import reactor.core.composable.Composable;
import reactor.core.composable.Deferred;
import reactor.core.composable.Promise;
import reactor.core.composable.Stream;
import reactor.core.composable.spec.Promises;
import reactor.function.Consumer;
import reactor.function.support.SingleUseConsumer;
import reactor.io.Buffer;
import reactor.tcp.Reconnect;
import reactor.tcp.TcpClient;
import reactor.tcp.TcpConnection;
import reactor.tcp.encoding.Codec;
import reactor.tcp.netty.NettyTcpClient;
import reactor.tcp.spec.TcpClientSpec;
import reactor.tuple.Tuple;
import reactor.tuple.Tuple2;


/**
 * An implementation of {@link org.springframework.messaging.tcp.TcpOperations}
 * based on the TCP client support of the Reactor project.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class ReactorTcpClient<P> implements TcpOperations<P> {

	public static final Class<NettyTcpClient> REACTOR_TCP_CLIENT_TYPE = NettyTcpClient.class;


	private final static Log logger = LogFactory.getLog(ReactorTcpClient.class);

	private final TcpClient<Message<P>, Message<P>> tcpClient;

	private final Environment environment;


	/**
	 * A constructor that creates a {@link reactor.tcp.netty.NettyTcpClient} with
	 * a {@link reactor.event.dispatch.SynchronousDispatcher} as a result of which
	 * network I/O is handled in Netty threads.
	 *
	 * <p>Also see the constructor accepting a pre-configured Reactor
	 * {@link reactor.tcp.TcpClient}.
	 *
	 * @param host the host to connect to
	 * @param port the port to connect to
	 * @param codec the codec to use for encoding and decoding the TCP stream
	 */
	public ReactorTcpClient(String host, int port, Codec<Buffer, Message<P>, Message<P>> codec) {

		// Revisit in 1.1: is Environment still required w/ sync dispatcher?
		this.environment = new Environment();

		this.tcpClient = new TcpClientSpec<Message<P>, Message<P>>(REACTOR_TCP_CLIENT_TYPE)
				.env(this.environment)
				.codec(codec)
				.connect(host, port)
				.synchronousDispatcher()
				.get();

		checkReactorVersion();
	}

	/**
	 * A constructor with a pre-configured {@link reactor.tcp.TcpClient}.
	 *
	 * <p><strong>NOTE:</strong> if the client is configured with a thread-creating
	 * dispatcher, you are responsible for shutting down the {@link reactor.core.Environment}
	 * instance with which the client is configured.
	 *
	 * @param tcpClient the TcpClient to use
	 */
	public ReactorTcpClient(TcpClient<Message<P>, Message<P>> tcpClient) {
		Assert.notNull(tcpClient, "'tcpClient' must not be null");
		this.tcpClient = tcpClient;
		this.environment = null;
		checkReactorVersion();
	}

	private static void checkReactorVersion() {
		Class<?> type = null;
		try {
			type = ReactorTcpClient.class.getClassLoader().loadClass("reactor.event.dispatch.BaseDispatcher");
			Assert.isTrue(Modifier.isPublic(type.getModifiers()),
					"Detected older version of reactor-tcp. Switch to 1.0.1.RELEASE or higher.");
		}
		catch (ClassNotFoundException e) {
			// Ignore, must be 1.1+
		}
	}


	@Override
	public ListenableFuture<Void> connect(TcpConnectionHandler<P> connectionHandler) {

		Promise<TcpConnection<Message<P>, Message<P>>> promise = this.tcpClient.open();
		composeConnectionHandling(promise, connectionHandler);

		return new AbstractPromiseToListenableFutureAdapter<TcpConnection<Message<P>, Message<P>>, Void>(promise) {
			@Override
			protected Void adapt(TcpConnection<Message<P>, Message<P>> result) {
				return null;
			}
		};
	}

	@Override
	public ListenableFuture<Void> connect(final TcpConnectionHandler<P> connectionHandler,
			final ReconnectStrategy reconnectStrategy) {

		Assert.notNull(reconnectStrategy, "ReconnectStrategy must not be null");

		Stream<TcpConnection<Message<P>, Message<P>>> stream =
				this.tcpClient.open(new Reconnect() {
					@Override
					public Tuple2<InetSocketAddress, Long> reconnect(InetSocketAddress address, int attempt) {
						return Tuple.of(address, reconnectStrategy.getTimeToNextAttempt(attempt));
					}
				});
		composeConnectionHandling(stream, connectionHandler);

		return new PassThroughPromiseToListenableFutureAdapter<Void>(toPromise(stream));
	}

	private void composeConnectionHandling(Composable<TcpConnection<Message<P>, Message<P>>> composable,
			final TcpConnectionHandler<P> connectionHandler) {

		composable.when(Throwable.class, new Consumer<Throwable>() {
			@Override
			public void accept(Throwable ex) {
				connectionHandler.afterConnectFailure(ex);
			}
		});

		composable.consume(new Consumer<TcpConnection<Message<P>, Message<P>>>() {
			@Override
			public void accept(TcpConnection<Message<P>, Message<P>> connection) {
				connection.on().close(new Runnable() {
					@Override
					public void run() {
						connectionHandler.afterConnectionClosed();
					}
				});
				connection.consume(new Consumer<Message<P>>() {
					@Override
					public void accept(Message<P> message) {
						connectionHandler.handleMessage(message);
					}
				});
				connection.when(Throwable.class, new Consumer<Throwable>() {
					@Override
					public void accept(Throwable t) {
						logger.error("Exception on connection " + connectionHandler, t);
					}
				});
				connectionHandler.afterConnected(new ReactorTcpConnection<P>(connection));
			}
		});
	}

	private Promise<Void> toPromise(Stream<TcpConnection<Message<P>, Message<P>>> stream) {

		final Deferred<Void,Promise<Void>> deferred = Promises.<Void>defer().get();

		stream.consume(SingleUseConsumer.once(new Consumer<TcpConnection<Message<P>, Message<P>>>() {
			@Override
			public void accept(TcpConnection<Message<P>, Message<P>> conn) {
				deferred.accept((Void) null);
			}
		}));

		stream.when(Throwable.class, SingleUseConsumer.once(new Consumer<Throwable>() {
			@Override
			public void accept(Throwable throwable) {
				deferred.accept(throwable);
			}
		}));

		return deferred.compose();
	}

	@Override
	public ListenableFuture<Void> shutdown() {
		try {
			Promise<Void> promise = this.tcpClient.close();
			return new AbstractPromiseToListenableFutureAdapter<Void, Void>(promise) {
				@Override
				protected Void adapt(Void result) {
					return result;
				}
			};
		}
		finally {
			this.environment.shutdown();

		}
	}

}