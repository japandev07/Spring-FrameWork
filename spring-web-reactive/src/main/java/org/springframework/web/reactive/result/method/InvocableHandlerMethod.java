/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.reactive.result.method;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import reactor.core.publisher.Mono;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.ui.ModelMap;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.server.ServerWebExchange;

/**
 * A sub-class of {@link HandlerMethod} that can resolve method arguments from
 * a {@link ServerWebExchange} and use that to invoke the underlying method.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class InvocableHandlerMethod extends HandlerMethod {

	private static final Mono<Object[]> EMPTY_ARGS = Mono.just(new Object[0]);

	private static final Object NO_ARG_VALUE = new Object();


	private List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


	public InvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}

	public InvocableHandlerMethod(Object bean, Method method) {
		super(bean, method);
	}


	/**
	 * Configure the argument resolvers to use to use for resolving method
	 * argument values against a {@code ServerWebExchange}.
	 */
	public void setArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		this.resolvers.clear();
		this.resolvers.addAll(resolvers);
	}

	/**
	 * Set the ParameterNameDiscoverer for resolving parameter names when needed
	 * (e.g. default request attribute name).
	 * <p>Default is a {@link DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer nameDiscoverer) {
		this.parameterNameDiscoverer = nameDiscoverer;
	}


	/**
	 * Invoke the method for the given exchange.
	 *
	 * @param exchange the current exchange
	 * @param bindingContext the binding context to use
	 * @param providedArgs optional list of argument values to match by type
	 * @return Mono with a {@link HandlerResult}.
	 */
	public Mono<HandlerResult> invoke(ServerWebExchange exchange,
			BindingContext bindingContext, Object... providedArgs) {

		return resolveArguments(exchange, bindingContext, providedArgs).then(args -> {
			try {
				Object value = doInvoke(args);
				ModelMap model = bindingContext.getModel();
				HandlerResult handlerResult = new HandlerResult(this, value, getReturnType(), model);
				return Mono.just(handlerResult);
			}
			catch (InvocationTargetException ex) {
				return Mono.error(ex.getTargetException());
			}
			catch (Throwable ex) {
				String msg = getInvocationErrorMessage(args);
				return Mono.error(new IllegalStateException(msg));
			}
		});
	}

	private Mono<Object[]> resolveArguments(ServerWebExchange exchange,
			BindingContext bindingContext, Object... providedArgs) {

		if (ObjectUtils.isEmpty(getMethodParameters())) {
			return EMPTY_ARGS;
		}
		try {
			List<Mono<Object>> argMonos = Stream.of(getMethodParameters())
					.map(param -> {
						param.initParameterNameDiscovery(this.parameterNameDiscoverer);
						GenericTypeResolver.resolveParameterType(param, getBean().getClass());
						return findProvidedArg(param, providedArgs)
								.map(Mono::just)
								.orElseGet(() -> {
									HandlerMethodArgumentResolver resolver = findResolver(param);
									return resolveArg(resolver, param, bindingContext, exchange);
								});

					})
					.collect(Collectors.toList());

			// Create Mono with array of resolved values...
			return Mono.when(argMonos, argValues ->
					Stream.of(argValues).map(o -> o != NO_ARG_VALUE ? o : null).toArray());
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}
	}

	private Optional<Object> findProvidedArg(MethodParameter param, Object... providedArgs) {
		if (ObjectUtils.isEmpty(providedArgs)) {
			return Optional.empty();
		}
		return Arrays.stream(providedArgs)
				.filter(arg -> param.getParameterType().isInstance(arg))
				.findFirst();
	}

	private HandlerMethodArgumentResolver findResolver(MethodParameter param) {
		return this.resolvers.stream()
				.filter(r -> r.supportsParameter(param))
				.findFirst()
				.orElseThrow(() -> getArgumentError("No resolver for ", param, null));
	}

	private Mono<Object> resolveArg(HandlerMethodArgumentResolver resolver, MethodParameter param,
			BindingContext bindingContext, ServerWebExchange exchange) {

		try {
			return resolver.resolveArgument(param, bindingContext, exchange)
					.defaultIfEmpty(NO_ARG_VALUE)
					.doOnError(cause -> {
						if(logger.isDebugEnabled()) {
							logger.debug(getDetailedErrorMessage("Error resolving ", param), cause);
						}
					});
		}
		catch (Exception ex) {
			throw getArgumentError("Error resolving ", param, ex);
		}
	}

	private IllegalStateException getArgumentError(String message, MethodParameter param, Throwable ex) {
		return new IllegalStateException(getDetailedErrorMessage(message, param), ex);
	}

	private String getDetailedErrorMessage(String message, MethodParameter param) {
		return message + "argument [" + param.getParameterIndex() + "] " +
				"of type [" + param.getParameterType().getName() + "] " +
				"on method [" + getBridgedMethod().toGenericString() + "]";
	}

	private Object doInvoke(Object[] args) throws Exception {
		if (logger.isTraceEnabled()) {
			logger.trace("Invoking '" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType()) +
					"' with arguments " + Arrays.toString(args));
		}
		ReflectionUtils.makeAccessible(getBridgedMethod());
		Object returnValue = getBridgedMethod().invoke(getBean(), args);
		if (logger.isTraceEnabled()) {
			logger.trace("Method [" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType()) +
					"] returned [" + returnValue + "]");
		}
		return returnValue;
	}

	private String getInvocationErrorMessage(Object[] args) {
		String argumentDetails = IntStream.range(0, args.length)
				.mapToObj(i -> (args[i] != null ?
						"[" + i + "][type=" + args[i].getClass().getName() + "][value=" + args[i] + "]" :
						"[" + i + "][null]"))
				.collect(Collectors.joining(",", " ", " "));
		return "Failed to invoke controller with resolved arguments:" + argumentDetails +
				"on method [" + getBridgedMethod().toGenericString() + "]";
	}

}
