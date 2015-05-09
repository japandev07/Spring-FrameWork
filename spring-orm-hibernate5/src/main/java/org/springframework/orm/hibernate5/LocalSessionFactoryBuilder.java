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

package org.springframework.orm.hibernate5;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A Spring-provided extension of the standard Hibernate {@link Configuration} class,
 * adding {@link SpringSessionContext} as a default and providing convenient ways
 * to specify a DataSource and an application class loader.
 *
 * <p>This is designed for programmatic use, e.g. in {@code @Bean} factory methods.
 * Consider using {@link LocalSessionFactoryBean} for XML bean definition files.
 *
 * @author Juergen Hoeller
 * @since 4.2
 * @see LocalSessionFactoryBean
 */
@SuppressWarnings("serial")
public class LocalSessionFactoryBuilder extends Configuration {

	private static final String RESOURCE_PATTERN = "/**/*.class";

	private static final String PACKAGE_INFO_SUFFIX = ".package-info";

	private static final TypeFilter[] DEFAULT_ENTITY_TYPE_FILTERS = new TypeFilter[] {
			new AnnotationTypeFilter(Entity.class, false),
			new AnnotationTypeFilter(Embeddable.class, false),
			new AnnotationTypeFilter(MappedSuperclass.class, false)};

	private final TypeFilter CONVERTER_TYPE_FILTER = new AnnotationTypeFilter(Converter.class, false);


	private final ResourcePatternResolver resourcePatternResolver;

	private TypeFilter[] entityTypeFilters = DEFAULT_ENTITY_TYPE_FILTERS;


	/**
	 * Create a new LocalSessionFactoryBuilder for the given DataSource.
	 * @param dataSource the JDBC DataSource that the resulting Hibernate SessionFactory should be using
	 * (may be {@code null})
	 */
	public LocalSessionFactoryBuilder(DataSource dataSource) {
		this(dataSource, new PathMatchingResourcePatternResolver());
	}

	/**
	 * Create a new LocalSessionFactoryBuilder for the given DataSource.
	 * @param dataSource the JDBC DataSource that the resulting Hibernate SessionFactory should be using
	 * (may be {@code null})
	 * @param classLoader the ClassLoader to load application classes from
	 */
	public LocalSessionFactoryBuilder(DataSource dataSource, ClassLoader classLoader) {
		this(dataSource, new PathMatchingResourcePatternResolver(classLoader));
	}

	/**
	 * Create a new LocalSessionFactoryBuilder for the given DataSource.
	 * @param dataSource the JDBC DataSource that the resulting Hibernate SessionFactory should be using
	 * (may be {@code null})
	 * @param resourceLoader the ResourceLoader to load application classes from
	 */
	public LocalSessionFactoryBuilder(DataSource dataSource, ResourceLoader resourceLoader) {
		getProperties().put(Environment.CURRENT_SESSION_CONTEXT_CLASS, SpringSessionContext.class.getName());
		if (dataSource != null) {
			getProperties().put(Environment.DATASOURCE, dataSource);
		}
		getProperties().put(AvailableSettings.CLASSLOADERS, Collections.singleton(resourceLoader.getClassLoader()));
		this.resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
	}


	/**
	 * Set the Spring {@link JtaTransactionManager} or the JTA {@link TransactionManager}
	 * to be used with Hibernate, if any. Allows for using a Spring-managed transaction
	 * manager for Hibernate 5's session and cache synchronization, with the
	 * "hibernate.transaction.jta.platform" automatically set to it.
	 * <p>A passed-in Spring {@link JtaTransactionManager} needs to contain a JTA
	 * {@link TransactionManager} reference to be usable here, except for the WebSphere
	 * case where we'll automatically set {@code WebSphereExtendedJtaPlatform} accordingly.
	 * <p>Note: If this is set, the Hibernate settings should not contain a JTA platform
	 * setting to avoid meaningless double configuration.
	 */
	public LocalSessionFactoryBuilder setJtaTransactionManager(Object jtaTransactionManager) {
		Assert.notNull(jtaTransactionManager, "Transaction manager reference must not be null");
		if (jtaTransactionManager instanceof JtaTransactionManager) {
			boolean webspherePresent = ClassUtils.isPresent("com.ibm.wsspi.uow.UOWManager", getClass().getClassLoader());
			if (webspherePresent) {
				getProperties().put(AvailableSettings.JTA_PLATFORM,
						"org.hibernate.engine.transaction.jta.platform.internal.WebSphereExtendedJtaPlatform");
			}
			else {
				JtaTransactionManager jtaTm = (JtaTransactionManager) jtaTransactionManager;
				if (jtaTm.getTransactionManager() == null) {
					throw new IllegalArgumentException(
							"Can only apply JtaTransactionManager which has a TransactionManager reference set");
				}
				getProperties().put(AvailableSettings.JTA_PLATFORM,
						new ConfigurableJtaPlatform(jtaTm.getTransactionManager(), jtaTm.getUserTransaction(),
								jtaTm.getTransactionSynchronizationRegistry()));
			}
		}
		else if (jtaTransactionManager instanceof TransactionManager) {
			getProperties().put(AvailableSettings.JTA_PLATFORM,
					new ConfigurableJtaPlatform((TransactionManager) jtaTransactionManager, null, null));
		}
		else {
			throw new IllegalArgumentException(
					"Unknown transaction manager type: " + jtaTransactionManager.getClass().getName());
		}
		return this;
	}

	/**
	 * Specify custom type filters for Spring-based scanning for entity classes.
	 * <p>Default is to search all specified packages for classes annotated with
	 * {@code @javax.persistence.Entity}, {@code @javax.persistence.Embeddable}
	 * or {@code @javax.persistence.MappedSuperclass}.
	 * @since 4.2
	 * @see #scanPackages
	 */
	public LocalSessionFactoryBuilder setEntityTypeFilters(TypeFilter... entityTypeFilters) {
		this.entityTypeFilters = entityTypeFilters;
		return this;
	}

	/**
	 * Add the given annotated classes in a batch.
	 * @see #addAnnotatedClass
	 * @see #scanPackages
	 */
	public LocalSessionFactoryBuilder addAnnotatedClasses(Class<?>... annotatedClasses) {
		for (Class<?> annotatedClass : annotatedClasses) {
			addAnnotatedClass(annotatedClass);
		}
		return this;
	}

	/**
	 * Add the given annotated packages in a batch.
	 * @see #addPackage
	 * @see #scanPackages
	 */
	public LocalSessionFactoryBuilder addPackages(String... annotatedPackages) {
		for (String annotatedPackage : annotatedPackages) {
			addPackage(annotatedPackage);
		}
		return this;
	}

	/**
	 * Perform Spring-based scanning for entity classes, registering them
	 * as annotated classes with this {@code Configuration}.
	 * @param packagesToScan one or more Java package names
	 * @throws HibernateException if scanning fails for any reason
	 */
	@SuppressWarnings("unchecked")
	public LocalSessionFactoryBuilder scanPackages(String... packagesToScan) throws HibernateException {
		Set<String> entityClassNames = new TreeSet<String>();
		Set<String> converterClassNames = new TreeSet<String>();
		Set<String> packageNames = new TreeSet<String>();
		try {
			for (String pkg : packagesToScan) {
				String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
						ClassUtils.convertClassNameToResourcePath(pkg) + RESOURCE_PATTERN;
				Resource[] resources = this.resourcePatternResolver.getResources(pattern);
				MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(this.resourcePatternResolver);
				for (Resource resource : resources) {
					if (resource.isReadable()) {
						MetadataReader reader = readerFactory.getMetadataReader(resource);
						String className = reader.getClassMetadata().getClassName();
						if (matchesEntityTypeFilter(reader, readerFactory)) {
							entityClassNames.add(className);
						}
						else if (CONVERTER_TYPE_FILTER.match(reader, readerFactory)) {
							converterClassNames.add(className);
						}
						else if (className.endsWith(PACKAGE_INFO_SUFFIX)) {
							packageNames.add(className.substring(0, className.length() - PACKAGE_INFO_SUFFIX.length()));
						}
					}
				}
			}
		}
		catch (IOException ex) {
			throw new MappingException("Failed to scan classpath for unlisted classes", ex);
		}
		try {
			ClassLoader cl = this.resourcePatternResolver.getClassLoader();
			for (String className : entityClassNames) {
				addAnnotatedClass(cl.loadClass(className));
			}
			for (String className : converterClassNames) {
				addAttributeConverter((Class<? extends AttributeConverter<?, ?>>) cl.loadClass(className));
			}
			for (String packageName : packageNames) {
				addPackage(packageName);
			}
		}
		catch (ClassNotFoundException ex) {
			throw new MappingException("Failed to load annotated classes from classpath", ex);
		}
		return this;
	}

	/**
	 * Check whether any of the configured entity type filters matches
	 * the current class descriptor contained in the metadata reader.
	 */
	private boolean matchesEntityTypeFilter(MetadataReader reader, MetadataReaderFactory readerFactory) throws IOException {
		if (this.entityTypeFilters != null) {
			for (TypeFilter filter : this.entityTypeFilters) {
				if (filter.match(reader, readerFactory)) {
					return true;
				}
			}
		}
		return false;
	}

}
