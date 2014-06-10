/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.method.annotation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.EmptyTargetSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PathMatcher;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.support.CompositeUriComponentsContributor;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A UriComponentsBuilder that helps to build URIs to Spring MVC controllers
 * and methods from their request mappings.
 *
 * @author Oliver Gierke
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class MvcUriComponentsBuilder extends UriComponentsBuilder {

	/**
	 * Well-known name for the {@link CompositeUriComponentsContributor} object in the bean factory.
	 */
	public static final String MVC_URI_COMPONENTS_CONTRIBUTOR_BEAN_NAME = "mvcUriComponentsContributor";


	private static final Log logger = LogFactory.getLog(MvcUriComponentsBuilder.class);

	private static final ObjenesisStd objenesis = new ObjenesisStd(true);

	private static final PathMatcher pathMatcher = new AntPathMatcher();

	private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	private static final CompositeUriComponentsContributor defaultUriComponentsContributor;

	static {
		defaultUriComponentsContributor = new CompositeUriComponentsContributor(
				new PathVariableMethodArgumentResolver(), new RequestParamMethodArgumentResolver(false));
	}


	/**
	 * Create a {@link UriComponentsBuilder} from the mapping of a controller class
	 * and current request information including Servlet mapping. If the controller
	 * contains multiple mappings, only the first one is used.
	 * @param controllerType the controller to build a URI for
	 * @return a UriComponentsBuilder instance (never {@code null})
	 */
	public static UriComponentsBuilder fromController(Class<?> controllerType) {
		String mapping = getTypeRequestMapping(controllerType);
		return ServletUriComponentsBuilder.fromCurrentServletMapping().path(mapping);
	}

	private static String getTypeRequestMapping(Class<?> controllerType) {
		Assert.notNull(controllerType, "'controllerType' must not be null");
		RequestMapping annot = AnnotationUtils.findAnnotation(controllerType, RequestMapping.class);
		if (annot == null || ObjectUtils.isEmpty(annot.value()) || StringUtils.isEmpty(annot.value()[0])) {
			return "/";
		}
		if (annot.value().length > 1 && logger.isWarnEnabled()) {
			logger.warn("Multiple paths on controller " + controllerType.getName() + ", using first one");
		}
		return annot.value()[0];
	}

	/**
	 * Create a {@link UriComponentsBuilder} from the mapping of a controller
	 * method and an array of method argument values. This method delegates
	 * to {@link #fromMethod(java.lang.reflect.Method, Object...)}.
	 * @param controllerType the controller
	 * @param methodName the method name
	 * @param argumentValues the argument values
	 * @return a UriComponentsBuilder instance, never {@code null}
	 * @throws IllegalArgumentException if there is no matching or
	 * if there is more than one matching method
	 */
	public static UriComponentsBuilder fromMethodName(Class<?> controllerType, String methodName, Object... argumentValues) {
		Method method = getMethod(controllerType, methodName, argumentValues);
		return fromMethod(method, argumentValues);
	}

	private static Method getMethod(Class<?> controllerType, String methodName, Object... argumentValues) {
		Method match = null;
		for (Method method : controllerType.getDeclaredMethods()) {
			if (method.getName().equals(methodName) && method.getParameterTypes().length == argumentValues.length) {
				if (match != null) {
					throw new IllegalArgumentException("Found two methods named '" + methodName + "' having " +
							Arrays.asList(argumentValues) + " arguments, controller " + controllerType.getName());
				}
				match = method;
			}
		}
		if (match == null) {
			throw new IllegalArgumentException("No method '" + methodName + "' with " + argumentValues.length +
					" parameters found in " + controllerType.getName());
		}
		return match;
	}

	/**
	 * Create a {@link UriComponentsBuilder} by invoking a "mock" controller method.
	 * The controller method and the supplied argument values are then used to
	 * delegate to {@link #fromMethod(java.lang.reflect.Method, Object...)}.
	 * <p>For example, given this controller:
	 * <pre class="code">
	 * &#064;RequestMapping("/people/{id}/addresses")
	 * class AddressController {
	 *
	 *   &#064;RequestMapping("/{country}")
	 *   public HttpEntity<Void> getAddressesForCountry(&#064;PathVariable String country) { ... }
	 *
	 *   &#064;RequestMapping(value="/", method=RequestMethod.POST)
	 *   public void addAddress(Address address) { ... }
	 * }
	 * </pre>
	 * A UriComponentsBuilder can be created:
	 * <pre class="code">
	 * // Inline style with static import of "MvcUriComponentsBuilder.on"
	 *
	 * MvcUriComponentsBuilder.fromMethodCall(
	 * 		on(CustomerController.class).showAddresses("US")).buildAndExpand(1);
	 *
	 * // Longer form useful for repeated invocation (and void controller methods)
	 *
	 * CustomerController controller = MvcUriComponentsBuilder.on(CustomController.class);
	 * controller.addAddress(null);
	 * builder = MvcUriComponentsBuilder.fromMethodCall(controller);
	 * controller.getAddressesForCountry("US")
	 * builder = MvcUriComponentsBuilder.fromMethodCall(controller);
	 * </pre>
	 * @param invocationInfo either the value returned from a "mock" controller
	 * invocation or the "mock" controller itself after an invocation
	 * @return a UriComponents instance
	 */
	public static UriComponentsBuilder fromMethodCall(Object invocationInfo) {
		Assert.isInstanceOf(MethodInvocationInfo.class, invocationInfo);
		MethodInvocationInfo info = (MethodInvocationInfo) invocationInfo;
		return fromMethod(info.getControllerMethod(), info.getArgumentValues());
	}

	/**
	 * Create a URL from the name of a Spring MVC controller method's request mapping.
	 *
	 * <p>The configured
	 * {@link org.springframework.web.servlet.handler.HandlerMethodMappingNamingStrategy
	 * HandlerMethodMappingNamingStrategy} determines the names of controller
	 * method request mappings at startup. By default all mappings are assigned
	 * a name based on the capital letters of the class name, followed by "#" as
	 * separator, and then the method name. For example "PC#getPerson"
	 * for a class named PersonController with method getPerson. In case the
	 * naming convention does not produce unique results, an explicit name may
	 * be assigned through the name attribute of the {@code @RequestMapping}
	 * annotation.
	 *
	 * <p>This is aimed primarily for use in view rendering technologies and EL
	 * expressions. The Spring URL tag library registers this method as a function
	 * called "mvcUrl".
	 *
	 * <p>For example, given this controller:
	 * <pre class="code">
	 * &#064;RequestMapping("/people")
	 * class PersonController {
	 *
	 *   &#064;RequestMapping("/{id}")
	 *   public HttpEntity<Void> getPerson(&#064;PathVariable String id) { ... }
	 *
	 * }
	 * </pre>
	 *
	 * A JSP can prepare a URL to the controller method as follows:
	 *
	 * <pre class="code">
	 * <%@ taglib uri="http://www.springframework.org/tags" prefix="s" %>
	 *
	 * &lt;a href="${s:mvcUrl('PC#getPerson').arg(0,"123").build()}"&gt;Get Person&lt;/a&gt;
	 * </pre>
	 *
	 * <p>Note that it's not necessary to specify all arguments. Only the ones
	 * required to prepare the URL, mainly {@code @RequestParam} and {@code @PathVariable}).
	 *
	 * @param mappingName the mapping name
	 * @return a builder to to prepare the URI String
	 * @throws IllegalArgumentException if the mapping name is not found or
	 * if there is no unique match
	 * @since 4.1
	 */
	public static MethodArgumentBuilder fromMappingName(String mappingName) {
		RequestMappingInfoHandlerMapping handlerMapping = getRequestMappingInfoHandlerMapping();
		List<HandlerMethod> handlerMethods = handlerMapping.getHandlerMethodsForMappingName(mappingName);
		if (handlerMethods == null) {
			throw new IllegalArgumentException("Mapping mappingName not found: " + mappingName);
		}
		if (handlerMethods.size() != 1) {
			throw new IllegalArgumentException(
					"No unique match for mapping mappingName " + mappingName + ": " + handlerMethods);
		}
		return new MethodArgumentBuilder(handlerMethods.get(0).getMethod());
	}

	/**
	 * Create a {@link UriComponentsBuilder} from the mapping of a controller method
	 * and an array of method argument values. The array of values  must match the
	 * signature of the controller method. Values for {@code @RequestParam} and
	 * {@code @PathVariable} are used for building the URI (via implementations of
	 * {@link org.springframework.web.method.support.UriComponentsContributor})
	 * while remaining argument values are ignored and can be {@code null}.
	 * @param method the controller method
	 * @param argumentValues argument values for the controller method
	 * @return a UriComponentsBuilder instance, never {@code null}
	 */
	public static UriComponentsBuilder fromMethod(Method method, Object... argumentValues) {
		String typePath = getTypeRequestMapping(method.getDeclaringClass());
		String methodPath = getMethodRequestMapping(method);
		String path = pathMatcher.combine(typePath, methodPath);

		UriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentServletMapping().path(path);
		UriComponents uriComponents = applyContributors(builder, method, argumentValues);
		return ServletUriComponentsBuilder.newInstance().uriComponents(uriComponents);
	}

	private static String getMethodRequestMapping(Method method) {
		RequestMapping annot = AnnotationUtils.findAnnotation(method, RequestMapping.class);
		if (annot == null) {
			throw new IllegalArgumentException("No @RequestMapping on: " + method.toGenericString());
		}
		if (ObjectUtils.isEmpty(annot.value()) || StringUtils.isEmpty(annot.value()[0])) {
			return "/";
		}
		if (annot.value().length > 1 && logger.isWarnEnabled()) {
			logger.warn("Multiple paths on method " + method.toGenericString() + ", using first one");
		}
		return annot.value()[0];
	}

	private static UriComponents applyContributors(UriComponentsBuilder builder, Method method, Object... args) {
		CompositeUriComponentsContributor contributor = getConfiguredUriComponentsContributor();
		if (contributor == null) {
			logger.debug("Using default CompositeUriComponentsContributor");
			contributor = defaultUriComponentsContributor;
		}

		int paramCount = method.getParameterTypes().length;
		int argCount = args.length;
		if (paramCount != argCount) {
			throw new IllegalArgumentException("Number of method parameters " + paramCount +
					" does not match number of argument values " + argCount);
		}

		final Map<String, Object> uriVars = new HashMap<String, Object>();
		for (int i = 0; i < paramCount; i++) {
			MethodParameter param = new MethodParameter(method, i);
			param.initParameterNameDiscovery(parameterNameDiscoverer);
			contributor.contributeMethodArgument(param, args[i], builder, uriVars);
		}

		// We may not have all URI var values, expand only what we have
		return builder.build().expand(new UriComponents.UriTemplateVariables() {
			@Override
			public Object getValue(String name) {
				return uriVars.containsKey(name) ? uriVars.get(name) : UriComponents.UriTemplateVariables.SKIP_VALUE;
			}
		});
	}

	protected static CompositeUriComponentsContributor getConfiguredUriComponentsContributor() {
		WebApplicationContext wac = getWebApplicationContext();
		if (wac == null) {
			return null;
		}
		try {
			return wac.getBean(MVC_URI_COMPONENTS_CONTRIBUTOR_BEAN_NAME, CompositeUriComponentsContributor.class);
		}
		catch (NoSuchBeanDefinitionException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("No CompositeUriComponentsContributor bean with name '" +
						MVC_URI_COMPONENTS_CONTRIBUTOR_BEAN_NAME + "'");
			}
			return null;
		}
	}

	protected static RequestMappingInfoHandlerMapping getRequestMappingInfoHandlerMapping() {
		WebApplicationContext wac = getWebApplicationContext();
		Assert.notNull(wac, "Cannot lookup handler method mappings without WebApplicationContext");
		try {
			return wac.getBean(RequestMappingInfoHandlerMapping.class);
		}
		catch (NoUniqueBeanDefinitionException ex) {
			throw new IllegalStateException("More than one RequestMappingInfoHandlerMapping beans found", ex);
		}
		catch (NoSuchBeanDefinitionException ex) {
			throw new IllegalStateException("No RequestMappingInfoHandlerMapping bean", ex);
		}
	}

	private static WebApplicationContext getWebApplicationContext() {
		RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
		if (requestAttributes == null) {
			logger.debug("No request bound to the current thread: is DispatcherSerlvet used?");
			return null;
		}

		HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
		if (request == null) {
			logger.debug("Request bound to current thread is not an HttpServletRequest");
			return null;
		}

		String attributeName = DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE;
		WebApplicationContext wac = (WebApplicationContext) request.getAttribute(attributeName);
		if (wac == null) {
			logger.debug("No WebApplicationContext found: not in a DispatcherServlet request?");
			return null;
		}
		return wac;
	}

	/**
	 * Return a "mock" controller instance. When an {@code @RequestMapping} method
	 * on the controller is invoked, the supplied argument values are remembered
	 * and the result can then be used to create a {@code UriComponentsBuilder}
	 * via {@link #fromMethodCall(Object)}.
	 * <p>
	 * Note that this is a shorthand version of {@link #controller(Class)} intended
	 * for inline use (with a static import), for example:
	 * <pre class="code">
	 * MvcUriComponentsBuilder.fromMethodCall(on(FooController.class).getFoo(1)).build();
	 * </pre>
	 * @param controllerType the target controller
	 */
	public static <T> T on(Class<T> controllerType) {
		return controller(controllerType);
	}

	/**
	 * Return a "mock" controller instance. When an {@code @RequestMapping} method
	 * on the controller is invoked, the supplied argument values are remembered
	 * and the result can then be used to create {@code UriComponentsBuilder} via
	 * {@link #fromMethodCall(Object)}.
	 * <p>
	 * This is a longer version of {@link #on(Class)}. It is needed with controller
	 * methods returning void as well for repeated invocations.
	 * <pre class="code">
	 * FooController fooController = controller(FooController.class);
	 *
	 * fooController.saveFoo(1, null);
	 * builder = MvcUriComponentsBuilder.fromMethodCall(fooController);
	 *
	 * fooController.saveFoo(2, null);
	 * builder = MvcUriComponentsBuilder.fromMethodCall(fooController);
	 * </pre>
	 * @param controllerType the target controller
	 */
	public static <T> T controller(Class<T> controllerType) {
		Assert.notNull(controllerType, "'controllerType' must not be null");
		return initProxy(controllerType, new ControllerMethodInvocationInterceptor());
	}

	@SuppressWarnings("unchecked")
	private static <T> T initProxy(Class<?> type, ControllerMethodInvocationInterceptor interceptor) {
		if (type.isInterface()) {
			ProxyFactory factory = new ProxyFactory(EmptyTargetSource.INSTANCE);
			factory.addInterface(type);
			factory.addInterface(MethodInvocationInfo.class);
			factory.addAdvice(interceptor);
			return (T) factory.getProxy();
		}
		else {
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(type);
			enhancer.setInterfaces(new Class<?>[] {MethodInvocationInfo.class});
			enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
			enhancer.setCallbackType(org.springframework.cglib.proxy.MethodInterceptor.class);
			Factory factory = (Factory) objenesis.newInstance(enhancer.createClass());
			factory.setCallbacks(new Callback[] {interceptor});
			return (T) factory;
		}
	}


	private static class ControllerMethodInvocationInterceptor
			implements org.springframework.cglib.proxy.MethodInterceptor, MethodInterceptor {

		private static final Method getControllerMethod =
				ReflectionUtils.findMethod(MethodInvocationInfo.class, "getControllerMethod");

		private static final Method getArgumentValues =
				ReflectionUtils.findMethod(MethodInvocationInfo.class, "getArgumentValues");

		private Method controllerMethod;

		private Object[] argumentValues;


		@Override
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) {
			if (getControllerMethod.equals(method)) {
				return this.controllerMethod;
			}
			else if (getArgumentValues.equals(method)) {
				return this.argumentValues;
			}
			else if (ReflectionUtils.isObjectMethod(method)) {
				return ReflectionUtils.invokeMethod(method, obj, args);
			}
			else {
				this.controllerMethod = method;
				this.argumentValues = args;
				Class<?> returnType = method.getReturnType();
				return (void.class.equals(returnType) ? null : returnType.cast(initProxy(returnType, this)));
			}
		}

		@Override
		public Object invoke(org.aopalliance.intercept.MethodInvocation inv) throws Throwable {
			return intercept(inv.getThis(), inv.getMethod(), inv.getArguments(), null);
		}
	}


	public interface MethodInvocationInfo {

		Method getControllerMethod();

		Object[] getArgumentValues();
	}


	public static class MethodArgumentBuilder {

		private final Method method;

		private final Object[] argumentValues;


		public MethodArgumentBuilder(Method method) {
			Assert.notNull(method, "'method' is required");
			this.method = method;
			this.argumentValues = new Object[method.getParameterTypes().length];
			for (int i = 0; i < this.argumentValues.length; i++) {
				this.argumentValues[i] = null;
			}
		}

		public MethodArgumentBuilder arg(int index, Object value) {
			this.argumentValues[index] = value;
			return this;
		}

		public String build() {
			return MvcUriComponentsBuilder.fromMethod(this.method, this.argumentValues)
					.build(false).encode().toUriString();
		}

		public String buildAndExpand(Object... uriVariables) {
			return MvcUriComponentsBuilder.fromMethod(this.method, this.argumentValues)
					.build(false).expand(uriVariables).encode().toString();
		}
	}

}
