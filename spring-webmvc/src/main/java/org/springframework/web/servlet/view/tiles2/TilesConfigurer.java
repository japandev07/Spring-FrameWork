/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.web.servlet.view.tiles2;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.jsp.JspFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tiles.TilesApplicationContext;
import org.apache.tiles.TilesException;
import org.apache.tiles.awareness.TilesApplicationContextAware;
import org.apache.tiles.context.TilesRequestContextFactory;
import org.apache.tiles.definition.DefinitionsFactory;
import org.apache.tiles.definition.DefinitionsFactoryException;
import org.apache.tiles.definition.DefinitionsReader;
import org.apache.tiles.definition.Refreshable;
import org.apache.tiles.definition.dao.BaseLocaleUrlDefinitionDAO;
import org.apache.tiles.definition.dao.CachingLocaleUrlDefinitionDAO;
import org.apache.tiles.definition.digester.DigesterDefinitionsReader;
import org.apache.tiles.el.ELAttributeEvaluator;
import org.apache.tiles.evaluator.AttributeEvaluator;
import org.apache.tiles.evaluator.AttributeEvaluatorFactory;
import org.apache.tiles.evaluator.BasicAttributeEvaluatorFactory;
import org.apache.tiles.evaluator.impl.DirectAttributeEvaluator;
import org.apache.tiles.extras.complete.CompleteAutoloadTilesContainerFactory;
import org.apache.tiles.extras.complete.CompleteAutoloadTilesInitializer;
import org.apache.tiles.factory.AbstractTilesContainerFactory;
import org.apache.tiles.factory.BasicTilesContainerFactory;
import org.apache.tiles.impl.BasicTilesContainer;
import org.apache.tiles.impl.mgmt.CachingTilesContainer;
import org.apache.tiles.locale.LocaleResolver;
import org.apache.tiles.preparer.PreparerFactory;
import org.apache.tiles.startup.AbstractTilesInitializer;
import org.apache.tiles.startup.TilesInitializer;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.ServletContextAware;

/**
 * Helper class to configure Tiles 2.x for the Spring Framework. See
 * <a href="http://tiles.apache.org">http://tiles.apache.org</a>
 * for more information about Tiles, which basically is a templating mechanism
 * for web applications using JSPs and other template engines.
 *
 * <b>Note: Spring 4.0 requires Tiles 2.2.2.</b> Tiles' EL support will
 * be activated by default when the Tiles EL module is present in the classpath.
 *
 * <p>The TilesConfigurer simply configures a TilesContainer using a set of files
 * containing definitions, to be accessed by {@link TilesView} instances. This is a
 * Spring-based alternative (for usage in Spring configuration) to the Tiles-provided
 * {@code ServletContextListener}
 * (e.g. {@link org.apache.tiles.extras.complete.CompleteAutoloadTilesListener}
 * for usage in {@code web.xml}.
 *
 * <p>TilesViews can be managed by any {@link org.springframework.web.servlet.ViewResolver}.
 * For simple convention-based view resolution, consider using {@link TilesViewResolver}.
 *
 * <p>A typical TilesConfigurer bean definition looks as follows:
 *
 * <pre class="code">
 * &lt;bean id="tilesConfigurer" class="org.springframework.web.servlet.view.tiles2.TilesConfigurer">
 *   &lt;property name="definitions">
 *     &lt;list>
 *       &lt;value>/WEB-INF/defs/general.xml&lt;/value>
 *       &lt;value>/WEB-INF/defs/widgets.xml&lt;/value>
 *       &lt;value>/WEB-INF/defs/administrator.xml&lt;/value>
 *       &lt;value>/WEB-INF/defs/customer.xml&lt;/value>
 *       &lt;value>/WEB-INF/defs/templates.xml&lt;/value>
 *     &lt;/list>
 *   &lt;/property>
 * &lt;/bean>
 * </pre>
 *
 * The values in the list are the actual Tiles XML files containing the definitions.
 * If the list is not specified, the default is {@code "/WEB-INF/tiles.xml"}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see TilesView
 * @see TilesViewResolver
 */
public class TilesConfigurer implements ServletContextAware, InitializingBean, DisposableBean {

	private static final boolean tilesElPresent =
			ClassUtils.isPresent("org.apache.tiles.el.ELAttributeEvaluator", TilesConfigurer.class.getClassLoader());


	protected final Log logger = LogFactory.getLog(getClass());

	private TilesInitializer tilesInitializer;

	private String[] definitions;

	private boolean checkRefresh = false;

	private boolean validateDefinitions = true;

	private Class<? extends DefinitionsFactory> definitionsFactoryClass;

	private Class<? extends PreparerFactory> preparerFactoryClass;

	private boolean useMutableTilesContainer = false;

	private ServletContext servletContext;


	/**
	 * Configure Tiles using a custom TilesInitializer, typically specified as an inner bean.
	 * <p>Default is a variant of {@link org.apache.tiles.startup.DefaultTilesInitializer},
	 * respecting the "definitions", "preparerFactoryClass" etc properties on this configurer.
	 * <p><b>NOTE: Specifying a custom TilesInitializer effectively disables all other bean
	 * properties on this configurer.</b> The entire initialization procedure is then left
	 * to the TilesInitializer as specified.
	 */
	public void setTilesInitializer(TilesInitializer tilesInitializer) {
		this.tilesInitializer = tilesInitializer;
	}

	/**
	 * Specify whether to apply Tiles 2.2's "complete-autoload" configuration.
	 * <p>See {@link org.apache.tiles.extras.complete.CompleteAutoloadTilesContainerFactory}
	 * for details on the complete-autoload mode.
	 * <p><b>NOTE: Specifying the complete-autoload mode effectively disables all other bean
	 * properties on this configurer.</b> The entire initialization procedure is then left
	 * to {@link org.apache.tiles.extras.complete.CompleteAutoloadTilesInitializer}.
	 * @see org.apache.tiles.extras.complete.CompleteAutoloadTilesContainerFactory
	 * @see org.apache.tiles.extras.complete.CompleteAutoloadTilesInitializer
	 */
	public void setCompleteAutoload(boolean completeAutoload) {
		if (completeAutoload) {
			try {
				this.tilesInitializer = new SpringCompleteAutoloadTilesInitializer();
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Tiles-Extras 2.2 not available", ex);
			}
		}
		else {
			this.tilesInitializer = null;
		}
	}

	/**
	 * Set the Tiles definitions, i.e. the list of files containing the definitions.
	 * Default is "/WEB-INF/tiles.xml".
	 */
	public void setDefinitions(String... definitions) {
		this.definitions = definitions;
	}

	/**
	 * Set whether to check Tiles definition files for a refresh at runtime.
	 * Default is "false".
	 */
	public void setCheckRefresh(boolean checkRefresh) {
		this.checkRefresh = checkRefresh;
	}

	/**
	 * Set whether to validate the Tiles XML definitions. Default is "true".
	 */
	public void setValidateDefinitions(boolean validateDefinitions) {
		this.validateDefinitions = validateDefinitions;
	}

	/**
	 * Set the {@link org.apache.tiles.definition.DefinitionsFactory} implementation to use.
	 * Default is {@link org.apache.tiles.definition.UnresolvingLocaleDefinitionsFactory},
	 * operating on definition resource URLs.
	 * <p>Specify a custom DefinitionsFactory, e.g. a UrlDefinitionsFactory subclass,
	 * to customize the creation of Tiles Definition objects. Note that such a
	 * DefinitionsFactory has to be able to handle {@link java.net.URL} source objects,
	 * unless you configure a different TilesContainerFactory.
	 */
	public void setDefinitionsFactoryClass(Class<? extends DefinitionsFactory> definitionsFactoryClass) {
		this.definitionsFactoryClass = definitionsFactoryClass;
	}

	/**
	 * Set the {@link org.apache.tiles.preparer.PreparerFactory} implementation to use.
	 * Default is {@link org.apache.tiles.preparer.BasicPreparerFactory}, creating
	 * shared instances for specified preparer classes.
	 * <p>Specify {@link SimpleSpringPreparerFactory} to autowire
	 * {@link org.apache.tiles.preparer.ViewPreparer} instances based on specified
	 * preparer classes, applying Spring's container callbacks as well as applying
	 * configured Spring BeanPostProcessors. If Spring's context-wide annotation-config
	 * has been activated, annotations in ViewPreparer classes will be automatically
	 * detected and applied.
	 * <p>Specify {@link SpringBeanPreparerFactory} to operate on specified preparer
	 * <i>names</i> instead of classes, obtaining the corresponding Spring bean from
	 * the DispatcherServlet's application context. The full bean creation process
	 * will be in the control of the Spring application context in this case,
	 * allowing for the use of scoped beans etc. Note that you need to define one
	 * Spring bean definition per preparer name (as used in your Tiles definitions).
	 * @see SimpleSpringPreparerFactory
	 * @see SpringBeanPreparerFactory
	 */
	public void setPreparerFactoryClass(Class<? extends PreparerFactory> preparerFactoryClass) {
		this.preparerFactoryClass = preparerFactoryClass;
	}

	/**
	 * Set whether to use a MutableTilesContainer (typically the CachingTilesContainer
	 * implementation) for this application. Default is "false".
	 * @see org.apache.tiles.mgmt.MutableTilesContainer
	 * @see org.apache.tiles.impl.mgmt.CachingTilesContainer
	 */
	public void setUseMutableTilesContainer(boolean useMutableTilesContainer) {
		this.useMutableTilesContainer = useMutableTilesContainer;
	}

	@Override
	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	/**
	 * Creates and exposes a TilesContainer for this web application,
	 * delegating to the TilesInitializer.
	 * @throws TilesException in case of setup failure
	 */
	@Override
	public void afterPropertiesSet() throws TilesException {
		TilesApplicationContext preliminaryContext =
				new SpringWildcardServletTilesApplicationContext(this.servletContext);
		if (this.tilesInitializer == null) {
			this.tilesInitializer = createTilesInitializer();
		}
		this.tilesInitializer.initialize(preliminaryContext);
	}

	/**
	 * Creates a new instance of {@link SpringTilesInitializer}.
	 * <p>Override it to use a different initializer.
	 * @see org.apache.tiles.web.startup.AbstractTilesListener#createTilesInitializer()
	 */
	protected TilesInitializer createTilesInitializer() {
		return new SpringTilesInitializer();
	}

	/**
	 * Removes the TilesContainer from this web application.
	 * @throws TilesException in case of cleanup failure
	 */
	@Override
	public void destroy() throws TilesException {
		this.tilesInitializer.destroy();
	}


	private class SpringTilesInitializer extends AbstractTilesInitializer {

		@Override
		protected AbstractTilesContainerFactory createContainerFactory(TilesApplicationContext context) {
			return new SpringTilesContainerFactory();
		}
	}


	private class SpringTilesContainerFactory extends BasicTilesContainerFactory {

		@Override
		protected BasicTilesContainer instantiateContainer(TilesApplicationContext context) {
			return (useMutableTilesContainer ? new CachingTilesContainer() : new BasicTilesContainer());
		}

		@Override
		protected void registerRequestContextFactory(String className,
				List<TilesRequestContextFactory> factories, TilesRequestContextFactory parent) {
			// Avoid Tiles 2.2 warn logging when default RequestContextFactory impl class not found
			if (ClassUtils.isPresent(className, TilesConfigurer.class.getClassLoader())) {
				super.registerRequestContextFactory(className, factories, parent);
			}
		}

		@Override
		protected List<URL> getSourceURLs(TilesApplicationContext applicationContext,
				TilesRequestContextFactory contextFactory) {
			if (definitions != null) {
				try {
					List<URL> result = new LinkedList<URL>();
					for (String definition : definitions) {
						result.addAll(applicationContext.getResources(definition));
					}
					return result;
				}
				catch (IOException ex) {
					throw new DefinitionsFactoryException("Cannot load definition URLs", ex);
				}
			}
			else {
				return super.getSourceURLs(applicationContext, contextFactory);
			}
		}

		@Override
		protected BaseLocaleUrlDefinitionDAO instantiateLocaleDefinitionDao(TilesApplicationContext applicationContext,
				TilesRequestContextFactory contextFactory, LocaleResolver resolver) {
			BaseLocaleUrlDefinitionDAO dao = super.instantiateLocaleDefinitionDao(
					applicationContext, contextFactory, resolver);
			if (checkRefresh && dao instanceof CachingLocaleUrlDefinitionDAO) {
				((CachingLocaleUrlDefinitionDAO) dao).setCheckRefresh(checkRefresh);
			}
			return dao;
		}

		@Override
		protected DefinitionsReader createDefinitionsReader(TilesApplicationContext applicationContext,
				TilesRequestContextFactory contextFactory) {
			DigesterDefinitionsReader reader = new DigesterDefinitionsReader();
			if (!validateDefinitions){
				Map<String,String> map = new HashMap<String,String>();
				map.put(DigesterDefinitionsReader.PARSER_VALIDATE_PARAMETER_NAME, Boolean.FALSE.toString());
				reader.init(map);
			}
			return reader;
		}

		@Override
		protected DefinitionsFactory createDefinitionsFactory(TilesApplicationContext applicationContext,
				TilesRequestContextFactory contextFactory, LocaleResolver resolver) {
			if (definitionsFactoryClass != null) {
				DefinitionsFactory factory = BeanUtils.instantiate(definitionsFactoryClass);
				if (factory instanceof TilesApplicationContextAware) {
					((TilesApplicationContextAware) factory).setApplicationContext(applicationContext);
				}
				BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(factory);
				if (bw.isWritableProperty("localeResolver")) {
					bw.setPropertyValue("localeResolver", resolver);
				}
				if (bw.isWritableProperty("definitionDAO")) {
					bw.setPropertyValue("definitionDAO",
							createLocaleDefinitionDao(applicationContext, contextFactory, resolver));
				}
				if (factory instanceof Refreshable) {
					((Refreshable) factory).refresh();
				}
				return factory;
			}
			else {
				return super.createDefinitionsFactory(applicationContext, contextFactory, resolver);
			}
		}

		@Override
		protected PreparerFactory createPreparerFactory(TilesApplicationContext applicationContext,
				TilesRequestContextFactory contextFactory) {
			if (preparerFactoryClass != null) {
				return BeanUtils.instantiate(preparerFactoryClass);
			}
			else {
				return super.createPreparerFactory(applicationContext, contextFactory);
			}
		}

		@Override
		protected LocaleResolver createLocaleResolver(TilesApplicationContext applicationContext,
				TilesRequestContextFactory contextFactory) {
			return new SpringLocaleResolver();
		}

		@Override
		protected AttributeEvaluatorFactory createAttributeEvaluatorFactory(TilesApplicationContext applicationContext,
				TilesRequestContextFactory contextFactory, LocaleResolver resolver) {
			AttributeEvaluator evaluator;
			if (tilesElPresent && JspFactory.getDefaultFactory() != null) {
				evaluator = TilesElActivator.createEvaluator(applicationContext);
			}
			else {
				evaluator = new DirectAttributeEvaluator();
			}
			return new BasicAttributeEvaluatorFactory(evaluator);
		}
	}


	private static class SpringCompleteAutoloadTilesInitializer extends CompleteAutoloadTilesInitializer {

		@Override
		protected AbstractTilesContainerFactory createContainerFactory(TilesApplicationContext context) {
			return new SpringCompleteAutoloadTilesContainerFactory();
		}
	}


	private static class SpringCompleteAutoloadTilesContainerFactory extends CompleteAutoloadTilesContainerFactory {

		@Override
		protected LocaleResolver createLocaleResolver(TilesApplicationContext applicationContext,
				TilesRequestContextFactory contextFactory) {
			return new SpringLocaleResolver();
		}
	}


	private static class TilesElActivator {

		public static AttributeEvaluator createEvaluator(TilesApplicationContext applicationContext) {
			ELAttributeEvaluator evaluator = new ELAttributeEvaluator();
			evaluator.setApplicationContext(applicationContext);
			evaluator.init(Collections.<String, String>emptyMap());
			return evaluator;
		}
	}

}
