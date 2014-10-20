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

package org.springframework.web.servlet.config;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;

/**
 * Parse the <mvc:tiles-configurer> MVC namespace element and register
 * TilesConfigurer bean

 * @author Rossen Stoyanchev
 * @since 4.1
 */
public class TilesConfigurerBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	public static final String BEAN_NAME = "mvcTilesConfigurer";


	@Override
	protected String getBeanClassName(Element element) {
		return "org.springframework.web.servlet.view.tiles3.TilesConfigurer";
	}

	@Override
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext) {
		return BEAN_NAME;
	}

	@Override
	protected boolean shouldGenerateId() {
		return super.shouldGenerateId();
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		List<Element> childElements = DomUtils.getChildElementsByTagName(element, "definitions");
		if (!childElements.isEmpty()) {
			List<String> locations = new ArrayList<String>(childElements.size());
			for (Element childElement : childElements) {
				locations.add(childElement.getAttribute("location"));
			}
			builder.addPropertyValue("definitions", locations.toArray(new String[locations.size()]));
		}
		if (element.hasAttribute("check-refresh")) {
			builder.addPropertyValue("checkRefresh", element.getAttribute("check-refresh"));
		}
		if (element.hasAttribute("validate-definitions")) {
			builder.addPropertyValue("validateDefinitions", element.getAttribute("validate-definitions"));
		}
	}

}
