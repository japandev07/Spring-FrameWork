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

package org.springframework.test.web.reactive.server.samples;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static java.time.Duration.ofMillis;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;


/**
 * Annotated controllers accepting and returning typed Objects.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class ResponseEntityTests {

	private final WebTestClient client = WebTestClient.bindToController(new PersonController()).build();


	@Test
	public void entity() throws Exception {
		this.client.get().uri("/persons/John")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
				.expectBody(Person.class).isEqualTo(new Person("John"));
	}

	@Test
	public void entityWithConsumer() throws Exception {
		this.client.get().uri("/persons/John")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
				.expectBody(Person.class).consumeWith(p -> assertEquals(new Person("John"), p));
	}

	@Test
	public void entityList() throws Exception {

		List<Person> expected = Arrays.asList(
				new Person("Jane"), new Person("Jason"), new Person("John"));

		this.client.get().uri("/persons")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(MediaType.APPLICATION_JSON_UTF8)
				.expectBodyList(Person.class).isEqualTo(expected);
	}

	@Test
	public void entityMap() throws Exception {

		Map<String, Person> map = new LinkedHashMap<>();
		map.put("Jane", new Person("Jane"));
		map.put("Jason", new Person("Jason"));
		map.put("John", new Person("John"));

		this.client.get().uri("/persons?map=true")
				.exchange()
				.expectStatus().isOk()
				.expectBody(forClassWithGenerics(Map.class, String.class, Person.class)).isEqualTo(map);
	}

	@Test
	public void entityStream() throws Exception {

		FluxExchangeResult<Person> result = this.client.get()
				.uri("/persons")
				.accept(TEXT_EVENT_STREAM)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(TEXT_EVENT_STREAM)
				.returnResult(Person.class);

		StepVerifier.create(result.getResponseBody())
				.expectNext(new Person("N0"), new Person("N1"), new Person("N2"))
				.expectNextCount(4)
				.consumeNextWith(person -> assertThat(person.getName(), endsWith("7")))
				.thenCancel()
				.verify();
	}

	@Test
	public void postEntity() throws Exception {
		this.client.post().uri("/persons")
				.body(Mono.just(new Person("John")), Person.class)
				.exchange()
				.expectStatus().isCreated()
				.expectHeader().valueEquals("location", "/persons/John")
				.expectBody().isEmpty();
	}


	@RestController
	@RequestMapping("/persons")
	@SuppressWarnings("unused")
	static class PersonController {

		@GetMapping("/{name}")
		Person getPerson(@PathVariable String name) {
			return new Person(name);
		}

		@GetMapping
		Flux<Person> getPersons() {
			return Flux.just(new Person("Jane"), new Person("Jason"), new Person("John"));
		}

		@GetMapping(params = "map")
		Map<String, Person> getPersonsAsMap() {
			Map<String, Person> map = new LinkedHashMap<>();
			map.put("Jane", new Person("Jane"));
			map.put("Jason", new Person("Jason"));
			map.put("John", new Person("John"));
			return map;
		}

		@GetMapping(produces = "text/event-stream")
		Flux<Person> getPersonStream() {
			return Flux.interval(ofMillis(100)).onBackpressureBuffer(10).map(index -> new Person("N" + index));
		}

		@PostMapping
		ResponseEntity<String> savePerson(@RequestBody Person person) {
			return ResponseEntity.created(URI.create("/persons/" + person.getName())).build();
		}
	}

}
