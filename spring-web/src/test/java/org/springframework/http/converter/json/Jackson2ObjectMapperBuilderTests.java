/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.http.converter.json;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.cfg.SerializerFactoryConfig;
import com.fasterxml.jackson.databind.deser.BasicDeserializerFactory;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.std.DateDeserializers;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BasicSerializerFactory;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.databind.ser.std.ClassSerializer;
import com.fasterxml.jackson.databind.ser.std.NumberSerializer;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.Test;

import org.springframework.beans.FatalBeanException;

import static org.junit.Assert.*;

/**
 * Test class for {@link Jackson2ObjectMapperBuilder}.
 *
 * @author Sebastien Deleuze
 */
public class Jackson2ObjectMapperBuilderTests {

	private static final String DATE_FORMAT = "yyyy-MM-dd";


	@Test
	public void settersWithNullValues() {
		// Should not crash:
		Jackson2ObjectMapperBuilder.json().serializers((JsonSerializer<?>[]) null)
				.serializersByType(null).deserializersByType(null)
				.featuresToEnable((Object[]) null).featuresToDisable((Object[]) null);
	}

	@Test(expected = FatalBeanException.class)
	public void unknownFeature() {
		Jackson2ObjectMapperBuilder.json().featuresToEnable(Boolean.TRUE).build();
	}

	@Test
	public void defaultProperties() {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
		assertNotNull(objectMapper);
		assertFalse(objectMapper.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION));
		assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
		assertTrue(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_FIELDS));
		assertTrue(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_GETTERS));
		assertTrue(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_SETTERS));
		assertFalse(objectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
		assertTrue(objectMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS));
	}

	@Test
	public void propertiesShortcut() {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().autoDetectFields(false)
				.defaultViewInclusion(true).failOnUnknownProperties(true).failOnEmptyBeans(false)
				.autoDetectGettersSetters(false).indentOutput(true).build();
		assertNotNull(objectMapper);
		assertTrue(objectMapper.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION));
		assertTrue(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
		assertFalse(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_FIELDS));
		assertFalse(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_GETTERS));
		assertFalse(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_SETTERS));
		assertTrue(objectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
		assertFalse(objectMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS));
	}

	@Test
	public void booleanSetters() {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
				.featuresToEnable(MapperFeature.DEFAULT_VIEW_INCLUSION,
						DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
						SerializationFeature.INDENT_OUTPUT)
				.featuresToDisable(MapperFeature.AUTO_DETECT_FIELDS,
						MapperFeature.AUTO_DETECT_GETTERS,
						MapperFeature.AUTO_DETECT_SETTERS,
						SerializationFeature.FAIL_ON_EMPTY_BEANS).build();
		assertNotNull(objectMapper);
		assertTrue(objectMapper.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION));
		assertTrue(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
		assertFalse(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_FIELDS));
		assertFalse(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_GETTERS));
		assertFalse(objectMapper.isEnabled(MapperFeature.AUTO_DETECT_SETTERS));
		assertTrue(objectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
		assertFalse(objectMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS));
	}

	@Test
	public void setNotNullSerializationInclusion() {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
		assertTrue(objectMapper.getSerializationConfig().getSerializationInclusion() ==
				JsonInclude.Include.ALWAYS);
		objectMapper = Jackson2ObjectMapperBuilder.json().serializationInclusion(JsonInclude.Include.NON_NULL).build();
		assertTrue(objectMapper.getSerializationConfig().getSerializationInclusion() == JsonInclude.Include.NON_NULL);
	}

	@Test
	public void setNotDefaultSerializationInclusion() {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
		assertTrue(objectMapper.getSerializationConfig().getSerializationInclusion() == JsonInclude.Include.ALWAYS);
		objectMapper = Jackson2ObjectMapperBuilder.json().serializationInclusion(JsonInclude.Include.NON_DEFAULT).build();
		assertTrue(objectMapper.getSerializationConfig().getSerializationInclusion() == JsonInclude.Include.NON_DEFAULT);
	}

	@Test
	public void setNotEmptySerializationInclusion() {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
		assertTrue(objectMapper.getSerializationConfig().getSerializationInclusion() == JsonInclude.Include.ALWAYS);
		objectMapper = Jackson2ObjectMapperBuilder.json().serializationInclusion(JsonInclude.Include.NON_EMPTY).build();
		assertTrue(objectMapper.getSerializationConfig().getSerializationInclusion() == JsonInclude.Include.NON_EMPTY);
	}

	@Test
	public void dateTimeFormatSetter() {
		SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().dateFormat(dateFormat).build();
		assertEquals(dateFormat, objectMapper.getSerializationConfig().getDateFormat());
		assertEquals(dateFormat, objectMapper.getDeserializationConfig().getDateFormat());
	}

	@Test
	public void simpleDateFormatStringSetter() {
		SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().simpleDateFormat(DATE_FORMAT).build();
		assertEquals(dateFormat, objectMapper.getSerializationConfig().getDateFormat());
		assertEquals(dateFormat, objectMapper.getDeserializationConfig().getDateFormat());
	}

	@Test
	public void setModules() {
		NumberSerializer serializer1 = new NumberSerializer();
		SimpleModule module = new SimpleModule();
		module.addSerializer(Integer.class, serializer1);
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().modules(Arrays.asList(new Module[]{module})).build();
		Serializers serializers = getSerializerFactoryConfig(objectMapper).serializers().iterator().next();
		assertTrue(serializers.findSerializer(null, SimpleType.construct(Integer.class), null) == serializer1);
	}

	private static SerializerFactoryConfig getSerializerFactoryConfig(ObjectMapper objectMapper) {
		return ((BasicSerializerFactory) objectMapper.getSerializerFactory()).getFactoryConfig();
	}

	private static DeserializerFactoryConfig getDeserializerFactoryConfig(ObjectMapper objectMapper) {
		return ((BasicDeserializerFactory) objectMapper.getDeserializationContext().getFactory()).getFactoryConfig();
	}

	@Test
	public void propertyNamingStrategy() {
		PropertyNamingStrategy strategy = new PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy();
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().propertyNamingStrategy(strategy).build();
		assertSame(strategy, objectMapper.getSerializationConfig().getPropertyNamingStrategy());
		assertSame(strategy, objectMapper.getDeserializationConfig().getPropertyNamingStrategy());
	}

	@Test
	public void serializerByType() {
		JsonSerializer<Number> serializer = new NumberSerializer();
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
				.serializerByType(Boolean.class, serializer).build();
		assertTrue(getSerializerFactoryConfig(objectMapper).hasSerializers());
		Serializers serializers = getSerializerFactoryConfig(objectMapper).serializers().iterator().next();
		assertTrue(serializers.findSerializer(null, SimpleType.construct(Boolean.class), null) == serializer);
	}

	@Test
	public void deserializerByType() throws JsonMappingException {
		JsonDeserializer<Date> deserializer = new DateDeserializers.DateDeserializer();
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
				.deserializerByType(Date.class, deserializer).build();
		assertTrue(getDeserializerFactoryConfig(objectMapper).hasDeserializers());
		Deserializers deserializers = getDeserializerFactoryConfig(objectMapper).deserializers().iterator().next();
		assertTrue(deserializers.findBeanDeserializer(SimpleType.construct(Date.class), null, null) == deserializer);
	}

	@Test
	public void mixIn() {
		Class<?> target = String.class;
		Class<?> mixInSource = Object.class;

		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().mixIn(target,
				mixInSource).build();

		assertEquals(1, objectMapper.mixInCount());
		assertSame(mixInSource, objectMapper.findMixInClassFor(target));
	}

	@Test
	public void mixIns() {
		Class<?> target = String.class;
		Class<?> mixInSource = Object.class;
		Map<Class<?>, Class<?>> mixIns = new HashMap<Class<?>, Class<?>>();
		mixIns.put(target, mixInSource);

		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().mixIns(mixIns).build();

		assertEquals(1, objectMapper.mixInCount());
		assertSame(mixInSource, objectMapper.findMixInClassFor(target));
	}

	@Test
	public void completeSetup() throws JsonMappingException {
		NopAnnotationIntrospector annotationIntrospector = NopAnnotationIntrospector.instance;

		Map<Class<?>, JsonDeserializer<?>> deserializerMap = new HashMap<Class<?>, JsonDeserializer<?>>();
		JsonDeserializer<Date> deserializer = new DateDeserializers.DateDeserializer();
		deserializerMap.put(Date.class, deserializer);

		JsonSerializer<Class<?>> serializer1 = new ClassSerializer();
		JsonSerializer<Number> serializer2 = new NumberSerializer();

		Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json()
				.serializers(serializer1)
				.serializersByType(Collections.<Class<?>, JsonSerializer<?>>singletonMap(Boolean.class, serializer2))
				.deserializersByType(deserializerMap)
				.annotationIntrospector(annotationIntrospector)
				.featuresToEnable(SerializationFeature.FAIL_ON_EMPTY_BEANS,
						DeserializationFeature.UNWRAP_ROOT_VALUE,
						JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
						JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS)
				.featuresToDisable(MapperFeature.AUTO_DETECT_GETTERS,
						MapperFeature.AUTO_DETECT_FIELDS,
						JsonParser.Feature.AUTO_CLOSE_SOURCE,
						JsonGenerator.Feature.QUOTE_FIELD_NAMES)
						.serializationInclusion(JsonInclude.Include.NON_NULL);

		ObjectMapper objectMapper = new ObjectMapper();
		builder.configure(objectMapper);

		assertTrue(getSerializerFactoryConfig(objectMapper).hasSerializers());
		assertTrue(getDeserializerFactoryConfig(objectMapper).hasDeserializers());

		Serializers serializers = getSerializerFactoryConfig(objectMapper).serializers().iterator().next();
		assertTrue(serializers.findSerializer(null, SimpleType.construct(Class.class), null) == serializer1);
		assertTrue(serializers.findSerializer(null, SimpleType.construct(Boolean.class), null) == serializer2);
		assertNull(serializers.findSerializer(null, SimpleType.construct(Number.class), null));

		Deserializers deserializers = getDeserializerFactoryConfig(objectMapper).deserializers().iterator().next();
		assertTrue(deserializers.findBeanDeserializer(SimpleType.construct(Date.class), null, null) == deserializer);

		assertTrue(annotationIntrospector == objectMapper.getSerializationConfig().getAnnotationIntrospector());
		assertTrue(annotationIntrospector == objectMapper.getDeserializationConfig().getAnnotationIntrospector());

		assertTrue(objectMapper.getSerializationConfig().isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS));
		assertTrue(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
		assertTrue(objectMapper.getFactory().isEnabled(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER));
		assertTrue(objectMapper.getFactory().isEnabled(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS));

		assertFalse(objectMapper.getSerializationConfig().isEnabled(MapperFeature.AUTO_DETECT_GETTERS));
		assertFalse(objectMapper.getDeserializationConfig().isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION));
		assertFalse(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
		assertFalse(objectMapper.getDeserializationConfig().isEnabled(MapperFeature.AUTO_DETECT_FIELDS));
		assertFalse(objectMapper.getFactory().isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE));
		assertFalse(objectMapper.getFactory().isEnabled(JsonGenerator.Feature.QUOTE_FIELD_NAMES));
		assertTrue(objectMapper.getSerializationConfig().getSerializationInclusion() == JsonInclude.Include.NON_NULL);
	}

	@Test
	public void xmlMapper() {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.xml().build();
		assertNotNull(objectMapper);
		assertEquals(XmlMapper.class, objectMapper.getClass());
	}

	@Test
	public void createXmlMapper() {
		Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json().indentOutput(true);
		ObjectMapper jsonObjectMapper = builder.build();
		ObjectMapper xmlObjectMapper = builder.createXmlMapper(true).build();
		assertTrue(jsonObjectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
		assertTrue(xmlObjectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
		assertTrue(xmlObjectMapper.getClass().isAssignableFrom(XmlMapper.class));
	}

}
