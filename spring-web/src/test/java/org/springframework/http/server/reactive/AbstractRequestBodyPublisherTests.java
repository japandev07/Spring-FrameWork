/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.http.server.reactive;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;

import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link AbstractRequestBodyPublisher}
 * 
 * @author Violeta Georgieva
 * @since 5.0
 */
public class AbstractRequestBodyPublisherTests {

	@Test
	public void testReceiveTwoRequestCallsWhenOnSubscribe() {
		@SuppressWarnings("unchecked")
		Subscriber<DataBuffer> subscriber = mock(Subscriber.class);
		doAnswer(new SubscriptionAnswer()).when(subscriber).onSubscribe(isA(Subscription.class));

		TestRequestBodyPublisher publisher = new TestRequestBodyPublisher();
		publisher.subscribe(subscriber);
		publisher.onDataAvailable();

		assertTrue(publisher.getReadCalls() == 2);
	}

	private static final class TestRequestBodyPublisher extends AbstractRequestBodyPublisher {

		private int readCalls = 0;

		@Override
		protected void checkOnDataAvailable() {
			// no-op
		}

		@Override
		protected DataBuffer read() throws IOException {
			readCalls++;
			return mock(DataBuffer.class);
		}

		public int getReadCalls() {
			return this.readCalls;
		}

	}

	private static final class SubscriptionAnswer implements Answer<Subscription> {

		@Override
		public Subscription answer(InvocationOnMock invocation) throws Throwable {
			Subscription arg = (Subscription) invocation.getArguments()[0];
			arg.request(1);
			arg.request(1);
			return arg;
		}

	}
}
