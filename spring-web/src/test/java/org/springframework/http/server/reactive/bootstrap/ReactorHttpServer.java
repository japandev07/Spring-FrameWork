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

package org.springframework.http.server.reactive.bootstrap;

import java.util.concurrent.atomic.AtomicReference;

import reactor.netty.DisposableServer;

import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;

/**
 * @author Stephane Maldini
 */
public class ReactorHttpServer extends AbstractHttpServer {

	private ReactorHttpHandlerAdapter reactorHandler;

	private reactor.netty.http.server.HttpServer reactorServer;

	private AtomicReference<DisposableServer> disposableServer = new AtomicReference<>();


	@Override
	protected void initServer() throws Exception {
		this.reactorHandler = createHttpHandlerAdapter();
		this.reactorServer = reactor.netty.http.server.HttpServer.create()
				.tcpConfiguration(tcpServer -> tcpServer.host(getHost()))
				.port(getPort());
	}

	private ReactorHttpHandlerAdapter createHttpHandlerAdapter() {
		return new ReactorHttpHandlerAdapter(resolveHttpHandler());
	}

	@Override
	protected void startInternal() {
		DisposableServer disposableServer = this.reactorServer.handle(this.reactorHandler).bind().block();
		setPort(disposableServer.address().getPort());
		this.disposableServer.set(disposableServer);
	}

	@Override
	protected void stopInternal() {
		this.disposableServer.get().dispose();
	}

	@Override
	protected void resetInternal() {
		this.reactorServer = null;
		this.reactorHandler = null;
		this.disposableServer.set(null);
	}

}
