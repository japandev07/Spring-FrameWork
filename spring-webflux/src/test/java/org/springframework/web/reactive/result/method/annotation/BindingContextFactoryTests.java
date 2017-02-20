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

package org.springframework.web.reactive.result.method.annotation;

import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import rx.Single;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.WebExchangeDataBinder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.config.WebFluxConfigurationSupport;
import org.springframework.web.reactive.result.ResolvableMethod;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link BindingContextFactory}.
 * @author Rossen Stoyanchev
 */
public class BindingContextFactoryTests {

	private BindingContextFactory contextFactory;

	private ServerWebExchange exchange;


	@Before
	public void setup() throws Exception {
		WebFluxConfigurationSupport configurationSupport = new WebFluxConfigurationSupport();
		configurationSupport.setApplicationContext(new StaticApplicationContext());
		RequestMappingHandlerAdapter adapter = configurationSupport.requestMappingHandlerAdapter();
		adapter.afterPropertiesSet();
		this.contextFactory = new BindingContextFactory(adapter);

		MockServerHttpRequest request = MockServerHttpRequest.get("/path").build();
		MockServerHttpResponse response = new MockServerHttpResponse();
		this.exchange = new DefaultServerWebExchange(request, response);
	}


	@SuppressWarnings("unchecked")
	@Test
	public void basic() throws Exception {
		Validator validator = mock(Validator.class);
		TestController controller = new TestController(validator);

		HandlerMethod handlerMethod = ResolvableMethod.on(controller)
				.annotated(RequestMapping.class)
				.resolveHandlerMethod();

		BindingContext bindingContext =
				this.contextFactory.createBindingContext(handlerMethod, this.exchange)
						.blockMillis(5000);

		WebExchangeDataBinder binder = bindingContext.createDataBinder(this.exchange, "name");
		assertEquals(Collections.singletonList(validator), binder.getValidators());

		Map<String, Object> model = bindingContext.getModel().asMap();
		assertEquals(5, model.size());

		Object value = model.get("bean");
		assertEquals("Bean", ((TestBean) value).getName());

		value = model.get("monoBean");
		assertEquals("Mono Bean", ((Mono<TestBean>) value).blockMillis(5000).getName());

		value = model.get("singleBean");
		assertEquals("Single Bean", ((Single<TestBean>) value).toBlocking().value().getName());

		value = model.get("voidMethodBean");
		assertEquals("Void Method Bean", ((TestBean) value).getName());

		value = model.get("voidMonoMethodBean");
		assertEquals("Void Mono Method Bean", ((TestBean) value).getName());
	}


	@SuppressWarnings("unused")
	private static class TestController {

		private Validator[] validators;

		public TestController(Validator... validators) {
			this.validators = validators;
		}

		@InitBinder
		public void initDataBinder(WebDataBinder dataBinder) {
			if (!ObjectUtils.isEmpty(this.validators)) {
				dataBinder.addValidators(this.validators);
			}
		}

		@ModelAttribute("bean")
		public TestBean returnValue() {
			return new TestBean("Bean");
		}

		@ModelAttribute("monoBean")
		public Mono<TestBean> returnValueMono() {
			return Mono.just(new TestBean("Mono Bean"));
		}

		@ModelAttribute("singleBean")
		public Single<TestBean> returnValueSingle() {
			return Single.just(new TestBean("Single Bean"));
		}

		@ModelAttribute
		public void voidMethodBean(Model model) {
			model.addAttribute("voidMethodBean", new TestBean("Void Method Bean"));
		}

		@ModelAttribute
		public Mono<Void> voidMonoMethodBean(Model model) {
			return Mono.just("Void Mono Method Bean")
					.doOnNext(name -> model.addAttribute("voidMonoMethodBean", new TestBean(name)))
					.then();
		}

		@RequestMapping
		public void handle() {}
	}


	private static class TestBean {

		private final String name;

		TestBean(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}

		@Override
		public String toString() {
			return "TestBean[name=" + this.name + "]";
		}
	}

}
