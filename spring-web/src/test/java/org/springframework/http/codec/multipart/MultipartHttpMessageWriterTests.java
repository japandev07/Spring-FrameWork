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

package org.springframework.http.codec.multipart;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Sebastien Deleuze
 */
public class MultipartHttpMessageWriterTests {

	private final MultipartHttpMessageWriter writer = new MultipartHttpMessageWriter(Arrays.asList(
			new EncoderHttpMessageWriter<>(CharSequenceEncoder.textPlainOnly()),
			new ResourceHttpMessageWriter(),
			new EncoderHttpMessageWriter<>(new Jackson2JsonEncoder())
	));


	@Test
	public void canWrite() {
		assertTrue(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, Object.class),
				MediaType.MULTIPART_FORM_DATA));
		assertTrue(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, String.class),
				MediaType.MULTIPART_FORM_DATA));

		assertFalse(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class),
				MediaType.MULTIPART_FORM_DATA));
		assertFalse(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, Object.class),
				MediaType.APPLICATION_FORM_URLENCODED));
	}

	@Test
	public void writeMultipart() throws Exception {
		MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
		map.add("name 1", "value 1");
		map.add("name 2", "value 2+1");
		map.add("name 2", "value 2+2");

		Resource logo = new ClassPathResource("/org/springframework/http/converter/logo.jpg");
		map.add("logo", logo);

		// SPR-12108
		Resource utf8 = new ClassPathResource("/org/springframework/http/converter/logo.jpg") {
			@Override
			public String getFilename() {
				return "Hall\u00F6le.jpg";
			}
		};
		map.add("utf8", utf8);

		HttpHeaders entityHeaders = new HttpHeaders();
		entityHeaders.setContentType(MediaType.APPLICATION_JSON_UTF8);
		HttpEntity<Foo> entity = new HttpEntity<>(new Foo("bar"), entityHeaders);
		map.add("json", entity);

		MockServerHttpResponse response = new MockServerHttpResponse();
		Map<String, Object> hints = Collections.emptyMap();
		this.writer.write(Mono.just(map), null, MediaType.MULTIPART_FORM_DATA, response, hints).block();

		final MediaType contentType = response.getHeaders().getContentType();
		assertNotNull("No boundary found", contentType.getParameter("boundary"));

		// see if Synchronoss NIO Multipart can read what we wrote
		SynchronossPartHttpMessageReader synchronossReader = new SynchronossPartHttpMessageReader();
		MultipartHttpMessageReader reader = new MultipartHttpMessageReader(synchronossReader);

		MockServerHttpRequest request = MockServerHttpRequest.post("/foo")
				.header(HttpHeaders.CONTENT_TYPE, contentType.toString())
				.body(response.getBody());

		ResolvableType elementType = ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, Part.class);
		MultiValueMap<String, Part> requestParts = reader.readMono(elementType, request, hints).block();
		assertEquals(5, requestParts.size());

		Part part = requestParts.getFirst("name 1");
		assertTrue(part instanceof FormFieldPart);
		assertEquals("name 1", part.getName());
		assertEquals("value 1", ((FormFieldPart) part).getValue());

		List<Part> parts2 = requestParts.get("name 2");
		assertEquals(2, parts2.size());
		part = parts2.get(0);
		assertTrue(part instanceof FormFieldPart);
		assertEquals("name 2", part.getName());
		assertEquals("value 2+1", ((FormFieldPart) part).getValue());
		part = parts2.get(1);
		assertTrue(part instanceof FormFieldPart);
		assertEquals("name 2", part.getName());
		assertEquals("value 2+2", ((FormFieldPart) part).getValue());

		part = requestParts.getFirst("logo");
		assertTrue(part instanceof FilePart);
		assertEquals("logo", part.getName());
		assertEquals("logo.jpg", ((FilePart) part).getFilename());
		assertEquals(MediaType.IMAGE_JPEG, part.getHeaders().getContentType());
		assertEquals(logo.getFile().length(), part.getHeaders().getContentLength());

		part = requestParts.getFirst("utf8");
		assertTrue(part instanceof FilePart);
		assertEquals("utf8", part.getName());
		assertEquals("Hall\u00F6le.jpg", ((FilePart) part).getFilename());
		assertEquals(MediaType.IMAGE_JPEG, part.getHeaders().getContentType());
		assertEquals(utf8.getFile().length(), part.getHeaders().getContentLength());

		part = requestParts.getFirst("json");
		assertEquals("json", part.getName());
		assertEquals(MediaType.APPLICATION_JSON_UTF8, part.getHeaders().getContentType());
		assertEquals("{\"bar\":\"bar\"}", ((FormFieldPart) part).getValue());
	}


	private class Foo {

		private String bar;

		public Foo() {
		}

		public Foo(String bar) {
			this.bar = bar;
		}

		public String getBar() {
			return bar;
		}

		public void setBar(String bar) {
			this.bar = bar;
		}
	}

}
