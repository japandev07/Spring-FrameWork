/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.oxm.jaxb;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.ValidationException;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.Resource;
import org.springframework.oxm.GenericMarshaller;
import org.springframework.oxm.GenericUnmarshaller;
import org.springframework.oxm.MarshallingFailureException;
import org.springframework.oxm.UncategorizedMappingException;
import org.springframework.oxm.UnmarshallingFailureException;
import org.springframework.oxm.ValidationFailureException;
import org.springframework.oxm.XmlMappingException;
import org.springframework.oxm.mime.MimeContainer;
import org.springframework.oxm.mime.MimeMarshaller;
import org.springframework.oxm.mime.MimeUnmarshaller;
import org.springframework.oxm.support.SaxResourceUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.StaxUtils;

/**
 * Implementation of the <code>Marshaller</code> interface for JAXB 2.0.
 *
 * <p>The typical usage will be to set either the <code>contextPath</code> or the <code>classesToBeBound</code> property
 * on this bean, possibly customize the marshaller and unmarshaller by setting properties, schemas, adapters, and
 * listeners, and to refer to it.
 *
 * @author Arjen Poutsma
 * @see #setContextPath(String)
 * @see #setClassesToBeBound(Class[])
 * @see #setJaxbContextProperties(Map)
 * @see #setMarshallerProperties(Map)
 * @see #setUnmarshallerProperties(Map)
 * @see #setSchema(Resource)
 * @see #setSchemas(Resource[])
 * @see #setMarshallerListener(javax.xml.bind.Marshaller.Listener)
 * @see #setUnmarshallerListener(javax.xml.bind.Unmarshaller.Listener)
 * @see #setAdapters(XmlAdapter[])
 * @since 3.0
 */
public class Jaxb2Marshaller
		implements MimeMarshaller, MimeUnmarshaller, GenericMarshaller, GenericUnmarshaller, BeanClassLoaderAware,
		InitializingBean {

	private static final String CID = "cid:";


	/**
	 * Logger available to subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	private String contextPath;

	private Class<?>[] classesToBeBound;

	private Map<String, ?> jaxbContextProperties;

	private Map<String, ?> marshallerProperties;

	private Map<String, ?> unmarshallerProperties;

	private Marshaller.Listener marshallerListener;

	private Unmarshaller.Listener unmarshallerListener;

	private ValidationEventHandler validationEventHandler;

	private XmlAdapter<?, ?>[] adapters;

	private Resource[] schemaResources;

	private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;

	private boolean mtomEnabled = false;

	private ClassLoader beanClassLoader;

	private JAXBContext jaxbContext;

	private Schema schema;

	private boolean lazyInit = false;


	/**
	 * Set multiple JAXB context paths. The given array of context paths is converted to a
	 * colon-delimited string, as supported by JAXB.
	 */
	public void setContextPaths(String... contextPaths) {
		Assert.notEmpty(contextPaths, "'contextPaths' must not be empty");
		this.contextPath = StringUtils.arrayToDelimitedString(contextPaths, ":");
	}

	/**
	 * Set a JAXB context path.
	 */
	public void setContextPath(String contextPath) {
		Assert.hasText(contextPath, "'contextPath' must not be null");
		this.contextPath = contextPath;
	}

	/**
	 * Return the JAXB context path.
	 */
	public String getContextPath() {
		return this.contextPath;
	}

	/**
	 * Set the list of Java classes to be recognized by a newly created JAXBContext.
	 * Setting this property or {@link #setContextPath "contextPath"} is required.
	 */
	public void setClassesToBeBound(Class<?>... classesToBeBound) {
		Assert.notEmpty(classesToBeBound, "'classesToBeBound' must not be empty");
		this.classesToBeBound = classesToBeBound;
	}

	/**
	 * Return the list of Java classes to be recognized by a newly created JAXBContext.
	 */
	public Class<?>[] getClassesToBeBound() {
		return this.classesToBeBound;
	}

	/**
	 * Set the <code>JAXBContext</code> properties. These implementation-specific
	 * properties will be set on the underlying <code>JAXBContext</code>.
	 */
	public void setJaxbContextProperties(Map<String, ?> jaxbContextProperties) {
		this.jaxbContextProperties = jaxbContextProperties;
	}

	/**
	 * Set the JAXB <code>Marshaller</code> properties. These properties will be set on the
	 * underlying JAXB <code>Marshaller</code>, and allow for features such as indentation.
	 * @param properties the properties
	 * @see javax.xml.bind.Marshaller#setProperty(String,Object)
	 * @see javax.xml.bind.Marshaller#JAXB_ENCODING
	 * @see javax.xml.bind.Marshaller#JAXB_FORMATTED_OUTPUT
	 * @see javax.xml.bind.Marshaller#JAXB_NO_NAMESPACE_SCHEMA_LOCATION
	 * @see javax.xml.bind.Marshaller#JAXB_SCHEMA_LOCATION
	 */
	public void setMarshallerProperties(Map<String, ?> properties) {
		this.marshallerProperties = properties;
	}

	/**
	 * Set the JAXB <code>Unmarshaller</code> properties. These properties will be set on the
	 * underlying JAXB <code>Unmarshaller</code>.
	 * @param properties the properties
	 * @see javax.xml.bind.Unmarshaller#setProperty(String,Object)
	 */
	public void setUnmarshallerProperties(Map<String, ?> properties) {
		this.unmarshallerProperties = properties;
	}

	/**
	 * Specify the <code>Marshaller.Listener</code> to be registered with the JAXB <code>Marshaller</code>.
	 */
	public void setMarshallerListener(Marshaller.Listener marshallerListener) {
		this.marshallerListener = marshallerListener;
	}

	/**
	 * Set the <code>Unmarshaller.Listener</code> to be registered with the JAXB <code>Unmarshaller</code>.
	 */
	public void setUnmarshallerListener(Unmarshaller.Listener unmarshallerListener) {
		this.unmarshallerListener = unmarshallerListener;
	}

	/**
	 * Set the JAXB validation event handler. This event handler will be called by JAXB
	 * if any validation errors are encountered during calls to any of the marshal APIs.
	 */
	public void setValidationEventHandler(ValidationEventHandler validationEventHandler) {
		this.validationEventHandler = validationEventHandler;
	}

	/**
	 * Specify the <code>XmlAdapter</code>s to be registered with the JAXB <code>Marshaller</code>
	 * and <code>Unmarshaller</code>
	 */
	public void setAdapters(XmlAdapter<?, ?>[] adapters) {
		this.adapters = adapters;
	}

	/**
	 * Set the schema resource to use for validation.
	 */
	public void setSchema(Resource schemaResource) {
		this.schemaResources = new Resource[] {schemaResource};
	}

	/**
	 * Set the schema resources to use for validation.
	 */
	public void setSchemas(Resource[] schemaResources) {
		this.schemaResources = schemaResources;
	}

	/**
	 * Set the schema language. Default is the W3C XML Schema: <code>http://www.w3.org/2001/XMLSchema"</code>.
	 * @see XMLConstants#W3C_XML_SCHEMA_NS_URI
	 * @see XMLConstants#RELAXNG_NS_URI
	 */
	public void setSchemaLanguage(String schemaLanguage) {
		this.schemaLanguage = schemaLanguage;
	}

	/**
	 * Specify whether MTOM support should be enabled or not.
	 * Default is <code>false</code>: marshalling using XOP/MTOM not being enabled.
	 */
	public void setMtomEnabled(boolean mtomEnabled) {
		this.mtomEnabled = mtomEnabled;
	}

	/**
	 * Set whether to lazily initialize the {@link JAXBContext} for this marshaller.
	 * Default is {@code false} to initialize on startup; can be switched to {@code true}.
	 * <p>Early initialization just applies if {@link #afterPropertiesSet()} is called.
	 */
	public void setLazyInit(boolean lazyInit) {
		this.lazyInit = lazyInit;
	}

	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}


	public final void afterPropertiesSet() throws Exception {
		if (StringUtils.hasLength(getContextPath()) && !ObjectUtils.isEmpty(getClassesToBeBound())) {
			throw new IllegalArgumentException("Specify either 'contextPath' or 'classesToBeBound property'; not both");
		}
		else if (!StringUtils.hasLength(getContextPath()) && ObjectUtils.isEmpty(getClassesToBeBound())) {
			throw new IllegalArgumentException("Setting either 'contextPath' or 'classesToBeBound' is required");
		}
		if (!this.lazyInit) {
			getJaxbContext();
		}
		if (!ObjectUtils.isEmpty(this.schemaResources)) {
			this.schema = loadSchema(this.schemaResources, this.schemaLanguage);
		}
	}

	protected synchronized JAXBContext getJaxbContext() {
		if (this.jaxbContext == null) {
			try {
				if (StringUtils.hasLength(getContextPath())) {
					this.jaxbContext = createJaxbContextFromContextPath();
				}
				else if (!ObjectUtils.isEmpty(getClassesToBeBound())) {
					this.jaxbContext = createJaxbContextFromClasses();
				}
			}
			catch (JAXBException ex) {
				throw convertJaxbException(ex);
			}
		}
		return jaxbContext;
	}

	private JAXBContext createJaxbContextFromContextPath() throws JAXBException {
		if (logger.isInfoEnabled()) {
			logger.info("Creating JAXBContext with context path [" + getContextPath() + "]");
		}
		if (this.jaxbContextProperties != null) {
			if (this.beanClassLoader != null) {
				return JAXBContext.newInstance(getContextPath(), this.beanClassLoader, this.jaxbContextProperties);
			}
			else {
				return JAXBContext.newInstance(getContextPath(), ClassUtils.getDefaultClassLoader(), this.jaxbContextProperties);
			}
		}
		else {
			if (this.beanClassLoader != null) {
				return JAXBContext.newInstance(getContextPath(), this.beanClassLoader);
			}
			else {
				return JAXBContext.newInstance(getContextPath());
			}
		}
	}

	private JAXBContext createJaxbContextFromClasses() throws JAXBException {
		if (logger.isInfoEnabled()) {
			logger.info("Creating JAXBContext with classes to be bound [" +
					StringUtils.arrayToCommaDelimitedString(getClassesToBeBound()) + "]");
		}
		if (this.jaxbContextProperties != null) {
			return JAXBContext.newInstance(getClassesToBeBound(), this.jaxbContextProperties);
		}
		else {
			return JAXBContext.newInstance(getClassesToBeBound());
		}
	}

	private Schema loadSchema(Resource[] resources, String schemaLanguage) throws IOException, SAXException {
		if (logger.isDebugEnabled()) {
			logger.debug("Setting validation schema to " + StringUtils.arrayToCommaDelimitedString(this.schemaResources));
		}
		Assert.notEmpty(resources, "No resources given");
		Assert.hasLength(schemaLanguage, "No schema language provided");
		Source[] schemaSources = new Source[resources.length];
		XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		xmlReader.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
		for (int i = 0; i < resources.length; i++) {
			Assert.notNull(resources[i], "Resource is null");
			Assert.isTrue(resources[i].exists(), "Resource " + resources[i] + " does not exist");
			InputSource inputSource = SaxResourceUtils.createInputSource(resources[i]);
			schemaSources[i] = new SAXSource(xmlReader, inputSource);
		}
		SchemaFactory schemaFactory = SchemaFactory.newInstance(schemaLanguage);
		return schemaFactory.newSchema(schemaSources);
	}


	public boolean supports(Class<?> clazz) {
		return supportsInternal(clazz, true);
	}

	public boolean supports(Type genericType) {
		if (genericType instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) genericType;
			if (JAXBElement.class.equals(parameterizedType.getRawType()) &&
					parameterizedType.getActualTypeArguments().length == 1) {
				Type typeArgument = parameterizedType.getActualTypeArguments()[0];
				if (typeArgument instanceof Class) {
					Class<?> classArgument = (Class<?>) typeArgument;
					return (isPrimitiveWrapper(classArgument) || isStandardClass(classArgument) ||
							supportsInternal(classArgument, false));
				}
				else if (typeArgument instanceof GenericArrayType) {
					GenericArrayType arrayType = (GenericArrayType) typeArgument;
					return arrayType.getGenericComponentType().equals(Byte.TYPE);
				}
			}
		}
		else if (genericType instanceof Class) {
			Class<?> clazz = (Class<?>) genericType;
			return supportsInternal(clazz, true);
		}
		return false;
	}

	private boolean supportsInternal(Class<?> clazz, boolean checkForXmlRootElement) {
		if (checkForXmlRootElement && AnnotationUtils.findAnnotation(clazz, XmlRootElement.class) == null) {
			return false;
		}
		if (StringUtils.hasLength(getContextPath())) {
			String packageName = ClassUtils.getPackageName(clazz);
			String[] contextPaths = StringUtils.tokenizeToStringArray(getContextPath(), ":");
			for (String contextPath : contextPaths) {
				if (contextPath.equals(packageName)) {
					return true;
				}
			}
			return false;
		}
		else if (!ObjectUtils.isEmpty(getClassesToBeBound())) {
			return Arrays.asList(getClassesToBeBound()).contains(clazz);
		}
		return false;
	}

	/**
	 * Checks whether the given type is a primitive wrapper type.
	 * Compare section 8.5.1 of the JAXB2 spec.
	 */
	private boolean isPrimitiveWrapper(Class<?> clazz) {
		return Boolean.class.equals(clazz) ||
				Byte.class.equals(clazz) ||
				Short.class.equals(clazz) ||
				Integer.class.equals(clazz) ||
				Long.class.equals(clazz) ||
				Float.class.equals(clazz) ||
				Double.class.equals(clazz);
	}

	/**
	 * Checks whether the given type is a standard class.
	 * Compare section 8.5.2 of the JAXB2 spec.
	 */
	private boolean isStandardClass(Class<?> clazz) {
		return String.class.equals(clazz) ||
				BigInteger.class.isAssignableFrom(clazz) ||
				BigDecimal.class.isAssignableFrom(clazz) ||
				Calendar.class.isAssignableFrom(clazz) ||
				Date.class.isAssignableFrom(clazz) ||
				QName.class.isAssignableFrom(clazz) ||
				URI.class.equals(clazz) ||
				XMLGregorianCalendar.class.isAssignableFrom(clazz) ||
				Duration.class.isAssignableFrom(clazz) ||
				Image.class.equals(clazz) ||
				DataHandler.class.equals(clazz) ||
				// Source and subclasses should be supported according to the JAXB2 spec, but aren't in the RI
				// Source.class.isAssignableFrom(clazz) ||
				UUID.class.equals(clazz);

	}

	// Marshalling

	public void marshal(Object graph, Result result) throws XmlMappingException {
		marshal(graph, result, null);
	}

	public void marshal(Object graph, Result result, MimeContainer mimeContainer) throws XmlMappingException {
		try {
			Marshaller marshaller = createMarshaller();
			if (this.mtomEnabled && mimeContainer != null) {
				marshaller.setAttachmentMarshaller(new Jaxb2AttachmentMarshaller(mimeContainer));
			}
			if (StaxUtils.isStaxResult(result)) {
				marshalStaxResult(marshaller, graph, result);
			}
			else {
				marshaller.marshal(graph, result);
			}
		}
		catch (JAXBException ex) {
			throw convertJaxbException(ex);
		}
	}

	private void marshalStaxResult(Marshaller jaxbMarshaller, Object graph, Result staxResult) throws JAXBException {
		XMLStreamWriter streamWriter = StaxUtils.getXMLStreamWriter(staxResult);
		if (streamWriter != null) {
			jaxbMarshaller.marshal(graph, streamWriter);
		}
		else {
			XMLEventWriter eventWriter = StaxUtils.getXMLEventWriter(staxResult);
			if (eventWriter != null) {
				jaxbMarshaller.marshal(graph, eventWriter);
			}
			else {
				throw new IllegalArgumentException("StAX Result contains neither XMLStreamWriter nor XMLEventConsumer");
			}
		}
	}

	/**
	 * Return a newly created JAXB marshaller. JAXB marshallers are not necessarily thread safe.
	 */
	protected Marshaller createMarshaller() {
		try {
			Marshaller marshaller = getJaxbContext().createMarshaller();
			initJaxbMarshaller(marshaller);
			return marshaller;
		}
		catch (JAXBException ex) {
			throw convertJaxbException(ex);
		}
	}

	/**
	 * Template method that can be overridden by concrete JAXB marshallers for custom initialization behavior.
	 * Gets called after creation of JAXB <code>Marshaller</code>, and after the respective properties have been set.
	 * <p>The default implementation sets the {@link #setMarshallerProperties(Map) defined properties}, the {@link
	 * #setValidationEventHandler(ValidationEventHandler) validation event handler}, the {@link #setSchemas(Resource[])
	 * schemas}, {@link #setMarshallerListener(javax.xml.bind.Marshaller.Listener) listener}, and
	 * {@link #setAdapters(XmlAdapter[]) adapters}.
	 */
	protected void initJaxbMarshaller(Marshaller marshaller) throws JAXBException {
		if (this.marshallerProperties != null) {
			for (String name : this.marshallerProperties.keySet()) {
				marshaller.setProperty(name, this.marshallerProperties.get(name));
			}
		}
		if (this.marshallerListener != null) {
			marshaller.setListener(this.marshallerListener);
		}
		if (this.validationEventHandler != null) {
			marshaller.setEventHandler(this.validationEventHandler);
		}
		if (this.adapters != null) {
			for (XmlAdapter<?, ?> adapter : this.adapters) {
				marshaller.setAdapter(adapter);
			}
		}
		if (this.schema != null) {
			marshaller.setSchema(this.schema);
		}
	}


	// Unmarshalling

	public Object unmarshal(Source source) throws XmlMappingException {
		return unmarshal(source, null);
	}

	public Object unmarshal(Source source, MimeContainer mimeContainer) throws XmlMappingException {
		try {
			Unmarshaller unmarshaller = createUnmarshaller();
			if (this.mtomEnabled && mimeContainer != null) {
				unmarshaller.setAttachmentUnmarshaller(new Jaxb2AttachmentUnmarshaller(mimeContainer));
			}
			if (StaxUtils.isStaxSource(source)) {
				return unmarshalStaxSource(unmarshaller, source);
			}
			else {
				return unmarshaller.unmarshal(source);
			}
		}
		catch (JAXBException ex) {
			throw convertJaxbException(ex);
		}
	}

	private Object unmarshalStaxSource(Unmarshaller jaxbUnmarshaller, Source staxSource) throws JAXBException {
		XMLStreamReader streamReader = StaxUtils.getXMLStreamReader(staxSource);
		if (streamReader != null) {
			return jaxbUnmarshaller.unmarshal(streamReader);
		}
		else {
			XMLEventReader eventReader = StaxUtils.getXMLEventReader(staxSource);
			if (eventReader != null) {
				return jaxbUnmarshaller.unmarshal(eventReader);
			}
			else {
				throw new IllegalArgumentException("StaxSource contains neither XMLStreamReader nor XMLEventReader");
			}
		}
	}

	/**
	 * Return a newly created JAXB unmarshaller. JAXB unmarshallers are not necessarily thread safe.
	 */
	protected Unmarshaller createUnmarshaller() {
		try {
			Unmarshaller unmarshaller = getJaxbContext().createUnmarshaller();
			initJaxbUnmarshaller(unmarshaller);
			return unmarshaller;
		}
		catch (JAXBException ex) {
			throw convertJaxbException(ex);
		}
	}

	/**
	 * Template method that can be overridden by concrete JAXB marshallers for custom initialization behavior.
	 * Gets called after creation of JAXB <code>Marshaller</code>, and after the respective properties have been set.
	 * <p>The default implementation sets the {@link #setUnmarshallerProperties(Map) defined properties}, the {@link
	 * #setValidationEventHandler(ValidationEventHandler) validation event handler}, the {@link #setSchemas(Resource[])
	 * schemas}, {@link #setUnmarshallerListener(javax.xml.bind.Unmarshaller.Listener) listener}, and
	 * {@link #setAdapters(XmlAdapter[]) adapters}.
	 */
	protected void initJaxbUnmarshaller(Unmarshaller unmarshaller) throws JAXBException {
		if (this.unmarshallerProperties != null) {
			for (String name : this.unmarshallerProperties.keySet()) {
				unmarshaller.setProperty(name, this.unmarshallerProperties.get(name));
			}
		}
		if (this.unmarshallerListener != null) {
			unmarshaller.setListener(this.unmarshallerListener);
		}
		if (this.validationEventHandler != null) {
			unmarshaller.setEventHandler(this.validationEventHandler);
		}
		if (this.adapters != null) {
			for (XmlAdapter<?, ?> adapter : this.adapters) {
				unmarshaller.setAdapter(adapter);
			}
		}
		if (this.schema != null) {
			unmarshaller.setSchema(this.schema);
		}
	}


	/**
	 * Convert the given <code>JAXBException</code> to an appropriate exception from the
	 * <code>org.springframework.oxm</code> hierarchy.
	 * @param ex <code>JAXBException</code> that occured
	 * @return the corresponding <code>XmlMappingException</code>
	 */
	protected XmlMappingException convertJaxbException(JAXBException ex) {
		if (ex instanceof ValidationException) {
			return new ValidationFailureException("JAXB validation exception", ex);
		}
		else if (ex instanceof MarshalException) {
			return new MarshallingFailureException("JAXB marshalling exception", ex);
		}
		else if (ex instanceof UnmarshalException) {
			return new UnmarshallingFailureException("JAXB unmarshalling exception", ex);
		}
		else {
			// fallback
			return new UncategorizedMappingException("Unknown JAXB exception", ex);
		}
	}


	private static class Jaxb2AttachmentMarshaller extends AttachmentMarshaller {

		private final MimeContainer mimeContainer;

		private Jaxb2AttachmentMarshaller(MimeContainer mimeContainer) {
			this.mimeContainer = mimeContainer;
		}

		@Override
		public String addMtomAttachment(byte[] data, int offset, int length, String mimeType,
				String elementNamespace, String elementLocalName) {
			ByteArrayDataSource dataSource = new ByteArrayDataSource(mimeType, data, offset, length);
			return addMtomAttachment(new DataHandler(dataSource), elementNamespace, elementLocalName);
		}

		@Override
		public String addMtomAttachment(DataHandler dataHandler, String elementNamespace, String elementLocalName) {
			String host = getHost(elementNamespace, dataHandler);
			String contentId = UUID.randomUUID() + "@" + host;
			this.mimeContainer.addAttachment("<" + contentId + ">", dataHandler);
			try {
				contentId = URLEncoder.encode(contentId, "UTF-8");
			}
			catch (UnsupportedEncodingException e) {
				// ignore
			}
			return CID + contentId;
		}

		private String getHost(String elementNamespace, DataHandler dataHandler) {
			try {
				URI uri = new URI(elementNamespace);
				return uri.getHost();
			}
			catch (URISyntaxException e) {
				// ignore
			}
			return dataHandler.getName();
		}

		@Override
		public String addSwaRefAttachment(DataHandler dataHandler) {
			String contentId = UUID.randomUUID() + "@" + dataHandler.getName();
			mimeContainer.addAttachment(contentId, dataHandler);
			return contentId;
		}

		@Override
		public boolean isXOPPackage() {
			return this.mimeContainer.convertToXopPackage();
		}
	}


	private static class Jaxb2AttachmentUnmarshaller extends AttachmentUnmarshaller {

		private final MimeContainer mimeContainer;

		private Jaxb2AttachmentUnmarshaller(MimeContainer mimeContainer) {
			this.mimeContainer = mimeContainer;
		}

		@Override
		public byte[] getAttachmentAsByteArray(String cid) {
			try {
				DataHandler dataHandler = getAttachmentAsDataHandler(cid);
				return FileCopyUtils.copyToByteArray(dataHandler.getInputStream());
			}
			catch (IOException ex) {
				throw new UnmarshallingFailureException("Couldn't read attachment", ex);
			}
		}

		@Override
		public DataHandler getAttachmentAsDataHandler(String contentId) {
			if (contentId.startsWith(CID)) {
				contentId = contentId.substring(CID.length());
				try {
					contentId = URLDecoder.decode(contentId, "UTF-8");
				}
				catch (UnsupportedEncodingException e) {
					// ignore
				}
				contentId = '<' + contentId + '>';
			}
			return this.mimeContainer.getAttachment(contentId);
		}

		@Override
		public boolean isXOPPackage() {
			return this.mimeContainer.isXopPackage();
		}
	}


	/**
	 * DataSource that wraps around a byte array.
	 */
	private static class ByteArrayDataSource implements DataSource {

		private byte[] data;

		private String contentType;

		private int offset;

		private int length;

		private ByteArrayDataSource(String contentType, byte[] data, int offset, int length) {
			this.contentType = contentType;
			this.data = data;
			this.offset = offset;
			this.length = length;
		}

		public InputStream getInputStream() throws IOException {
			return new ByteArrayInputStream(this.data, this.offset, this.length);
		}

		public OutputStream getOutputStream() throws IOException {
			throw new UnsupportedOperationException();
		}

		public String getContentType() {
			return this.contentType;
		}

		public String getName() {
			return "ByteArrayDataSource";
		}
	}

}
