/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.http.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

/**
 * {@link org.springframework.http.client.ClientHttpRequestFactory} implementation that
 * uses <a href="http://hc.apache.org/httpcomponents-client-ga/">Apache HttpComponents
 * HttpClient</a> to create requests.
 *
 * <p>Allows to use a pre-configured {@link HttpClient} instance -
 * potentially with authentication, HTTP connection pooling, etc.
 *
 * <p><b>NOTE:</b> Requires Apache HttpComponents 4.3 or higher, as of Spring 4.0.
 *
 * @author Oleg Kalnichevski
 * @author Arjen Poutsma
 * @author Stephane Nicoll
 * @since 3.1
 */
public class HttpComponentsClientHttpRequestFactory implements ClientHttpRequestFactory, DisposableBean {

	private HttpClient httpClient;

	private RequestConfig requestConfig;

	private boolean bufferRequestBody = true;


	/**
	 * Create a new instance of the {@code HttpComponentsClientHttpRequestFactory}
	 * with a default {@link HttpClient}.
	 */
	public HttpComponentsClientHttpRequestFactory() {
		this(HttpClients.createSystem());
	}

	/**
	 * Create a new instance of the {@code HttpComponentsClientHttpRequestFactory}
	 * with the given {@link HttpClient} instance.
	 * @param httpClient the HttpClient instance to use for this request factory
	 */
	public HttpComponentsClientHttpRequestFactory(HttpClient httpClient) {
		Assert.notNull(httpClient, "HttpClient must not be null");
		this.httpClient = httpClient;
	}


	/**
	 * Set the {@code HttpClient} used for
	 * {@linkplain #createRequest(URI, HttpMethod) synchronous execution}.
	 */
	public void setHttpClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	/**
	 * Return the {@code HttpClient} used for
	 * {@linkplain #createRequest(URI, HttpMethod) synchronous execution}.
	 */
	public HttpClient getHttpClient() {
		return this.httpClient;
	}

	/**
	 * Set the connection timeout for the underlying HttpClient.
	 * A timeout value of 0 specifies an infinite timeout.
	 * <p>Additional properties can be configured by specifying a
	 * {@link RequestConfig} instance on a custom {@link HttpClient}.
	 * @param timeout the timeout value in milliseconds
	 * @see RequestConfig#getConnectTimeout()
	 */
	public void setConnectTimeout(int timeout) {
		Assert.isTrue(timeout >= 0, "Timeout must be a non-negative value");
		this.requestConfig = cloneRequestConfig()
				.setConnectTimeout(timeout).build();
		setLegacyConnectionTimeout(getHttpClient(), timeout);
	}

	/**
	 * Apply the specified connection timeout to deprecated {@link HttpClient}
	 * implementations.
	 * <p>As of HttpClient 4.3, default parameters have to be exposed through a
	 * {@link RequestConfig} instance instead of setting the parameters on the
	 * client. Unfortunately, this behavior is not backward-compatible and older
	 * {@link HttpClient} implementations will ignore the {@link RequestConfig}
	 * object set in the context.
	 * <p>If the specified client is an older implementation, we set the custom
	 * connection timeout through the deprecated API. Otherwise, we just return
	 * as it is set through {@link RequestConfig} with newer clients.
	 * @param client the client to configure
	 * @param timeout the custom connection timeout
	 */
	@SuppressWarnings("deprecation")
	private void setLegacyConnectionTimeout(HttpClient client, int timeout) {
		if (org.apache.http.impl.client.AbstractHttpClient.class.isInstance(client)) {
			client.getParams().setIntParameter(
					org.apache.http.params.CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
		}
	}

	/**
	 * Set the timeout in milliseconds used when requesting a connection from the connection
	 * manager using the underlying HttpClient.
	 * A timeout value of 0 specifies an infinite timeout.
	 * <p>Additional properties can be configured by specifying a
	 * {@link RequestConfig} instance on a custom {@link HttpClient}.
	 * @param connectionRequestTimeout the timeout value to request a connection in milliseconds
	 * @see RequestConfig#getConnectionRequestTimeout()
	 */
	public void setConnectionRequestTimeout(int connectionRequestTimeout) {
		this.requestConfig = cloneRequestConfig()
				.setConnectionRequestTimeout(connectionRequestTimeout).build();
	}

	/**
	 * Set the socket read timeout for the underlying HttpClient.
	 * A timeout value of 0 specifies an infinite timeout.
	 * <p>Additional properties can be configured by specifying a
	 * {@link RequestConfig} instance on a custom {@link HttpClient}.
	 * @param timeout the timeout value in milliseconds
	 * @see RequestConfig#getSocketTimeout()
	 */
	public void setReadTimeout(int timeout) {
		Assert.isTrue(timeout >= 0, "Timeout must be a non-negative value");
		this.requestConfig = cloneRequestConfig()
				.setSocketTimeout(timeout).build();
		setLegacySocketTimeout(getHttpClient(), timeout);
	}

	/**
	 * Apply the specified socket timeout to deprecated {@link HttpClient}
	 * implementations. See {@link #setLegacyConnectionTimeout}.
	 * @param client the client to configure
	 * @param timeout the custom socket timeout
	 * @see #setLegacyConnectionTimeout
	 */
	@SuppressWarnings("deprecation")
	private void setLegacySocketTimeout(HttpClient client, int timeout) {
		if (org.apache.http.impl.client.AbstractHttpClient.class.isInstance(client)) {
			client.getParams().setIntParameter(
					org.apache.http.params.CoreConnectionPNames.SO_TIMEOUT, timeout);
		}
	}

	private RequestConfig.Builder cloneRequestConfig() {
		return this.requestConfig != null ? RequestConfig.copy(this.requestConfig) : RequestConfig.custom();
	}

	/**
	 * Indicates whether this request factory should buffer the request body internally.
	 * <p>Default is {@code true}. When sending large amounts of data via POST or PUT, it is
	 * recommended to change this property to {@code false}, so as not to run out of memory.
	 */
	public void setBufferRequestBody(boolean bufferRequestBody) {
		this.bufferRequestBody = bufferRequestBody;
	}


	@Override
	@SuppressWarnings("deprecation")
	public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
		HttpClient client = getHttpClient();
		Assert.state(client != null, "Synchronous execution requires an HttpClient to be set");
		HttpUriRequest httpRequest = createHttpUriRequest(httpMethod, uri);
		postProcessHttpRequest(httpRequest);
		HttpContext context = createHttpContext(httpMethod, uri);
		if (context == null) {
			context = HttpClientContext.create();
		}
		// Request configuration not set in the context
		if (context.getAttribute(HttpClientContext.REQUEST_CONFIG) == null) {
			// Use request configuration given by the user, when available
			RequestConfig config = null;
			if (httpRequest instanceof Configurable) {
				config = ((Configurable) httpRequest).getConfig();
			}
			if (config == null) {
				config = createRequestConfig(client);
			}
			if (config != null) {
				context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);
			}
		}
		if (this.bufferRequestBody) {
			return new HttpComponentsClientHttpRequest(client, httpRequest, context);
		}
		else {
			return new HttpComponentsStreamingClientHttpRequest(client, httpRequest, context);
		}
	}

	/**
	 * Create a default {@link RequestConfig} to use with the given client.
	 * Can return {@code null} to indicate that no custom request config should
	 * be set and the defaults of the {@link HttpClient} should be used.
	 * <p>The default implementation tries to merge the defaults of the client
	 * with the local customizations of this instance, if any.
	 * @param client the client
	 * @return the RequestConfig to use
	 * @since 4.2
	 */
	protected RequestConfig createRequestConfig(HttpClient client) {
		if (client instanceof Configurable) {
			RequestConfig clientRequestConfig = ((Configurable) client).getConfig();
			return mergeRequestConfig(clientRequestConfig);
		}
		return this.requestConfig;
	}

	protected RequestConfig mergeRequestConfig(RequestConfig defaultRequestConfig) {
		if (this.requestConfig == null) { // nothing to merge
			return defaultRequestConfig;
		}
		RequestConfig.Builder builder = RequestConfig.copy(defaultRequestConfig);
		int connectTimeout = this.requestConfig.getConnectTimeout();
		if (connectTimeout >= 0) {
			builder.setConnectTimeout(connectTimeout);
		}
		int connectionRequestTimeout = this.requestConfig.getConnectionRequestTimeout();
		if (connectionRequestTimeout >= 0) {
			builder.setConnectionRequestTimeout(connectionRequestTimeout);
		}
		int socketTimeout = this.requestConfig.getSocketTimeout();
		if (socketTimeout >= 0) {
			builder.setSocketTimeout(socketTimeout);
		}
		return builder.build();
	}

	protected final RequestConfig getInternalRequestConfig() {
		return this.requestConfig;
	}

	/**
	 * Create a Commons HttpMethodBase object for the given HTTP method and URI specification.
	 * @param httpMethod the HTTP method
	 * @param uri the URI
	 * @return the Commons HttpMethodBase object
	 */
	protected HttpUriRequest createHttpUriRequest(HttpMethod httpMethod, URI uri) {
		switch (httpMethod) {
			case GET:
				return new HttpGet(uri);
			case DELETE:
				return new HttpDelete(uri);
			case HEAD:
				return new HttpHead(uri);
			case OPTIONS:
				return new HttpOptions(uri);
			case POST:
				return new HttpPost(uri);
			case PUT:
				return new HttpPut(uri);
			case TRACE:
				return new HttpTrace(uri);
			case PATCH:
				return new HttpPatch(uri);
			default:
				throw new IllegalArgumentException("Invalid HTTP method: " + httpMethod);
		}
	}

	/**
	 * Template method that allows for manipulating the {@link HttpUriRequest} before it is
	 * returned as part of a {@link HttpComponentsClientHttpRequest}.
	 * <p>The default implementation is empty.
	 * @param request the request to process
	 */
	protected void postProcessHttpRequest(HttpUriRequest request) {
	}

	/**
	 * Template methods that creates a {@link HttpContext} for the given HTTP method and URI.
	 * <p>The default implementation returns {@code null}.
	 * @param httpMethod the HTTP method
	 * @param uri the URI
	 * @return the http context
	 */
	protected HttpContext createHttpContext(HttpMethod httpMethod, URI uri) {
		return null;
	}


	/**
	 * Shutdown hook that closes the underlying
	 * {@link org.apache.http.conn.HttpClientConnectionManager ClientConnectionManager}'s
	 * connection pool, if any.
	 */
	@Override
	public void destroy() throws Exception {
		if (this.httpClient instanceof Closeable) {
			((Closeable) this.httpClient).close();
		}
	}


	/**
	 * An alternative to {@link org.apache.http.client.methods.HttpDelete} that
	 * extends {@link org.apache.http.client.methods.HttpEntityEnclosingRequestBase}
	 * rather than {@link org.apache.http.client.methods.HttpRequestBase} and
	 * hence allows HTTP delete with a request body. For use with the RestTemplate
	 * exchange methods which allow the combination of HTTP DELETE with entity.
	 * @since 4.1.2
	 */
	private static class HttpDelete extends HttpEntityEnclosingRequestBase {

		public HttpDelete(URI uri) {
			super();
			setURI(uri);
		}

		@Override
		public String getMethod() {
			return "DELETE";
		}
	}

}
