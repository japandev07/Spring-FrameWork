/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.web.socket.sockjs.transport;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.sockjs.AbstractSockJsSession;
import org.springframework.web.socket.sockjs.SockJsMessageCodec;
import org.springframework.web.socket.sockjs.TransportErrorException;
import org.springframework.web.socket.sockjs.TransportHandler;
import org.springframework.web.socket.sockjs.TransportType;

/**
 * A {@link TransportHandler} that receives messages over HTTP.
 *
 * @author Rossen Stoyanchev
 */
public class JsonpTransportHandler extends AbstractHttpReceivingTransportHandler {

	private final FormHttpMessageConverter formConverter = new FormHttpMessageConverter();


	@Override
	public TransportType getTransportType() {
		return TransportType.JSONP_SEND;
	}

	@Override
	public void handleRequestInternal(ServerHttpRequest request, ServerHttpResponse response,
			AbstractSockJsSession sockJsSession) throws TransportErrorException {

		super.handleRequestInternal(request, response, sockJsSession);

		try {
			response.getBody().write("ok".getBytes("UTF-8"));
		}
		catch (Throwable t) {
			throw new TransportErrorException("Failed to write response body", t, sockJsSession.getId());
		}
	}

	@Override
	protected String[] readMessages(ServerHttpRequest request) throws IOException {

		SockJsMessageCodec messageCodec = getSockJsConfig().getMessageCodecRequired();

		MediaType contentType = request.getHeaders().getContentType();
		if ((contentType != null) && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
			MultiValueMap<String, String> map = this.formConverter.read(null, request);
			String d = map.getFirst("d");
			return (StringUtils.hasText(d)) ? messageCodec.decode(d) : null;
		}
		else {
			return messageCodec.decodeInputStream(request.getBody());
		}
	}

	@Override
	protected HttpStatus getResponseStatus() {
		return HttpStatus.OK;
	}

}
