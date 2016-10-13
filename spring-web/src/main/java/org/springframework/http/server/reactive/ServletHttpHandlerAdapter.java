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

import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.util.Assert;

/**
 * Adapt {@link HttpHandler} to an {@link HttpServlet} using Servlet Async
 * support and Servlet 3.1 Non-blocking I/O.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 5.0
 */
@WebServlet(asyncSupported = true)
@SuppressWarnings("serial")
public class ServletHttpHandlerAdapter extends HttpServlet {

	private static final int DEFAULT_BUFFER_SIZE = 8192;


	private static final Log logger = LogFactory.getLog(ServletHttpHandlerAdapter.class);

	private final HttpHandler handler;

	// Servlet is based on blocking I/O, hence the usage of non-direct, heap-based buffers
	// (i.e. 'false' as constructor argument)
	private DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory(false);

	private int bufferSize = DEFAULT_BUFFER_SIZE;


	/**
	 * Create a new {@code ServletHttpHandlerAdapter} with the given HTTP handler.
	 * @param handler the handler
     */
	public ServletHttpHandlerAdapter(HttpHandler handler) {
		Assert.notNull(handler, "HttpHandler must not be null");
		this.handler = handler;
	}


	public void setDataBufferFactory(DataBufferFactory dataBufferFactory) {
		Assert.notNull(dataBufferFactory, "'dataBufferFactory' must not be null");
		this.dataBufferFactory = dataBufferFactory;
	}

	public void setBufferSize(int bufferSize) {
		Assert.isTrue(bufferSize > 0);
		this.bufferSize = bufferSize;
	}


	@Override
	protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
			throws ServletException, IOException {

		AsyncContext asyncContext = servletRequest.startAsync();
		ServletServerHttpRequest request = new ServletServerHttpRequest(
				servletRequest, this.dataBufferFactory, this.bufferSize);
		ServletServerHttpResponse response = new ServletServerHttpResponse(
				servletResponse, this.dataBufferFactory, this.bufferSize);
		asyncContext.addListener(new ErrorHandlingAsyncListener(request, response));
		HandlerResultSubscriber resultSubscriber = new HandlerResultSubscriber(asyncContext);
		this.handler.handle(request, response).subscribe(resultSubscriber);
	}


	private static class HandlerResultSubscriber implements Subscriber<Void> {

		private final AsyncContext asyncContext;

		public HandlerResultSubscriber(AsyncContext asyncContext) {
			this.asyncContext = asyncContext;
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			subscription.request(Long.MAX_VALUE);
		}

		@Override
		public void onNext(Void aVoid) {
			// no op
		}

		@Override
		public void onError(Throwable ex) {
			logger.error("Could not complete request", ex);
			HttpServletResponse response = (HttpServletResponse) this.asyncContext.getResponse();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			this.asyncContext.complete();
		}

		@Override
		public void onComplete() {
			logger.debug("Successfully completed request");
			this.asyncContext.complete();
		}
	}


	private static final class ErrorHandlingAsyncListener implements AsyncListener {

		private final ServletServerHttpRequest request;

		private final ServletServerHttpResponse response;


		public ErrorHandlingAsyncListener(ServletServerHttpRequest request,
				ServletServerHttpResponse response) {

			this.request = request;
			this.response = response;
		}


		@Override
		public void onTimeout(AsyncEvent event) {
			Throwable ex = event.getThrowable();
			if (ex == null) {
				ex = new IllegalStateException("Async operation timeout.");
			}
			this.request.handleAsyncListenerError(ex);
			this.response.handleAsyncListenerError(ex);
		}

		@Override
		public void onError(AsyncEvent event) {
			this.request.handleAsyncListenerError(event.getThrowable());
			this.response.handleAsyncListenerError(event.getThrowable());
		}

		@Override
		public void onStartAsync(AsyncEvent event) {
			// no op
		}

		@Override
		public void onComplete(AsyncEvent event) {
			// no op
		}
	}

}
