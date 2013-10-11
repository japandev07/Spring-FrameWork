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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

/**
 * {@link ClientHttpRequest} implementation that uses OkHttp to execute requests.
 *
 * <p>Created via the {@link OkHttpClientHttpRequestFactory}.
 *
 * @author Luciano Leggieri
 * @author Arjen Poutsma
 * @since 4.2
 */
class OkHttpClientHttpRequest extends AbstractBufferingAsyncClientHttpRequest
		implements ClientHttpRequest {

	private final OkHttpClient client;

	private final URI uri;

	private final HttpMethod method;


	public OkHttpClientHttpRequest(OkHttpClient client, URI uri, HttpMethod method) {
		this.client = client;
		this.uri = uri;
		this.method = method;
	}


	@Override
	public HttpMethod getMethod() {
		return method;
	}

	@Override
	public URI getURI() {
		return uri;
	}

	@Override
	protected ListenableFuture<ClientHttpResponse> executeInternal(HttpHeaders headers,
			byte[] bufferedOutput) throws IOException {
		RequestBody body = bufferedOutput.length > 0 ?
				RequestBody.create(getContentType(headers), bufferedOutput) : null;

		Request.Builder builder = new Request.Builder().
				url(this.uri.toURL()).
				method(this.method.name(), body);

		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
			String headerName = entry.getKey();
			for (String headerValue : entry.getValue()) {
				builder.addHeader(headerName, headerValue);
			}
		}
		Request request = builder.build();

		return new ListenableFutureCall(client.newCall(request));
	}

	private MediaType getContentType(HttpHeaders headers) {
		org.springframework.http.MediaType contentType = headers.getContentType();
		return contentType != null ? MediaType.parse(contentType.toString()) : null;
	}

	@Override
	public ClientHttpResponse execute() throws IOException {
		try {
			return executeAsync().get();
		}
		catch (InterruptedException ex) {
			throw new IOException(ex.getMessage(), ex);
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof IOException) {
				throw (IOException) ex.getCause();
			}
			else {
				throw new IOException(ex.getMessage(), ex);
			}
		}
	}

	private static class ListenableFutureCall extends
			SettableListenableFuture<ClientHttpResponse> {

	    private final Call call;

	    public ListenableFutureCall(Call call) {
	        this.call = call;
	        this.call.enqueue(new Callback() {
		        @Override
		        public void onResponse(Response response) throws IOException {
			        set(new OkHttpClientHttpResponse(response));
		        }

		        @Override
		        public void onFailure(Request request, IOException ex) {
			        setException(ex);
		        }
	        });
	    }

	    @Override
	    protected void interruptTask() {
	        call.cancel();
	    }
	}

}
