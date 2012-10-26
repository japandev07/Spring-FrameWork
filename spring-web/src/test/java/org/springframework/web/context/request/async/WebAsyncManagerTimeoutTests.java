/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.context.request.async;

import static org.springframework.web.context.request.async.CallableProcessingInterceptor.RESULT_NONE;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.notNull;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.concurrent.Callable;

import javax.servlet.AsyncEvent;
import javax.servlet.DispatcherType;

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * {@link WebAsyncManager} tests where container-triggered timeout/completion
 * events are simulated.
 *
 * @author Rossen Stoyanchev
 */
public class WebAsyncManagerTimeoutTests {

	private static final AsyncEvent ASYNC_EVENT = null;

	private WebAsyncManager asyncManager;

	private StandardServletAsyncWebRequest asyncWebRequest;

	private MockHttpServletRequest servletRequest;


	@Before
	public void setUp() {
		this.servletRequest = new MockHttpServletRequest();
		this.servletRequest.setAsyncSupported(true);
		this.asyncWebRequest = new StandardServletAsyncWebRequest(servletRequest, new MockHttpServletResponse());

		AsyncTaskExecutor executor = createMock(AsyncTaskExecutor.class);
		expect(executor.submit((Runnable) notNull())).andReturn(null);
		replay(executor);

		this.asyncManager = WebAsyncUtils.getAsyncManager(servletRequest);
		this.asyncManager.setTaskExecutor(executor);
		this.asyncManager.setAsyncWebRequest(this.asyncWebRequest);
	}

	@Test
	public void startCallableProcessingTimeoutAndComplete() throws Exception {

		StubCallable callable = new StubCallable();

		CallableProcessingInterceptor interceptor = createStrictMock(CallableProcessingInterceptor.class);
		expect(interceptor.afterTimeout(this.asyncWebRequest, callable)).andReturn(RESULT_NONE);
		interceptor.afterCompletion(this.asyncWebRequest, callable);
		replay(interceptor);

		this.asyncManager.registerCallableInterceptor("interceptor", interceptor);
		this.asyncManager.startCallableProcessing(callable);

		this.asyncWebRequest.onTimeout(ASYNC_EVENT);
		this.asyncWebRequest.onComplete(ASYNC_EVENT);

		assertFalse(this.asyncManager.hasConcurrentResult());
		assertEquals(DispatcherType.REQUEST, this.servletRequest.getDispatcherType());

		verify(interceptor);
	}

	@Test
	public void startCallableProcessingTimeoutAndResume() throws Exception {

		StubCallable callable = new StubCallable();

		CallableProcessingInterceptor interceptor = createStrictMock(CallableProcessingInterceptor.class);
		expect(interceptor.afterTimeout(this.asyncWebRequest, callable)).andReturn(22);
		replay(interceptor);

		this.asyncManager.registerCallableInterceptor("timeoutInterceptor", interceptor);
		this.asyncManager.startCallableProcessing(callable);

		this.asyncWebRequest.onTimeout(ASYNC_EVENT);

		assertEquals(22, this.asyncManager.getConcurrentResult());
		assertEquals(DispatcherType.ASYNC, this.servletRequest.getDispatcherType());

		verify(interceptor);
	}

	@Test
	public void startCallableProcessingAfterTimeoutException() throws Exception {

		StubCallable callable = new StubCallable();
		Exception exception = new Exception();

		CallableProcessingInterceptor interceptor = createStrictMock(CallableProcessingInterceptor.class);
		expect(interceptor.afterTimeout(this.asyncWebRequest, callable)).andThrow(exception);
		replay(interceptor);

		this.asyncManager.registerCallableInterceptor("timeoutInterceptor", interceptor);
		this.asyncManager.startCallableProcessing(callable);

		this.asyncWebRequest.onTimeout(ASYNC_EVENT);

		assertEquals(exception, this.asyncManager.getConcurrentResult());
		assertEquals(DispatcherType.ASYNC, this.servletRequest.getDispatcherType());

		verify(interceptor);
	}

	@Test
	public void startDeferredResultProcessingTimeoutAndComplete() throws Exception {

		DeferredResult<Integer> deferredResult = new DeferredResult<Integer>();

		DeferredResultProcessingInterceptor interceptor = createStrictMock(DeferredResultProcessingInterceptor.class);
		interceptor.preProcess(this.asyncWebRequest, deferredResult);
		interceptor.afterTimeout(this.asyncWebRequest, deferredResult);
		interceptor.afterCompletion(this.asyncWebRequest, deferredResult);
		replay(interceptor);

		this.asyncManager.registerDeferredResultInterceptor("interceptor", interceptor);
		this.asyncManager.startDeferredResultProcessing(deferredResult);

		this.asyncWebRequest.onTimeout(ASYNC_EVENT);
		this.asyncWebRequest.onComplete(ASYNC_EVENT);

		assertFalse(this.asyncManager.hasConcurrentResult());
		assertEquals(DispatcherType.REQUEST, this.servletRequest.getDispatcherType());

		verify(interceptor);
	}

	@Test
	public void startDeferredResultProcessingTimeoutAndResumeWithDefaultResult() throws Exception {

		DeferredResult<Integer> deferredResult = new DeferredResult<Integer>(null, 23);

		DeferredResultProcessingInterceptor interceptor = new DeferredResultProcessingInterceptorAdapter() {
			public <T> void afterTimeout(NativeWebRequest request, DeferredResult<T> result) throws Exception {
				result.setErrorResult("should not get here");
			}
		};

		this.asyncManager.registerDeferredResultInterceptor("interceptor", interceptor);
		this.asyncManager.startDeferredResultProcessing(deferredResult);

		AsyncEvent event = null;
		this.asyncWebRequest.onTimeout(event);

		assertEquals(23, this.asyncManager.getConcurrentResult());
		assertEquals(DispatcherType.ASYNC, this.servletRequest.getDispatcherType());
	}

	@Test
	public void startDeferredResultProcessingTimeoutAndResumeWithInterceptor() throws Exception {

		DeferredResult<Integer> deferredResult = new DeferredResult<Integer>();

		DeferredResultProcessingInterceptor interceptor = new DeferredResultProcessingInterceptorAdapter() {
			public <T> void afterTimeout(NativeWebRequest request, DeferredResult<T> result) throws Exception {
				result.setErrorResult(23);
			}
		};

		this.asyncManager.registerDeferredResultInterceptor("interceptor", interceptor);
		this.asyncManager.startDeferredResultProcessing(deferredResult);

		AsyncEvent event = null;
		this.asyncWebRequest.onTimeout(event);

		assertEquals(23, this.asyncManager.getConcurrentResult());
		assertEquals(DispatcherType.ASYNC, this.servletRequest.getDispatcherType());
	}

	@Test
	public void startDeferredResultProcessingAfterTimeoutException() throws Exception {

		DeferredResult<Integer> deferredResult = new DeferredResult<Integer>();
		final Exception exception = new Exception();

		DeferredResultProcessingInterceptor interceptor = new DeferredResultProcessingInterceptorAdapter() {
			public <T> void afterTimeout(NativeWebRequest request, DeferredResult<T> result) throws Exception {
				throw exception;
			}
		};

		this.asyncManager.registerDeferredResultInterceptor("interceptor", interceptor);
		this.asyncManager.startDeferredResultProcessing(deferredResult);

		AsyncEvent event = null;
		this.asyncWebRequest.onTimeout(event);

		assertEquals(exception, this.asyncManager.getConcurrentResult());
		assertEquals(DispatcherType.ASYNC, this.servletRequest.getDispatcherType());
	}


	private final class StubCallable implements Callable<Object> {
		public Object call() throws Exception {
			return 21;
		}
	}

}
