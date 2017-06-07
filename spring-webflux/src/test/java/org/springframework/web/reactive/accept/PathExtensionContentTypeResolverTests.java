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
package org.springframework.web.reactive.accept;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link PathExtensionContentTypeResolver}.
 *
 * @author Rossen Stoyanchev
 */
public class PathExtensionContentTypeResolverTests {

	@Test
	public void resolveFromRegistrations() throws Exception {
		ServerWebExchange exchange = MockServerHttpRequest.get("/test.html").toExchange();
		PathExtensionContentTypeResolver resolver = createResolver();
		List<MediaType> mediaTypes = resolver.resolveMediaTypes(exchange);

		assertEquals(Collections.singletonList(new MediaType("text", "html")), mediaTypes);

		Map<String, MediaType> mapping = Collections.singletonMap("HTML", MediaType.APPLICATION_XHTML_XML);
		resolver = new PathExtensionContentTypeResolver(mapping);
		mediaTypes = resolver.resolveMediaTypes(exchange);

		assertEquals(Collections.singletonList(new MediaType("application", "xhtml+xml")), mediaTypes);
	}

	@Test
	public void resolveFromMediaTypeFactory() throws Exception {
		ServerWebExchange exchange = MockServerHttpRequest.get("test.xls").toExchange();
		PathExtensionContentTypeResolver resolver = createResolver();
		List<MediaType> mediaTypes = resolver.resolveMediaTypes(exchange);

		assertEquals(Collections.singletonList(new MediaType("application", "vnd.ms-excel")), mediaTypes);
	}

	@Test // SPR-9390
	public void resolveFromFilenameWithEncodedURI() throws Exception {
		ServerWebExchange exchange = MockServerHttpRequest.get("/quo%20vadis%3f.html").toExchange();
		PathExtensionContentTypeResolver resolver = createResolver();
		List<MediaType> result = resolver.resolveMediaTypes(exchange);

		assertEquals("Invalid content type", Collections.singletonList(new MediaType("text", "html")), result);
	}

	@Test // SPR-10170
	public void resolveAndIgnoreUnknownExtension() throws Exception {
		ServerWebExchange exchange = MockServerHttpRequest.get("test.foobar").toExchange();
		PathExtensionContentTypeResolver resolver = createResolver();
		List<MediaType> mediaTypes = resolver.resolveMediaTypes(exchange);

		assertEquals(Collections.<MediaType>emptyList(), mediaTypes);
	}

	private PathExtensionContentTypeResolver createResolver() {
		return new PathExtensionContentTypeResolver(Collections.emptyMap());
	}

}
