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
package org.springframework.core.convert.support;

import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ReactorToRxJava1Converter}.
 * @author Rossen Stoyanchev
 */
public class MonoToCompletableFutureConverterTests {

	private GenericConversionService conversionService;


	@Before
	public void setUp() throws Exception {
		this.conversionService = new GenericConversionService();
		this.conversionService.addConverter(new MonoToCompletableFutureConverter());
	}

	@Test
	public void canConvert() throws Exception {
		assertTrue(this.conversionService.canConvert(Mono.class, CompletableFuture.class));
		assertTrue(this.conversionService.canConvert(CompletableFuture.class, Mono.class));

		assertFalse(this.conversionService.canConvert(Flux.class, CompletableFuture.class));
		assertFalse(this.conversionService.canConvert(CompletableFuture.class, Flux.class));

		assertFalse(this.conversionService.canConvert(Publisher.class, CompletableFuture.class));
		assertFalse(this.conversionService.canConvert(CompletableFuture.class, Publisher.class));
	}

}
