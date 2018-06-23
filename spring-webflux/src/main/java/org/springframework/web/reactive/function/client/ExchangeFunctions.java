/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.reactive.function.client;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.codec.LoggingCodecSupport;
import org.springframework.util.Assert;

/**
 * Static factory methods to create an {@link ExchangeFunction}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public abstract class ExchangeFunctions {

	private static final Log logger = LogFactory.getLog(ExchangeFunctions.class);


	/**
	 * Create an {@code ExchangeFunction} with the given {@code ClientHttpConnector}.
	 * This is the same as calling
	 * {@link #create(ClientHttpConnector, ExchangeStrategies)} and passing
	 * {@link ExchangeStrategies#withDefaults()}.
	 * @param connector the connector to use for connecting to servers
	 * @return the created {@code ExchangeFunction}
	 */
	public static ExchangeFunction create(ClientHttpConnector connector) {
		return create(connector, ExchangeStrategies.withDefaults());
	}

	/**
	 * Create an {@code ExchangeFunction} with the given
	 * {@code ClientHttpConnector} and {@code ExchangeStrategies}.
	 * @param connector the connector to use for connecting to servers
	 * @param strategies the {@code ExchangeStrategies} to use
	 * @return the created {@code ExchangeFunction}
	 */
	public static ExchangeFunction create(ClientHttpConnector connector, ExchangeStrategies strategies) {
		return new DefaultExchangeFunction(connector, strategies);
	}


	private static class DefaultExchangeFunction implements ExchangeFunction {

		private final ClientHttpConnector connector;

		private final ExchangeStrategies strategies;

		private boolean disableLoggingRequestDetails;


		public DefaultExchangeFunction(ClientHttpConnector connector, ExchangeStrategies strategies) {
			Assert.notNull(connector, "ClientHttpConnector must not be null");
			Assert.notNull(strategies, "ExchangeStrategies must not be null");
			this.connector = connector;
			this.strategies = strategies;

			strategies.messageWriters().stream()
					.filter(LoggingCodecSupport.class::isInstance)
					.forEach(reader -> {
						if (((LoggingCodecSupport) reader).isDisableLoggingRequestDetails()) {
							this.disableLoggingRequestDetails = true;
						}
					});
		}


		@Override
		public Mono<ClientResponse> exchange(ClientRequest request) {
			Assert.notNull(request, "ClientRequest must not be null");

			HttpMethod httpMethod = request.method();
			URI url = request.url();

			return this.connector
					.connect(httpMethod, url, httpRequest -> request.writeTo(httpRequest, this.strategies))
					.doOnRequest(n -> logRequest(request))
					.doOnCancel(() -> logger.debug("Cancel signal (to close connection)"))
					.map(response -> {
						logResponse(response);
						return new DefaultClientResponse(response, this.strategies);
					});
		}

		private void logRequest(ClientRequest request) {
			if (logger.isDebugEnabled()) {
				String formatted = request.url().toString();
				if (this.disableLoggingRequestDetails) {
					int index = formatted.indexOf("?");
					formatted = index != -1 ? formatted.substring(0, index) : formatted;
				}
				logger.debug("HTTP " + request.method() + " " + formatted);
			}
		}

		private void logResponse(ClientHttpResponse response) {
			if (logger.isDebugEnabled() || logger.isTraceEnabled()) {
				int code = response.getRawStatusCode();
				HttpStatus status = HttpStatus.resolve(code);
				String message = "Response " + (status != null ? status : code);
				if (logger.isTraceEnabled()) {
					String headers = this.disableLoggingRequestDetails ? "" : ", headers=" + response.getHeaders();
					logger.trace(message + headers);
				}
				else {
					logger.debug(message);
				}
			}
		}
	}

}
