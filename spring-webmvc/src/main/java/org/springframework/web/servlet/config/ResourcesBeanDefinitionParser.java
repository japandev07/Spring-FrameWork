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

import java.util.Arrays;
import java.util.Map;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter;
import org.springframework.web.servlet.resource.CachingResourceResolver;
import org.springframework.web.servlet.resource.CachingResourceTransformer;
import org.springframework.web.servlet.resource.ContentVersionStrategy;
import org.springframework.web.servlet.resource.CssLinkResourceTransformer;
import org.springframework.web.servlet.resource.FixedVersionStrategy;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser} that parses a
 * {@code resources} element to register a {@link ResourceHttpRequestHandler} and
 * register a {@link SimpleUrlHandlerMapping} for mapping resource requests,
 * and a {@link HttpRequestHandlerAdapter}.
 * Will also create a resource handling chain with {@link ResourceResolver}s and {@link ResourceTransformer}s.
 *
 * @author Keith Donald
 * @author Jeremy Grelle
 * @author Brian Clozel
 * @since 3.0.4
 */
class ResourcesBeanDefinitionParser implements BeanDefinitionParser {

	private static final String RESOURCE_CHAIN_CACHE = "spring-resource-chain-cache";
	private static final String VERSION_RESOLVER_ELEMENT = "version-resolver";
	private static final String VERSION_STRATEGY_ELEMENT = "version-strategy";
	private static final String FIXED_VERSION_STRATEGY_ELEMENT = "fixed-version-strategy";
	private static final String CONTENT_VERSION_STRATEGY_ELEMENT = "content-version-strategy";

	@Override
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		Object source = parserContext.extractSource(element);

		String resourceHandlerName = registerResourceHandler(parserContext, element, source);
		if (resourceHandlerName == null) {
			return null;
		}

		Map<String, String> urlMap = new ManagedMap<String, String>();
		String resourceRequestPath = element.getAttribute("mapping");
		if (!StringUtils.hasText(resourceRequestPath)) {
			parserContext.getReaderContext().error("The 'mapping' attribute is required.", parserContext.extractSource(element));
			return null;
		}
		urlMap.put(resourceRequestPath, resourceHandlerName);

		RuntimeBeanReference pathMatcherRef = MvcNamespaceUtils.registerPathMatcher(null, parserContext, source);
		RuntimeBeanReference pathHelperRef = MvcNamespaceUtils.registerUrlPathHelper(null, parserContext, source);

		RootBeanDefinition handlerMappingDef = new RootBeanDefinition(SimpleUrlHandlerMapping.class);
		handlerMappingDef.setSource(source);
		handlerMappingDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		handlerMappingDef.getPropertyValues().add("urlMap", urlMap);
		handlerMappingDef.getPropertyValues().add("pathMatcher", pathMatcherRef).add("urlPathHelper", pathHelperRef);

		String order = element.getAttribute("order");
		// use a default of near-lowest precedence, still allowing for even lower precedence in other mappings
		handlerMappingDef.getPropertyValues().add("order", StringUtils.hasText(order) ? order : Ordered.LOWEST_PRECEDENCE - 1);

		String beanName = parserContext.getReaderContext().generateBeanName(handlerMappingDef);
		parserContext.getRegistry().registerBeanDefinition(beanName, handlerMappingDef);
		parserContext.registerComponent(new BeanComponentDefinition(handlerMappingDef, beanName));

		// Ensure BeanNameUrlHandlerMapping (SPR-8289) and default HandlerAdapters are not "turned off"
		// Register HttpRequestHandlerAdapter
		MvcNamespaceUtils.registerDefaultComponents(parserContext, source);

		return null;
	}

	private String registerResourceHandler(ParserContext parserContext, Element element, Object source) {
		String locationAttr = element.getAttribute("location");
		if (!StringUtils.hasText(locationAttr)) {
			parserContext.getReaderContext().error("The 'location' attribute is required.", parserContext.extractSource(element));
			return null;
		}

		ManagedList<String> locations = new ManagedList<String>();
		locations.addAll(Arrays.asList(StringUtils.commaDelimitedListToStringArray(locationAttr)));

		RootBeanDefinition resourceHandlerDef = new RootBeanDefinition(ResourceHttpRequestHandler.class);
		resourceHandlerDef.setSource(source);
		resourceHandlerDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		resourceHandlerDef.getPropertyValues().add("locations", locations);

		String cacheSeconds = element.getAttribute("cache-period");
		if (StringUtils.hasText(cacheSeconds)) {
			resourceHandlerDef.getPropertyValues().add("cacheSeconds", cacheSeconds);
		}

		Element resourceChainElement = DomUtils.getChildElementByTagName(element, "resource-chain");
		if(resourceChainElement != null) {
			parseResourceChain(resourceHandlerDef, parserContext, resourceChainElement, source);
		}

		String beanName = parserContext.getReaderContext().generateBeanName(resourceHandlerDef);
		parserContext.getRegistry().registerBeanDefinition(beanName, resourceHandlerDef);
		parserContext.registerComponent(new BeanComponentDefinition(resourceHandlerDef, beanName));
		return beanName;
	}


	private void parseResourceChain(RootBeanDefinition resourceHandlerDef, ParserContext parserContext,
			Element element, Object source) {

		String autoRegistration = element.getAttribute("auto-registration");
		boolean isAutoRegistration = !(StringUtils.hasText(autoRegistration) && "false".equals(autoRegistration));

		ManagedList<? super Object> resourceResolvers = new ManagedList<Object>();
		resourceResolvers.setSource(source);
		ManagedList<? super Object> resourceTransformers = new ManagedList<Object>();
		resourceTransformers.setSource(source);

		parseResourceCache(resourceResolvers, resourceTransformers, element, source);
		parseResourceResolversTransformers(isAutoRegistration, resourceResolvers
				, resourceTransformers, parserContext, element, source);

		if (!resourceResolvers.isEmpty()) {
			resourceHandlerDef.getPropertyValues().add("resourceResolvers", resourceResolvers);
		}
		if (!resourceTransformers.isEmpty()) {
			resourceHandlerDef.getPropertyValues().add("resourceTransformers", resourceTransformers);
		}
	}

	private void parseResourceCache(ManagedList<? super Object> resourceResolvers,
			ManagedList<? super Object> resourceTransformers,
			Element element, Object source) {

		Element resourceCacheElement = DomUtils.getChildElementByTagName(element, "resource-cache");
		if(resourceCacheElement != null) {
			ConstructorArgumentValues cavs = new ConstructorArgumentValues();

			RootBeanDefinition cachingResolverDef = new RootBeanDefinition(CachingResourceResolver.class);
			cachingResolverDef.setSource(source);
			cachingResolverDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			cachingResolverDef.setConstructorArgumentValues(cavs);

			RootBeanDefinition cachingTransformerDef = new RootBeanDefinition(CachingResourceTransformer.class);
			cachingTransformerDef.setSource(source);
			cachingTransformerDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			cachingTransformerDef.setConstructorArgumentValues(cavs);

			String cacheManagerName = resourceCacheElement.getAttribute("cache-manager");
			String cacheName = resourceCacheElement.getAttribute("cache-name");
			if (StringUtils.hasText(cacheManagerName) && StringUtils.hasText(cacheName)) {
				RuntimeBeanReference cacheManagerRef = new RuntimeBeanReference(cacheManagerName);
				cavs.addIndexedArgumentValue(0, cacheManagerRef);
				cavs.addIndexedArgumentValue(1, cacheName);
			}
			else {
				ConstructorArgumentValues cacheCavs = new ConstructorArgumentValues();
				cacheCavs.addIndexedArgumentValue(0, RESOURCE_CHAIN_CACHE);

				RootBeanDefinition cacheDef = new RootBeanDefinition(ConcurrentMapCache.class);
				cacheDef.setSource(source);
				cacheDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				cacheDef.setConstructorArgumentValues(cacheCavs);
				cavs.addIndexedArgumentValue(0, cacheDef);
			}
			resourceResolvers.add(cachingResolverDef);
			resourceTransformers.add(cachingTransformerDef);
		}
	}


	private void parseResourceResolversTransformers(boolean isAutoRegistration,
			ManagedList<? super Object> resourceResolvers, ManagedList<? super Object> resourceTransformers,
			ParserContext parserContext, Element element, Object source) {

		Element resolversElement = DomUtils.getChildElementByTagName(element, "resolvers");
		if (resolversElement != null) {
			for (Element beanElement : DomUtils.getChildElements(resolversElement)) {
				if(VERSION_RESOLVER_ELEMENT.equals(beanElement.getLocalName())) {
					RootBeanDefinition versionResolverDef = parseVersionResolver(parserContext, beanElement, source);
					versionResolverDef.setSource(source);
					resourceResolvers.add(versionResolverDef);

					if(isAutoRegistration) {
						RootBeanDefinition cssLinkTransformerDef = new RootBeanDefinition(CssLinkResourceTransformer.class);
						cssLinkTransformerDef.setSource(source);
						cssLinkTransformerDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
						resourceTransformers.add(cssLinkTransformerDef);
					}
				} else {
					Object object = parserContext.getDelegate().parsePropertySubElement(beanElement, null);
					resourceResolvers.add(object);
				}
			}
		}

		if(isAutoRegistration) {
			RootBeanDefinition pathResolverDef = new RootBeanDefinition(PathResourceResolver.class);
			pathResolverDef.setSource(source);
			pathResolverDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			resourceResolvers.add(pathResolverDef);
		}

		Element transformersElement = DomUtils.getChildElementByTagName(element, "transformers");
		if (transformersElement != null) {
			for (Element beanElement : DomUtils.getChildElementsByTagName(transformersElement, "bean", "ref")) {
				Object object = parserContext.getDelegate().parsePropertySubElement(beanElement, null);
				resourceTransformers.add(object);
			}
		}
	}

	private RootBeanDefinition parseVersionResolver(ParserContext parserContext, Element element, Object source) {
		ManagedMap<String, ? super Object> strategyMap = new ManagedMap<String, Object>();
		strategyMap.setSource(source);
		RootBeanDefinition versionResolverDef = new RootBeanDefinition(VersionResourceResolver.class);
		versionResolverDef.setSource(source);
		versionResolverDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		versionResolverDef.getPropertyValues().addPropertyValue("strategyMap", strategyMap);

		for (Element beanElement : DomUtils.getChildElements(element)) {
			String[] patterns = StringUtils.commaDelimitedListToStringArray(beanElement.getAttribute("patterns"));
			Object strategy = null;
			if(FIXED_VERSION_STRATEGY_ELEMENT.equals(beanElement.getLocalName())) {
				ConstructorArgumentValues cavs = new ConstructorArgumentValues();
				cavs.addIndexedArgumentValue(0, beanElement.getAttribute("version"));
				RootBeanDefinition strategyDef = new RootBeanDefinition(FixedVersionStrategy.class);
				strategyDef.setSource(source);
				strategyDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				strategyDef.setConstructorArgumentValues(cavs);
				strategy = strategyDef;
			}
			else if (CONTENT_VERSION_STRATEGY_ELEMENT.equals(beanElement.getLocalName())) {
				RootBeanDefinition strategyDef = new RootBeanDefinition(ContentVersionStrategy.class);
				strategyDef.setSource(source);
				strategyDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				strategy = strategyDef;
			}
			else if (VERSION_STRATEGY_ELEMENT.equals(beanElement.getLocalName())) {
				Element childElement = DomUtils.getChildElementsByTagName(beanElement, "bean", "ref").get(0);
				strategy = parserContext.getDelegate().parsePropertySubElement(childElement, null);
			}
			for(String pattern : patterns) {
				strategyMap.put(pattern, strategy);
			}
		}

		return versionResolverDef;
	}
}
