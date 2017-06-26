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

package org.springframework.web.reactive.result.view.script;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;
import org.springframework.mock.http.server.reactive.test.MockServerWebExchange;

import static org.junit.Assert.*;

/**
 * Unit tests for Kotlin script templates running on Kotlin JSR-223 support.
 *
 * @author Sebastien Deleuze
 */
@Ignore  // for JDK 9 compatibility
public class KotlinScriptTemplateTests {

	@Test
	public void renderTemplateWithFrenchLocale() throws Exception {
		Map<String, Object> model = new HashMap<>();
		model.put("foo", "Foo");
		MockServerHttpResponse response = renderViewWithModel("org/springframework/web/reactive/result/view/script/kotlin/template.kts",
				model, Locale.FRENCH, ScriptTemplatingConfiguration.class);
		assertEquals("<html><body>\n<p>Bonjour Foo</p>\n</body></html>",
				response.getBodyAsString().block());
	}

	@Test
	public void renderTemplateWithEnglishLocale() throws Exception {
		Map<String, Object> model = new HashMap<>();
		model.put("foo", "Foo");
		MockServerHttpResponse response = renderViewWithModel("org/springframework/web/reactive/result/view/script/kotlin/template.kts",
				model, Locale.ENGLISH, ScriptTemplatingConfiguration.class);
		assertEquals("<html><body>\n<p>Hello Foo</p>\n</body></html>",
				response.getBodyAsString().block());
	}

	@Test
	public void renderTemplateWithoutRenderFunction() throws Exception {
		Map<String, Object> model = new HashMap<>();
		model.put("header", "<html><body>");
		model.put("hello", "Hello");
		model.put("foo", "Foo");
		model.put("footer", "</body></html>");
		MockServerHttpResponse response = renderViewWithModel("org/springframework/web/reactive/result/view/script/kotlin/eval.kts",
				model, Locale.ENGLISH, ScriptTemplatingConfigurationWithoutRenderFunction.class);
		assertEquals("<html><body>\n<p>Hello Foo</p>\n</body></html>",
				response.getBodyAsString().block());
	}


	private MockServerHttpResponse renderViewWithModel(String viewUrl, Map<String, Object> model, Locale locale, Class<?> configuration) throws Exception {
		ScriptTemplateView view = createViewWithUrl(viewUrl, configuration);
		view.setLocale(locale);
		MockServerWebExchange exchange = MockServerHttpRequest.get("/").toExchange();
		view.renderInternal(model, MediaType.TEXT_HTML, exchange).block();
		return exchange.getResponse();
	}

	private ScriptTemplateView createViewWithUrl(String viewUrl, Class<?> configuration) throws Exception {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(configuration);
		ctx.refresh();

		ScriptTemplateView view = new ScriptTemplateView();
		view.setApplicationContext(ctx);
		view.setUrl(viewUrl);
		view.afterPropertiesSet();
		return view;
	}


	@Configuration
	static class ScriptTemplatingConfiguration {

		@Bean
		public ScriptTemplateConfigurer kotlinScriptConfigurer() {
			ScriptTemplateConfigurer configurer = new ScriptTemplateConfigurer();
			configurer.setEngineName("kotlin");
			configurer.setScripts("org/springframework/web/reactive/result/view/script/kotlin/render.kts");
			configurer.setRenderFunction("render");
			return configurer;
		}

		@Bean
		public ResourceBundleMessageSource messageSource() {
			ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
			messageSource.setBasename("org/springframework/web/reactive/result/view/script/messages");
			return messageSource;
		}
	}


	@Configuration
	static class ScriptTemplatingConfigurationWithoutRenderFunction {

		@Bean
		public ScriptTemplateConfigurer kotlinScriptConfigurer() {
			return new ScriptTemplateConfigurer("kotlin");
		}
	}

}
