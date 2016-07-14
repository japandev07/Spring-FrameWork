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

package org.springframework.web.reactive.result.method.annotation;

import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.WebReactiveConfiguration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


/**
 * Integration tests with {@code @RequestMapping} handler methods.
 *
 * <p>Before adding tests here consider if they are a better fit for any of the
 * other {@code RequestMapping*IntegrationTests}.
 *
 * @author Rossen Stoyanchev
 * @author Stephane Maldini
 */
public class RequestMappingIntegrationTests extends AbstractRequestMappingIntegrationTests {

	@Override
	protected ApplicationContext initApplicationContext() {
		AnnotationConfigApplicationContext wac = new AnnotationConfigApplicationContext();
		wac.register(WebConfig.class);
		wac.refresh();
		return wac;
	}

	@Test
	public void handleWithParam() throws Exception {
		String expected = "Hello George!";
		assertEquals(expected, performGet("/param?name=George", null, String.class).getBody());
	}

	@Test
	public void streamResult() throws Exception {
		String[] expected = {"0", "1", "2", "3", "4"};
		assertArrayEquals(expected, performGet("/stream-result", null, String[].class).getBody());
	}


	@Configuration
	@ComponentScan(resourcePattern = "**/RequestMappingIntegrationTests$*.class")
	@SuppressWarnings({"unused", "WeakerAccess"})
	static class WebConfig extends WebReactiveConfiguration {
	}

	@RestController
	@SuppressWarnings("unused")
	private static class TestRestController {

		@GetMapping("/param")
		public Publisher<String> handleWithParam(@RequestParam String name) {
			return Flux.just("Hello ", name, "!");
		}

		@GetMapping("/stream-result")
		public Publisher<Long> stringStreamResponseBody() {
			return Flux.intervalMillis(100).take(5);
		}

	}

}
