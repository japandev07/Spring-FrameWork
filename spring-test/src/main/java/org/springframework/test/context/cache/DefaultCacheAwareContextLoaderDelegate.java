/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.test.context.cache;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.lang.Nullable;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.ApplicationContextFailureProcessor;
import org.springframework.test.context.CacheAwareContextLoaderDelegate;
import org.springframework.test.context.ContextLoadException;
import org.springframework.test.context.ContextLoader;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.SmartContextLoader;
import org.springframework.test.context.aot.AotContextLoader;
import org.springframework.test.context.aot.AotTestContextInitializers;
import org.springframework.test.context.aot.TestContextAotException;
import org.springframework.util.Assert;

/**
 * Default implementation of the {@link CacheAwareContextLoaderDelegate} interface.
 *
 * <p>To use a static {@link DefaultContextCache}, invoke the
 * {@link #DefaultCacheAwareContextLoaderDelegate()} constructor; otherwise,
 * invoke the {@link #DefaultCacheAwareContextLoaderDelegate(ContextCache)}
 * and provide a custom {@link ContextCache} implementation.
 *
 * <p>As of Spring Framework 6.0, this class loads {@link ApplicationContextFailureProcessor}
 * implementations via the {@link SpringFactoriesLoader} mechanism and delegates to
 * them in {@link #loadContext(MergedContextConfiguration)} to process context
 * load failures.
 *
 * @author Sam Brannen
 * @since 4.1
 */
public class DefaultCacheAwareContextLoaderDelegate implements CacheAwareContextLoaderDelegate {

	private static final Log logger = LogFactory.getLog(DefaultCacheAwareContextLoaderDelegate.class);


	/**
	 * Default static cache of Spring application contexts.
	 */
	static final ContextCache defaultContextCache = new DefaultContextCache();

	private List<ApplicationContextFailureProcessor> contextFailureProcessors =
			loadApplicationContextFailureProcessors();

	private final AotTestContextInitializers aotTestContextInitializers = new AotTestContextInitializers();

	private final ContextCache contextCache;


	/**
	 * Construct a new {@code DefaultCacheAwareContextLoaderDelegate} using
	 * a static {@link DefaultContextCache}.
	 * <p>This default cache is static so that each context can be cached
	 * and reused for all subsequent tests that declare the same unique
	 * context configuration within the same JVM process.
	 * @see #DefaultCacheAwareContextLoaderDelegate(ContextCache)
	 */
	public DefaultCacheAwareContextLoaderDelegate() {
		this(defaultContextCache);
	}

	/**
	 * Construct a new {@code DefaultCacheAwareContextLoaderDelegate} using
	 * the supplied {@link ContextCache}.
	 * @see #DefaultCacheAwareContextLoaderDelegate()
	 */
	public DefaultCacheAwareContextLoaderDelegate(ContextCache contextCache) {
		Assert.notNull(contextCache, "ContextCache must not be null");
		this.contextCache = contextCache;
	}


	@Override
	public boolean isContextLoaded(MergedContextConfiguration mergedContextConfiguration) {
		synchronized (this.contextCache) {
			return this.contextCache.contains(replaceIfNecessary(mergedContextConfiguration));
		}
	}

	@Override
	public ApplicationContext loadContext(MergedContextConfiguration mergedContextConfiguration) {
		mergedContextConfiguration = replaceIfNecessary(mergedContextConfiguration);
		synchronized (this.contextCache) {
			ApplicationContext context = this.contextCache.get(mergedContextConfiguration);
			if (context == null) {
				try {
					if (mergedContextConfiguration instanceof AotMergedContextConfiguration aotMergedConfig) {
						context = loadContextInAotMode(aotMergedConfig);
					}
					else {
						context = loadContextInternal(mergedContextConfiguration);
					}
					if (logger.isTraceEnabled()) {
						logger.trace("Storing ApplicationContext [%s] in cache under key %s".formatted(
								System.identityHashCode(context), mergedContextConfiguration));
					}
					this.contextCache.put(mergedContextConfiguration, context);
				}
				catch (Exception ex) {
					Throwable cause = ex;
					if (ex instanceof ContextLoadException cle) {
						cause = cle.getCause();
						for (ApplicationContextFailureProcessor contextFailureProcessor : this.contextFailureProcessors) {
							try {
								contextFailureProcessor.processLoadFailure(cle.getApplicationContext(), cause);
							}
							catch (Throwable throwable) {
								if (logger.isDebugEnabled()) {
									logger.debug("Ignoring exception thrown from ApplicationContextFailureProcessor [%s]: %s"
											.formatted(contextFailureProcessor, throwable));
								}
							}
						}
					}
					throw new IllegalStateException(
						"Failed to load ApplicationContext for " + mergedContextConfiguration, cause);
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Retrieved ApplicationContext [%s] from cache with key %s".formatted(
							System.identityHashCode(context), mergedContextConfiguration));
				}
			}

			this.contextCache.logStatistics();

			return context;
		}
	}

	@Override
	public void closeContext(MergedContextConfiguration mergedContextConfiguration, @Nullable HierarchyMode hierarchyMode) {
		synchronized (this.contextCache) {
			this.contextCache.remove(replaceIfNecessary(mergedContextConfiguration), hierarchyMode);
		}
	}

	/**
	 * Get the {@link ContextCache} used by this context loader delegate.
	 */
	protected ContextCache getContextCache() {
		return this.contextCache;
	}

	/**
	 * Load the {@code ApplicationContext} for the supplied merged context configuration.
	 * <p>Supports both the {@link SmartContextLoader} and {@link ContextLoader} SPIs.
	 * @throws Exception if an error occurs while loading the application context
	 */
	@SuppressWarnings("deprecation")
	protected ApplicationContext loadContextInternal(MergedContextConfiguration mergedContextConfiguration)
			throws Exception {

		ContextLoader contextLoader = getContextLoader(mergedContextConfiguration);
		if (contextLoader instanceof SmartContextLoader smartContextLoader) {
			return smartContextLoader.loadContext(mergedContextConfiguration);
		}
		else {
			String[] locations = mergedContextConfiguration.getLocations();
			Assert.notNull(locations, """
					Cannot load an ApplicationContext with a NULL 'locations' array. \
					Consider annotating test class [%s] with @ContextConfiguration or \
					@ContextHierarchy.""".formatted(mergedContextConfiguration.getTestClass().getName()));
			return contextLoader.loadContext(locations);
		}
	}

	protected ApplicationContext loadContextInAotMode(AotMergedContextConfiguration aotMergedConfig) throws Exception {
		Class<?> testClass = aotMergedConfig.getTestClass();
		ApplicationContextInitializer<ConfigurableApplicationContext> contextInitializer =
				this.aotTestContextInitializers.getContextInitializer(testClass);
		Assert.state(contextInitializer != null,
				() -> "Failed to load AOT ApplicationContextInitializer for test class [%s]"
						.formatted(testClass.getName()));
		ContextLoader contextLoader = getContextLoader(aotMergedConfig);

		if (logger.isTraceEnabled()) {
			logger.trace("Loading ApplicationContext for AOT runtime for " + aotMergedConfig.getOriginal());
		}
		else if (logger.isDebugEnabled()) {
			logger.debug("Loading ApplicationContext for AOT runtime for test class " +
					aotMergedConfig.getTestClass().getName());
		}

		if (!((contextLoader instanceof AotContextLoader aotContextLoader) &&
				(aotContextLoader.loadContextForAotRuntime(aotMergedConfig.getOriginal(), contextInitializer)
						instanceof GenericApplicationContext gac))) {
			throw new TestContextAotException("""
					Cannot load ApplicationContext for AOT runtime for %s. The configured \
					ContextLoader [%s] must be an AotContextLoader and must create a \
					GenericApplicationContext."""
						.formatted(aotMergedConfig.getOriginal(), contextLoader.getClass().getName()));
		}
		gac.registerShutdownHook();
		return gac;
	}

	private ContextLoader getContextLoader(MergedContextConfiguration mergedConfig) {
		ContextLoader contextLoader = mergedConfig.getContextLoader();
		Assert.notNull(contextLoader, """
				Cannot load an ApplicationContext with a NULL 'contextLoader'. \
				Consider annotating test class [%s] with @ContextConfiguration or \
				@ContextHierarchy.""".formatted(mergedConfig.getTestClass().getName()));
		return contextLoader;
	}

	/**
	 * If the test class associated with the supplied {@link MergedContextConfiguration}
	 * has an AOT-optimized {@link ApplicationContext}, this method will create an
	 * {@link AotMergedContextConfiguration} to replace the provided {@code MergedContextConfiguration}.
	 * <p>Otherwise, this method simply returns the supplied {@code MergedContextConfiguration}
	 * unmodified.
	 * <p>This allows for transparent {@link org.springframework.test.context.cache.ContextCache ContextCache}
	 * support for AOT-optimized application contexts.
	 * @since 6.0
	 */
	@SuppressWarnings("unchecked")
	private MergedContextConfiguration replaceIfNecessary(MergedContextConfiguration mergedConfig) {
		Class<?> testClass = mergedConfig.getTestClass();
		if (this.aotTestContextInitializers.isSupportedTestClass(testClass)) {
			Class<? extends ApplicationContextInitializer<?>> contextInitializerClass =
					this.aotTestContextInitializers.getContextInitializerClass(testClass);
			return new AotMergedContextConfiguration(testClass, contextInitializerClass, mergedConfig, this);
		}
		return mergedConfig;
	}

	/**
	 * Get the {@link ApplicationContextFailureProcessor} implementations to use,
	 * loaded via the {@link SpringFactoriesLoader} mechanism.
	 * @return the context failure processors to use
	 * @since 6.0
	 */
	private static List<ApplicationContextFailureProcessor> loadApplicationContextFailureProcessors() {
		SpringFactoriesLoader loader = SpringFactoriesLoader.forDefaultResourceLocation(
				DefaultCacheAwareContextLoaderDelegate.class.getClassLoader());
		List<ApplicationContextFailureProcessor> processors = loader.load(ApplicationContextFailureProcessor.class,
				DefaultCacheAwareContextLoaderDelegate::handleInstantiationFailure);
		if (logger.isTraceEnabled()) {
			logger.trace("Loaded default ApplicationContextFailureProcessor implementations from location [%s]: %s"
					.formatted(SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION, classNames(processors)));
		}
		else if (logger.isDebugEnabled()) {
			logger.debug("Loaded default ApplicationContextFailureProcessor implementations from location [%s]: %s"
					.formatted(SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION, classSimpleNames(processors)));
		}
		return processors;
	}

	private static void handleInstantiationFailure(
			Class<?> factoryType, String factoryImplementationName, Throwable failure) {

		Throwable ex = (failure instanceof InvocationTargetException ite ?
				ite.getTargetException() : failure);
		if (ex instanceof ClassNotFoundException || ex instanceof NoClassDefFoundError) {
			logSkippedComponent(factoryType, factoryImplementationName, ex);
		}
		else if (ex instanceof LinkageError) {
			if (logger.isDebugEnabled()) {
				logger.debug("""
						Could not load %1$s [%2$s]. Specify custom %1$s classes or make the default %1$s classes \
						available.""".formatted(factoryType.getSimpleName(), factoryImplementationName), ex);
			}
		}
		else {
			if (ex instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (ex instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(
				"Failed to load %s [%s].".formatted(factoryType.getSimpleName(), factoryImplementationName), ex);
		}
	}

	private static void logSkippedComponent(Class<?> factoryType, String factoryImplementationName, Throwable ex) {
		if (logger.isDebugEnabled()) {
			logger.debug("""
					Skipping candidate %1$s [%2$s] due to a missing dependency. \
					Specify custom %1$s classes or make the default %1$s classes \
					and their required dependencies available. Offending class: [%3$s]"""
						.formatted(factoryType.getSimpleName(), factoryImplementationName, ex.getMessage()));
		}
	}

	private static List<String> classNames(Collection<?> components) {
		return components.stream().map(Object::getClass).map(Class::getName).toList();
	}

	private static List<String> classSimpleNames(Collection<?> components) {
		return components.stream().map(Object::getClass).map(Class::getSimpleName).toList();
	}

}
