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

package org.springframework.web.reactive.socket.adapter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * Base class for {@link WebSocketSession} implementations wrapping and
 * delegating to the native WebSocket session (or connection) of the underlying
 * WebSocket runtime.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public abstract class WebSocketSessionSupport<T> implements WebSocketSession {

	protected final Log logger = LogFactory.getLog(getClass());


	private final T delegate;


	/**
	 * Create a new instance and associate the given attributes with it.
	 * @param delegate the underlying WebSocket connection
	 */
	protected WebSocketSessionSupport(T delegate) {
		Assert.notNull(delegate, "'delegate' session is required.");
		this.delegate = delegate;
	}


	/**
	 * Return the native session of the underlying runtime.
	 */
	public T getDelegate() {
		return this.delegate;
	}


	@Override
	public final Mono<Void> close(CloseStatus status) {
		if (logger.isDebugEnabled()) {
			logger.debug("Closing " + this);
		}
		return closeInternal(status);
	}

	protected abstract Mono<Void> closeInternal(CloseStatus status);


	@Override
	public String toString() {
		return getClass().getSimpleName() + "[id=" + getId() + ", uri=" + getUri() + "]";
	}

}
