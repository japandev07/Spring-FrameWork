/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.socket.handler;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import static org.junit.Assert.*;

/**
 * Unit tests for
 * {@link org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator}.
 *
 * @author Rossen Stoyanchev
 */
@SuppressWarnings("resource")
public class ConcurrentWebSocketSessionDecoratorTests {

	@Test
	public void send() throws IOException {

		TestWebSocketSession session = new TestWebSocketSession();
		session.setOpen(true);

		ConcurrentWebSocketSessionDecorator concurrentSession =
				new ConcurrentWebSocketSessionDecorator(session, 1000, 1024);

		TextMessage textMessage = new TextMessage("payload");
		concurrentSession.sendMessage(textMessage);

		assertEquals(1, session.getSentMessages().size());
		assertEquals(textMessage, session.getSentMessages().get(0));

		assertEquals(0, concurrentSession.getBufferSize());
		assertEquals(0, concurrentSession.getTimeSinceSendStarted());
		assertTrue(session.isOpen());
	}

	@Test
	public void sendAfterBlockedSend() throws IOException, InterruptedException {

		BlockingSession blockingSession = new BlockingSession();
		blockingSession.setOpen(true);
		CountDownLatch sentMessageLatch = blockingSession.getSentMessageLatch();

		final ConcurrentWebSocketSessionDecorator concurrentSession =
				new ConcurrentWebSocketSessionDecorator(blockingSession, 10 * 1000, 1024);

		Executors.newSingleThreadExecutor().submit(new Runnable() {
			@Override
			public void run() {
				TextMessage textMessage = new TextMessage("slow message");
				try {
					concurrentSession.sendMessage(textMessage);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		assertTrue(sentMessageLatch.await(5, TimeUnit.SECONDS));

		// ensure some send time elapses
		Thread.sleep(100);
		assertTrue(concurrentSession.getTimeSinceSendStarted() > 0);

		TextMessage payload = new TextMessage("payload");
		for (int i=0; i < 5; i++) {
			concurrentSession.sendMessage(payload);
		}

		assertTrue(concurrentSession.getTimeSinceSendStarted() > 0);
		assertEquals(5 * payload.getPayloadLength(), concurrentSession.getBufferSize());
		assertTrue(blockingSession.isOpen());
	}

	@Test
	public void sendTimeLimitExceeded() throws IOException, InterruptedException {

		BlockingSession blockingSession = new BlockingSession();
		blockingSession.setOpen(true);
		CountDownLatch sentMessageLatch = blockingSession.getSentMessageLatch();

		int sendTimeLimit = 100;
		int bufferSizeLimit = 1024;

		final ConcurrentWebSocketSessionDecorator concurrentSession =
				new ConcurrentWebSocketSessionDecorator(blockingSession, sendTimeLimit, bufferSizeLimit);

		Executors.newSingleThreadExecutor().submit(new Runnable() {
			@Override
			public void run() {
				TextMessage textMessage = new TextMessage("slow message");
				try {
					concurrentSession.sendMessage(textMessage);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		assertTrue(sentMessageLatch.await(5, TimeUnit.SECONDS));

		// ensure some send time elapses
		Thread.sleep(sendTimeLimit + 100);

		try {
			TextMessage payload = new TextMessage("payload");
			concurrentSession.sendMessage(payload);
			fail("Expected exception");
		}
		catch (SessionLimitExceededException ex) {
			assertEquals(CloseStatus.SESSION_NOT_RELIABLE, ex.getStatus());
		}
	}

	@Test
	public void sendBufferSizeExceeded() throws IOException, InterruptedException {

		BlockingSession blockingSession = new BlockingSession();
		blockingSession.setOpen(true);
		CountDownLatch sentMessageLatch = blockingSession.getSentMessageLatch();

		int sendTimeLimit = 10 * 1000;
		int bufferSizeLimit = 1024;

		final ConcurrentWebSocketSessionDecorator concurrentSession =
				new ConcurrentWebSocketSessionDecorator(blockingSession, sendTimeLimit, bufferSizeLimit);

		Executors.newSingleThreadExecutor().submit(new Runnable() {
			@Override
			public void run() {
				TextMessage textMessage = new TextMessage("slow message");
				try {
					concurrentSession.sendMessage(textMessage);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		assertTrue(sentMessageLatch.await(5, TimeUnit.SECONDS));

		StringBuilder sb = new StringBuilder();
		for (int i=0 ; i < 1023; i++) {
			sb.append("a");
		}

		TextMessage message = new TextMessage(sb.toString());
		concurrentSession.sendMessage(message);

		assertEquals(1023, concurrentSession.getBufferSize());
		assertTrue(blockingSession.isOpen());

		try {
			concurrentSession.sendMessage(message);
			fail("Expected exception");
		}
		catch (SessionLimitExceededException ex) {
		}
	}


	private static class BlockingSession extends TestWebSocketSession {

		private AtomicReference<CountDownLatch> nextMessageLatch = new AtomicReference<>();

		private AtomicReference<CountDownLatch> releaseLatch = new AtomicReference<>();


		public CountDownLatch getSentMessageLatch() {
			this.nextMessageLatch.set(new CountDownLatch(1));
			return this.nextMessageLatch.get();
		}

		@Override
		public void sendMessage(WebSocketMessage<?> message) throws IOException {
			super.sendMessage(message);
			if (this.nextMessageLatch != null) {
				this.nextMessageLatch.get().countDown();
			}
			block();
		}

		private void block() {
			try {
				this.releaseLatch.set(new CountDownLatch(1));
				this.releaseLatch.get().await();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

//	@Test
//	public void sendSessionLimitException() throws IOException, InterruptedException {
//
//		BlockingSession blockingSession = new BlockingSession();
//		blockingSession.setOpen(true);
//		CountDownLatch sentMessageLatch = blockingSession.getSentMessageLatch();
//
//		int sendTimeLimit = 10 * 1000;
//		int bufferSizeLimit = 1024;
//
//		final ConcurrentWebSocketSessionDecorator concurrentSession =
//				new ConcurrentWebSocketSessionDecorator(blockingSession, sendTimeLimit, bufferSizeLimit);
//
//		Executors.newSingleThreadExecutor().submit(new Runnable() {
//			@Override
//			public void run() {
//				TextMessage textMessage = new TextMessage("slow message");
//				try {
//					concurrentSession.sendMessage(textMessage);
//				}
//				catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//		});
//
//		assertTrue(sentMessageLatch.await(5, TimeUnit.SECONDS));
//
//		StringBuilder sb = new StringBuilder();
//		for (int i=0 ; i < 1023; i++) {
//			sb.append("a");
//		}
//
//		TextMessage message = new TextMessage(sb.toString());
//		concurrentSession.sendMessage(message);
//
//		assertEquals(1023, concurrentSession.getBufferSize());
//		assertTrue(blockingSession.isOpen());
//
//		concurrentSession.sendMessage(message);
//		assertFalse(blockingSession.isOpen());
//	}

}
