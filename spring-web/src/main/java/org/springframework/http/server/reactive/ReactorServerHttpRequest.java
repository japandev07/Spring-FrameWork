/*
 * Copyright 2002-2017 the original author or authors.
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.ssl.SslHandler;
import reactor.core.publisher.Flux;
import reactor.ipc.netty.http.server.HttpServerRequest;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Adapt {@link ServerHttpRequest} to the Reactor {@link HttpServerRequest}.
 *
 * @author Stephane Maldini
 * @author Rossen Stoyanchev
 * @since 5.0
 */
class ReactorServerHttpRequest extends AbstractServerHttpRequest {

	private final HttpServerRequest request;

	private final NettyDataBufferFactory bufferFactory;


	public ReactorServerHttpRequest(HttpServerRequest request, NettyDataBufferFactory bufferFactory)
			throws URISyntaxException {

		super(initUri(request), "", initHeaders(request));
		Assert.notNull(bufferFactory, "'bufferFactory' must not be null");
		this.request = request;
		this.bufferFactory = bufferFactory;
	}

	private static URI initUri(HttpServerRequest request) throws URISyntaxException {
		Assert.notNull(request, "'request' must not be null");
		return new URI(resolveBaseUrl(request).toString() + request.uri());
	}

	private static URI resolveBaseUrl(HttpServerRequest request) throws URISyntaxException {
		String scheme = getScheme(request);
		String header = request.requestHeaders().get(HttpHeaderNames.HOST);
		if (header != null) {
			final int portIndex;
			if (header.startsWith("[")) {
				portIndex = header.indexOf(':', header.indexOf(']'));
			}
			else {
				portIndex = header.indexOf(':');
			}
			if (portIndex != -1) {
				try {
					return new URI(scheme, null, header.substring(0, portIndex),
							Integer.parseInt(header.substring(portIndex + 1)), null, null, null);
				}
				catch (NumberFormatException ex) {
					throw new URISyntaxException(header, "Unable to parse port", portIndex);
				}
			}
			else {
				return new URI(scheme, header, null, null);
			}
		}
		else {
			InetSocketAddress localAddress = (InetSocketAddress) request.context().channel().localAddress();
			return new URI(scheme, null, localAddress.getHostString(),
					localAddress.getPort(), null, null, null);
		}
	}

	private static String getScheme(HttpServerRequest request) {
		ChannelPipeline pipeline = request.context().channel().pipeline();
		boolean ssl = pipeline.get(SslHandler.class) != null;
		return ssl ? "https" : "http";
	}

	private static HttpHeaders initHeaders(HttpServerRequest channel) {
		HttpHeaders headers = new HttpHeaders();
		for (String name : channel.requestHeaders().names()) {
			headers.put(name, channel.requestHeaders().getAll(name));
		}
		return headers;
	}

	@Override
	public String getMethodValue() {
		return this.request.method().name();
	}

	@Override
	protected MultiValueMap<String, HttpCookie> initCookies() {
		MultiValueMap<String, HttpCookie> cookies = new LinkedMultiValueMap<>();
		for (CharSequence name : this.request.cookies().keySet()) {
			for (Cookie cookie : this.request.cookies().get(name)) {
				HttpCookie httpCookie = new HttpCookie(name.toString(), cookie.value());
				cookies.add(name.toString(), httpCookie);
			}
		}
		return cookies;
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return this.request.remoteAddress();
	}

	@Override
	public Flux<DataBuffer> getBody() {
		return this.request.receive().retain().map(this.bufferFactory::wrap);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getNativeRequest() {
		return (T) this.request;
	}

}
