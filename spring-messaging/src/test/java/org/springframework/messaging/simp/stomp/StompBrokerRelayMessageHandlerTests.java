/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.messaging.simp.stomp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.Before;
import org.junit.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.StubMessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.tcp.ReconnectStrategy;
import org.springframework.messaging.tcp.TcpConnection;
import org.springframework.messaging.tcp.TcpConnectionHandler;
import org.springframework.messaging.tcp.TcpOperations;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureTask;

import static org.junit.Assert.*;

/**
 * Unit tests for StompBrokerRelayMessageHandler.
 *
 * @author Rossen Stoyanchev
 */
public class StompBrokerRelayMessageHandlerTests {

	private StompBrokerRelayMessageHandler brokerRelay;

	private StubTcpOperations tcpClient;


	@Before
	public void setup() {

		this.tcpClient = new StubTcpOperations();

		this.brokerRelay = new StompBrokerRelayMessageHandler(new StubMessageChannel(),
				new StubMessageChannel(), new StubMessageChannel(), Arrays.asList("/topic")) {

			@Override
			protected void startInternal() {
				publishBrokerAvailableEvent(); // Force this, since we'll never actually connect
				super.startInternal();
			}
		};

		this.brokerRelay.setTcpClient(this.tcpClient);
	}


	@Test
	public void testVirtualHostHeader() throws Exception {

		String virtualHost = "ABC";
		this.brokerRelay.setVirtualHost(virtualHost);
		this.brokerRelay.start();

		String sessionId = "sess1";
		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
		headers.setSessionId(sessionId);
		this.brokerRelay.handleMessage(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));

		List<Message<byte[]>> sent = this.tcpClient.connection.messages;
		assertEquals(2, sent.size());

		StompHeaderAccessor headers1 = StompHeaderAccessor.wrap(sent.get(0));
		assertEquals(virtualHost, headers1.getHost());
		assertNotNull("The prepared message does not have an accessor",
				MessageHeaderAccessor.getAccessor(sent.get(0), MessageHeaderAccessor.class));

		StompHeaderAccessor headers2 = StompHeaderAccessor.wrap(sent.get(1));
		assertEquals(sessionId, headers2.getSessionId());
		assertEquals(virtualHost, headers2.getHost());
		assertNotNull("The prepared message does not have an accessor",
				MessageHeaderAccessor.getAccessor(sent.get(1), MessageHeaderAccessor.class));
	}

	@Test
	public void testLoginPasscode() throws Exception {

		this.brokerRelay.setClientLogin("clientlogin");
		this.brokerRelay.setClientPasscode("clientpasscode");

		this.brokerRelay.setSystemLogin("syslogin");
		this.brokerRelay.setSystemPasscode("syspasscode");

		this.brokerRelay.start();

		String sessionId = "sess1";
		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
		headers.setSessionId(sessionId);
		this.brokerRelay.handleMessage(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));

		List<Message<byte[]>> sent = this.tcpClient.connection.messages;
		assertEquals(2, sent.size());

		StompHeaderAccessor headers1 = StompHeaderAccessor.wrap(sent.get(0));
		assertEquals("syslogin", headers1.getLogin());
		assertEquals("syspasscode", headers1.getPasscode());

		StompHeaderAccessor headers2 = StompHeaderAccessor.wrap(sent.get(1));
		assertEquals("clientlogin", headers2.getLogin());
		assertEquals("clientpasscode", headers2.getPasscode());
	}

	@Test
	public void testDestinationExcluded() throws Exception {

		this.brokerRelay.start();

		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headers.setSessionId("sess1");
		headers.setDestination("/user/daisy/foo");
		this.brokerRelay.handleMessage(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));

		List<Message<byte[]>> sent = this.tcpClient.connection.messages;
		assertEquals(1, sent.size());
		assertEquals(StompCommand.CONNECT, StompHeaderAccessor.wrap(sent.get(0)).getCommand());
		assertNotNull("The prepared message does not have an accessor",
				MessageHeaderAccessor.getAccessor(sent.get(0), MessageHeaderAccessor.class));
	}


	private static ListenableFutureTask<Void> getVoidFuture() {
		ListenableFutureTask<Void> futureTask = new ListenableFutureTask<>(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				return null;
			}
		});
		futureTask.run();
		return futureTask;
	}

	private static ListenableFutureTask<Boolean> getBooleanFuture() {
		ListenableFutureTask<Boolean> futureTask = new ListenableFutureTask<>(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return null;
			}
		});
		futureTask.run();
		return futureTask;
	}


	private static class StubTcpOperations implements TcpOperations<byte[]> {

		private StubTcpConnection connection = new StubTcpConnection();


		@Override
		public ListenableFuture<Void> connect(TcpConnectionHandler<byte[]> connectionHandler) {
			connectionHandler.afterConnected(this.connection);
			return getVoidFuture();
		}

		@Override
		public ListenableFuture<Void> connect(TcpConnectionHandler<byte[]> connectionHandler, ReconnectStrategy reconnectStrategy) {
			connectionHandler.afterConnected(this.connection);
			return getVoidFuture();
		}

		@Override
		public ListenableFuture<Boolean> shutdown() {
			return getBooleanFuture();
		}
	}


	private static class StubTcpConnection implements TcpConnection<byte[]> {

		private final List<Message<byte[]>> messages = new ArrayList<>();


		@Override
		public ListenableFuture<Void> send(Message<byte[]> message) {
			this.messages.add(message);
			return getVoidFuture();
		}

		@Override
		public void onReadInactivity(Runnable runnable, long duration) {
		}

		@Override
		public void onWriteInactivity(Runnable runnable, long duration) {
		}

		@Override
		public void close() {
		}
	}

}
