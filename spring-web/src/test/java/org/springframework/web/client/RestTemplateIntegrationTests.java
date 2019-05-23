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

package org.springframework.web.client;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assume.assumeFalse;
import static org.springframework.http.HttpMethod.POST;

/**
 * @author Arjen Poutsma
 * @author Brian Clozel
 */
@RunWith(Parameterized.class)
public class RestTemplateIntegrationTests extends AbstractMockWebServerTestCase {

	private RestTemplate template;

	@Parameter
	public ClientHttpRequestFactory clientHttpRequestFactory;

	@SuppressWarnings("deprecation")
	@Parameters
	public static Iterable<? extends ClientHttpRequestFactory> data() {
		return Arrays.asList(
				new SimpleClientHttpRequestFactory(),
				new HttpComponentsClientHttpRequestFactory(),
				new org.springframework.http.client.Netty4ClientHttpRequestFactory(),
				new OkHttp3ClientHttpRequestFactory()
		);
	}


	@Before
	public void setupClient() {
		this.template = new RestTemplate(this.clientHttpRequestFactory);
	}


	@Test
	public void getString() {
		String s = template.getForObject(baseUrl + "/{method}", String.class, "get");
		assertThat(s).as("Invalid content").isEqualTo(helloWorld);
	}

	@Test
	public void getEntity() {
		ResponseEntity<String> entity = template.getForEntity(baseUrl + "/{method}", String.class, "get");
		assertThat(entity.getBody()).as("Invalid content").isEqualTo(helloWorld);
		assertThat(entity.getHeaders().isEmpty()).as("No headers").isFalse();
		assertThat(entity.getHeaders().getContentType()).as("Invalid content-type").isEqualTo(textContentType);
		assertThat(entity.getStatusCode()).as("Invalid status code").isEqualTo(HttpStatus.OK);
	}

	@Test
	public void getNoResponse() {
		String s = template.getForObject(baseUrl + "/get/nothing", String.class);
		assertThat(s).as("Invalid content").isNull();
	}

	@Test
	public void getNoContentTypeHeader() throws UnsupportedEncodingException {
		byte[] bytes = template.getForObject(baseUrl + "/get/nocontenttype", byte[].class);
		assertThat(bytes).as("Invalid content").isEqualTo(helloWorld.getBytes("UTF-8"));
	}

	@Test
	public void getNoContent() {
		String s = template.getForObject(baseUrl + "/status/nocontent", String.class);
		assertThat(s).as("Invalid content").isNull();

		ResponseEntity<String> entity = template.getForEntity(baseUrl + "/status/nocontent", String.class);
		assertThat(entity.getStatusCode()).as("Invalid response code").isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(entity.getBody()).as("Invalid content").isNull();
	}

	@Test
	public void getNotModified() {
		String s = template.getForObject(baseUrl + "/status/notmodified", String.class);
		assertThat(s).as("Invalid content").isNull();

		ResponseEntity<String> entity = template.getForEntity(baseUrl + "/status/notmodified", String.class);
		assertThat(entity.getStatusCode()).as("Invalid response code").isEqualTo(HttpStatus.NOT_MODIFIED);
		assertThat(entity.getBody()).as("Invalid content").isNull();
	}

	@Test
	public void postForLocation() throws URISyntaxException {
		URI location = template.postForLocation(baseUrl + "/{method}", helloWorld, "post");
		assertThat(location).as("Invalid location").isEqualTo(new URI(baseUrl + "/post/1"));
	}

	@Test
	public void postForLocationEntity() throws URISyntaxException {
		HttpHeaders entityHeaders = new HttpHeaders();
		entityHeaders.setContentType(new MediaType("text", "plain", StandardCharsets.ISO_8859_1));
		HttpEntity<String> entity = new HttpEntity<>(helloWorld, entityHeaders);
		URI location = template.postForLocation(baseUrl + "/{method}", entity, "post");
		assertThat(location).as("Invalid location").isEqualTo(new URI(baseUrl + "/post/1"));
	}

	@Test
	public void postForObject() throws URISyntaxException {
		String s = template.postForObject(baseUrl + "/{method}", helloWorld, String.class, "post");
		assertThat(s).as("Invalid content").isEqualTo(helloWorld);
	}

	@Test
	public void patchForObject() throws URISyntaxException {
		// JDK client does not support the PATCH method
		assumeFalse(this.clientHttpRequestFactory instanceof SimpleClientHttpRequestFactory);

		String s = template.patchForObject(baseUrl + "/{method}", helloWorld, String.class, "patch");
		assertThat(s).as("Invalid content").isEqualTo(helloWorld);
	}

	@Test
	public void notFound() {
		assertThatExceptionOfType(HttpClientErrorException.class).isThrownBy(() ->
				template.execute(baseUrl + "/status/notfound", HttpMethod.GET, null, null))
			.satisfies(ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
				assertThat(ex.getStatusText()).isNotNull();
				assertThat(ex.getResponseBodyAsString()).isNotNull();
			});
	}

	@Test
	public void badRequest() {
		assertThatExceptionOfType(HttpClientErrorException.class).isThrownBy(() ->
				template.execute(baseUrl + "/status/badrequest", HttpMethod.GET, null, null))
			.satisfies(ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
				assertThat(ex.getMessage()).isEqualTo("400 Client Error");
			});
	}

	@Test
	public void serverError() {
		assertThatExceptionOfType(HttpServerErrorException.class).isThrownBy(() ->
				template.execute(baseUrl + "/status/server", HttpMethod.GET, null, null))
			.satisfies(ex -> {
				assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
				assertThat(ex.getStatusText()).isNotNull();
				assertThat(ex.getResponseBodyAsString()).isNotNull();
			});
	}

	@Test
	public void optionsForAllow() throws URISyntaxException {
		Set<HttpMethod> allowed = template.optionsForAllow(new URI(baseUrl + "/get"));
		assertThat(allowed).as("Invalid response").isEqualTo(EnumSet.of(HttpMethod.GET, HttpMethod.OPTIONS, HttpMethod.HEAD, HttpMethod.TRACE));
	}

	@Test
	public void uri() throws InterruptedException, URISyntaxException {
		String result = template.getForObject(baseUrl + "/uri/{query}", String.class, "Z\u00fcrich");
		assertThat(result).as("Invalid request URI").isEqualTo("/uri/Z%C3%BCrich");

		result = template.getForObject(baseUrl + "/uri/query={query}", String.class, "foo@bar");
		assertThat(result).as("Invalid request URI").isEqualTo("/uri/query=foo@bar");

		result = template.getForObject(baseUrl + "/uri/query={query}", String.class, "T\u014dky\u014d");
		assertThat(result).as("Invalid request URI").isEqualTo("/uri/query=T%C5%8Dky%C5%8D");
	}

	@Test
	public void multipart() throws UnsupportedEncodingException {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("name 1", "value 1");
		parts.add("name 2", "value 2+1");
		parts.add("name 2", "value 2+2");
		Resource logo = new ClassPathResource("/org/springframework/http/converter/logo.jpg");
		parts.add("logo", logo);

		template.postForLocation(baseUrl + "/multipart", parts);
	}

	@Test
	public void form() throws UnsupportedEncodingException {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("name 1", "value 1");
		form.add("name 2", "value 2+1");
		form.add("name 2", "value 2+2");

		template.postForLocation(baseUrl + "/form", form);
	}

	@Test
	public void exchangeGet() throws Exception {
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.set("MyHeader", "MyValue");
		HttpEntity<String> requestEntity = new HttpEntity<>(requestHeaders);
		ResponseEntity<String> response =
				template.exchange(baseUrl + "/{method}", HttpMethod.GET, requestEntity, String.class, "get");
		assertThat(response.getBody()).as("Invalid content").isEqualTo(helloWorld);
	}

	@Test
	public void exchangePost() throws Exception {
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.set("MyHeader", "MyValue");
		requestHeaders.setContentType(MediaType.TEXT_PLAIN);
		HttpEntity<String> entity = new HttpEntity<>(helloWorld, requestHeaders);
		HttpEntity<Void> result = template.exchange(baseUrl + "/{method}", POST, entity, Void.class, "post");
		assertThat(result.getHeaders().getLocation()).as("Invalid location").isEqualTo(new URI(baseUrl + "/post/1"));
		assertThat(result.hasBody()).isFalse();
	}

	@Test
	public void jsonPostForObject() throws URISyntaxException {
		HttpHeaders entityHeaders = new HttpHeaders();
		entityHeaders.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		MySampleBean bean = new MySampleBean();
		bean.setWith1("with");
		bean.setWith2("with");
		bean.setWithout("without");
		HttpEntity<MySampleBean> entity = new HttpEntity<>(bean, entityHeaders);
		String s = template.postForObject(baseUrl + "/jsonpost", entity, String.class);
		assertThat(s.contains("\"with1\":\"with\"")).isTrue();
		assertThat(s.contains("\"with2\":\"with\"")).isTrue();
		assertThat(s.contains("\"without\":\"without\"")).isTrue();
	}

	@Test
	public void jsonPostForObjectWithJacksonView() throws URISyntaxException {
		HttpHeaders entityHeaders = new HttpHeaders();
		entityHeaders.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		MySampleBean bean = new MySampleBean("with", "with", "without");
		MappingJacksonValue jacksonValue = new MappingJacksonValue(bean);
		jacksonValue.setSerializationView(MyJacksonView1.class);
		HttpEntity<MappingJacksonValue> entity = new HttpEntity<>(jacksonValue, entityHeaders);
		String s = template.postForObject(baseUrl + "/jsonpost", entity, String.class);
		assertThat(s.contains("\"with1\":\"with\"")).isTrue();
		assertThat(s.contains("\"with2\":\"with\"")).isFalse();
		assertThat(s.contains("\"without\":\"without\"")).isFalse();
	}

	@Test  // SPR-12123
	public void serverPort() {
		String s = template.getForObject("http://localhost:{port}/get", String.class, port);
		assertThat(s).as("Invalid content").isEqualTo(helloWorld);
	}

	@Test  // SPR-13154
	public void jsonPostForObjectWithJacksonTypeInfoList() throws URISyntaxException {
		List<ParentClass> list = new ArrayList<>();
		list.add(new Foo("foo"));
		list.add(new Bar("bar"));
		ParameterizedTypeReference<?> typeReference = new ParameterizedTypeReference<List<ParentClass>>() {};
		RequestEntity<List<ParentClass>> entity = RequestEntity
				.post(new URI(baseUrl + "/jsonpost"))
				.contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
				.body(list, typeReference.getType());
		String content = template.exchange(entity, String.class).getBody();
		assertThat(content.contains("\"type\":\"foo\"")).isTrue();
		assertThat(content.contains("\"type\":\"bar\"")).isTrue();
	}

	@Test  // SPR-15015
	public void postWithoutBody() throws Exception {
		assertThat(template.postForObject(baseUrl + "/jsonpost", null, String.class)).isNull();
	}


	public interface MyJacksonView1 {}

	public interface MyJacksonView2 {}


	public static class MySampleBean {

		@JsonView(MyJacksonView1.class)
		private String with1;

		@JsonView(MyJacksonView2.class)
		private String with2;

		private String without;

		private MySampleBean() {
		}

		private MySampleBean(String with1, String with2, String without) {
			this.with1 = with1;
			this.with2 = with2;
			this.without = without;
		}

		public String getWith1() {
			return with1;
		}

		public void setWith1(String with1) {
			this.with1 = with1;
		}

		public String getWith2() {
			return with2;
		}

		public void setWith2(String with2) {
			this.with2 = with2;
		}

		public String getWithout() {
			return without;
		}

		public void setWithout(String without) {
			this.without = without;
		}
	}


	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	public static class ParentClass {

		private String parentProperty;

		public ParentClass() {
		}

		public ParentClass(String parentProperty) {
			this.parentProperty = parentProperty;
		}

		public String getParentProperty() {
			return parentProperty;
		}

		public void setParentProperty(String parentProperty) {
			this.parentProperty = parentProperty;
		}
	}


	@JsonTypeName("foo")
	public static class Foo extends ParentClass {

		public Foo() {
		}

		public Foo(String parentProperty) {
			super(parentProperty);
		}
	}


	@JsonTypeName("bar")
	public static class Bar extends ParentClass {

		public Bar() {
		}

		public Bar(String parentProperty) {
			super(parentProperty);
		}
	}

}
