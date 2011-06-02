/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.test.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Abstract, generic extension of {@link AbstractContextLoader} which loads a
 * {@link GenericApplicationContext} from the <em>locations</em> provided to
 * {@link #loadContext loadContext()}.
 *
 * <p>Concrete subclasses must provide an appropriate implementation of
 * {@link #createBeanDefinitionReader createBeanDefinitionReader()},
 * potentially overriding {@link #loadBeanDefinitions loadBeanDefinitions()}
 * in addition.
 *
 * @author Sam Brannen
 * @author Juergen Hoeller
 * @since 2.5
 * @see #loadContext
 */
public abstract class AbstractGenericContextLoader extends AbstractContextLoader {

	protected static final Log logger = LogFactory.getLog(AbstractGenericContextLoader.class);


	/**
	 * TODO Document loadContext().
	 *
	 * @see org.springframework.test.context.SmartContextLoader#loadContext(org.springframework.test.context.MergedContextConfiguration)
	 */
	public final ApplicationContext loadContext(MergedContextConfiguration mergedContextConfiguration) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Loading ApplicationContext for merged context configuration [%s].",
				mergedContextConfiguration));
		}

		String[] locations = mergedContextConfiguration.getLocations();
		Assert.notNull(locations, "Can not load an ApplicationContext with a NULL 'locations' array. "
				+ "Consider annotating your test class with @ContextConfiguration.");

		GenericApplicationContext context = new GenericApplicationContext();
		context.getEnvironment().setActiveProfiles(mergedContextConfiguration.getActiveProfiles());
		prepareContext(context);
		customizeBeanFactory(context.getDefaultListableBeanFactory());
		loadBeanDefinitions(context, locations);
		AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
		customizeContext(context);
		context.refresh();
		context.registerShutdownHook();
		return context;
	}

	/**
	 * Loads a Spring ApplicationContext from the supplied <code>locations</code>.
	 * <p>Implementation details:
	 * <ul>
	 * <li>Creates a {@link GenericApplicationContext} instance.</li>
	 * <li>Calls {@link #prepareContext(GenericApplicationContext)} to
	 * prepare the context.</li>
	 * <li>Calls {@link #customizeBeanFactory(DefaultListableBeanFactory)} to
	 * allow for customizing the context's <code>DefaultListableBeanFactory</code>.</li>
	 * <li>Delegates to {@link #loadBeanDefinitions(GenericApplicationContext, String...)}
	 * to populate the context from the specified config locations.</li>
	 * <li>Delegates to {@link AnnotationConfigUtils} for
	 * {@link AnnotationConfigUtils#registerAnnotationConfigProcessors registering}
	 * annotation configuration processors.</li>
	 * <li>Calls {@link #customizeContext(GenericApplicationContext)} to allow
	 * for customizing the context before it is refreshed.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh() Refreshes} the
	 * context and registers a JVM shutdown hook for it.</li>
	 * </ul>
	 * @return a new application context
	 * @see org.springframework.test.context.ContextLoader#loadContext
	 * @see GenericApplicationContext
	 */
	public final ConfigurableApplicationContext loadContext(String... locations) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Loading ApplicationContext for locations [%s].",
				StringUtils.arrayToCommaDelimitedString(locations)));
		}
		GenericApplicationContext context = new GenericApplicationContext();
		prepareContext(context);
		customizeBeanFactory(context.getDefaultListableBeanFactory());
		loadBeanDefinitions(context, locations);
		AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
		customizeContext(context);
		context.refresh();
		context.registerShutdownHook();
		return context;
	}

	/**
	 * Prepare the {@link GenericApplicationContext} created by this <code>ContextLoader</code>.
	 * Called <i>before</> bean definitions are read.
	 * <p>The default implementation is empty. Can be overridden in subclasses to
	 * customize GenericApplicationContext's standard settings.
	 * @param context the context for which the BeanDefinitionReader should be created
	 * @see #loadContext
	 * @see org.springframework.context.support.GenericApplicationContext#setResourceLoader
	 * @see org.springframework.context.support.GenericApplicationContext#setId
	 */
	protected void prepareContext(GenericApplicationContext context) {
	}

	/**
	 * Customize the internal bean factory of the ApplicationContext created by this <code>ContextLoader</code>.
	 * <p>The default implementation is empty but can be overridden in subclasses
	 * to customize DefaultListableBeanFactory's standard settings.
	 * @param beanFactory the bean factory created by this <code>ContextLoader</code>
	 * @see #loadContext
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowEagerClassLoading(boolean)
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowCircularReferences(boolean)
	 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#setAllowRawInjectionDespiteWrapping(boolean)
	 */
	protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
	}

	/**
	 * Load bean definitions into the supplied {@link GenericApplicationContext context}
	 * from the specified resource locations.
	 * <p>The default implementation delegates to the {@link BeanDefinitionReader}
	 * returned by {@link #createBeanDefinitionReader} to 
	 * {@link BeanDefinitionReader#loadBeanDefinitions(String) load} the
	 * bean definitions.
	 * <p>Subclasses must provide an appropriate implementation of
	 * {@link #createBeanDefinitionReader}. Alternatively subclasses may
	 * provide a <em>no-op</em> implementation of {@link #createBeanDefinitionReader}
	 * and override this method to provide a custom strategy for loading or
	 * registering bean definitions.
	 * @param context the context into which the bean definitions should be loaded
	 * @param locations the resource locations from which to load the bean definitions
	 * @since 3.1
	 * @see #loadContext
	 */
	protected void loadBeanDefinitions(GenericApplicationContext context, String... locations) {
		createBeanDefinitionReader(context).loadBeanDefinitions(locations);
	}

	/**
	 * Factory method for creating a new {@link BeanDefinitionReader} for
	 * loading bean definitions into the supplied {@link GenericApplicationContext context}.
	 * @param context the context for which the BeanDefinitionReader should be created
	 * @return a BeanDefinitionReader for the supplied context
	 * @see #loadBeanDefinitions
	 * @see BeanDefinitionReader
	 */
	protected abstract BeanDefinitionReader createBeanDefinitionReader(GenericApplicationContext context);

	/**
	 * Customize the {@link GenericApplicationContext} created by this
	 * <code>ContextLoader</code> <i>after</i> bean definitions have been
	 * loaded into the context but before the context is refreshed.
	 * <p>The default implementation is empty but can be overridden in subclasses
	 * to customize the application context.
	 * @param context the newly created application context
	 * @see #loadContext
	 */
	protected void customizeContext(GenericApplicationContext context) {
	}

}
