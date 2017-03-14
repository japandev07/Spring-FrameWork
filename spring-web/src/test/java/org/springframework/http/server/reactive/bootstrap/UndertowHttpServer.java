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

package org.springframework.http.server.reactive.bootstrap;

import java.net.InetSocketAddress;

import io.undertow.Undertow;

import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.UndertowHttpHandlerAdapter;

/**
 * @author Marek Hawrylczak
 */
public class UndertowHttpServer extends AbstractHttpServer {

	private Undertow server;


	@Override
	protected void initServer() throws Exception {
		this.server = Undertow.builder().addHttpListener(getPort(), getHost())
				.setHandler(initHttpHandlerAdapter())
				.build();
	}

	private UndertowHttpHandlerAdapter initHttpHandlerAdapter() {
		return new UndertowHttpHandlerAdapter(getHttpHandlerMap() != null
				? HttpHandler.of(getHttpHandlerMap()) : getHttpHandler());
	}

	@Override
	protected void startInternal() {
		this.server.start();
		Undertow.ListenerInfo info = this.server.getListenerInfo().get(0);
		setPort(((InetSocketAddress) info.getAddress()).getPort());
	}

	@Override
	protected void stopInternal() {
		this.server.stop();
	}

	@Override
	protected void resetInternal() {
		this.server = null;
	}

}
