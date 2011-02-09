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

package org.springframework.aop.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.ComponentRegistrar;
import org.springframework.beans.factory.parsing.ComponentRegistrarAdapter;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * Utility class for handling registration of auto-proxy creators used internally
 * by the '<code>aop</code>' namespace tags.
 *
 * <p>Only a single auto-proxy creator can be registered and multiple tags may wish
 * to register different concrete implementations. As such this class delegates to
 * {@link AopConfigUtils} which wraps a simple escalation protocol. Therefore classes
 * may request a particular auto-proxy creator and know that class, <i>or a subclass
 * thereof</i>, will eventually be resident in the application context.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.0
 * @see AopConfigUtils
 */
public abstract class AopNamespaceUtils {

	/**
	 * The <code>proxy-target-class</code> attribute as found on AOP-related XML tags.
	 */
	public static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";

	/**
	 * The <code>expose-proxy</code> attribute as found on AOP-related XML tags.
	 */
	private static final String EXPOSE_PROXY_ATTRIBUTE = "expose-proxy";


	/**
	 * @deprecated since Spring 3.1 in favor of
	 * {@link #registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry, ComponentRegistrar, Object, Boolean, Boolean)}
	 */
	@Deprecated
	public static void registerAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, new ComponentRegistrarAdapter(parserContext));
	}

	public static void registerAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, ComponentRegistrar parserContext, Object source, Boolean proxyTargetClass, Boolean exposeProxy) {

		BeanDefinition beanDefinition =
			AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry, source);
		useClassProxyingIfNecessary(registry, proxyTargetClass, exposeProxy);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	public static void registerAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, ComponentRegistrar parserContext, Object source, Boolean proxyTargetClass) {
		registerAutoProxyCreatorIfNecessary(registry, parserContext, source, proxyTargetClass, false);
	}

	public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, new ComponentRegistrarAdapter(parserContext));
	}

	public static void registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, new ComponentRegistrarAdapter(parserContext));
	}

	/**
	 * @deprecated since Spring 2.5, in favor of
	 * {@link #registerAutoProxyCreatorIfNecessary(ParserContext, Element)} and
	 * {@link AopConfigUtils#registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry, Object)}
	 */
	@Deprecated
	public static void registerAutoProxyCreatorIfNecessary(ParserContext parserContext, Object source) {
		BeanDefinition beanDefinition = AopConfigUtils.registerAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), source);
	registerComponentIfNecessary(beanDefinition, new ComponentRegistrarAdapter(parserContext));
	}

	/**
	 * @deprecated since Spring 2.5, in favor of
	 * {@link AopConfigUtils#forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry)}
	 */
	@Deprecated
	public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
		AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
	}


	/**
	 * @deprecated since Spring 3.1 in favor of
	 * {@link #useClassProxyingIfNecessary(BeanDefinitionRegistry, Boolean, Boolean)}
	 * which does not require a parameter of type org.w3c.dom.Element
	 */
	@Deprecated
	private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, Element sourceElement) {
		if (sourceElement != null) {
			boolean proxyTargetClass = Boolean.valueOf(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE));
			if (proxyTargetClass) {
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
			}
			boolean exposeProxy = Boolean.valueOf(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE));
			if (exposeProxy) {
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
			}
		}
	}

	private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, Boolean proxyTargetClass, Boolean exposeProxy) {
		if (proxyTargetClass) {
			AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
		}
		if (exposeProxy) {
			AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
		}
	}

	private static void registerComponentIfNecessary(BeanDefinition beanDefinition, ComponentRegistrar componentRegistrar) {
		if (beanDefinition != null) {
			BeanComponentDefinition componentDefinition =
					new BeanComponentDefinition(beanDefinition, AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME);
			componentRegistrar.registerComponent(componentDefinition);
		}
	}

}
