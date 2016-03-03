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

package org.springframework.web.util;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.Assert;

/**
 * Default implementation of {@link UriTemplateHandler} that uses
 * {@link UriComponentsBuilder} to expand and encode variables.
 *
 * @author Rossen Stoyanchev
 * @since 4.2
 */
public class DefaultUriTemplateHandler implements UriTemplateHandler {

	private String baseUrl;

	private boolean parsePath;

	private boolean strictEncoding;


	/**
	 * Configure a base URL to prepend URI templates with. The base URL must
	 * have a scheme and host but may optionally contain a port and a path.
	 * The base URL must be fully expanded and encoded which can be done via
	 * {@link UriComponentsBuilder}.
	 * @param baseUrl the base URL.
	 */
	public void setBaseUrl(String baseUrl) {
		if (baseUrl != null) {
			UriComponents uriComponents = UriComponentsBuilder.fromUriString(baseUrl).build();
			Assert.hasText(uriComponents.getScheme(), "'baseUrl' must have a scheme");
			Assert.hasText(uriComponents.getHost(), "'baseUrl' must have a host");
			Assert.isNull(uriComponents.getQuery(), "'baseUrl' cannot have a query");
			Assert.isNull(uriComponents.getFragment(), "'baseUrl' cannot have a fragment");
		}
		this.baseUrl = baseUrl;
	}

	/**
	 * Return the configured base URL.
	 */
	public String getBaseUrl() {
		return this.baseUrl;
	}

	/**
	 * Whether to parse the path of a URI template string into path segments.
	 * <p>If set to {@code true} the URI template path is immediately decomposed
	 * into path segments any URI variables expanded into it are then subject to
	 * path segment encoding rules. In effect URI variables in the path have any
	 * "/" characters percent encoded.
	 * <p>By default this is set to {@code false} in which case the path is kept
	 * as a full path and expanded URI variables will preserve "/" characters.
	 * @param parsePath whether to parse the path into path segments
	 */
	public void setParsePath(boolean parsePath) {
		this.parsePath = parsePath;
	}

	/**
	 * Whether the handler is configured to parse the path into path segments.
	 */
	public boolean shouldParsePath() {
		return this.parsePath;
	}

	/**
	 * Whether to encode characters outside the unreserved set as defined in
	 * <a href="https://tools.ietf.org/html/rfc3986#section-2">RFC 3986 Section 2</a>.
	 * This ensures a URI variable value will not contain any characters with a
	 * reserved purpose.
	 * <p>By default this is set to {@code false} in which case only characters
	 * illegal for the given URI component are encoded. For example when expanding
	 * a URI variable into a path segment the "/" character is illegal and
	 * encoded. The ";" character however is legal and not encoded even though
	 * it has a reserved purpose.
	 * <p><strong>Note:</strong> this property supersedes the need to also set
	 * the {@link #setParsePath parsePath} property.
	 * @param strictEncoding whether to perform strict encoding
	 * @since 4.3
	 */
	public void setStrictEncoding(boolean strictEncoding) {
		this.strictEncoding = strictEncoding;
	}

	/**
	 * Whether to strictly encode any character outside the unreserved set.
	 */
	public boolean isStrictEncoding() {
		return this.strictEncoding;
	}


	@Override
	public URI expand(String uriTemplate, Map<String, ?> uriVariables) {
		UriComponentsBuilder uriComponentsBuilder = initUriComponentsBuilder(uriTemplate);
		UriComponents uriComponents = expandAndEncode(uriComponentsBuilder, uriVariables);
		return insertBaseUrl(uriComponents);
	}

	@Override
	public URI expand(String uriTemplate, Object... uriVariables) {
		UriComponentsBuilder uriComponentsBuilder = initUriComponentsBuilder(uriTemplate);
		UriComponents uriComponents = expandAndEncode(uriComponentsBuilder, uriVariables);
		return insertBaseUrl(uriComponents);
	}

	/**
	 * Create a {@code UriComponentsBuilder} from the UriTemplate string. The
	 * default implementation also parses the path into path segments if
	 * {@link #setParsePath parsePath} is enabled.
	 */
	protected UriComponentsBuilder initUriComponentsBuilder(String uriTemplate) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uriTemplate);
		if (shouldParsePath() && !isStrictEncoding()) {
			List<String> pathSegments = builder.build().getPathSegments();
			builder.replacePath(null);
			for (String pathSegment : pathSegments) {
				builder.pathSegment(pathSegment);
			}
		}
		return builder;
	}

	protected UriComponents expandAndEncode(UriComponentsBuilder builder, Map<String, ?> uriVariables) {
		if (!isStrictEncoding()) {
			return builder.build().expand(uriVariables).encode();
		}
		else {
			Map<String, Object> encodedUriVars = new HashMap<String, Object>(uriVariables.size());
			for (Map.Entry<String, ?> entry : uriVariables.entrySet()) {
				encodedUriVars.put(entry.getKey(), encodeValue(entry.getValue()));
			}
			return builder.build().expand(encodedUriVars);
		}
	}

	protected UriComponents expandAndEncode(UriComponentsBuilder builder, Object[] uriVariables) {
		if (!isStrictEncoding()) {
			return builder.build().expand(uriVariables).encode();
		}
		else {
			Object[] encodedUriVars = new Object[uriVariables.length];
			for (int i = 0; i < uriVariables.length; i++) {
				encodedUriVars[i] = encodeValue(uriVariables[i]);
			}
			return builder.build().expand(encodedUriVars);
		}
	}

	private String encodeValue(Object value) {
		String stringValue = (value != null ? value.toString() : "");
		try {
			return UriUtils.encode(stringValue, "UTF-8");
		}
		catch (UnsupportedEncodingException ex) {
			// Should never happen
			throw new IllegalStateException("Failed to encode URI variable", ex);
		}
	}

	/**
	 * Invoked after the URI template has been expanded and encoded to prepend
	 * the configured {@link #setBaseUrl(String) baseUrl} if any.
	 * @param uriComponents the expanded and encoded URI
	 * @return the final URI
	 */
	protected URI insertBaseUrl(UriComponents uriComponents) {
		String url = uriComponents.toUriString();
		if (getBaseUrl() != null && uriComponents.getHost() == null) {
			url = getBaseUrl() + url;
		}
		try {
			return new URI(url);
		}
		catch (URISyntaxException ex) {
			throw new IllegalArgumentException("Invalid URL after inserting base URL: " + url, ex);
		}
	}

}
