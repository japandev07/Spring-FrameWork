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

package org.springframework.web.client.reactive.test;

import java.net.URI;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.AbstractClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequest;

/**
 * Mock implementation of {@link ClientHttpRequest}.
 * @author Brian Clozel
 */
public class MockClientHttpRequest extends AbstractClientHttpRequest {

	private HttpMethod httpMethod;

	private URI uri;

	private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

	private Publisher<DataBuffer> body;

	private Publisher<Publisher<DataBuffer>> bodyWithFlushes;


	public MockClientHttpRequest() {
	}

	public MockClientHttpRequest(HttpMethod httpMethod, String uri) {
		this(httpMethod, (uri != null ? URI.create(uri) : null));
	}

	public MockClientHttpRequest(HttpMethod httpMethod, URI uri) {
		super();
		this.httpMethod = httpMethod;
		this.uri = uri;
	}

	@Override
	public HttpMethod getMethod() {
		return this.httpMethod;
	}

	public MockClientHttpRequest setMethod(HttpMethod httpMethod) {
		this.httpMethod = httpMethod;
		return this;
	}

	@Override
	public URI getURI() {
		return this.uri;
	}

	public MockClientHttpRequest setUri(String uri) {
		this.uri = URI.create(uri);
		return this;
	}

	public MockClientHttpRequest setUri(URI uri) {
		this.uri = uri;
		return this;
	}

	@Override
	public DataBufferFactory bufferFactory() {
		return this.bufferFactory;
	}

	@Override
	public Mono<Void> writeWith(Publisher<DataBuffer> body) {
		this.body = body;
		return applyBeforeCommit().then(Flux.from(this.body).then());
	}

	@Override
	public Mono<Void> writeAndFlushWith(Publisher<Publisher<DataBuffer>> body) {
		this.bodyWithFlushes = body;
		return applyBeforeCommit().then(Flux.from(this.bodyWithFlushes).then());
	}

	public Publisher<DataBuffer> getBody() {
		return body;
	}

	public Publisher<Publisher<DataBuffer>> getBodyWithFlush() {
		return bodyWithFlushes;
	}

	@Override
	public Mono<Void> setComplete() {
		return applyBeforeCommit().then();
	}

	@Override
	protected void writeHeaders() { }

	@Override
	protected void writeCookies() { }
}
