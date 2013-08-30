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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.broker.BrokerService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.BrokerAvailabilityEvent;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.channel.ExecutorSubscribableChannel;
import org.springframework.util.Assert;
import org.springframework.util.SocketUtils;

import static org.junit.Assert.*;

/**
 * @author Rossen Stoyanchev
 */
public class StompBrokerRelayMessageHandlerIntegrationTests {

	private static final Log logger = LogFactory.getLog(StompBrokerRelayMessageHandlerIntegrationTests.class);

	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private StompBrokerRelayMessageHandler relay;

	private BrokerService activeMQBroker;

	private ExecutorSubscribableChannel responseChannel;

	private ExpectationMatchingMessageHandler responseHandler;

	private ExpectationMatchingEventPublisher eventPublisher;

	@Before
	public void setUp() throws Exception {

		int port = SocketUtils.findAvailableTcpPort(61613);

		this.activeMQBroker = new BrokerService();
		this.activeMQBroker.addConnector("stomp://localhost:" + port);
		this.activeMQBroker.setStartAsync(false);
		this.activeMQBroker.setDeleteAllMessagesOnStartup(true);
		this.activeMQBroker.start();

		this.responseChannel = new ExecutorSubscribableChannel();
		this.responseHandler = new ExpectationMatchingMessageHandler();
		this.responseChannel.subscribe(this.responseHandler);

		this.eventPublisher = new ExpectationMatchingEventPublisher();

		this.relay = new StompBrokerRelayMessageHandler(this.responseChannel, Arrays.asList("/queue/", "/topic/"));
		this.relay.setRelayPort(port);
		this.relay.setApplicationEventPublisher(this.eventPublisher);
		this.relay.start();
	}

	@After
	public void tearDown() throws Exception {
		try {
			this.relay.stop();
		}
		finally {
			stopBrokerAndAwait();
		}
	}

	@Test
	public void publishSubscribe() throws Exception {

		String sess1 = "sess1";
		MessageExchange conn1 = MessageExchangeBuilder.connect(sess1).build();
		this.relay.handleMessage(conn1.message);

		String sess2 = "sess2";
		MessageExchange conn2 = MessageExchangeBuilder.connect(sess2).build();
		this.relay.handleMessage(conn2.message);

		String subs1 = "subs1";
		String destination = "/topic/test";

		MessageExchange subscribe = MessageExchangeBuilder.subscribeWithReceipt(sess1, subs1, destination, "r1").build();
		this.responseHandler.expect(subscribe);

		this.relay.handleMessage(subscribe.message);
		this.responseHandler.awaitAndAssert();

		MessageExchange send = MessageExchangeBuilder.send(destination, "foo").andExpectMessage(sess1, subs1).build();
		this.responseHandler.reset();
		this.responseHandler.expect(send);

		this.relay.handleMessage(send.message);
		this.responseHandler.awaitAndAssert();
	}

	@Test
	public void brokerUnvailableErrorFrameOnConnect() throws Exception {

		stopBrokerAndAwait();

		MessageExchange connect = MessageExchangeBuilder.connect("sess1").andExpectError().build();
		this.responseHandler.expect(connect);

		this.relay.handleMessage(connect.message);
		this.responseHandler.awaitAndAssert();
	}

	@Test
	public void brokerUnvailableErrorFrameOnSend() throws Exception {

		String sess1 = "sess1";
		MessageExchange connect = MessageExchangeBuilder.connect(sess1).build();
		this.relay.handleMessage(connect.message);

		// TODO: expect CONNECTED
		Thread.sleep(2000);

		stopBrokerAndAwait();

		MessageExchange subscribe = MessageExchangeBuilder.subscribe(sess1, "s1", "/topic/a").andExpectError().build();
		this.responseHandler.expect(subscribe);

		this.relay.handleMessage(subscribe.message);
		this.responseHandler.awaitAndAssert();
	}

	@Test
	public void brokerAvailabilityEvents() throws Exception {

		// TODO: expect CONNECTED
		Thread.sleep(2000);

		this.eventPublisher.expect(true, false);

		stopBrokerAndAwait();

		// TODO: remove when stop is detecteded
		this.relay.handleMessage(MessageExchangeBuilder.connect("sess1").build().message);

		this.eventPublisher.awaitAndAssert();
	}

	@Test
	public void relayReconnectsIfBrokerComesBackUp() throws Exception {

		String sess1 = "sess1";
		MessageExchange conn1 = MessageExchangeBuilder.connect(sess1).build();
		this.relay.handleMessage(conn1.message);

		String subs1 = "subs1";
		String destination = "/topic/test";
		MessageExchange subscribe = MessageExchangeBuilder.subscribeWithReceipt(sess1, subs1, destination, "r1").build();
		this.responseHandler.expect(subscribe);

		this.relay.handleMessage(subscribe.message);
		this.responseHandler.awaitAndAssert();

		stopBrokerAndAwait();

		// 1st message will see ERROR frame (broker shutdown is not but should be detected)
		// 2nd message will be queued (a side effect of CONNECT/CONNECTED-buffering, likely to be removed)
		// Finish this once the above changes are made.

/*		MessageExchange send = MessageExchangeBuilder.send(destination, "foo").build();
		this.responseHandler.reset();
		this.relay.handleMessage(send.message);
		Thread.sleep(2000);

		this.activeMQBroker.start();
		Thread.sleep(5000);

		send = MessageExchangeBuilder.send(destination, "foo").andExpectMessage(sess1, subs1).build();
		this.responseHandler.reset();
		this.responseHandler.expect(send);
		this.relay.handleMessage(send.message);

		this.responseHandler.awaitAndAssert();
*/
	}


	private void stopBrokerAndAwait() throws Exception {
		logger.debug("Stopping ActiveMQ broker and will await shutdown");
		if (!this.activeMQBroker.isStarted()) {
			logger.debug("Broker not running");
			return;
		}
		final CountDownLatch latch = new CountDownLatch(1);
		this.activeMQBroker.addShutdownHook(new Runnable() {
			public void run() {
				latch.countDown();
			}
		});
		this.activeMQBroker.stop();
		assertTrue("Broker did not stop", latch.await(5, TimeUnit.SECONDS));
		logger.debug("Broker stopped");
	}


	/**
	 * Handles messages by matching them to expectations including a latch to wait for
	 * the completion of expected messages.
	 */
	private static class ExpectationMatchingMessageHandler implements MessageHandler {

		private final List<MessageExchange> expected;

		private final List<MessageExchange> actual = new CopyOnWriteArrayList<>();

		private final List<Message<?>> unexpected = new CopyOnWriteArrayList<>();

		private CountDownLatch latch = new CountDownLatch(1);


		public ExpectationMatchingMessageHandler(MessageExchange... expected) {
			this.expected = new CopyOnWriteArrayList<>(expected);
		}


		public void expect(MessageExchange... expected) {
			this.expected.addAll(Arrays.asList(expected));
		}

		public void awaitAndAssert() throws InterruptedException {
			boolean result = this.latch.await(10000, TimeUnit.MILLISECONDS);
			assertTrue(getAsString(), result && this.unexpected.isEmpty());
		}

		public void reset() {
			this.latch = new CountDownLatch(1);
			this.expected.clear();
			this.actual.clear();
			this.unexpected.clear();
		}

		@Override
		public void handleMessage(Message<?> message) throws MessagingException {
			for (MessageExchange exch : this.expected) {
				if (exch.matchMessage(message)) {
					if (exch.isDone()) {
						this.expected.remove(exch);
						this.actual.add(exch);
						if (this.expected.isEmpty()) {
							this.latch.countDown();
						}
					}
					return;
				}
			}
			this.unexpected.add(message);
		}

		public String getAsString() {
			StringBuilder sb = new StringBuilder("\n");
			sb.append("INCOMPLETE:\n").append(this.expected).append("\n");
			sb.append("COMPLETE:\n").append(this.actual).append("\n");
			sb.append("UNMATCHED MESSAGES:\n").append(this.unexpected).append("\n");
			return sb.toString();
		}
	}

	/**
	 * Holds a message as well as expected and actual messages matched against expectations.
	 */
	private static class MessageExchange {

		private final Message<?> message;

		private final MessageMatcher[] expected;

		private final Message<?>[] actual;


		public MessageExchange(Message<?> message, MessageMatcher... expected) {
			this.message = message;
			this.expected = expected;
			this.actual = new Message<?>[expected.length];
		}


		public boolean isDone() {
			for (int i=0 ; i < actual.length; i++) {
				if (actual[i] == null) {
					return false;
				}
			}
			return true;
		}

		public boolean matchMessage(Message<?> message) {
			for (int i=0 ; i < this.expected.length; i++) {
				if (this.expected[i].match(message)) {
					this.actual[i] = message;
					return true;
				}
			}
			return false;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Forwarded message:\n").append(this.message).append("\n");
			sb.append("Should receive back:\n").append(Arrays.toString(this.expected)).append("\n");
			sb.append("Actually received:\n").append(Arrays.toString(this.actual)).append("\n");
			return sb.toString();
		}
	}

	private static class MessageExchangeBuilder {

		private final Message<?> message;

		private final StompHeaderAccessor headers;

		private final List<MessageMatcher> expected = new ArrayList<>();


		private MessageExchangeBuilder(Message<?> message) {
			this.message = message;
			this.headers = StompHeaderAccessor.wrap(message);
		}


		public static MessageExchangeBuilder connect(String sessionId) {
			StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
			headers.setSessionId(sessionId);
			headers.setAcceptVersion("1.1,1.2");
			Message<?> message = MessageBuilder.withPayloadAndHeaders(new byte[0], headers).build();
			return new MessageExchangeBuilder(message);
		}

		public static MessageExchangeBuilder subscribe(String sessionId, String subscriptionId, String destination) {
			StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
			headers.setSessionId(sessionId);
			headers.setSubscriptionId(subscriptionId);
			headers.setDestination(destination);
			Message<?> message = MessageBuilder.withPayloadAndHeaders(new byte[0], headers).build();
			return new MessageExchangeBuilder(message);
		}

		public static MessageExchangeBuilder subscribeWithReceipt(String sessionId, String subscriptionId,
				String destination, String receiptId) {

			StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
			headers.setSessionId(sessionId);
			headers.setSubscriptionId(subscriptionId);
			headers.setDestination(destination);
			headers.setReceipt(receiptId);
			Message<?> message = MessageBuilder.withPayloadAndHeaders(new byte[0], headers).build();

			MessageExchangeBuilder builder = new MessageExchangeBuilder(message);
			builder.expected.add(new StompReceiptFrameMessageMatcher(sessionId, receiptId));
			return builder;
		}

		public static MessageExchangeBuilder send(String destination, String payload) {
			StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
			headers.setDestination(destination);
			Message<?> message = MessageBuilder.withPayloadAndHeaders(payload.getBytes(UTF_8), headers).build();
			return new MessageExchangeBuilder(message);
		}

		public MessageExchangeBuilder andExpectMessage(String sessionId, String subscriptionId) {
			Assert.isTrue(StompCommand.SEND.equals(headers.getCommand()), "MESSAGE can only be expected after SEND");
			String destination = this.headers.getDestination();
			Object payload = this.message.getPayload();
			this.expected.add(new StompMessageFrameMessageMatcher(sessionId, subscriptionId, destination, payload));
			return this;
		}

		public MessageExchangeBuilder andExpectError() {
			String sessionId = this.headers.getSessionId();
			Assert.notNull(sessionId, "No sessionId to match the ERROR frame to");
			return andExpectError(sessionId);
		}

		public MessageExchangeBuilder andExpectError(String sessionId) {
			this.expected.add(new StompFrameMessageMatcher(StompCommand.ERROR, sessionId));
			return this;
		}

		public MessageExchange build() {
			return new MessageExchange(this.message, this.expected.toArray(new MessageMatcher[this.expected.size()]));
		}
	}

	private static interface MessageMatcher {

		boolean match(Message<?> message);

	}

	private static class StompFrameMessageMatcher implements MessageMatcher {

		private final StompCommand command;

		private final String sessionId;


		public StompFrameMessageMatcher(StompCommand command, String sessionId) {
			this.command = command;
			this.sessionId = sessionId;
		}


		@Override
		public final boolean match(Message<?> message) {
			StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
			if (!this.command.equals(headers.getCommand()) || (this.sessionId != headers.getSessionId())) {
				return false;
			}
			return matchInternal(headers, message.getPayload());
		}

		protected boolean matchInternal(StompHeaderAccessor headers, Object payload) {
			return true;
		}

		@Override
		public String toString() {
			return "command=" + this.command  + ", session=\"" + this.sessionId + "\"";
		}
	}

	private static class StompReceiptFrameMessageMatcher extends StompFrameMessageMatcher {

		private final String receiptId;

		public StompReceiptFrameMessageMatcher(String sessionId, String receipt) {
			super(StompCommand.RECEIPT, sessionId);
			this.receiptId = receipt;
		}

		@Override
		protected boolean matchInternal(StompHeaderAccessor headers, Object payload) {
			return (this.receiptId.equals(headers.getReceiptId()));
		}

		@Override
		public String toString() {
			return super.toString() + ", receiptId=\"" + this.receiptId + "\"";
		}
	}

	private static class StompMessageFrameMessageMatcher extends StompFrameMessageMatcher {

		private final String subscriptionId;

		private final String destination;

		private final Object payload;


		public StompMessageFrameMessageMatcher(String sessionId, String subscriptionId, String destination, Object payload) {
			super(StompCommand.MESSAGE, sessionId);
			this.subscriptionId = subscriptionId;
			this.destination = destination;
			this.payload = payload;
		}

		@Override
		protected boolean matchInternal(StompHeaderAccessor headers, Object payload) {
			if (!this.subscriptionId.equals(headers.getSubscriptionId()) ||  !this.destination.equals(headers.getDestination())) {
				return false;
			}
			if (payload instanceof byte[] && this.payload instanceof byte[]) {
				return Arrays.equals((byte[]) payload, (byte[]) this.payload);
			}
			else {
				return this.payload.equals(payload);
			}
		}

		@Override
		public String toString() {
			return super.toString() + ", subscriptionId=\"" + this.subscriptionId
					+ "\", destination=\"" + this.destination + "\", payload=\"" + getPayloadAsText() + "\"";
		}

		protected String getPayloadAsText() {
			return (this.payload instanceof byte[])
					? new String((byte[]) this.payload, UTF_8) : payload.toString();
		}
	}

	private static class ExpectationMatchingEventPublisher implements ApplicationEventPublisher {

		private final List<Boolean> expected = new CopyOnWriteArrayList<>();

		private final List<Boolean> actual = new CopyOnWriteArrayList<>();

		private CountDownLatch latch = new CountDownLatch(1);


		public void expect(Boolean... expected) {
			this.expected.addAll(Arrays.asList(expected));
		}

		public void awaitAndAssert() throws InterruptedException {
			if (this.expected.size() == this.actual.size()) {
				assertEquals(this.expected, this.actual);
			}
			else {
				assertTrue("Expected=" + this.expected + ", actual=" + this.actual,
						this.latch.await(5, TimeUnit.SECONDS));
			}
		}

		@Override
		public void publishEvent(ApplicationEvent event) {
			if (event instanceof BrokerAvailabilityEvent) {
				this.actual.add(((BrokerAvailabilityEvent) event).isBrokerAvailable());
				if (this.actual.size() == this.expected.size()) {
					this.latch.countDown();
				}
			}
		}
	}

}
