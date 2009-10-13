/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.web.client;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Interface specifying a basic set of RESTful operations. Implemented by {@link RestTemplate}. Not often used directly,
 * but a useful option to enhance testability, as it can easily be mocked or stubbed.
 *
 * @author Arjen Poutsma
 * @see RestTemplate
 * @since 3.0
 */
public interface RestOperations {

	// GET

	/**
	 * Retrieve a representation by doing a GET on the specified URL. The response (if any) is converted and returned.
	 * <p>URI Template variables are expanded using the given URI variables, if any.
	 *
	 * @param url the URL
	 * @param responseType the type of the return value
	 * @param uriVariables the variables to expand the template
	 * @return the converted object
	 */
	<T> T getForObject(String url, Class<T> responseType, String... uriVariables) throws RestClientException;

	/**
	 * Retrieve a representation by doing a GET on the URI template. The response (if any) is converted and returned.
	 * <p>URI Template variables are expanded using the given map.
	 *
	 * @param url the URL
	 * @param responseType the type of the return value
	 * @param uriVariables the map containing variables for the URI template
	 * @return the converted object
	 */
	<T> T getForObject(String url, Class<T> responseType, Map<String, String> uriVariables) throws RestClientException;

	/**
	 * Retrieve a representation by doing a GET on the URL . The response (if any) is converted and returned.
	 *
	 * @param url the URL
	 * @param responseType the type of the return value
	 * @return the converted object
	 */
	<T> T getForObject(URI url, Class<T> responseType) throws RestClientException;

	// HEAD

	/**
	 * Retrieve all headers of the resource specified by the URI template. <p>URI Template variables are expanded using the
	 * given URI variables, if any.
	 *
	 * @param url the URL
	 * @param uriVariables the variables to expand the template
	 * @return all HTTP headers of that resource
	 */
	HttpHeaders headForHeaders(String url, String... uriVariables) throws RestClientException;

	/**
	 * Retrieve all headers of the resource specified by the URI template. <p>URI Template variables are expanded using the
	 * given map.
	 *
	 * @param url the URL
	 * @param uriVariables the map containing variables for the URI template
	 * @return all HTTP headers of that resource
	 */
	HttpHeaders headForHeaders(String url, Map<String, String> uriVariables) throws RestClientException;

	/**
	 * Retrieve all headers of the resource specified by the URL.
	 *
	 * @param url the URL
	 * @return all HTTP headers of that resource
	 */
	HttpHeaders headForHeaders(URI url) throws RestClientException;

	// POST

	/**
	 * Create a new resource by POSTing the given object to the URI template, and returns the value of the
	 * <code>Location</code> header. This header typically indicates where the new resource is stored. <p>URI Template
	 * variables are expanded using the given URI variables, if any.
	 *
	 * @param url the URL
	 * @param request the Object to be POSTed, may be <code>null</code>
	 * @param uriVariables the variables to expand the template
	 * @return the value for the <code>Location</code> header
	 */
	URI postForLocation(String url, Object request, String... uriVariables) throws RestClientException;

	/**
	 * Create a new resource by POSTing the given object to the URI template, and returns the value of the
	 * <code>Location</code> header. This header typically indicates where the new resource is stored. <p>URI Template
	 * variables are expanded using the given map.
	 *
	 * @param url the URL
	 * @param request the Object to be POSTed, may be <code>null</code>
	 * @param uriVariables the variables to expand the template
	 * @return the value for the <code>Location</code> header
	 */
	URI postForLocation(String url, Object request, Map<String, String> uriVariables) throws RestClientException;

	/**
	 * Create a new resource by POSTing the given object to the URL, and returns the value of the
	 * <code>Location</code> header. This header typically indicates where the new resource is stored.
	 *
	 * @param url the URL
	 * @param request the Object to be POSTed, may be <code>null</code>
	 * @return the value for the <code>Location</code> header
	 */
	URI postForLocation(URI url, Object request) throws RestClientException;

	/**
	 * Create a new resource by POSTing the given object to the URI template, and returns the representation
	 * found in the response. <p>URI Template variables are expanded using the given URI variables, if any.
	 *
	 * @param url the URL
	 * @param request the Object to be POSTed, may be <code>null</code>
	 * @param uriVariables the variables to expand the template
	 * @return the converted object
	 */
	<T> T postForObject(String url, Object request, Class<T> responseType, String... uriVariables)
			throws RestClientException;

	/**
	 * Create a new resource by POSTing the given object to the URI template, and returns the representation
	 * found in the response. <p>URI Template variables are expanded using the given map.
	 *
	 * @param url the URL
	 * @param request the Object to be POSTed, may be <code>null</code>
	 * @param uriVariables the variables to expand the template
	 * @return the converted object
	 */
	<T> T postForObject(String url, Object request, Class<T> responseType, Map<String, String> uriVariables)
			throws RestClientException;

	/**
	 * Create a new resource by POSTing the given object to the URL, and returns the representation
	 * found in the response.
	 *
	 * @param url the URL
	 * @param request the Object to be POSTed, may be <code>null</code>
	 * @return the converted object
	 */
	<T> T postForObject(URI url, Object request, Class<T> responseType) throws RestClientException;

	// PUT

	/**
	 * Create or update a resource by PUTting the given object to the URI. <p>URI Template variables are expanded using the
	 * given URI variables, if any.
	 *
	 * @param url the URL
	 * @param request the Object to be PUT, may be <code>null</code>
	 * @param uriVariables the variables to expand the template
	 */
	void put(String url, Object request, String... uriVariables) throws RestClientException;

	/**
	 * Creates a new resource by PUTting the given object to URI template. <p>URI Template variables are expanded using the
	 * given map.
	 *
	 * @param url the URL
	 * @param request the Object to be PUT, may be <code>null</code>
	 * @param uriVariables the variables to expand the template
	 */
	void put(String url, Object request, Map<String, String> uriVariables) throws RestClientException;

	/**
	 * Creates a new resource by PUTting the given object to URL.
	 *
	 * @param url the URL
	 * @param request the Object to be PUT, may be <code>null</code>
	 */
	void put(URI url, Object request) throws RestClientException;

	// DELETE

	/**
	 * Delete the resources at the specified URI. <p>URI Template variables are expanded using the given URI variables, if
	 * any.
	 *
	 * @param url the URL
	 * @param uriVariables the variables to expand in the template
	 */
	void delete(String url, String... uriVariables) throws RestClientException;

	/**
	 * Delete the resources at the specified URI. <p>URI Template variables are expanded using the given map.
	 *
	 * @param url the URL
	 * @param uriVariables the variables to expand the template
	 */
	void delete(String url, Map<String, String> uriVariables) throws RestClientException;

	/**
	 * Delete the resources at the specified URL.
	 *
	 * @param url the URL
	 */
	void delete(URI url) throws RestClientException;

	// OPTIONS

	/**
	 * Return the value of the Allow header for the given URI. <p>URI Template variables are expanded using the given URI
	 * variables, if any.
	 *
	 * @param url the URL
	 * @param uriVariables the variables to expand in the template
	 * @return the value of the allow header
	 */
	Set<HttpMethod> optionsForAllow(String url, String... uriVariables) throws RestClientException;

	/**
	 * Return the value of the Allow header for the given URI. <p>URI Template variables are expanded using the given map.
	 *
	 * @param url the URL
	 * @param uriVariables the variables to expand in the template
	 * @return the value of the allow header
	 */
	Set<HttpMethod> optionsForAllow(String url, Map<String, String> uriVariables) throws RestClientException;

	/**
	 * Return the value of the Allow header for the given URL.
	 *
	 * @param url the URL
	 * @return the value of the allow header
	 */
	Set<HttpMethod> optionsForAllow(URI url) throws RestClientException;

	// general execution

	/**
	 * Execute the HTTP methods to the given URI template, preparing the request with the {@link RequestCallback}, and reading the
	 * response with a {@link ResponseExtractor}. <p>URI Template variables are expanded using the given URI variables, if
	 * any.
	 *
	 * @param url the URL
	 * @param method the HTTP method (GET, POST, etc)
	 * @param requestCallback object that prepares the request
	 * @param responseExtractor object that extracts the return value from the response
	 * @param uriVariables the variables to expand in the template
	 * @return an arbitrary object, as returned by the {@link ResponseExtractor}
	 */
	<T> T execute(String url,
			HttpMethod method,
			RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor,
			String... uriVariables) throws RestClientException;

	/**
	 * Execute the HTTP methods to the given URI template, preparing the request with the {@link RequestCallback}, and reading the
	 * response with a {@link ResponseExtractor}. <p>URI Template variables are expanded using the given URI variables
	 * map.
	 *
	 * @param url the URL
	 * @param method the HTTP method (GET, POST, etc)
	 * @param requestCallback object that prepares the request
	 * @param responseExtractor object that extracts the return value from the response
	 * @param uriVariables the variables to expand in the template
	 * @return an arbitrary object, as returned by the {@link ResponseExtractor}
	 */
	<T> T execute(String url,
			HttpMethod method,
			RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor,
			Map<String, String> uriVariables) throws RestClientException;

	/**
	 * Execute the HTTP methods to the given URL, preparing the request with the {@link RequestCallback}, and reading the
	 * response with a {@link ResponseExtractor}.
	 *
	 * @param url the URL
	 * @param method the HTTP method (GET, POST, etc)
	 * @param requestCallback object that prepares the request
	 * @param responseExtractor object that extracts the return value from the response
	 * @return an arbitrary object, as returned by the {@link ResponseExtractor}
	 */
	<T> T execute(URI url,
			HttpMethod method,
			RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor) throws RestClientException;

}
