/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.reactive.function.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.io.Resource;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.Assert;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

/**
 * <strong>Central entry point to Spring's functional web framework.</strong>
 * Exposes routing functionality, such as to
 * {@linkplain #route(RequestPredicate, HandlerFunction) create} a {@code RouterFunction}
 * given a {@code RequestPredicate} and {@code HandlerFunction}, and to do further
 * {@linkplain #nest(RequestPredicate, RouterFunction) subrouting} on an existing routing
 * function.
 *
 * <p>Additionally, this class can {@linkplain #toHttpHandler(RouterFunction) transform} a
 * {@code RouterFunction} into an {@code HttpHandler}, which can be run in Servlet 3.1+,
 * Reactor, or Undertow.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public abstract class RouterFunctions {

	private static final Log logger = LogFactory.getLog(RouterFunctions.class);

	/**
	 * Name of the {@link ServerWebExchange} attribute that contains the {@link ServerRequest}.
	 */
	public static final String REQUEST_ATTRIBUTE = RouterFunctions.class.getName() + ".request";

	/**
	 * Name of the {@link ServerWebExchange} attribute that contains the URI
	 * templates map, mapping variable names to values.
	 */
	public static final String URI_TEMPLATE_VARIABLES_ATTRIBUTE =
			RouterFunctions.class.getName() + ".uriTemplateVariables";

	private static final HandlerFunction<ServerResponse> NOT_FOUND_HANDLER = request -> ServerResponse.notFound().build();

	/**
	 * Return a {@linkplain Builder builder} that offers a discoverable way to create router
	 * functions.
	 * @return a router function builder
	 */
	public static Builder builder() {
		return new RouterFunctionBuilder();
	}

	/**
	 * Route to the given handler function if the given request predicate applies.
	 * <p>For instance, the following example routes GET requests for "/user" to the
	 * {@code listUsers} method in {@code userController}:
	 * <pre class="code">
	 * RouterFunction&lt;ServerResponse&gt; route =
	 *     RouterFunctions.route(RequestPredicates.GET("/user"), userController::listUsers);
	 * </pre>
	 * @param predicate the predicate to test
	 * @param handlerFunction the handler function to route to if the predicate applies
	 * @param <T> the type of response returned by the handler function
	 * @return a router function that routes to {@code handlerFunction} if
	 * {@code predicate} evaluates to {@code true}
	 * @see RequestPredicates
	 */
	public static <T extends ServerResponse> RouterFunction<T> route(
			RequestPredicate predicate, HandlerFunction<T> handlerFunction) {

		return new DefaultRouterFunction<>(predicate, handlerFunction);
	}

	/**
	 * Route to the given router function if the given request predicate applies. This method can be
	 * used to create <strong>nested routes</strong>, where a group of routes share a common path
	 * (prefix), header, or other request predicate.
	 * <p>For instance, the following example first creates a composed route that resolves to
	 * {@code listUsers} for a GET, and {@code createUser} for a POST. This composed route then gets
	 * nested with a "/user" path predicate, so that GET requests for "/user" will list users,
	 * and POST request for "/user" will create a new user.
	 * <pre class="code">
	 * RouterFunction&lt;ServerResponse&gt; userRoutes =
	 *   RouterFunctions.route(RequestPredicates.method(HttpMethod.GET), this::listUsers)
	 *     .andRoute(RequestPredicates.method(HttpMethod.POST), this::createUser);
	 *
	 * RouterFunction&lt;ServerResponse&gt; nestedRoute =
	 *   RouterFunctions.nest(RequestPredicates.path("/user"),userRoutes);
	 * </pre>
	 * @param predicate the predicate to test
	 * @param routerFunction the nested router function to delegate to if the predicate applies
	 * @param <T> the type of response returned by the handler function
	 * @return a router function that routes to {@code routerFunction} if
	 * {@code predicate} evaluates to {@code true}
	 * @see RequestPredicates
	 */
	public static <T extends ServerResponse> RouterFunction<T> nest(
			RequestPredicate predicate, RouterFunction<T> routerFunction) {

		return new DefaultNestedRouterFunction<>(predicate, routerFunction);
	}

	/**
	 * Route requests that match the given pattern to resources relative to the given root location.
	 * For instance
	 * <pre class="code">
	 * Resource location = new FileSystemResource("public-resources/");
	 * RouterFunction&lt;ServerResponse&gt; resources = RouterFunctions.resources("/resources/**", location);
     * </pre>
	 * @param pattern the pattern to match
	 * @param location the location directory relative to which resources should be resolved
	 * @return a router function that routes to resources
	 * @see #resourceLookupFunction(String, Resource)
	 */
	public static RouterFunction<ServerResponse> resources(String pattern, Resource location) {
		return resources(resourceLookupFunction(pattern, location));
	}

	/**
	 * Returns the resource lookup function used by {@link #resources(String, Resource)}.
	 * The returned function can be {@linkplain Function#andThen(Function) composed} on, for
	 * instance to return a default resource when the lookup function does not match:
	 * <pre class="code">
	 * Mono&lt;Resource&gt; defaultResource = Mono.just(new ClassPathResource("index.html"));
	 * Function&lt;ServerRequest, Mono&lt;Resource&gt;&gt; lookupFunction =
	 *   RouterFunctions.resourceLookupFunction("/resources/**", new FileSystemResource("public-resources/"))
	 *     .andThen(resourceMono -&gt; resourceMono.switchIfEmpty(defaultResource));
	 *
	 * RouterFunction&lt;ServerResponse&gt; resources = RouterFunctions.resources(lookupFunction);
     * </pre>
	 * @param pattern the pattern to match
	 * @param location the location directory relative to which resources should be resolved
	 * @return the default resource lookup function for the given parameters.
	 */
	public static Function<ServerRequest, Mono<Resource>> resourceLookupFunction(String pattern, Resource location) {
		return new PathResourceLookupFunction(pattern, location);
	}

	/**
	 * Route to resources using the provided lookup function. If the lookup function provides a
	 * {@link Resource} for the given request, it will be it will be exposed using a
	 * {@link HandlerFunction} that handles GET, HEAD, and OPTIONS requests.
	 * @param lookupFunction the function to provide a {@link Resource} given the {@link ServerRequest}
	 * @return a router function that routes to resources
	 */
	public static RouterFunction<ServerResponse> resources(Function<ServerRequest, Mono<Resource>> lookupFunction) {
		return new ResourcesRouterFunction(lookupFunction);
	}

	/**
	 * Convert the given {@linkplain RouterFunction router function} into a {@link HttpHandler}.
	 * This conversion uses {@linkplain HandlerStrategies#builder() default strategies}.
	 * <p>The returned handler can be adapted to run in
	 * <ul>
	 * <li>Servlet 3.1+ using the
	 * {@link org.springframework.http.server.reactive.ServletHttpHandlerAdapter},</li>
	 * <li>Reactor using the
	 * {@link org.springframework.http.server.reactive.ReactorHttpHandlerAdapter},</li>
	 * <li>Undertow using the
	 * {@link org.springframework.http.server.reactive.UndertowHttpHandlerAdapter}.</li>
	 * </ul>
	 * <p>Note that {@code HttpWebHandlerAdapter} also implements {@link WebHandler}, allowing
	 * for additional filter and exception handler registration through
	 * {@link WebHttpHandlerBuilder}.
	 * @param routerFunction the router function to convert
	 * @return an http handler that handles HTTP request using the given router function
	 */
	public static HttpHandler toHttpHandler(RouterFunction<?> routerFunction) {
		return toHttpHandler(routerFunction, HandlerStrategies.withDefaults());
	}

	/**
	 * Convert the given {@linkplain RouterFunction router function} into a {@link HttpHandler},
	 * using the given strategies.
	 * <p>The returned {@code HttpHandler} can be adapted to run in
	 * <ul>
	 * <li>Servlet 3.1+ using the
	 * {@link org.springframework.http.server.reactive.ServletHttpHandlerAdapter},</li>
	 * <li>Reactor using the
	 * {@link org.springframework.http.server.reactive.ReactorHttpHandlerAdapter},</li>
	 * <li>Undertow using the
	 * {@link org.springframework.http.server.reactive.UndertowHttpHandlerAdapter}.</li>
	 * </ul>
	 * @param routerFunction the router function to convert
	 * @param strategies the strategies to use
	 * @return an http handler that handles HTTP request using the given router function
	 */
	public static HttpHandler toHttpHandler(RouterFunction<?> routerFunction, HandlerStrategies strategies) {
		WebHandler webHandler = toWebHandler(routerFunction, strategies);
		return WebHttpHandlerBuilder.webHandler(webHandler)
				.filters(filters -> filters.addAll(strategies.webFilters()))
				.exceptionHandlers(handlers -> handlers.addAll(strategies.exceptionHandlers()))
				.localeContextResolver(strategies.localeContextResolver())
				.build();
	}

	/**
	 * Convert the given {@linkplain RouterFunction router function} into a {@link WebHandler}.
	 * This conversion uses {@linkplain HandlerStrategies#builder() default strategies}.
	 * @param routerFunction the router function to convert
	 * @return a web handler that handles web request using the given router function
	 */
	public static WebHandler toWebHandler(RouterFunction<?> routerFunction) {
		return toWebHandler(routerFunction, HandlerStrategies.withDefaults());
	}

	/**
	 * Convert the given {@linkplain RouterFunction router function} into a {@link WebHandler},
	 * using the given strategies.
	 * @param routerFunction the router function to convert
	 * @param strategies the strategies to use
	 * @return a web handler that handles web request using the given router function
	 */
	public static WebHandler toWebHandler(RouterFunction<?> routerFunction, HandlerStrategies strategies) {
		Assert.notNull(routerFunction, "RouterFunction must not be null");
		Assert.notNull(strategies, "HandlerStrategies must not be null");

		return exchange -> {
			ServerRequest request = new DefaultServerRequest(exchange, strategies.messageReaders());
			addAttributes(exchange, request);
			return routerFunction.route(request)
					.defaultIfEmpty(notFound())
					.flatMap(handlerFunction -> wrapException(() -> handlerFunction.handle(request)))
					.flatMap(response -> wrapException(() -> response.writeTo(exchange,
							new HandlerStrategiesResponseContext(strategies))));
		};
	}

	private static <T> Mono<T> wrapException(Supplier<Mono<T>> supplier) {
		try {
			return supplier.get();
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}
	}

	private static void addAttributes(ServerWebExchange exchange, ServerRequest request) {
		Map<String, Object> attributes = exchange.getAttributes();
		attributes.put(REQUEST_ATTRIBUTE, request);
	}

	@SuppressWarnings("unchecked")
	private static <T extends ServerResponse> HandlerFunction<T> notFound() {
		return (HandlerFunction<T>) NOT_FOUND_HANDLER;
	}

	@SuppressWarnings("unchecked")
	static <T extends ServerResponse> HandlerFunction<T> cast(HandlerFunction<?> handlerFunction) {
		return (HandlerFunction<T>) handlerFunction;
	}

	/**
	 * Represents a builder for router functions.
	 * <p>Each invocation of {@code route} creates a new {@link RouterFunction} that is
	 * {@linkplain RouterFunction#and(RouterFunction) composed} with any previously built functions.
	 * @since 5.1
	 */
	public interface Builder {

		/**
		 * Adds a route to the given handler function that matches if the given request predicate
		 * applies.
		 * <p>For instance, the following example routes GET requests for "/user" to the
		 * {@code listUsers} method in {@code userController}:
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; route =
		 *     RouterFunctions.builder()
		 *     .route(RequestPredicates.GET("/user"), userController::listUsers)
		 *     .build();
		 * </pre>
		 * @param predicate the predicate to test
		 * @param handlerFunction the handler function to route to if the predicate applies
		 * @return this builder
		 * @see RequestPredicates
		 */
		Builder route(RequestPredicate predicate, HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code GET} requests.
		 * @param handlerFunction the handler function to handle all {@code GET} requests
		 * @return this builder
		 */
		Builder routeGet(HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code GET} requests
		 * that match the given pattern.
		 * @param pattern the pattern to match to
		 * @param handlerFunction the handler function to handle all {@code GET} requests that
		 * match {@code pattern}
		 * @return this builder
		 */
		Builder routeGet(String pattern, HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code HEAD} requests.
		 * @param handlerFunction the handler function to handle all {@code HEAD} requests
		 * @return this builder
		 */
		Builder routeHead(HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code HEAD} requests
		 * that match the given pattern.
		 * @param pattern the pattern to match to
		 * @param handlerFunction the handler function to handle all {@code HEAD} requests that
		 * match {@code pattern}
		 * @return this builder
		 */
		Builder routeHead(String pattern, HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code POST} requests.
		 * @param handlerFunction the handler function to handle all {@code POST} requests
		 * @return this builder
		 */
		Builder routePost(HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code POST} requests
		 * that match the given pattern.
		 * @param pattern the pattern to match to
		 * @param handlerFunction the handler function to handle all {@code POST} requests that
		 * match {@code pattern}
		 * @return this builder
		 */
		Builder routePost(String pattern, HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code PUT} requests.
		 * @param handlerFunction the handler function to handle all {@code PUT} requests
		 * @return this builder
		 */
		Builder routePut(HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code PUT} requests
		 * that match the given pattern.
		 * @param pattern the pattern to match to
		 * @param handlerFunction the handler function to handle all {@code PUT} requests that
		 * match {@code pattern}
		 * @return this builder
		 */
		Builder routePut(String pattern, HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code PATCH} requests.
		 * @param handlerFunction the handler function to handle all {@code PATCH} requests
		 * @return this builder
		 */
		Builder routePatch(HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code PATCH} requests
		 * that match the given pattern.
		 * @param pattern the pattern to match to
		 * @param handlerFunction the handler function to handle all {@code PATCH} requests that
		 * match {@code pattern}
		 * @return this builder
		 */
		Builder routePatch(String pattern, HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code DELETE} requests.
		 * @param handlerFunction the handler function to handle all {@code DELETE} requests
		 * @return this builder
		 */
		Builder routeDelete(HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code DELETE} requests
		 * that match the given pattern.
		 * @param pattern the pattern to match to
		 * @param handlerFunction the handler function to handle all {@code DELETE} requests that
		 * match {@code pattern}
		 * @return this builder
		 */
		Builder routeDelete(String pattern, HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code OPTIONS} requests.
		 * @param handlerFunction the handler function to handle all {@code OPTIONS} requests
		 * @return this builder
		 */
		Builder routeOptions(HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Adds a route to the given handler function that handles all HTTP {@code OPTIONS} requests
		 * that match the given pattern.
		 * @param pattern the pattern to match to
		 * @param handlerFunction the handler function to handle all {@code OPTIONS} requests that
		 * match {@code pattern}
		 * @return this builder
		 */
		Builder routeOptions(String pattern, HandlerFunction<ServerResponse> handlerFunction);

		/**
		 * Route to the supplied router function if the given request predicate applies. This method
		 * can be used to create <strong>nested routes</strong>, where a group of routes share a
		 * common path (prefix), header, or other request predicate.
		 * <p>For instance, the following example creates a nested route with a "/user" path
		 * predicate, so that GET requests for "/user" will list users,
		 * and POST request for "/user" will create a new user.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; nestedRoute =
		 *   RouterFunctions.builder()
		 *     .nest(RequestPredicates.path("/user"), () ->
		 *       RouterFunctions.builder()
		 *       .routeGet(this::listUsers)
		 *       .routePost(this::createUser);
		 *       .build();
		 *     )
		 *     .build();
		 * </pre>
		 * @param predicate the predicate to test
		 * @param routerFunctionSupplier supplier for the nested router function to delegate to if
		 * the predicate applies
		 * @return this builder
		 * @see RequestPredicates
		 */
		Builder nest(RequestPredicate predicate, Supplier<RouterFunction<ServerResponse>> routerFunctionSupplier);

		/**
		 * Route to a built router function if the given request predicate applies.
		 * This method can be used to create <strong>nested routes</strong>, where a group of routes
		 * share a common path (prefix), header, or other request predicate.
		 * <p>For instance, the following example creates a nested route with a "/user" path
		 * predicate, so that GET requests for "/user" will list users,
		 * and POST request for "/user" will create a new user.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; nestedRoute =
		 *   RouterFunctions.builder()
		 *     .nest(RequestPredicates.path("/user"), builder ->
		 *       builder.routeGet(this::listUsers)
		 *              .routePost(this::createUser);
		 *     )
		 *     .build();
		 * </pre>
		 * @param predicate the predicate to test
		 * @param builderConsumer consumer for a {@code Builder} that provides the nested router
		 * function
		 * @return this builder
		 * @see RequestPredicates
		 */
		Builder nest(RequestPredicate predicate, Consumer<Builder> builderConsumer);

		/**
		 * Route to the supplied router function if the given path prefix pattern applies. This method
		 * can be used to create <strong>nested routes</strong>, where a group of routes share a
		 * common path (prefix).
		 * <p>For instance, the following example creates a nested route with a "/user" path
		 * predicate, so that GET requests for "/user" will list users,
		 * and POST request for "/user" will create a new user.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; nestedRoute =
		 *   RouterFunctions.builder()
		 *     .nestPath("/user", () ->
		 *       RouterFunctions.builder()
		 *       .routeGet(this::listUsers)
		 *       .routePost(this::createUser);
		 *       .build();
		 *     )
		 *     .build();
		 * </pre>
		 * @param pattern the pattern to match to
		 * @param routerFunctionSupplier supplier for the nested router function to delegate to if
		 * the pattern matches
		 * @return this builder
		 */
		Builder nestPath(String pattern, Supplier<RouterFunction<ServerResponse>> routerFunctionSupplier);

		/**
		 * Route to a built router function if the given path prefix pattern applies.
		 * This method can be used to create <strong>nested routes</strong>, where a group of routes
		 * share a common path (prefix), header, or other request predicate.
		 * <p>For instance, the following example creates a nested route with a "/user" path
		 * predicate, so that GET requests for "/user" will list users,
		 * and POST request for "/user" will create a new user.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; nestedRoute =
		 *   RouterFunctions.builder()
		 *     .nestPath("/user", builder ->
		 *       builder.routeGet(this::listUsers)
		 *              .routePost(this::createUser);
		 *     )
		 *     .build();
		 * </pre>
		 * @param pattern the pattern to match to
		 * @param builderConsumer consumer for a {@code Builder} that provides the nested router
		 * function
		 * @return this builder
		 */
		Builder nestPath(String pattern, Consumer<Builder> builderConsumer);

		/**
		 * Filters all routes created by this builder with the given filter function. Filter
		 * functions are typically used to address cross-cutting concerns, such as logging,
		 * security, etc.
		 * <p>For instance, the following example creates a filter that logs the request before
		 * the handler function executes, and logs the response after.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; filteredRoute =
		 *   RouterFunctions.builder()
		 *     .routeGet("/user", this::listUsers)
		 *     .filter((request, next) -> {
		 *       log(request);
         *       Mono&lt;ServerResponse&gt; responseMono = next.handle(request);
		 *       return responseMono.doOnNext(response -> log(response);
		 *     })
		 *     .build();
		 * </pre>
		 * @param filterFunction the function to filter all routes built by this builder
		 * @return this builder
		 */
		Builder filter(HandlerFilterFunction<ServerResponse, ServerResponse> filterFunction);

		/**
		 * Filters the request for all routes created by this builder with the given request
		 * processing function. Filters are typically used to address cross-cutting concerns, such
		 * as logging, security, etc.
		 * <p>For instance, the following example creates a filter that logs the request before
		 * the handler function executes.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; filteredRoute =
		 *   RouterFunctions.builder()
		 *     .routeGet("/user", this::listUsers)
		 *     .filterBefore(request -> {
		 *       log(request);
		 *       return Mono.just(request);
		 *     })
		 *     .build();
		 * </pre>
		 * @param requestProcessor a function that transforms the request
		 * @return this builder
		 */
		Builder filterBefore(Function<ServerRequest, Mono<ServerRequest>> requestProcessor);

		/**
		 * Filters the response for all routes created by this builder with the given response
		 * processing function. Filters are typically used to address cross-cutting concerns, such
		 * as logging, security, etc.
		 * <p>For instance, the following example creates a filter that logs the response after
		 * the handler function executes.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; filteredRoute =
		 *   RouterFunctions.builder()
		 *     .routeGet("/user", this::listUsers)
		 *     .filterAfter((request, response) -> {
		 *       log(response);
		 *       return Mono.just(response);
		 *     })
		 *     .build();
		 * </pre>
		 * @param responseProcessor a function that transforms the response
		 * @return this builder
		 */
		Builder filterAfter(BiFunction<ServerRequest, ServerResponse, Mono<ServerResponse>> responseProcessor);

		/**
		 * Filters all exceptions that match the predicate by applying the given response provider
		 * function.
		 * <p>For instance, the following example creates a filter that returns a 500 response
		 * status when an {@code IllegalStateException} occurs.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; filteredRoute =
		 *   RouterFunctions.builder()
		 *     .routeGet("/user", this::listUsers)
		 *     .filterException(e -> e instanceof IllegalStateException,
		 *       (e, request) -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
		 *     .build();
		 * </pre>
		 * @param predicate the type of exception to filter
		 * @param responseProvider a function that creates a response
		 * @return this builder
		 */
		Builder filterException(Predicate<? super Throwable> predicate,
				BiFunction<? super  Throwable, ServerRequest, Mono<ServerResponse>> responseProvider);

		/**
		 * Filters all exceptions of the given type by applying the given response provider
		 * function.
		 * <p>For instance, the following example creates a filter that returns a 500 response
		 * status when an {@code IllegalStateException} occurs.
		 * <pre class="code">
		 * RouterFunction&lt;ServerResponse&gt; filteredRoute =
		 *   RouterFunctions.builder()
		 *     .routeGet("/user", this::listUsers)
		 *     .filterException(IllegalStateException.class,
		 *       (e, request) -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
		 *     .build();
		 * </pre>
		 * @param exceptionType the type of exception to filter
		 * @param responseProvider a function that creates a response
		 * @return this builder
		 */
		<T extends Throwable> Builder filterException(Class<T> exceptionType,
				BiFunction<? super T, ServerRequest, Mono<ServerResponse>> responseProvider);

		/**
		 * Builds the {@code RouterFunction}. All created routes are
		 * {@linkplain RouterFunction#and(RouterFunction) composed} with one another, and filters
		 * (f any) are applied to the result.
		 * @return the built router function
		 */
		RouterFunction<ServerResponse> build();

	}


	/**
	 * Receives notifications from the logical structure of router functions.
	 */
	public interface Visitor {

		/**
		 * Receive notification of the beginning of a nested router function.
		 * @param predicate the predicate that applies to the nested router functions
		 * @see RouterFunctions#nest(RequestPredicate, RouterFunction)
		 */
		void startNested(RequestPredicate predicate);

		/**
		 * Receive notification of the end of a nested router function.
		 * @param predicate the predicate that applies to the nested router functions
		 * @see RouterFunctions#nest(RequestPredicate, RouterFunction)
		 */
		void endNested(RequestPredicate predicate);

		/**
		 * Receive notification of a standard predicated route to a handler function.
		 * @param predicate the predicate that applies to the handler function
		 * @param handlerFunction the handler function.
		 * @see RouterFunctions#route(RequestPredicate, HandlerFunction)
		 */
		void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction);

		/**
		 * Receive notification of a resource router function.
		 * @param lookupFunction the lookup function for the resources
		 * @see RouterFunctions#resources(Function)
		 */
		void resources(Function<ServerRequest, Mono<Resource>> lookupFunction);

		/**
		 * Receive notification of an unknown router function. This method is called for router
		 * functions that were not created via the various {@link RouterFunctions} methods.
		 * @param routerFunction the router function
		 */
		void unknown(RouterFunction<?> routerFunction);
	}


	private abstract static class AbstractRouterFunction<T extends ServerResponse> implements RouterFunction<T> {

		@Override
		public String toString() {
			ToStringVisitor visitor = new ToStringVisitor();
			accept(visitor);
			return visitor.toString();
		}
	}

	/**
	 * A composed routing function that first invokes one function, and then invokes the
	 * another function (of the same response type {@code T}) if this route had
	 * {@linkplain Mono#empty() no result}.
	 *
	 * @param <T> the server response type
	 */
	static final class SameComposedRouterFunction<T extends ServerResponse> extends AbstractRouterFunction<T> {

		private final RouterFunction<T> first;

		private final RouterFunction<T> second;

		public SameComposedRouterFunction(RouterFunction<T> first, RouterFunction<T> second) {
			this.first = first;
			this.second = second;
		}

		@Override
		public Mono<HandlerFunction<T>> route(ServerRequest request) {
			return this.first.route(request)
					.switchIfEmpty(Mono.defer(() -> this.second.route(request)));
		}

		@Override
		public void accept(Visitor visitor) {
			this.first.accept(visitor);
			this.second.accept(visitor);
		}
	}

	/**
	 * A composed routing function that first invokes one function, and then invokes
	 * another function (of a different response type) if this route had
	 * {@linkplain Mono#empty() no result}.
	 */
	static final class DifferentComposedRouterFunction extends AbstractRouterFunction<ServerResponse> {

		private final RouterFunction<?> first;

		private final RouterFunction<?> second;

		public DifferentComposedRouterFunction(RouterFunction<?> first, RouterFunction<?> second) {
			this.first = first;
			this.second = second;
		}

		@Override
		public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
			return this.first.route(request)
					.map(RouterFunctions::cast)
					.switchIfEmpty(Mono.defer(() -> this.second.route(request).map(RouterFunctions::cast)));
		}

		@Override
		public void accept(Visitor visitor) {
			this.first.accept(visitor);
			this.second.accept(visitor);
		}

	}

	/**
	 * Filter the specified {@linkplain HandlerFunction handler functions} with the given
	 * {@linkplain HandlerFilterFunction filter function}.
	 *
	 * @param <T> the type of the {@linkplain HandlerFunction handler function} to filter
	 * @param <S> the type of the response of the function
	 */
	static final class FilteredRouterFunction<T extends ServerResponse, S extends ServerResponse>
			implements RouterFunction<S> {

		private final RouterFunction<T> routerFunction;

		private final HandlerFilterFunction<T, S> filterFunction;

		public FilteredRouterFunction(
				RouterFunction<T> routerFunction,
				HandlerFilterFunction<T, S> filterFunction) {
			this.routerFunction = routerFunction;
			this.filterFunction = filterFunction;
		}

		@Override
		public Mono<HandlerFunction<S>> route(ServerRequest request) {
			return this.routerFunction.route(request).map(this.filterFunction::apply);
		}

		@Override
		public void accept(Visitor visitor) {
			this.routerFunction.accept(visitor);
		}

		@Override
		public String toString() {
			return this.routerFunction.toString();
		}
	}

	private static final class DefaultRouterFunction<T extends ServerResponse>
			extends AbstractRouterFunction<T> {

		private final RequestPredicate predicate;

		private final HandlerFunction<T> handlerFunction;

		public DefaultRouterFunction(RequestPredicate predicate, HandlerFunction<T> handlerFunction) {
			Assert.notNull(predicate, "Predicate must not be null");
			Assert.notNull(handlerFunction, "HandlerFunction must not be null");
			this.predicate = predicate;
			this.handlerFunction = handlerFunction;
		}

		@Override
		public Mono<HandlerFunction<T>> route(ServerRequest request) {
			if (this.predicate.test(request)) {
				if (logger.isTraceEnabled()) {
					logger.trace(String.format("Matched %s", this.predicate));
				}
				return Mono.just(this.handlerFunction);
			}
			else {
				return Mono.empty();
			}
		}

		@Override
		public void accept(Visitor visitor) {
			visitor.route(this.predicate, this.handlerFunction);
		}

	}

	private static final class DefaultNestedRouterFunction<T extends ServerResponse>
			extends AbstractRouterFunction<T> {

		private final RequestPredicate predicate;

		private final RouterFunction<T> routerFunction;

		public DefaultNestedRouterFunction(RequestPredicate predicate, RouterFunction<T> routerFunction) {
			Assert.notNull(predicate, "Predicate must not be null");
			Assert.notNull(routerFunction, "RouterFunction must not be null");
			this.predicate = predicate;
			this.routerFunction = routerFunction;
		}

		@Override
		public Mono<HandlerFunction<T>> route(ServerRequest serverRequest) {
			return this.predicate.nest(serverRequest)
					.map(nestedRequest -> {
								if (logger.isTraceEnabled()) {
									logger.trace(String.format("Matched nested %s", this.predicate));
								}
								return this.routerFunction.route(nestedRequest)
										.doOnNext(match -> {
											mergeTemplateVariables(serverRequest, nestedRequest.pathVariables());
										});
							}
					).orElseGet(Mono::empty);
		}

		@SuppressWarnings("unchecked")
		private void mergeTemplateVariables(ServerRequest request, Map<String, String> variables) {
			if (!variables.isEmpty()) {
				Map<String, Object> attributes = request.attributes();
				Map<String, String> oldVariables = (Map<String, String>)request.attribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
						.orElseGet(LinkedHashMap::new);
				Map<String, String> mergedVariables = new LinkedHashMap<>(oldVariables);
				mergedVariables.putAll(variables);
				attributes.put(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
						Collections.unmodifiableMap(mergedVariables));
			}
		}

		@Override
		public void accept(Visitor visitor) {
			visitor.startNested(this.predicate);
			this.routerFunction.accept(visitor);
			visitor.endNested(this.predicate);
		}

	}

	private static class ResourcesRouterFunction extends  AbstractRouterFunction<ServerResponse> {

		private final Function<ServerRequest, Mono<Resource>> lookupFunction;

		public ResourcesRouterFunction(Function<ServerRequest, Mono<Resource>> lookupFunction) {
			Assert.notNull(lookupFunction, "Function must not be null");
			this.lookupFunction = lookupFunction;
		}

		@Override
		public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
			return this.lookupFunction.apply(request).map(ResourceHandlerFunction::new);
		}

		@Override
		public void accept(Visitor visitor) {
			visitor.resources(this.lookupFunction);
		}
	}

	private static class HandlerStrategiesResponseContext implements ServerResponse.Context {

		private final HandlerStrategies strategies;

		public HandlerStrategiesResponseContext(HandlerStrategies strategies) {
			this.strategies = strategies;
		}

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return this.strategies.messageWriters();
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return this.strategies.viewResolvers();
		}
	}

}
