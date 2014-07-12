/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.web.servlet.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Adding this annotation to an {@code @Configuration} class imports the Spring MVC
 * configuration from {@link WebMvcConfigurationSupport}, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableWebMvc
 * &#064;ComponentScan(basePackageClasses = { MyConfiguration.class })
 * public class MyWebConfiguration {
 *
 * }
 * </pre>
 *
 * <p>As of 4.1 this annotation may also import {@link WebMvcFreeMarkerConfiguration},
 * {@link WebMvcVelocityConfiguration}, or {@link WebMvcTilesConfiguration} if
 * those libraries are found on the classpath.
 *
 * <p>To customize the imported configuration, implement the interface
 * {@link WebMvcConfigurer} or more likely extend the empty method base class
 * {@link WebMvcConfigurerAdapter} and override individual methods, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableWebMvc
 * &#064;ComponentScan(basePackageClasses = { MyConfiguration.class })
 * public class MyConfiguration extends WebMvcConfigurerAdapter {
 *
 * 	&#064;Override
 * 	public void addFormatters(FormatterRegistry formatterRegistry) {
 * 		formatterRegistry.addConverter(new MyConverter());
 * 	}
 *
 * 	&#064;Override
 * 	public void configureMessageConverters(List&lt;HttpMessageConverter&lt;?&gt;&gt; converters) {
 * 		converters.add(new MyHttpMessageConverter());
 * 	}
 *
 * 	// More overridden methods ...
 * }
 * </pre>
 *
 * <p>To customize the FreeMarker, Velocity, or Tiles configuration, additionally
 * implement {@link FreeMarkerWebMvcConfigurer}, {@link VelocityWebMvcConfigurer},
 * and/or {@link TilesWebMvcConfigurer}.
 *
 * <p>If {@link WebMvcConfigurer} does not expose some advanced setting that
 * needs to be configured, consider removing the {@code @EnableWebMvc}
 * annotation and extending directly from {@link WebMvcConfigurationSupport}, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;ComponentScan(basePackageClasses = { MyConfiguration.class })
 * public class MyConfiguration extends WebMvcConfigurationSupport {
 *
 * 	&#064;Override
 *	public void addFormatters(FormatterRegistry formatterRegistry) {
 *		formatterRegistry.addConverter(new MyConverter());
 *	}
 *
 *	&#064;Bean
 *	public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
 *		// Create or delegate to "super" to create and
 *		// customize properties of RequestMapingHandlerAdapter
 *	}
 * }
 * </pre>
 *
 * <p>When the {@code @EnableWebMvc} annotation is removed, the FreeMarker,
 * Velocity, and Tiles configuration is no longer automatically imported and need
 * to be imported explicitly.
 *
 * @author Dave Syer
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 * @since 3.1
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurer
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
 * @see org.springframework.web.servlet.config.annotation.FreeMarkerWebMvcConfigurer
 * @see org.springframework.web.servlet.config.annotation.VelocityWebMvcConfigurer
 * @see org.springframework.web.servlet.config.annotation.TilesWebMvcConfigurer
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({DelegatingWebMvcConfiguration.class, ViewConfigurationImportSelector.class})
public @interface EnableWebMvc {
}
