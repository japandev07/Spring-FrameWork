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

package org.springframework.web.reactive.result.method.annotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.AbstractHttpHandlerIntegrationTests;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class MultipartIntegrationTests extends AbstractHttpHandlerIntegrationTests {

	private WebClient webClient;


	@Override
	@Before
	public void setup() throws Exception {
		super.setup();
		this.webClient = WebClient.create("http://localhost:" + this.port);
	}


	@Override
	protected HttpHandler createHttpHandler() {
		AnnotationConfigApplicationContext wac = new AnnotationConfigApplicationContext();
		wac.register(TestConfiguration.class);
		wac.refresh();
		return WebHttpHandlerBuilder.webHandler(new DispatcherHandler(wac)).build();
	}

	@Test
	public void requestPart() {
		Mono<ClientResponse> result = webClient
				.post()
				.uri("/requestPart")
				.syncBody(generateBody())
				.exchange();

		StepVerifier
				.create(result)
				.consumeNextWith(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
				.verifyComplete();
	}

	@Test
	public void requestBodyMap() {
		Mono<String> result = webClient
				.post()
				.uri("/requestBodyMap")
				.syncBody(generateBody())
				.retrieve()
				.bodyToMono(String.class);

		StepVerifier.create(result)
				.consumeNextWith(body -> assertThat(body).isEqualTo("Map[[fieldPart],[fileParts:foo.txt,fileParts:logo.png],[jsonPart]]"))
				.verifyComplete();
	}

	@Test
	public void requestBodyFlux() {
		Mono<String> result = webClient
				.post()
				.uri("/requestBodyFlux")
				.syncBody(generateBody())
				.retrieve()
				.bodyToMono(String.class);

		StepVerifier.create(result)
				.consumeNextWith(body -> assertThat(body).isEqualTo("[fieldPart,fileParts:foo.txt,fileParts:logo.png,jsonPart]"))
				.verifyComplete();
	}

	@Test
	public void filePartsFlux() {
		Mono<String> result = webClient
				.post()
				.uri("/filePartFlux")
				.syncBody(generateBody())
				.retrieve()
				.bodyToMono(String.class);

		StepVerifier.create(result)
				.consumeNextWith(body -> assertThat(body).isEqualTo("[fileParts:foo.txt,fileParts:logo.png]"))
				.verifyComplete();
	}

	@Test
	public void filePartsMono() {
		Mono<String> result = webClient
				.post()
				.uri("/filePartMono")
				.syncBody(generateBody())
				.retrieve()
				.bodyToMono(String.class);

		StepVerifier.create(result)
				.consumeNextWith(body -> assertThat(body).isEqualTo("[fileParts:foo.txt]"))
				.verifyComplete();
	}

	@Test
	public void transferTo() {
		Flux<String> result = webClient
				.post()
				.uri("/transferTo")
				.syncBody(generateBody())
				.retrieve()
				.bodyToFlux(String.class);

		StepVerifier.create(result)
				.consumeNextWith(filename -> verifyContents(Paths.get(filename), new ClassPathResource("foo.txt", MultipartHttpMessageReader.class)))
				.consumeNextWith(filename -> verifyContents(Paths.get(filename), new ClassPathResource("logo.png", getClass())))
				.verifyComplete();

	}

	private static void verifyContents(Path tempFile, Resource resource) {
		try {
			byte[] tempBytes = Files.readAllBytes(tempFile);
			byte[] resourceBytes = Files.readAllBytes(resource.getFile().toPath());
			assertThat(tempBytes).isEqualTo(resourceBytes);
		}
		catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}


	@Test
	public void modelAttribute() {
		Mono<String> result = webClient
				.post()
				.uri("/modelAttribute")
				.syncBody(generateBody())
				.retrieve()
				.bodyToMono(String.class);

		StepVerifier.create(result)
				.consumeNextWith(body -> assertThat(body).isEqualTo("FormBean[fieldValue,[fileParts:foo.txt,fileParts:logo.png]]"))
				.verifyComplete();
	}

	private MultiValueMap<String, HttpEntity<?>> generateBody() {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("fieldPart", "fieldValue");
		builder.part("fileParts", new ClassPathResource("foo.txt", MultipartHttpMessageReader.class));
		builder.part("fileParts", new ClassPathResource("logo.png", getClass()));
		builder.part("jsonPart", new Person("Jason"));
		return builder.build();
	}


	@Configuration
	@EnableWebFlux
	@SuppressWarnings("unused")
	static class TestConfiguration {

		@Bean
		public MultipartController testController() {
			return new MultipartController();
		}
	}

	@RestController
	@SuppressWarnings("unused")
	static class MultipartController {

		@PostMapping("/requestPart")
		void requestPart(@RequestPart FormFieldPart fieldPart,
				@RequestPart("fileParts") FilePart fileParts,
				@RequestPart("jsonPart") Mono<Person> personMono) {

			assertThat(fieldPart.value()).isEqualTo("fieldValue");
			assertThat(partDescription(fileParts)).isEqualTo("fileParts:foo.txt");

			StepVerifier.create(personMono)
					.consumeNextWith(p -> assertThat(p.getName()).isEqualTo("Jason"))
					.verifyComplete();
		}

		@PostMapping("/requestBodyMap")
		Mono<String> requestBodyMap(@RequestBody Mono<MultiValueMap<String, Part>> partsMono) {
			return partsMono.map(MultipartIntegrationTests::partMapDescription);
		}

		@PostMapping("/requestBodyFlux")
		Mono<String> requestBodyFlux(@RequestBody Flux<Part> parts) {
			return partFluxDescription(parts);
		}

		@PostMapping("/filePartFlux")
		Mono<String> filePartsFlux(@RequestPart("fileParts") Flux<FilePart> parts) {
			return partFluxDescription(parts);
		}

		@PostMapping("/filePartMono")
		Mono<String> filePartsFlux(@RequestPart("fileParts") Mono<FilePart> parts) {
			return partFluxDescription(Flux.from(parts));
		}

		@PostMapping("/transferTo")
		Flux<String> transferTo(@RequestPart("fileParts") Flux<FilePart> parts) {
			return parts.flatMap(filePart -> {
				try {
					Path tempFile = Files.createTempFile("MultipartIntegrationTests", filePart.filename());
					return filePart.transferTo(tempFile)
							.then(Mono.just(tempFile.toString() + "\n"));

				}
				catch (IOException e) {
					return Mono.error(e);
				}
			});
		}

		@PostMapping("/modelAttribute")
		String modelAttribute(@ModelAttribute FormBean formBean) {
			return formBean.toString();
		}
	}

	private static String partMapDescription(MultiValueMap<String, Part> partsMap) {
		return partsMap.keySet().stream().sorted()
				.map(key -> partListDescription(partsMap.get(key)))
				.collect(Collectors.joining(",", "Map[", "]"));
	}

	private static Mono<String> partFluxDescription(Flux<? extends Part> partsFlux) {
		return partsFlux.collectList().map(MultipartIntegrationTests::partListDescription);
	}

	private static String partListDescription(List<? extends Part> parts) {
		return parts.stream().map(MultipartIntegrationTests::partDescription)
				.collect(Collectors.joining(",", "[", "]"));
	}

	private static String partDescription(Part part) {
		return part instanceof FilePart ? part.name() + ":" + ((FilePart) part).filename() : part.name();
	}

	static class FormBean {

		private String fieldPart;

		private List<FilePart> fileParts;


		public String getFieldPart() {
			return this.fieldPart;
		}

		public void setFieldPart(String fieldPart) {
			this.fieldPart = fieldPart;
		}

		public List<FilePart> getFileParts() {
			return this.fileParts;
		}

		public void setFileParts(List<FilePart> fileParts) {
			this.fileParts = fileParts;
		}

		@Override
		public String toString() {
			return "FormBean[" + getFieldPart() + "," + partListDescription(getFileParts()) + "]";
		}
	}

	private static class Person {

		private String name;

		@JsonCreator
		public Person(@JsonProperty("name") String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

}
