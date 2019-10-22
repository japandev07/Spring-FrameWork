/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http.codec.multipart;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.AbstractLeakCheckingTests;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.support.DataBufferTestUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.test.MockClientHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.util.MultiValueMap;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;

/**
 * Unit tests for {@link SynchronossPartHttpMessageReader}.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
public class SynchronossPartHttpMessageReaderTests extends AbstractLeakCheckingTests {

	private final MultipartHttpMessageReader reader =
			new MultipartHttpMessageReader(new SynchronossPartHttpMessageReader(this.bufferFactory));

	private static final ResolvableType PARTS_ELEMENT_TYPE =
			forClassWithGenerics(MultiValueMap.class, String.class, Part.class);

	@Test
	void canRead() {
		assertThat(this.reader.canRead(
				PARTS_ELEMENT_TYPE,
				MediaType.MULTIPART_FORM_DATA)).isTrue();

		assertThat(this.reader.canRead(
				forClassWithGenerics(MultiValueMap.class, String.class, Object.class),
				MediaType.MULTIPART_FORM_DATA)).isFalse();

		assertThat(this.reader.canRead(
				forClassWithGenerics(MultiValueMap.class, String.class, String.class),
				MediaType.MULTIPART_FORM_DATA)).isFalse();

		assertThat(this.reader.canRead(
				forClassWithGenerics(Map.class, String.class, String.class),
				MediaType.MULTIPART_FORM_DATA)).isFalse();

		assertThat(this.reader.canRead(
				forClassWithGenerics(MultiValueMap.class, String.class, Part.class),
				MediaType.APPLICATION_FORM_URLENCODED)).isFalse();
	}

	@Test
	void resolveParts() {
		ServerHttpRequest request = generateMultipartRequest();
		MultiValueMap<String, Part> parts = this.reader.readMono(PARTS_ELEMENT_TYPE, request, emptyMap()).block();

		assertThat(parts).containsOnlyKeys("filePart", "textPart");

		Part part = parts.getFirst("filePart");
		assertThat(part).isInstanceOf(FilePart.class);
		assertThat(part.name()).isEqualTo("filePart");
		assertThat(((FilePart) part).filename()).isEqualTo("foo.txt");
		DataBuffer buffer = DataBufferUtils.join(part.content()).block();
		assertThat(DataBufferTestUtils.dumpString(buffer, StandardCharsets.UTF_8)).isEqualTo("Lorem Ipsum.");
		DataBufferUtils.release(buffer);

		part = parts.getFirst("textPart");
		assertThat(part).isInstanceOf(FormFieldPart.class);
		assertThat(part.name()).isEqualTo("textPart");
		assertThat(((FormFieldPart) part).value()).isEqualTo("sample-text");
	}

	@Test // SPR-16545
	void transferTo() throws IOException {
		ServerHttpRequest request = generateMultipartRequest();
		MultiValueMap<String, Part> parts = this.reader.readMono(PARTS_ELEMENT_TYPE, request, emptyMap()).block();

		assertThat(parts).isNotNull();
		FilePart part = (FilePart) parts.getFirst("filePart");
		assertThat(part).isNotNull();

		File dest = File.createTempFile(part.filename(), "multipart");
		part.transferTo(dest).block(Duration.ofSeconds(5));

		assertThat(dest.exists()).isTrue();
		assertThat(dest.length()).isEqualTo(12);
		assertThat(dest.delete()).isTrue();
	}

	@Test
	void bodyError() {
		ServerHttpRequest request = generateErrorMultipartRequest();
		StepVerifier.create(this.reader.readMono(PARTS_ELEMENT_TYPE, request, emptyMap())).verifyError();
	}

	@Test
	void readPartsWithoutDemand() {
		ServerHttpRequest request = generateMultipartRequest();
		Mono<MultiValueMap<String, Part>> parts = this.reader.readMono(PARTS_ELEMENT_TYPE, request, emptyMap());
		ZeroDemandSubscriber subscriber = new ZeroDemandSubscriber();
		parts.subscribe(subscriber);
		subscriber.cancel();
	}

	@Test
	void readTooManyParts() {
		testMultipartExceptions(
				reader -> reader.setMaxPartCount(1),
				err -> {
					assertThat(err).isInstanceOf(MultipartException.class)
							.hasMessage("Could not parse multipart request");
					assertThat(err.getCause()).hasMessage("Exceeded limit on maximum number of multipart parts");
				}
		);
	}
	
	@Test
	void readFilePartTooBig() {
		testMultipartExceptions(
				reader -> reader.setMaxFilePartSize(5),
				err -> {
					assertThat(err).isInstanceOf(MultipartException.class)
							.hasMessage("Could not parse multipart request");
					assertThat(err.getCause()).hasMessage("Exceeded limit on max size of multipart file : 5");
				}
		);
	}

	@Test
	void readPartTooBig() {
		testMultipartExceptions(
				reader -> reader.setMaxPartSize(6),
				err -> {
					assertThat(err).isInstanceOf(MultipartException.class)
							.hasMessage("Could not parse multipart request");
					assertThat(err.getCause()).hasMessage("Exceeded limit on max size of multipart part : 6");
				}
		);
	}

	private void testMultipartExceptions(Consumer<SynchronossPartHttpMessageReader> configurer,
		Consumer<Throwable> assertions) {
		SynchronossPartHttpMessageReader synchronossReader = new SynchronossPartHttpMessageReader(this.bufferFactory);
		configurer.accept(synchronossReader);
		MultipartHttpMessageReader reader = new MultipartHttpMessageReader(synchronossReader);
		ServerHttpRequest request = generateMultipartRequest();
		StepVerifier.create(reader.readMono(PARTS_ELEMENT_TYPE, request, emptyMap()))
				.consumeErrorWith(assertions)
				.verify();
	}

	private ServerHttpRequest generateMultipartRequest() {
		MultipartBodyBuilder partsBuilder = new MultipartBodyBuilder();
		partsBuilder.part("filePart", new ClassPathResource("org/springframework/http/codec/multipart/foo.txt"));
		partsBuilder.part("textPart", "sample-text");

		MockClientHttpRequest outputMessage = new MockClientHttpRequest(HttpMethod.POST, "/");
		new MultipartHttpMessageWriter()
				.write(Mono.just(partsBuilder.build()), null, MediaType.MULTIPART_FORM_DATA, outputMessage, null)
				.block(Duration.ofSeconds(5));
		Flux<DataBuffer> requestBody = outputMessage.getBody().map(buffer -> this.bufferFactory.wrap(buffer.asByteBuffer()));
		return MockServerHttpRequest.post("/")
				.contentType(outputMessage.getHeaders().getContentType())
				.body(requestBody);
	}

	private ServerHttpRequest generateErrorMultipartRequest() {
		return MockServerHttpRequest.post("/")
				.header(CONTENT_TYPE, MULTIPART_FORM_DATA.toString())
				.body(Flux.just(this.bufferFactory.wrap("invalid content".getBytes())));
	}

	private static class ZeroDemandSubscriber extends BaseSubscriber<MultiValueMap<String, Part>> {

		@Override
		protected void hookOnSubscribe(Subscription subscription) {
			// Just subscribe without requesting
		}
	}

}
