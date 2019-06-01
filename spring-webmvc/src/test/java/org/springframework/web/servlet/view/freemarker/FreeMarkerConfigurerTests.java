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

package org.springframework.web.servlet.view.freemarker;

import java.util.HashMap;
import java.util.Properties;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.Test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.ui.freemarker.SpringTemplateLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

/**
 * @author Juergen Hoeller
 * @author Issam El-atif
 */
public class FreeMarkerConfigurerTests {

	@Test
	public void freeMarkerConfigurationFactoryBeanWithConfigLocation() throws Exception {
		FreeMarkerConfigurer freeMarkerConfigurer = new FreeMarkerConfigurer();
		freeMarkerConfigurer.setConfigLocation(new FileSystemResource("myprops.properties"));
		Properties props = new Properties();
		props.setProperty("myprop", "/mydir");
		freeMarkerConfigurer.setFreemarkerSettings(props);
		assertThatIOException().isThrownBy(
				freeMarkerConfigurer::afterPropertiesSet);
	}

	@Test
	public void freeMarkerConfigurationFactoryBeanWithResourceLoaderPath() throws Exception {
		FreeMarkerConfigurer freeMarkerConfigurer = new FreeMarkerConfigurer();
		freeMarkerConfigurer.setTemplateLoaderPath("file:/mydir");
		freeMarkerConfigurer.afterPropertiesSet();
		Configuration cfg = freeMarkerConfigurer.getConfiguration();
		assertThat(cfg.getTemplateLoader()).isInstanceOf(MultiTemplateLoader.class);
		MultiTemplateLoader multiTemplateLoader = (MultiTemplateLoader)cfg.getTemplateLoader();
		assertThat(multiTemplateLoader.getTemplateLoader(0)).isInstanceOf(SpringTemplateLoader.class);
		assertThat(multiTemplateLoader.getTemplateLoader(1)).isInstanceOf(ClassTemplateLoader.class);
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void freeMarkerConfigurationFactoryBeanWithNonFileResourceLoaderPath() throws Exception {
		FreeMarkerConfigurer freeMarkerConfigurer = new FreeMarkerConfigurer();
		freeMarkerConfigurer.setTemplateLoaderPath("file:/mydir");
		Properties settings = new Properties();
		settings.setProperty("localized_lookup", "false");
		freeMarkerConfigurer.setFreemarkerSettings(settings);
		freeMarkerConfigurer.setResourceLoader(new ResourceLoader() {
			@Override
			public Resource getResource(String location) {
				if (!("file:/mydir".equals(location) || "file:/mydir/test".equals(location))) {
					throw new IllegalArgumentException(location);
				}
				return new ByteArrayResource("test".getBytes(), "test");
			}
			@Override
			public ClassLoader getClassLoader() {
				return getClass().getClassLoader();
			}
		});
		freeMarkerConfigurer.afterPropertiesSet();
		assertThat(freeMarkerConfigurer.getConfiguration()).isInstanceOf(Configuration.class);
		Configuration fc = freeMarkerConfigurer.getConfiguration();
		Template ft = fc.getTemplate("test");
		assertThat(FreeMarkerTemplateUtils.processTemplateIntoString(ft, new HashMap())).isEqualTo("test");
	}

	@Test  // SPR-12448
	public void freeMarkerConfigurationAsBean() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		RootBeanDefinition loaderDef = new RootBeanDefinition(SpringTemplateLoader.class);
		loaderDef.getConstructorArgumentValues().addGenericArgumentValue(new DefaultResourceLoader());
		loaderDef.getConstructorArgumentValues().addGenericArgumentValue("/freemarker");
		RootBeanDefinition configDef = new RootBeanDefinition(Configuration.class);
		configDef.getPropertyValues().add("templateLoader", loaderDef);
		beanFactory.registerBeanDefinition("freeMarkerConfig", configDef);
		beanFactory.getBean(Configuration.class);
	}

}
