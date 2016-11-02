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

package org.springframework.http.codec;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.util.MimeTypeUtils;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link ResourceHttpMessageWriter}.
 *
 * @author Brian Clozel
 */
public class ResourceHttpMessageWriterTests {

	private ResourceHttpMessageWriter writer = new ResourceHttpMessageWriter();

	private MockServerHttpRequest request = new MockServerHttpRequest();

	private MockServerHttpResponse response = new MockServerHttpResponse();

	private Resource resource;


	@Before
	public void setUp() throws Exception {
		String content = "Spring Framework test resource content.";
		this.resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
	}


	@Test
	public void writableMediaTypes() throws Exception {
		assertThat(this.writer.getWritableMediaTypes(),
				containsInAnyOrder(MimeTypeUtils.APPLICATION_OCTET_STREAM, MimeTypeUtils.ALL));
	}

	@Test
	public void shouldWriteResource() throws Exception {

		Mono<Void> mono = this.writer.write(Mono.just(resource), null,
				ResolvableType.forClass(Resource.class),
				MediaType.TEXT_PLAIN, this.request, this.response, Collections.emptyMap());
		StepVerifier.create(mono)
				.expectNextCount(0)
				.expectComplete()
				.verify();

		assertThat(this.response.getHeaders().getContentType(), is(MediaType.TEXT_PLAIN));
		assertThat(this.response.getHeaders().getContentLength(), is(39L));
		assertThat(this.response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES), is("bytes"));

		Mono<String> result = this.response.getBodyAsString();
		StepVerifier.create(result)
				.expectNext("Spring Framework test resource content.")
				.expectComplete()
				.verify();
	}

	@Test
	public void shouldWriteResourceRange() throws Exception {
		this.request.getHeaders().setRange(Collections.singletonList(HttpRange.createByteRange(0, 5)));
		Mono<Void> mono = this.writer.write(Mono.just(resource), null, ResolvableType.forClass(Resource.class),
				MediaType.TEXT_PLAIN, this.request, this.response, Collections.emptyMap());
		StepVerifier.create(mono)
				.expectNextCount(0)
				.expectComplete()
				.verify();

		assertThat(this.response.getHeaders().getContentType(), is(MediaType.TEXT_PLAIN));
		assertThat(this.response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE), is("bytes 0-5/39"));
		assertThat(this.response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES), is("bytes"));
		assertThat(this.response.getHeaders().getContentLength(), is(6L));

		Mono<String> result = this.response.getBodyAsString();
		StepVerifier.create(result)
				.expectNext("Spring")
				.expectComplete()
				.verify();
	}

	@Test
	public void shouldSetRangeNotSatisfiableStatus() throws Exception {
		this.request.getHeaders().set(HttpHeaders.RANGE, "invalid");

		Mono<Void> mono = this.writer.write(Mono.just(resource), null, ResolvableType.forClass(Resource.class),
				MediaType.TEXT_PLAIN, this.request, this.response, Collections.emptyMap());
		StepVerifier.create(mono)
				.expectNextCount(0)
				.expectComplete()
				.verify();

		assertThat(this.response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES), is("bytes"));
		assertThat(this.response.getStatusCode(), is(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE));
	}

}
