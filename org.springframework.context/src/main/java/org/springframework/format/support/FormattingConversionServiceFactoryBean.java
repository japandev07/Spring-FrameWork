/*
 * Copyright 2002-2009 the original author or authors.
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
package org.springframework.format.support;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.joda.JodaTimeFormattingConfigurer;
import org.springframework.format.number.NumberFormatAnnotationFormatterFactory;
import org.springframework.format.number.NumberFormatter;
import org.springframework.util.ClassUtils;

/**
 * A factory for a FormattingConversionService that installs default formatters for common types such as numbers and datetimes.
 * Subclasses may override {@link #installFormatters(FormatterRegistry)} to register custom formatters.
 * @author Keith Donald
 * @since 3.0
 */
public class FormattingConversionServiceFactoryBean implements FactoryBean<ConversionService>, InitializingBean {

	private static final boolean jodaTimePresent = ClassUtils.isPresent(
			"org.joda.time.DateTime", FormattingConversionService.class.getClassLoader());

	private FormattingConversionService conversionService;

	// implementing InitializingBean
	
	public void afterPropertiesSet() {
		this.conversionService = new FormattingConversionService();
		installFormatters(this.conversionService);
	}
	
	// implementing FactoryBean
	
	public ConversionService getObject() {
		return this.conversionService;
	}

	public Class<ConversionService> getObjectType() {
		return ConversionService.class;
	}

	public boolean isSingleton() {
		return true;
	}

	// subclassing hooks 
	
	/**
	 * Install Formatters and Converters into the new FormattingConversionService using the FormatterRegistry SPI.
	 * Subclasses may override to customize the set of formatters and/or converters that are installed.
	 */
	protected void installFormatters(FormatterRegistry registry) {
		registry.addFormatterForFieldType(Number.class, new NumberFormatter());
		registry.addFormatterForFieldAnnotation(new NumberFormatAnnotationFormatterFactory());		
		if (jodaTimePresent) {
			new JodaTimeFormattingConfigurer().installJodaTimeFormatting(registry);			
		}		
	}

}