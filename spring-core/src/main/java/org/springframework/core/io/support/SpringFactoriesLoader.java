/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.core.io.support;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.KotlinDetector;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.UrlResource;
import org.springframework.core.log.LogMessage;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * General purpose factory loading mechanism for internal use within the framework.
 *
 * <p>{@code SpringFactoriesLoader} {@linkplain #loadFactories loads} and instantiates
 * factories of a given type from {@value #FACTORIES_RESOURCE_LOCATION} files which
 * may be present in multiple JAR files in the classpath. The {@code spring.factories}
 * file must be in {@link Properties} format, where the key is the fully qualified
 * name of the interface or abstract class, and the value is a comma-separated list of
 * implementation class names. For example:
 *
 * <pre class="code">example.MyService=example.MyServiceImpl1,example.MyServiceImpl2</pre>
 *
 * where {@code example.MyService} is the name of the interface, and {@code MyServiceImpl1}
 * and {@code MyServiceImpl2} are two implementations.
 * <p>
 * Implementation classes <b>must</b> have a single resolvable constructor that will
 * be used to create the instance, either:
 * <ul>
 * <li>a primary or single constructor</li>
 * <li>a single public constructor</li>
 * <li>the default constructor</li>
 * </ul>
 * If the resolvable constructor has arguments, a suitable {@link ArgumentResolver
 * ArgumentResolver} should be provided. To customize how instantiation failures
 * are handled, consider providing a {@link FailureHandler FailureHandler}.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Andy Wilkinson
 * @author Madhura Bhave
 * @author Phillip Webb
 * @since 3.2
 */
public final class SpringFactoriesLoader {

	/**
	 * The location to look for factories.
	 * <p>Can be present in multiple JAR files.
	 */
	public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";


	private static final Log logger = LogFactory.getLog(SpringFactoriesLoader.class);

	private static final FailureHandler THROWING_HANDLER = FailureHandler.throwing();

	static final Map<ClassLoader, Map<String, List<String>>> cache = new ConcurrentReferenceHashMap<>();


	private SpringFactoriesLoader() {
	}


	/**
	 * Load and instantiate the factory implementations of the given type from
	 * {@value #FACTORIES_RESOURCE_LOCATION}, using the given class loader and
	 * a default argument resolver that expects a no-arg constructor.
	 * <p>The returned factories are sorted through {@link AnnotationAwareOrderComparator}.
	 * <p>If a custom instantiation strategy is required, use {@code loadFactories}
	 * with a custom {@link ArgumentResolver ArgumentResolver} and/or
	 * {@link FailureHandler FailureHandler}.
	 * <p>As of Spring Framework 5.3, if duplicate implementation class names are
	 * discovered for a given factory type, only one instance of the duplicated
	 * implementation type will be instantiated.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
	 * @throws IllegalArgumentException if any factory implementation class cannot
	 * be loaded or if an error occurs while instantiating any factory
	 */
	public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader) {
		return loadFactories(factoryType, classLoader, null, null);
	}

	/**
	 * Load and instantiate the factory implementations of the given type from
	 * {@value #FACTORIES_RESOURCE_LOCATION}, using the given class loader and
	 * argument resolver.
	 * <p>The returned factories are sorted through {@link AnnotationAwareOrderComparator}.
	 * <p>As of Spring Framework 5.3, if duplicate implementation class names are
	 * discovered for a given factory type, only one instance of the duplicated
	 * implementation type will be instantiated.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
	 * @param argumentResolver strategy used to resolve constructor arguments by their type
	 * @throws IllegalArgumentException if any factory implementation class cannot
	 * be loaded or if an error occurs while instantiating any factory
	 * @since 6.0
	 */
	public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader,
			@Nullable ArgumentResolver argumentResolver) {

		return loadFactories(factoryType, classLoader, argumentResolver, null);
	}

	/**
	 * Load and instantiate the factory implementations of the given type from
	 * {@value #FACTORIES_RESOURCE_LOCATION}, using the given class loader with
	 * custom failure handling provided by the given failure handler.
	 * <p>The returned factories are sorted through {@link AnnotationAwareOrderComparator}.
	 * <p>As of Spring Framework 5.3, if duplicate implementation class names are
	 * discovered for a given factory type, only one instance of the duplicated
	 * implementation type will be instantiated.
	 * <p>For any factory implementation class that cannot be loaded or error that occurs while
	 * instantiating it, the given failure handler is called.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
	 * @param failureHandler strategy used to handle factory instantiation failures
	 * @since 6.0
	 */
	public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader,
			@Nullable FailureHandler failureHandler) {

		return loadFactories(factoryType, classLoader, null, failureHandler);
	}

	/**
	 * Load and instantiate the factory implementations of the given type from
	 * {@value #FACTORIES_RESOURCE_LOCATION}, using the given class loader,
	 * argument resolver, and custom failure handling provided by the given
	 * failure handler.
	 * <p>The returned factories are sorted through {@link AnnotationAwareOrderComparator}.
	 * <p>As of Spring Framework 5.3, if duplicate implementation class names are
	 * discovered for a given factory type, only one instance of the duplicated
	 * implementation type will be instantiated.
	 * <p>For any factory implementation class that cannot be loaded or error that occurs while
	 * instantiating it, the given failure handler is called.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
	 * @param argumentResolver strategy used to resolve constructor arguments by their type
	 * @param failureHandler strategy used to handle factory instantiation failures
	 * @since 6.0
	 */
	public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader,
			@Nullable ArgumentResolver argumentResolver, @Nullable FailureHandler failureHandler) {

		Assert.notNull(factoryType, "'factoryType' must not be null");
		ClassLoader classLoaderToUse = (classLoader != null ? classLoader
				: SpringFactoriesLoader.class.getClassLoader());
		List<String> factoryImplementationNames = loadFactoryNames(factoryType, classLoaderToUse);
		logger.trace(LogMessage.format("Loaded [%s] names: %s", factoryType.getName(), factoryImplementationNames));
		List<T> result = new ArrayList<>(factoryImplementationNames.size());
		FailureHandler failureHandlerToUse = (failureHandler != null) ? failureHandler : THROWING_HANDLER;
		for (String factoryImplementationName : factoryImplementationNames) {
			T factory = instantiateFactory(factoryImplementationName, factoryType,
					classLoaderToUse, argumentResolver, failureHandlerToUse);
			if (factory != null) {
				result.add(factory);
			}
		}
		AnnotationAwareOrderComparator.sort(result);
		return result;
	}

	/**
	 * Load the fully qualified class names of factory implementations of the
	 * given type from {@value #FACTORIES_RESOURCE_LOCATION}, using the given
	 * class loader.
	 * <p>As of Spring Framework 5.3, if a particular implementation class name
	 * is discovered more than once for the given factory type, duplicates will
	 * be ignored.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading resources; can be
	 * {@code null} to use the default
	 * @throws IllegalArgumentException if an error occurs while loading factory names
	 * @see #loadFactories
	 */
	public static List<String> loadFactoryNames(Class<?> factoryType, @Nullable ClassLoader classLoader) {
		ClassLoader classLoaderToUse = (classLoader != null ? classLoader
				: SpringFactoriesLoader.class.getClassLoader());
		String factoryTypeName = factoryType.getName();
		return getAllFactories(classLoaderToUse).getOrDefault(factoryTypeName, Collections.emptyList());
	}

	private static Map<String, List<String>> getAllFactories(ClassLoader classLoader) {
		Map<String, List<String>> result = cache.get(classLoader);
		if (result != null) {
			return result;
		}
		result = loadAllFactories(classLoader);
		cache.put(classLoader, result);
		return result;
	}

	private static Map<String, List<String>> loadAllFactories(ClassLoader classLoader) {
		Map<String, List<String>> result;
		result = new HashMap<>();
		try {
			Enumeration<URL> urls = classLoader.getResources(FACTORIES_RESOURCE_LOCATION);
			while (urls.hasMoreElements()) {
				UrlResource resource = new UrlResource(urls.nextElement());
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);
				for (Map.Entry<?, ?> entry : properties.entrySet()) {
					String factoryTypeName = ((String) entry.getKey()).trim();
					String[] factoryImplementationNames =
							StringUtils.commaDelimitedListToStringArray((String) entry.getValue());
					for (String factoryImplementationName : factoryImplementationNames) {
						result.computeIfAbsent(factoryTypeName, key -> new ArrayList<>())
								.add(factoryImplementationName.trim());
					}
				}
			}
			result.replaceAll(SpringFactoriesLoader::toDistinctUnmodifiableList);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" +
					FACTORIES_RESOURCE_LOCATION + "]", ex);
		}
		return Collections.unmodifiableMap(result);
	}

	private static List<String> toDistinctUnmodifiableList(String factoryType, List<String> implementations) {
		return implementations.stream().distinct().toList();
	}

	@Nullable
	private static <T> T instantiateFactory(String factoryImplementationName,
			Class<T> factoryType, ClassLoader classLoader, @Nullable ArgumentResolver argumentResolver,
			FailureHandler failureHandler) {
		try {
			Class<?> factoryImplementationClass = ClassUtils.forName(factoryImplementationName, classLoader);
			Assert.isTrue(factoryType.isAssignableFrom(factoryImplementationClass),
					() -> "Class [" + factoryImplementationName + "] is not assignable to factory type [" + factoryType.getName() + "]");
			FactoryInstantiator<T> factoryInstantiator = FactoryInstantiator.forClass(factoryImplementationClass);
			return factoryInstantiator.instantiate(argumentResolver);
		}
		catch (Throwable ex) {
			failureHandler.handleFailure(factoryType, factoryImplementationName, ex);
			return null;
		}
	}


	/**
	 * Internal instantiator used to create the factory instance.
	 * @param <T> the instance implementation type
	 */
	static final class FactoryInstantiator<T> {

		private final Constructor<T> constructor;


		private FactoryInstantiator(Constructor<T> constructor) {
			ReflectionUtils.makeAccessible(constructor);
			this.constructor = constructor;
		}

		T instantiate(@Nullable ArgumentResolver argumentResolver) throws Exception {
			Object[] args = resolveArgs(argumentResolver);
			if (isKotlinType(this.constructor.getDeclaringClass())) {
				return KotlinDelegate.instantiate(this.constructor, args);
			}
			return this.constructor.newInstance(args);
		}

		private Object[] resolveArgs(@Nullable ArgumentResolver argumentResolver) {
			Class<?>[] types = this.constructor.getParameterTypes();
			return (argumentResolver != null ?
					Arrays.stream(types).map(argumentResolver::resolve).toArray() :
					new Object[types.length]);
		}

		@SuppressWarnings("unchecked")
		static <T> FactoryInstantiator<T> forClass(Class<?> factoryImplementationClass) {
			Constructor<?> constructor = findConstructor(factoryImplementationClass);
			Assert.state(constructor != null, "Class [" + factoryImplementationClass.getName() + "] has no suitable constructor");
			return new FactoryInstantiator<>((Constructor<T>) constructor);
		}

		@Nullable
		private static Constructor<?> findConstructor(Class<?> factoryImplementationClass) {
			// Same algorithm as BeanUtils.getResolvableConstructor
			Constructor<?> constructor = findPrimaryKotlinConstructor(factoryImplementationClass);
			constructor = (constructor != null ? constructor :
					findSingleConstructor(factoryImplementationClass.getConstructors()));
			constructor = (constructor != null ? constructor :
					findSingleConstructor(factoryImplementationClass.getDeclaredConstructors()));
			constructor = (constructor != null ? constructor :
					findDeclaredConstructor(factoryImplementationClass));
			return constructor;
		}

		@Nullable
		private static Constructor<?> findPrimaryKotlinConstructor(Class<?> factoryImplementationClass) {
			return (isKotlinType(factoryImplementationClass)
					? KotlinDelegate.findPrimaryConstructor(factoryImplementationClass) : null);
		}

		private static boolean isKotlinType(Class<?> factoryImplementationClass) {
			return KotlinDetector.isKotlinReflectPresent() && KotlinDetector.isKotlinType(factoryImplementationClass);
		}

		@Nullable
		private static Constructor<?> findSingleConstructor(Constructor<?>[] constructors) {
			return (constructors.length == 1 ? constructors[0] : null);
		}

		@Nullable
		private static Constructor<?> findDeclaredConstructor(Class<?> factoryImplementationClass) {
			try {
				return factoryImplementationClass.getDeclaredConstructor();
			}
			catch (NoSuchMethodException ex) {
				return null;
			}
		}

	}

	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		@Nullable
		public static <T> Constructor<T> findPrimaryConstructor(Class<T> clazz) {
			try {
				KFunction<T> primaryConstructor = KClasses.getPrimaryConstructor(JvmClassMappingKt.getKotlinClass(clazz));
				if (primaryConstructor != null) {
					Constructor<T> constructor = ReflectJvmMapping.getJavaConstructor(
							primaryConstructor);
					Assert.state(constructor != null, () ->
							"Failed to find Java constructor for Kotlin primary constructor: " + clazz.getName());
					return constructor;
				}
			}
			catch (UnsupportedOperationException ex) {
				// ignore
			}
			return null;
		}

		public static <T> T instantiate(Constructor<T> constructor, Object[] args)
				throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			KFunction<T> kotlinConstructor = ReflectJvmMapping.getKotlinFunction(constructor);
			if (kotlinConstructor == null) {
				return constructor.newInstance(args);
			}
			makeAccessible(constructor, kotlinConstructor);
			return instantiate(kotlinConstructor, convertArgs(args, kotlinConstructor.getParameters()));
		}

		private static <T> void makeAccessible(Constructor<T> constructor,
				KFunction<T> kotlinConstructor) {
			if ((!Modifier.isPublic(constructor.getModifiers())
					|| !Modifier.isPublic(constructor.getDeclaringClass().getModifiers()))) {
				KCallablesJvm.setAccessible(kotlinConstructor, true);
			}
		}

		private static Map<KParameter, Object> convertArgs(Object[] args, List<KParameter> parameters) {
			Map<KParameter, Object> result = CollectionUtils.newHashMap(parameters.size());
			Assert.isTrue(args.length <= parameters.size(),
					"Number of provided arguments should be less of equals than number of constructor parameters");
			for (int i = 0; i < args.length; i++) {
				if (!parameters.get(i).isOptional() || args[i] != null) {
					result.put(parameters.get(i), args[i]);
				}
			}
			return result;
		}

		private static <T> T instantiate(KFunction<T> kotlinConstructor, Map<KParameter, Object> args) {
			return kotlinConstructor.callBy(args);
		}

	}


	/**
	 * Strategy for resolving constructor arguments based on their type.
	 *
	 * @since 6.0
	 * @see ArgumentResolver#of(Class, Object)
	 * @see ArgumentResolver#ofSupplied(Class, Supplier)
	 * @see ArgumentResolver#from(Function)
	 */
	@FunctionalInterface
	public interface ArgumentResolver {

		/**
		 * Resolve the given argument if possible.
		 * @param <T> the argument type
		 * @param type the argument type
		 * @return the resolved argument value or {@code null}
		 */
		@Nullable
		<T> T resolve(Class<T> type);

		/**
		 * Create a new composed {@link ArgumentResolver} by combining this resolver
		 * with the given type and value.
		 * @param <T> the argument type
		 * @param type the argument type
		 * @param value the argument value
		 * @return a new composite {@link ArgumentResolver} instance
		 */
		default <T> ArgumentResolver and(Class<T> type, T value) {
			return and(ArgumentResolver.of(type, value));
		}

		/**
		 * Create a new composed {@link ArgumentResolver} by combining this resolver
		 * with the given type and value.
		 * @param <T> the argument type
		 * @param type the argument type
		 * @param valueSupplier the argument value supplier
		 * @return a new composite {@link ArgumentResolver} instance
		 */
		default <T> ArgumentResolver andSupplied(Class<T> type, Supplier<T> valueSupplier) {
			return and(ArgumentResolver.ofSupplied(type, valueSupplier));
		}

		/**
		 * Create a new composed {@link ArgumentResolver} by combining this resolver
		 * with the given resolver.
		 * @param argumentResolver the argument resolver to add
		 * @return a new composite {@link ArgumentResolver} instance
		 */
		default ArgumentResolver and(ArgumentResolver argumentResolver) {
			return from(type -> {
				Object resolved = resolve(type);
				return (resolved != null) ? resolved : argumentResolver.resolve(type);
			});
		}

		/**
		 * Factory method that returns a {@link ArgumentResolver} that always
		 * returns {@code null}.
		 * @return a new {@link ArgumentResolver} instance
		 */
		static ArgumentResolver none() {
			return from(type -> null);
		}

		/**
		 * Factory method that can be used to create a {@link ArgumentResolver}
		 * that resolves only the given type.
		 * @param <T> the argument type
		 * @param type the argument type
		 * @param value the argument value
		 * @return a new {@link ArgumentResolver} instance
		 */
		static <T> ArgumentResolver of(Class<T> type, T value) {
			return ofSupplied(type, () -> value);
		}

		/**
		 * Factory method that can be used to create a {@link ArgumentResolver}
		 * that resolves only the given type.
		 * @param <T> the argument type
		 * @param type the argument type
		 * @param valueSupplier the argument value supplier
		 * @return a new {@link ArgumentResolver} instance
		 */
		static <T> ArgumentResolver ofSupplied(Class<T> type, Supplier<T> valueSupplier) {
			return from(candidateType -> (candidateType.equals(type) ? valueSupplier.get() : null));
		}

		/**
		 * Factory method that creates a new {@link ArgumentResolver} from a
		 * lambda friendly function. The given function is provided with the
		 * argument type and must provide an instance of that type or {@code null}.
		 * @param function the resolver function
		 * @return a new {@link ArgumentResolver} instance backed by the function
		 */
		static ArgumentResolver from(Function<Class<?>, Object> function) {
			return new ArgumentResolver() {

				@SuppressWarnings("unchecked")
				@Override
				public <T> T resolve(Class<T> type) {
					return (T) function.apply(type);
				}

			};
		}

	}

	/**
	 * Strategy for handling a failure that occurs when instantiating a factory.
	 *
	 * @since 6.0
	 * @see FailureHandler#throwing()
	 * @see FailureHandler#logging(Log)
	 */
	@FunctionalInterface
	public interface FailureHandler {

		/**
		 * Handle the {@code failure} that occurred when instantiating the
		 * {@code factoryImplementationName} that was expected to be of the
		 * given {@code factoryType}.
		 * @param factoryType the type of the factory
		 * @param factoryImplementationName the name of the factory implementation
		 * @param failure the failure that occurred
		 * @see #throwing()
		 * @see #logging
		 */
		void handleFailure(Class<?> factoryType, String factoryImplementationName, Throwable failure);

		/**
		 * Return a new {@link FailureHandler} that handles
		 * errors by throwing an {@link IllegalArgumentException}.
		 * @return a new {@link FailureHandler} instance
		 */
		static FailureHandler throwing() {
			return throwing(IllegalArgumentException::new);
		}

		/**
		 * Return a new {@link FailureHandler} that handles
		 * errors by throwing an exception.
		 * @param exceptionFactory factory used to create the exception
		 * @return a new {@link FailureHandler} instance
		 */
		static FailureHandler throwing(BiFunction<String, Throwable, ? extends RuntimeException> exceptionFactory) {
			return handleMessage((message, failure) -> {
				throw exceptionFactory.apply(message.get(), failure);
			});
		}

		/**
		 * Return a new {@link FailureHandler} that handles
		 * errors by logging trace messages.
		 * @param logger the logger used to log message
		 * @return a new {@link FailureHandler} instance
		 */
		static FailureHandler logging(Log logger) {
			return handleMessage((message, failure) -> logger.trace(LogMessage.of(message), failure));
		}

		/**
		 * Return a new {@link FailureHandler} that handles
		 * errors with using a standard formatted message.
		 * @param messageHandler the message handler used to handle the problem
		 * @return a new {@link FailureHandler} instance
		 */
		static FailureHandler handleMessage(BiConsumer<Supplier<String>, Throwable> messageHandler) {
			return (factoryType, factoryImplementationName, failure) -> {
				Supplier<String> message = () -> "Unable to instantiate factory class [" + factoryImplementationName +
						"] for factory type [" + factoryType.getName() + "]";
				messageHandler.accept(message, failure);
			};
		}

	}

}
