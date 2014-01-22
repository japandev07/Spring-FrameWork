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

package org.springframework.messaging.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GenericMessagingTemplate}.
 *
 * @author Rossen Stoyanchev
 */
public class GenericMessagingTemplateTests {

	private GenericMessagingTemplate template;

	private ThreadPoolTaskExecutor executor;


	@Before
	public void setup() {
		this.template = new GenericMessagingTemplate();
		this.executor = new ThreadPoolTaskExecutor();
		this.executor.afterPropertiesSet();
	}


	@Test
	public void sendAndReceive() {

		SubscribableChannel channel = new ExecutorSubscribableChannel(this.executor);
		channel.subscribe(new MessageHandler() {
			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				MessageChannel replyChannel = (MessageChannel) message.getHeaders().getReplyChannel();
				replyChannel.send(new GenericMessage<String>("response"));
			}
		});

		String actual = this.template.convertSendAndReceive(channel, "request", String.class);
		assertEquals("response", actual);
	}

	@Test
	public void sendAndReceiveTimeout() throws InterruptedException {

		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		final CountDownLatch latch = new CountDownLatch(1);

		this.template.setReceiveTimeout(1);
		this.template.setThrowExceptionOnLateReply(true);

		SubscribableChannel channel = new ExecutorSubscribableChannel(this.executor);
		channel.subscribe(new MessageHandler() {
			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				try {
					Thread.sleep(500);
					MessageChannel replyChannel = (MessageChannel) message.getHeaders().getReplyChannel();
					replyChannel.send(new GenericMessage<String>("response"));
					failure.set(new IllegalStateException("Expected exception"));
				}
				catch (InterruptedException e) {
					failure.set(e);
				}
				catch (MessageDeliveryException ex) {
					String expected = "Reply message received but the receiving thread has exited due to a timeout";
					String actual = ex.getMessage();
					if (!expected.equals(actual)) {
						failure.set(new IllegalStateException("Unexpected error: '" + actual + "'"));
					}
				}
				finally {
					latch.countDown();
				}
			}
		});

		assertNull(this.template.convertSendAndReceive(channel, "request", String.class));
		assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));

		if (failure.get() != null) {
			throw new AssertionError(failure.get());
		}
	}

}
