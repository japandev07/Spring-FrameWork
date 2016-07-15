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

import java.net.URI;
import java.net.URISyntaxException;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.cookie.Cookie;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import reactor.adapter.RxJava1Adapter;
import reactor.core.publisher.Flux;
import rx.Observable;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Adapt {@link ServerHttpRequest} to the RxNetty {@link HttpServerRequest}.
 *
 * @author Rossen Stoyanchev
 * @author Stephane Maldini
 * @since 5.0
 */
public class RxNettyServerHttpRequest extends AbstractServerHttpRequest {

	private final HttpServerRequest<ByteBuf> request;

	private final NettyDataBufferFactory dataBufferFactory;

	public RxNettyServerHttpRequest(HttpServerRequest<ByteBuf> request,
			NettyDataBufferFactory dataBufferFactory) {
		Assert.notNull("'request', request must not be null");
		Assert.notNull(dataBufferFactory, "'dataBufferFactory' must not be null");
		this.dataBufferFactory = dataBufferFactory;
		this.request = request;
	}


	public HttpServerRequest<ByteBuf> getRxNettyRequest() {
		return this.request;
	}

	@Override
	public HttpMethod getMethod() {
		return HttpMethod.valueOf(this.request.getHttpMethod().name());
	}

	@Override
	protected URI initUri() throws URISyntaxException {
		return new URI(this.request.getUri());
	}

	@Override
	protected HttpHeaders initHeaders() {
		HttpHeaders headers = new HttpHeaders();
		for (String name : this.request.getHeaderNames()) {
			headers.put(name, this.request.getAllHeaderValues(name));
		}
		return headers;
	}

	@Override
	protected MultiValueMap<String, HttpCookie> initCookies() {
		MultiValueMap<String, HttpCookie> cookies = new LinkedMultiValueMap<>();
		for (String name : this.request.getCookies().keySet()) {
			for (Cookie cookie : this.request.getCookies().get(name)) {
				HttpCookie httpCookie = new HttpCookie(name, cookie.value());
				cookies.add(name, httpCookie);
			}
		}
		return cookies;
	}

	@Override
	public Flux<DataBuffer> getBody() {
		Observable<DataBuffer> content = this.request.getContent().map(dataBufferFactory::wrap);
		return RxJava1Adapter.observableToFlux(content);
	}

}
