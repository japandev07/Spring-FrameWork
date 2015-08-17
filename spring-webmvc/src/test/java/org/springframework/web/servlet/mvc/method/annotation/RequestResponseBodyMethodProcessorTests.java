/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import org.junit.Before;
import org.junit.Test;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

/**
 * Test fixture for a {@link RequestResponseBodyMethodProcessor} with
 * actual delegation to {@link HttpMessageConverter} instances.
 *
 * <p>Also see {@link RequestResponseBodyMethodProcessorMockTests}.
 *
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 */
@SuppressWarnings("unused")
public class RequestResponseBodyMethodProcessorTests {

	private MethodParameter paramGenericList;
	private MethodParameter paramSimpleBean;
	private MethodParameter paramMultiValueMap;
	private MethodParameter paramString;
	private MethodParameter returnTypeString;

	private ModelAndViewContainer mavContainer;

	private NativeWebRequest webRequest;

	private MockHttpServletRequest servletRequest;

	private MockHttpServletResponse servletResponse;

	private ValidatingBinderFactory binderFactory;


	@Before
	public void setUp() throws Exception {

		Method method = getClass().getDeclaredMethod("handle", List.class,
				SimpleBean.class, MultiValueMap.class, String.class);

		paramGenericList = new MethodParameter(method, 0);
		paramSimpleBean = new MethodParameter(method, 1);
		paramMultiValueMap = new MethodParameter(method, 2);
		paramString = new MethodParameter(method, 3);
		returnTypeString = new MethodParameter(method, -1);

		mavContainer = new ModelAndViewContainer();

		servletRequest = new MockHttpServletRequest();
		servletRequest.setMethod("POST");
		servletResponse = new MockHttpServletResponse();
		webRequest = new ServletWebRequest(servletRequest, servletResponse);

		this.binderFactory = new ValidatingBinderFactory();
	}

	@Test
	public void resolveArgumentParameterizedType() throws Exception {
		String content = "[{\"name\" : \"Jad\"}, {\"name\" : \"Robert\"}]";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		@SuppressWarnings("unchecked")
		List<SimpleBean> result = (List<SimpleBean>) processor.resolveArgument(
				paramGenericList, mavContainer, webRequest, binderFactory);

		assertNotNull(result);
		assertEquals("Jad", result.get(0).getName());
		assertEquals("Robert", result.get(1).getName());
	}

	@Test
	public void resolveArgumentRawTypeFromParameterizedType() throws Exception {
		String content = "fruit=apple&vegetable=kale";
		this.servletRequest.setMethod("GET");
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new AllEncompassingFormHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		@SuppressWarnings("unchecked")
		MultiValueMap<String, String> result = (MultiValueMap<String, String>) processor.resolveArgument(
				paramMultiValueMap, mavContainer, webRequest, binderFactory);

		assertNotNull(result);
		assertEquals("apple", result.getFirst("fruit"));
		assertEquals("kale", result.getFirst("vegetable"));
	}

	@Test
	public void resolveArgumentClassJson() throws Exception {
		String content = "{\"name\" : \"Jad\"}";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType("application/json");

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		SimpleBean result = (SimpleBean) processor.resolveArgument(
				paramSimpleBean, mavContainer, webRequest, binderFactory);

		assertNotNull(result);
		assertEquals("Jad", result.getName());
	}

	@Test
	public void resolveArgumentClassString() throws Exception {
		String content = "foobarbaz";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType("application/json");

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new StringHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		String result = (String) processor.resolveArgument(
				paramString, mavContainer, webRequest, binderFactory);

		assertNotNull(result);
		assertEquals("foobarbaz", result);
	}

	@Test(expected = HttpMessageNotReadableException.class)  // SPR-9942
	public void resolveArgumentRequiredNoContent() throws Exception {
		this.servletRequest.setContent(new byte[0]);
		this.servletRequest.setContentType("text/plain");
		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new StringHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);
		processor.resolveArgument(paramString, mavContainer, webRequest, binderFactory);
	}

	@Test  // SPR-12778
	public void resolveArgumentRequiredNoContentDefaultValue() throws Exception {
		this.servletRequest.setContent(new byte[0]);
		this.servletRequest.setContentType("text/plain");
		List<HttpMessageConverter<?>> converters = Collections.singletonList(new StringHttpMessageConverter());
		List<Object> advice = Collections.singletonList(new EmptyRequestBodyAdvice());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters, advice);
		String arg = (String) processor.resolveArgument(paramString, mavContainer, webRequest, binderFactory);
		assertNotNull(arg);
		assertEquals("default value for empty body", arg);
	}

	@Test  // SPR-9964
	public void resolveArgumentTypeVariable() throws Exception {
		Method method = MyParameterizedController.class.getMethod("handleDto", Identifiable.class);
		HandlerMethod handlerMethod = new HandlerMethod(new MySimpleParameterizedController(), method);
		MethodParameter methodParam = handlerMethod.getMethodParameters()[0];

		String content = "{\"name\" : \"Jad\"}";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		SimpleBean result = (SimpleBean) processor.resolveArgument(methodParam, mavContainer, webRequest, binderFactory);

		assertNotNull(result);
		assertEquals("Jad", result.getName());
	}

	@Test  // SPR-11225
	public void resolveArgumentTypeVariableWithNonGenericConverter() throws Exception {
		Method method = MyParameterizedController.class.getMethod("handleDto", Identifiable.class);
		HandlerMethod handlerMethod = new HandlerMethod(new MySimpleParameterizedController(), method);
		MethodParameter methodParam = handlerMethod.getMethodParameters()[0];

		String content = "{\"name\" : \"Jad\"}";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		HttpMessageConverter target = new MappingJackson2HttpMessageConverter();
		HttpMessageConverter proxy = ProxyFactory.getProxy(HttpMessageConverter.class, new SingletonTargetSource(target));
		converters.add(proxy);
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		SimpleBean result = (SimpleBean) processor.resolveArgument(methodParam, mavContainer, webRequest, binderFactory);

		assertNotNull(result);
		assertEquals("Jad", result.getName());
	}

	@Test  // SPR-9160
	public void handleReturnValueSortByQuality() throws Exception {
		this.servletRequest.addHeader("Accept", "text/plain; q=0.5, application/json");

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());
		converters.add(new StringHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		processor.writeWithMessageConverters("Foo", returnTypeString, webRequest);

		assertEquals("application/json;charset=UTF-8", servletResponse.getHeader("Content-Type"));
	}

	@Test
	public void handleReturnValueString() throws Exception {
		List<HttpMessageConverter<?>>converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new ByteArrayHttpMessageConverter());
		converters.add(new StringHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);
		processor.handleReturnValue("Foo", returnTypeString, mavContainer, webRequest);

		assertEquals("text/plain;charset=ISO-8859-1", servletResponse.getHeader("Content-Type"));
		assertEquals("Foo", servletResponse.getContentAsString());
	}

	@Test
	public void handleReturnValueStringAcceptCharset() throws Exception {
		this.servletRequest.addHeader("Accept", "text/plain;charset=UTF-8");

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new ByteArrayHttpMessageConverter());
		converters.add(new StringHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		processor.writeWithMessageConverters("Foo", returnTypeString, webRequest);

		assertEquals("text/plain;charset=UTF-8", servletResponse.getHeader("Content-Type"));
	}

	// SPR-12894

	@Test
	public void handleReturnValueImage() throws Exception {
		this.servletRequest.addHeader("Accept", "*/*");

		Method method = getClass().getDeclaredMethod("getImage");
		MethodParameter returnType = new MethodParameter(method, -1);

		List<HttpMessageConverter<?>> converters = Collections.singletonList(new ResourceHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		ClassPathResource resource = new ClassPathResource("logo.jpg", getClass());
		processor.writeWithMessageConverters(resource, returnType, this.webRequest);

		assertEquals("image/jpeg", this.servletResponse.getHeader("Content-Type"));
	}

	// SPR-13135

	@Test(expected = IllegalArgumentException.class)
	public void handleReturnValueWithInvalidReturnType() throws Exception {
		Method method = getClass().getDeclaredMethod("handleAndReturnOutputStream");
		MethodParameter returnType = new MethodParameter(method, -1);
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(new ArrayList<>());
		processor.writeWithMessageConverters(new ByteArrayOutputStream(), returnType, this.webRequest);
	}

	@Test
	public void supportsReturnTypeResponseBodyOnType() throws Exception {
		Method method = ResponseBodyController.class.getMethod("handle");
		MethodParameter returnType = new MethodParameter(method, -1);

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new StringHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		assertTrue("Failed to recognize type-level @ResponseBody", processor.supportsReturnType(returnType));
	}

	@Test
	public void supportsReturnTypeRestController() throws Exception {
		Method method = TestRestController.class.getMethod("handle");
		MethodParameter returnType = new MethodParameter(method, -1);

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new StringHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		assertTrue("Failed to recognize type-level @RestController", processor.supportsReturnType(returnType));
	}

	@Test
	public void jacksonJsonViewWithResponseBodyAndJsonMessageConverter() throws Exception {
		Method method = JacksonController.class.getMethod("handleResponseBody");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewResponseBodyAdvice()));

		Object returnValue = new JacksonController().handleResponseBody();
		processor.handleReturnValue(returnValue, methodReturnType, this.mavContainer, this.webRequest);

		String content = this.servletResponse.getContentAsString();
		assertFalse(content.contains("\"withView1\":\"with\""));
		assertTrue(content.contains("\"withView2\":\"with\""));
		assertFalse(content.contains("\"withoutView\":\"without\""));
	}

	@Test
	public void jacksonJsonViewWithResponseEntityAndJsonMessageConverter() throws Exception {
		Method method = JacksonController.class.getMethod("handleResponseEntity");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());

		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewResponseBodyAdvice()));

		Object returnValue = new JacksonController().handleResponseEntity();
		processor.handleReturnValue(returnValue, methodReturnType, this.mavContainer, this.webRequest);

		String content = this.servletResponse.getContentAsString();
		assertFalse(content.contains("\"withView1\":\"with\""));
		assertTrue(content.contains("\"withView2\":\"with\""));
		assertFalse(content.contains("\"withoutView\":\"without\""));
	}

	@Test  // SPR-12149
	public void jacksonJsonViewWithResponseBodyAndXmlMessageConverter() throws Exception {
		Method method = JacksonController.class.getMethod("handleResponseBody");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2XmlHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewResponseBodyAdvice()));

		Object returnValue = new JacksonController().handleResponseBody();
		processor.handleReturnValue(returnValue, methodReturnType, this.mavContainer, this.webRequest);

		String content = this.servletResponse.getContentAsString();
		assertFalse(content.contains("<withView1>with</withView1>"));
		assertTrue(content.contains("<withView2>with</withView2>"));
		assertFalse(content.contains("<withoutView>without</withoutView>"));
	}

	@Test  // SPR-12149
	public void jacksonJsonViewWithResponseEntityAndXmlMessageConverter() throws Exception {
		Method method = JacksonController.class.getMethod("handleResponseEntity");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2XmlHttpMessageConverter());

		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewResponseBodyAdvice()));

		Object returnValue = new JacksonController().handleResponseEntity();
		processor.handleReturnValue(returnValue, methodReturnType, this.mavContainer, this.webRequest);

		String content = this.servletResponse.getContentAsString();
		assertFalse(content.contains("<withView1>with</withView1>"));
		assertTrue(content.contains("<withView2>with</withView2>"));
		assertFalse(content.contains("<withoutView>without</withoutView>"));
	}

	@Test  // SPR-12501
	public void resolveArgumentWithJacksonJsonView() throws Exception {
		String content = "{\"withView1\" : \"with\", \"withView2\" : \"with\", \"withoutView\" : \"without\"}";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		Method method = JacksonController.class.getMethod("handleRequestBody", JacksonViewBean.class);
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewRequestBodyAdvice()));

		@SuppressWarnings("unchecked")
		JacksonViewBean result = (JacksonViewBean)processor.resolveArgument(methodParameter,
				this.mavContainer, this.webRequest, this.binderFactory);

		assertNotNull(result);
		assertEquals("with", result.getWithView1());
		assertNull(result.getWithView2());
		assertNull(result.getWithoutView());
	}

	@Test  // SPR-12501
	public void resolveHttpEntityArgumentWithJacksonJsonView() throws Exception {
		String content = "{\"withView1\" : \"with\", \"withView2\" : \"with\", \"withoutView\" : \"without\"}";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		Method method = JacksonController.class.getMethod("handleHttpEntity", HttpEntity.class);
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());

		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewRequestBodyAdvice()));

		@SuppressWarnings("unchecked")
		HttpEntity<JacksonViewBean> result = (HttpEntity<JacksonViewBean>)processor.resolveArgument(methodParameter,
				this.mavContainer, this.webRequest, this.binderFactory);

		assertNotNull(result);
		assertNotNull(result.getBody());
		assertEquals("with", result.getBody().getWithView1());
		assertNull(result.getBody().getWithView2());
		assertNull(result.getBody().getWithoutView());
	}

	@Test  // SPR-12501
	public void resolveArgumentWithJacksonJsonViewAndXmlMessageConverter() throws Exception {
		String content = "<root><withView1>with</withView1><withView2>with</withView2><withoutView>without</withoutView></root>";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType(MediaType.APPLICATION_XML_VALUE);

		Method method = JacksonController.class.getMethod("handleRequestBody", JacksonViewBean.class);
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2XmlHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewRequestBodyAdvice()));

		@SuppressWarnings("unchecked")
		JacksonViewBean result = (JacksonViewBean)processor.resolveArgument(methodParameter,
				this.mavContainer, this.webRequest, this.binderFactory);

		assertNotNull(result);
		assertEquals("with", result.getWithView1());
		assertNull(result.getWithView2());
		assertNull(result.getWithoutView());
	}

	@Test  // SPR-12501
	public void resolveHttpEntityArgumentWithJacksonJsonViewAndXmlMessageConverter() throws Exception {
		String content = "<root><withView1>with</withView1><withView2>with</withView2><withoutView>without</withoutView></root>";
		this.servletRequest.setContent(content.getBytes("UTF-8"));
		this.servletRequest.setContentType(MediaType.APPLICATION_XML_VALUE);

		Method method = JacksonController.class.getMethod("handleHttpEntity", HttpEntity.class);
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2XmlHttpMessageConverter());

		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewRequestBodyAdvice()));

		@SuppressWarnings("unchecked")
		HttpEntity<JacksonViewBean> result = (HttpEntity<JacksonViewBean>)processor.resolveArgument(methodParameter,
				this.mavContainer, this.webRequest, this.binderFactory);

		assertNotNull(result);
		assertNotNull(result.getBody());
		assertEquals("with", result.getBody().getWithView1());
		assertNull(result.getBody().getWithView2());
		assertNull(result.getBody().getWithoutView());
	}

	@Test  // SPR-12811
	public void jacksonTypeInfoList() throws Exception {
		Method method = JacksonController.class.getMethod("handleTypeInfoList");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		Object returnValue = new JacksonController().handleTypeInfoList();
		processor.handleReturnValue(returnValue, methodReturnType, this.mavContainer, this.webRequest);

		String content = this.servletResponse.getContentAsString();
		assertTrue(content.contains("\"type\":\"foo\""));
		assertTrue(content.contains("\"type\":\"bar\""));
	}

	@Test  // SPR-13318
	public void jacksonSubType() throws Exception {
		Method method = JacksonController.class.getMethod("handleSubType");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		Object returnValue = new JacksonController().handleSubType();
		processor.handleReturnValue(returnValue, methodReturnType, this.mavContainer, this.webRequest);

		String content = this.servletResponse.getContentAsString();
		assertTrue(content.contains("\"id\":123"));
		assertTrue(content.contains("\"name\":\"foo\""));
	}

	@Test  // SPR-13318
	public void jacksonSubTypeList() throws Exception {
		Method method = JacksonController.class.getMethod("handleSubTypeList");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<HttpMessageConverter<?>>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		Object returnValue = new JacksonController().handleSubTypeList();
		processor.handleReturnValue(returnValue, methodReturnType, this.mavContainer, this.webRequest);

		String content = this.servletResponse.getContentAsString();
		assertTrue(content.contains("\"id\":123"));
		assertTrue(content.contains("\"name\":\"foo\""));
		assertTrue(content.contains("\"id\":456"));
		assertTrue(content.contains("\"name\":\"bar\""));
	}


	String handle(
			@RequestBody List<SimpleBean> list,
			@RequestBody SimpleBean simpleBean,
			@RequestBody MultiValueMap<String, String> multiValueMap,
			@RequestBody String string) {

		return null;
	}

	Resource getImage() {
		return null;
	}

	@RequestMapping
	OutputStream handleAndReturnOutputStream() {
		return null;
	}

	private static abstract class MyParameterizedController<DTO extends Identifiable> {

		@SuppressWarnings("unused")
		public void handleDto(@RequestBody DTO dto) {}
	}

	private static class MySimpleParameterizedController extends MyParameterizedController<SimpleBean> {
	}

	private interface Identifiable extends Serializable {

		Long getId();

		void setId(Long id);
	}


	@SuppressWarnings({ "serial" })
	private static class SimpleBean implements Identifiable {

		private Long id;

		private String name;

		@Override
		public Long getId() {
			return id;
		}

		@Override
		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}


	private final class ValidatingBinderFactory implements WebDataBinderFactory {

		@Override
		public WebDataBinder createBinder(NativeWebRequest webRequest, Object target, String objectName) throws Exception {
			LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
			validator.afterPropertiesSet();
			WebDataBinder dataBinder = new WebDataBinder(target, objectName);
			dataBinder.setValidator(validator);
			return dataBinder;
		}
	}


	@ResponseBody
	private static class ResponseBodyController {

		@RequestMapping
		public String handle() {
			return "hello";
		}
	}


	@RestController
	private static class TestRestController {

		@RequestMapping
		public String handle() {
			return "hello";
		}
	}

	private interface MyJacksonView1 {}
	private interface MyJacksonView2 {}

	private static class JacksonViewBean {

		@JsonView(MyJacksonView1.class)
		private String withView1;

		@JsonView(MyJacksonView2.class)
		private String withView2;

		private String withoutView;

		public String getWithView1() {
			return withView1;
		}

		public void setWithView1(String withView1) {
			this.withView1 = withView1;
		}

		public String getWithView2() {
			return withView2;
		}

		public void setWithView2(String withView2) {
			this.withView2 = withView2;
		}

		public String getWithoutView() {
			return withoutView;
		}

		public void setWithoutView(String withoutView) {
			this.withoutView = withoutView;
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

	private static class JacksonController {

		@RequestMapping
		@ResponseBody
		@JsonView(MyJacksonView2.class)
		public JacksonViewBean handleResponseBody() {
			JacksonViewBean bean = new JacksonViewBean();
			bean.setWithView1("with");
			bean.setWithView2("with");
			bean.setWithoutView("without");
			return bean;
		}

		@RequestMapping
		@JsonView(MyJacksonView2.class)
		public ResponseEntity<JacksonViewBean> handleResponseEntity() {
			JacksonViewBean bean = new JacksonViewBean();
			bean.setWithView1("with");
			bean.setWithView2("with");
			bean.setWithoutView("without");
			ModelAndView mav = new ModelAndView(new MappingJackson2JsonView());
			mav.addObject("bean", bean);
			return new ResponseEntity<JacksonViewBean>(bean, HttpStatus.OK);
		}

		@RequestMapping
		@ResponseBody
		public JacksonViewBean handleRequestBody(@JsonView(MyJacksonView1.class) @RequestBody JacksonViewBean bean) {
			return bean;
		}

		@RequestMapping
		@ResponseBody
		public JacksonViewBean handleHttpEntity(@JsonView(MyJacksonView1.class) HttpEntity<JacksonViewBean> entity) {
			return entity.getBody();
		}

		@RequestMapping
		@ResponseBody
		public List<ParentClass> handleTypeInfoList() {
			List<ParentClass> list = new ArrayList<>();
			list.add(new Foo("foo"));
			list.add(new Bar("bar"));
			return list;
		}

		@RequestMapping
		@ResponseBody
		public Identifiable handleSubType() {
			SimpleBean foo = new SimpleBean();
			foo.setId(123L);
			foo.setName("foo");
			return foo;
		}

		@RequestMapping
		@ResponseBody
		public List<Identifiable> handleSubTypeList() {
			SimpleBean foo = new SimpleBean();
			foo.setId(123L);
			foo.setName("foo");
			SimpleBean bar = new SimpleBean();
			bar.setId(456L);
			bar.setName("bar");
			return Arrays.asList(foo, bar);
		}
	}

	private static class EmptyRequestBodyAdvice implements RequestBodyAdvice {

		@Override
		public boolean supports(MethodParameter methodParameter, Type targetType,
				Class<? extends HttpMessageConverter<?>> converterType) {

			return StringHttpMessageConverter.class.equals(converterType);
		}

		@Override
		public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
				Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

			return "default value for empty body";
		}

		@Override
		public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
				Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

			return inputMessage;
		}

		@Override
		public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
				Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

			return body;
		}
	}

}
