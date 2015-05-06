/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Utility class used to collect and merge all annotation attributes on a
 * given {@link AnnotatedElement}, including those declared via meta-annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
public class AnnotatedElementUtils {

	private static final Boolean CONTINUE = null;


	/**
	 * <em>Get</em> the fully qualified class names of all meta-annotation
	 * types <em>present</em> on the annotation (of the specified
	 * {@code annotationType}) on the supplied {@link AnnotatedElement}.
	 *
	 * <p>This method finds all meta-annotations in the annotation hierarchy
	 * above the specified annotation.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the annotation type on which to find
	 * meta-annotations; never {@code null}
	 * @return the names of all meta-annotations present on the annotation,
	 * or {@code null} if not found
	 * @see #getMetaAnnotationTypes(AnnotatedElement, String)
	 * @see #hasMetaAnnotationTypes
	 */
	public static Set<String> getMetaAnnotationTypes(AnnotatedElement element,
			Class<? extends Annotation> annotationType) {
		Assert.notNull(annotationType, "annotationType must not be null");
		return getMetaAnnotationTypes(element, annotationType.getName());
	}

	/**
	 * <em>Get</em> the fully qualified class names of all meta-annotation
	 * types <em>present</em> on the annotation (of the specified
	 * {@code annotationType}) on the supplied {@link AnnotatedElement}.
	 *
	 * <p>This method finds all meta-annotations in the annotation hierarchy
	 * above the specified annotation.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type on which to find meta-annotations; never {@code null} or empty
	 * @return the names of all meta-annotations present on the annotation,
	 * or {@code null} if not found
	 * @see #getMetaAnnotationTypes(AnnotatedElement, Class)
	 * @see #hasMetaAnnotationTypes
	 */
	public static Set<String> getMetaAnnotationTypes(AnnotatedElement element, String annotationType) {
		Assert.notNull(element, "AnnotatedElement must not be null");
		Assert.hasText(annotationType, "annotationType must not be null or empty");

		final Set<String> types = new LinkedHashSet<String>();

		try {
			Annotation annotation = getAnnotation(element, annotationType);
			if (annotation != null) {
				processWithGetSemantics(annotation.annotationType(), annotationType, new SimpleAnnotationProcessor<Object>() {

					@Override
					public Object process(Annotation annotation, int metaDepth) {
						types.add(annotation.annotationType().getName());
						return CONTINUE;
					}
				}, new HashSet<AnnotatedElement>(), 1);
			}
		}
		catch (Throwable ex) {
			throw new IllegalStateException("Failed to introspect annotations on " + element, ex);
		}

		return (types.isEmpty() ? null : types);
	}

	/**
	 * Determine if the supplied {@link AnnotatedElement} is annotated with
	 * a <em>composed annotation</em> that is meta-annotated with an
	 * annotation of the specified {@code annotationType}.
	 *
	 * <p>This method finds all meta-annotations in the annotation hierarchy
	 * above the specified element.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the meta-annotation
	 * type to find; never {@code null} or empty
	 * @return {@code true} if a matching meta-annotation is present
	 * @see #getMetaAnnotationTypes
	 */
	public static boolean hasMetaAnnotationTypes(AnnotatedElement element, final String annotationType) {
		Assert.notNull(element, "AnnotatedElement must not be null");
		Assert.hasText(annotationType, "annotationType must not be null or empty");

		return Boolean.TRUE.equals(processWithGetSemantics(element, annotationType, new SimpleAnnotationProcessor<Boolean>() {
			@Override
			public Boolean process(Annotation annotation, int metaDepth) {
				boolean found = annotation.annotationType().getName().equals(annotationType);
				return ((found && (metaDepth > 0)) ? Boolean.TRUE : CONTINUE);
			}
		}));
	}

	/**
	 * Determine if an annotation of the specified {@code annotationType}
	 * is <em>present</em> on the supplied {@link AnnotatedElement} or
	 * within the annotation hierarchy above the specified element.
	 *
	 * <p>If this method returns {@code true}, then {@link #getAnnotationAttributes}
	 * will return a non-null value.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @return {@code true} if a matching annotation is present
	 */
	public static boolean isAnnotated(AnnotatedElement element, final String annotationType) {
		Assert.notNull(element, "AnnotatedElement must not be null");
		Assert.hasText(annotationType, "annotationType must not be null or empty");

		return Boolean.TRUE.equals(processWithGetSemantics(element, annotationType, new SimpleAnnotationProcessor<Boolean>() {
			@Override
			public Boolean process(Annotation annotation, int metaDepth) {
				boolean found = annotation.annotationType().getName().equals(annotationType);
				return (found ? Boolean.TRUE : CONTINUE);
			}
		}));
	}

	/**
	 * <em>Get</em> annotation attributes of the specified {@code annotationType}
	 * in the annotation hierarchy of the supplied {@link AnnotatedElement},
	 * and merge the results into an {@link AnnotationAttributes} map.
	 *
	 * <p>Delegates to {@link #getAnnotationAttributes(AnnotatedElement, String, boolean, boolean)},
	 * supplying {@code false} for {@code classValuesAsString} and {@code nestedAnnotationsAsMap}.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @return the merged {@code AnnotationAttributes}, or {@code null} if
	 * not found
	 * @see #getAnnotationAttributes(AnnotatedElement, String, boolean, boolean)
	 */
	public static AnnotationAttributes getAnnotationAttributes(AnnotatedElement element, String annotationType) {
		return getAnnotationAttributes(element, annotationType, false, false);
	}

	/**
	 * <em>Get</em> annotation attributes of the specified {@code annotationType}
	 * in the annotation hierarchy of the supplied {@link AnnotatedElement},
	 * and merge the results into an {@link AnnotationAttributes} map.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @param classValuesAsString whether to convert Class references into
	 * Strings or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to convert nested Annotation
	 * instances into {@link AnnotationAttributes} maps or to preserve them
	 * as Annotation instances
	 * @return the merged {@code AnnotationAttributes}, or {@code null} if
	 * not found
	 */
	public static AnnotationAttributes getAnnotationAttributes(AnnotatedElement element, String annotationType,
			boolean classValuesAsString, boolean nestedAnnotationsAsMap) {
		return processWithGetSemantics(element, annotationType, new MergedAnnotationAttributesProcessor(annotationType,
			classValuesAsString, nestedAnnotationsAsMap));
	}

	/**
	 * <em>Find</em> annotation attributes of the specified {@code annotationType}
	 * in the annotation hierarchy of the supplied {@link AnnotatedElement},
	 * and merge the results into an {@link AnnotationAttributes} map.
	 *
	 * <p>If the annotated element is a class, this algorithm will additionally
	 * search on interfaces and superclasses.
	 * <p>If the annotated element is a method, this algorithm will additionally
	 * search on methods in interfaces and superclasses.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the annotation type to find; never {@code null}
	 * @return the merged {@code AnnotationAttributes}, or {@code null} if
	 * not found
	 * @since 4.2
	 */
	public static AnnotationAttributes findAnnotationAttributes(AnnotatedElement element,
			Class<? extends Annotation> annotationType) {
		Assert.notNull(annotationType, "annotationType must not be null");
		return findAnnotationAttributes(element, annotationType.getName());
	}

	/**
	 * <em>Find</em> annotation attributes of the specified {@code annotationType}
	 * in the annotation hierarchy of the supplied {@link AnnotatedElement},
	 * and merge the results into an {@link AnnotationAttributes} map.
	 *
	 * <p>If the annotated element is a class, this algorithm will additionally
	 * search on interfaces and superclasses.
	 * <p>If the annotated element is a method, this algorithm will additionally
	 * search on methods in interfaces and superclasses.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @return the merged {@code AnnotationAttributes}, or {@code null} if
	 * not found
	 * @since 4.2
	 */
	public static AnnotationAttributes findAnnotationAttributes(AnnotatedElement element, String annotationType) {
		return findAnnotationAttributes(element, annotationType, false, false);
	}

	/**
	 * <em>Find</em> annotation attributes of the specified {@code annotationType}
	 * in the annotation hierarchy of the supplied {@link AnnotatedElement},
	 * and merge the results into an {@link AnnotationAttributes} map.
	 *
	 * <p>If the annotated element is a class, this algorithm will additionally
	 * search on interfaces and superclasses.
	 * <p>If the annotated element is a method, this algorithm will additionally
	 * search on methods in interfaces and superclasses.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @param classValuesAsString whether to convert Class references into
	 * Strings or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to convert nested Annotation
	 * instances into {@link AnnotationAttributes} maps or to preserve them
	 * as Annotation instances
	 * @return the merged {@code AnnotationAttributes}, or {@code null} if
	 * not found
	 * @since 4.2
	 */
	public static AnnotationAttributes findAnnotationAttributes(AnnotatedElement element, String annotationType,
			boolean classValuesAsString, boolean nestedAnnotationsAsMap) {
		return findAnnotationAttributes(element, annotationType, true, true, true, true, classValuesAsString,
			nestedAnnotationsAsMap);
	}

	/**
	 * <em>Find</em> annotation attributes of the specified {@code annotationType}
	 * in the annotation hierarchy of the supplied {@link AnnotatedElement},
	 * and merge the results into an {@link AnnotationAttributes} map.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @param searchOnInterfaces whether to search on interfaces, if the
	 * annotated element is a class
	 * @param searchOnSuperclasses whether to search on superclasses, if
	 * the annotated element is a class
	 * @param searchOnMethodsInInterfaces whether to search on methods in
	 * interfaces, if the annotated element is a method
	 * @param searchOnMethodsInSuperclasses whether to search on methods
	 * in superclasses, if the annotated element is a method
	 * @param classValuesAsString whether to convert Class references into
	 * Strings or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to convert nested Annotation
	 * instances into {@link AnnotationAttributes} maps or to preserve them
	 * as Annotation instances
	 * @return the merged {@code AnnotationAttributes}, or {@code null} if
	 * not found
	 * @since 4.2
	 */
	private static AnnotationAttributes findAnnotationAttributes(AnnotatedElement element, String annotationType,
			boolean searchOnInterfaces, boolean searchOnSuperclasses, boolean searchOnMethodsInInterfaces,
			boolean searchOnMethodsInSuperclasses, boolean classValuesAsString, boolean nestedAnnotationsAsMap) {

		return processWithFindSemantics(element, annotationType, searchOnInterfaces, searchOnSuperclasses,
			searchOnMethodsInInterfaces, searchOnMethodsInSuperclasses, new MergedAnnotationAttributesProcessor(
				annotationType, classValuesAsString, nestedAnnotationsAsMap));
	}

	/**
	 * <em>Get</em> the annotation attributes of <strong>all</strong> annotations
	 * of the specified {@code annotationType} in the annotation hierarchy above
	 * the supplied {@link AnnotatedElement} and store the results in a
	 * {@link MultiValueMap}.
	 *
	 * <p>Note: in contrast to {@link #getAnnotationAttributes(AnnotatedElement, String)},
	 * this method does <em>not</em> take attribute overrides into account.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @return a {@link MultiValueMap} keyed by attribute name, containing
	 * the annotation attributes from all annotations found, or {@code null}
	 * if not found
	 * @see #getAllAnnotationAttributes(AnnotatedElement, String, boolean, boolean)
	 */
	public static MultiValueMap<String, Object> getAllAnnotationAttributes(AnnotatedElement element, String annotationType) {
		return getAllAnnotationAttributes(element, annotationType, false, false);
	}

	/**
	 * <em>Get</em> the annotation attributes of <strong>all</strong> annotations
	 * of the specified {@code annotationType} in the annotation hierarchy above
	 * the supplied {@link AnnotatedElement} and store the results in a
	 * {@link MultiValueMap}.
	 *
	 * <p>Note: in contrast to {@link #getAnnotationAttributes(AnnotatedElement, String)},
	 * this method does <em>not</em> take attribute overrides into account.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @param classValuesAsString whether to convert Class references into
	 * Strings or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to convert nested Annotation
	 * instances into {@link AnnotationAttributes} maps or to preserve them
	 * as Annotation instances
	 * @return a {@link MultiValueMap} keyed by attribute name, containing
	 * the annotation attributes from all annotations found, or {@code null}
	 * if not found
	 */
	public static MultiValueMap<String, Object> getAllAnnotationAttributes(AnnotatedElement element,
			final String annotationType, final boolean classValuesAsString, final boolean nestedAnnotationsAsMap) {

		final MultiValueMap<String, Object> attributesMap = new LinkedMultiValueMap<String, Object>();

		processWithGetSemantics(element, annotationType, new SimpleAnnotationProcessor<Void>() {

			@Override
			public Void process(Annotation annotation, int metaDepth) {
				boolean found = annotation.annotationType().getName().equals(annotationType);
				if (found) {
					AnnotationAttributes annotationAttributes = AnnotationUtils.getAnnotationAttributes(annotation,
						classValuesAsString, nestedAnnotationsAsMap);
					for (Map.Entry<String, Object> entry : annotationAttributes.entrySet()) {
						attributesMap.add(entry.getKey(), entry.getValue());
					}
				}

				// Continue searching...
				return null;
			}
		});

		return (attributesMap.isEmpty() ? null : attributesMap);
	}

	/**
	 * Process all annotations of the specified {@code annotationType} and
	 * recursively all meta-annotations on the specified {@code element}.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @param processor the processor to delegate to
	 * @return the result of the processor, potentially {@code null}
	 */
	private static <T> T processWithGetSemantics(AnnotatedElement element, String annotationType, Processor<T> processor) {
		try {
			return processWithGetSemantics(element, annotationType, processor, new HashSet<AnnotatedElement>(), 0);
		}
		catch (Throwable ex) {
			throw new IllegalStateException("Failed to introspect annotations on " + element, ex);
		}
	}

	/**
	 * Perform the search algorithm for the {@link #processWithGetSemantics}
	 * method, avoiding endless recursion by tracking which annotated elements
	 * have already been <em>visited</em>.
	 *
	 * <p>The {@code metaDepth} parameter is explained in the
	 * {@link Processor#process process()} method of the {@link Processor}
	 * API.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @param processor the processor to delegate to
	 * @param visited the set of annotated elements that have already been visited
	 * @param metaDepth the meta-depth of the annotation
	 * @return the result of the processor, potentially {@code null}
	 */
	private static <T> T processWithGetSemantics(AnnotatedElement element, String annotationType,
			Processor<T> processor, Set<AnnotatedElement> visited, int metaDepth) {

		Assert.notNull(element, "AnnotatedElement must not be null");
		Assert.hasText(annotationType, "annotationType must not be null or empty");

		if (visited.add(element)) {
			try {
				// Local annotations: declared OR inherited
				Annotation[] annotations = element.getAnnotations();

				// Search in local annotations
				for (Annotation annotation : annotations) {
					if (!AnnotationUtils.isInJavaLangAnnotationPackage(annotation)
							&& (annotation.annotationType().getName().equals(annotationType) || metaDepth > 0)) {
						T result = processor.process(annotation, metaDepth);
						if (result != null) {
							return result;
						}
					}
				}

				// Search in meta annotations on local annotations
				for (Annotation annotation : annotations) {
					if (!AnnotationUtils.isInJavaLangAnnotationPackage(annotation)) {
						T result = processWithGetSemantics(annotation.annotationType(), annotationType, processor,
							visited, metaDepth + 1);
						if (result != null) {
							processor.postProcess(annotation, result);
							return result;
						}
					}
				}

			}
			catch (Exception ex) {
				AnnotationUtils.logIntrospectionFailure(element, ex);
			}
		}
		return null;
	}

	/**
	 * Process all annotations of the specified {@code annotationType} and
	 * recursively all meta-annotations on the specified {@code element}.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @param searchOnInterfaces whether to search on interfaces, if the
	 * annotated element is a class
	 * @param searchOnSuperclasses whether to search on superclasses, if
	 * the annotated element is a class
	 * @param searchOnMethodsInInterfaces whether to search on methods in
	 * interfaces, if the annotated element is a method
	 * @param searchOnMethodsInSuperclasses whether to search on methods
	 * in superclasses, if the annotated element is a method
	 * @param processor the processor to delegate to
	 * @return the result of the processor, potentially {@code null}
	 */
	private static <T> T processWithFindSemantics(AnnotatedElement element, String annotationType,
			boolean searchOnInterfaces, boolean searchOnSuperclasses, boolean searchOnMethodsInInterfaces,
			boolean searchOnMethodsInSuperclasses, Processor<T> processor) {

		try {
			return processWithFindSemantics(element, annotationType, searchOnInterfaces, searchOnSuperclasses,
				searchOnMethodsInInterfaces, searchOnMethodsInSuperclasses, processor, new HashSet<AnnotatedElement>(), 0);
		}
		catch (Throwable ex) {
			throw new IllegalStateException("Failed to introspect annotations on " + element, ex);
		}
	}

	/**
	 * Perform the search algorithm for the {@link #processWithFindSemantics}
	 * method, avoiding endless recursion by tracking which annotated elements
	 * have already been <em>visited</em>.
	 *
	 * <p>The {@code metaDepth} parameter is explained in the
	 * {@link Processor#process process()} method of the {@link Processor}
	 * API.
	 *
	 * @param element the annotated element; never {@code null}
	 * @param annotationType the fully qualified class name of the annotation
	 * type to find; never {@code null} or empty
	 * @param searchOnInterfaces whether to search on interfaces, if the
	 * annotated element is a class
	 * @param searchOnSuperclasses whether to search on superclasses, if
	 * the annotated element is a class
	 * @param searchOnMethodsInInterfaces whether to search on methods in
	 * interfaces, if the annotated element is a method
	 * @param searchOnMethodsInSuperclasses whether to search on methods
	 * in superclasses, if the annotated element is a method
	 * @param processor the processor to delegate to
	 * @param visited the set of annotated elements that have already been visited
	 * @param metaDepth the meta-depth of the annotation
	 * @return the result of the processor, potentially {@code null}
	 */
	private static <T> T processWithFindSemantics(AnnotatedElement element, String annotationType,
			boolean searchOnInterfaces, boolean searchOnSuperclasses, boolean searchOnMethodsInInterfaces,
			boolean searchOnMethodsInSuperclasses, Processor<T> processor, Set<AnnotatedElement> visited, int metaDepth) {

		Assert.notNull(element, "AnnotatedElement must not be null");
		Assert.hasText(annotationType, "annotationType must not be null or empty");

		if (visited.add(element)) {
			try {

				// Local annotations: declared or (declared + inherited).
				Annotation[] annotations = (searchOnSuperclasses ? element.getDeclaredAnnotations()
						: element.getAnnotations());

				// Search in local annotations
				for (Annotation annotation : annotations) {
					if (!AnnotationUtils.isInJavaLangAnnotationPackage(annotation)
							&& (annotation.annotationType().getName().equals(annotationType) || metaDepth > 0)) {
						T result = processor.process(annotation, metaDepth);
						if (result != null) {
							return result;
						}
					}
				}

				// Search in meta annotations on local annotations
				for (Annotation annotation : annotations) {
					if (!AnnotationUtils.isInJavaLangAnnotationPackage(annotation)) {
						T result = processWithFindSemantics(annotation.annotationType(), annotationType,
							searchOnInterfaces, searchOnSuperclasses, searchOnMethodsInInterfaces,
							searchOnMethodsInSuperclasses, processor, visited, metaDepth + 1);
						if (result != null) {
							processor.postProcess(annotation, result);
							return result;
						}
					}
				}

				if (element instanceof Method) {
					Method method = (Method) element;

					// Search on possibly bridged method
					Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
					T result = processWithFindSemantics(resolvedMethod, annotationType, searchOnInterfaces,
						searchOnSuperclasses, searchOnMethodsInInterfaces, searchOnMethodsInSuperclasses, processor,
						visited, metaDepth);
					if (result != null) {
						return result;
					}

					// Search on methods in interfaces declared locally
					if (searchOnMethodsInInterfaces) {
						Class<?>[] ifcs = method.getDeclaringClass().getInterfaces();
						result = searchOnInterfaces(method, annotationType, searchOnInterfaces, searchOnSuperclasses,
							searchOnMethodsInInterfaces, searchOnMethodsInSuperclasses, processor, visited, metaDepth,
							ifcs);
						if (result != null) {
							return result;
						}
					}

					// Search on methods in class hierarchy and interface hierarchy
					if (searchOnMethodsInSuperclasses) {
						Class<?> clazz = method.getDeclaringClass();
						while (true) {
							clazz = clazz.getSuperclass();
							if (clazz == null || clazz.equals(Object.class)) {
								break;
							}

							try {
								Method equivalentMethod = clazz.getDeclaredMethod(method.getName(),
									method.getParameterTypes());
								Method resolvedEquivalentMethod = BridgeMethodResolver.findBridgedMethod(equivalentMethod);
								result = processWithFindSemantics(resolvedEquivalentMethod, annotationType,
									searchOnInterfaces, searchOnSuperclasses, searchOnMethodsInInterfaces,
									searchOnMethodsInSuperclasses, processor, visited, metaDepth);
								if (result != null) {
									return result;
								}
							}
							catch (NoSuchMethodException ex) {
								// No equivalent method found
							}

							// Search on interfaces declared on superclass
							if (searchOnMethodsInInterfaces) {
								result = searchOnInterfaces(method, annotationType, searchOnInterfaces,
									searchOnSuperclasses, searchOnMethodsInInterfaces, searchOnMethodsInSuperclasses,
									processor, visited, metaDepth, clazz.getInterfaces());
								if (result != null) {
									return result;
								}
							}
						}
					}
				}

				if (element instanceof Class) {
					Class<?> clazz = (Class<?>) element;

					// Search on interfaces
					if (searchOnInterfaces) {
						for (Class<?> ifc : clazz.getInterfaces()) {
							T result = processWithFindSemantics(ifc, annotationType, searchOnInterfaces,
								searchOnSuperclasses, searchOnMethodsInInterfaces, searchOnMethodsInSuperclasses,
								processor, visited, metaDepth);
							if (result != null) {
								return result;
							}
						}
					}

					// Search on superclass
					if (searchOnSuperclasses) {
						Class<?> superclass = clazz.getSuperclass();
						if (superclass != null && !superclass.equals(Object.class)) {
							T result = processWithFindSemantics(superclass, annotationType, searchOnInterfaces,
								searchOnSuperclasses, searchOnMethodsInInterfaces, searchOnMethodsInSuperclasses,
								processor, visited, metaDepth);
							if (result != null) {
								return result;
							}
						}
					}
				}
			}
			catch (Exception ex) {
				AnnotationUtils.logIntrospectionFailure(element, ex);
			}
		}
		return null;
	}

	private static <T> T searchOnInterfaces(Method method, String annotationType, boolean searchOnInterfaces,
			boolean searchOnSuperclasses, boolean searchOnMethodsInInterfaces, boolean searchOnMethodsInSuperclasses,
			Processor<T> processor, Set<AnnotatedElement> visited, int metaDepth, Class<?>[] ifcs) {

		for (Class<?> iface : ifcs) {
			if (AnnotationUtils.isInterfaceWithAnnotatedMethods(iface)) {
				try {
					Method equivalentMethod = iface.getMethod(method.getName(), method.getParameterTypes());
					T result = processWithFindSemantics(equivalentMethod, annotationType, searchOnInterfaces,
						searchOnSuperclasses, searchOnMethodsInInterfaces, searchOnMethodsInSuperclasses, processor,
						visited, metaDepth);

					if (result != null) {
						return result;
					}
				}
				catch (NoSuchMethodException ex) {
					// Skip this interface - it doesn't have the method...
				}
			}
		}

		return null;
	}

	private static Annotation getAnnotation(AnnotatedElement element, String annotationType) {
		for (Annotation annotation : element.getAnnotations()) {
			if (annotation.annotationType().getName().equals(annotationType)) {
				return annotation;
			}
		}
		return null;
	}


	/**
	 * Callback interface that is used to process annotations during a search.
	 *
	 * <p>Depending on the use case, a processor may choose to
	 * {@linkplain #process} a single target annotation, multiple target
	 * annotations, or all annotations discovered by the currently executing
	 * search. The term "target" in this context refers to a matching
	 * annotation (i.e., a specific annotation type that was found during
	 * the search). Returning a non-null value from the {@link #process}
	 * method instructs the search algorithm to stop searching further;
	 * whereas, returning {@code null} from the {@link #process} method
	 * instructs the search algorithm to continue searching for additional
	 * annotations.
	 *
	 * <p>Processors can optionally {@linkplain #postProcess post-process}
	 * the result of the {@link #process} method as the search algorithm
	 * goes back down the annotation hierarchy from an invocation of
	 * {@link #process} that returned a non-null value down to the
	 * {@link AnnotatedElement} that was supplied as the starting point to
	 * the search algorithm.
	 *
	 * @param <T> the type of result returned by the processor
	 */
	private static interface Processor<T> {

		/**
		 * Process the supplied annotation.
		 *
		 * <p>Depending on the use case, the supplied annotation may be an
		 * actual target annotation that has been found by the search
		 * algorithm, or it may be some other annotation within the
		 * annotation hierarchy. In the latter case, the {@code metaDepth}
		 * should have a value greater than {@code 0}. In any case, it is
		 * up to concrete implementations of this method to decide what to
		 * do with the supplied annotation.
		 *
		 * <p>The {@code metaDepth} parameter represents the depth of the
		 * annotation relative to the first annotated element in the
		 * annotation hierarchy. For example, an annotation that is
		 * <em>present</em> on a non-annotation element will have a depth
		 * of 0; a meta-annotation will have a depth of 1; and a
		 * meta-meta-annotation will have a depth of 2; etc.
		 *
		 * @param annotation the annotation to process
		 * @param metaDepth the meta-depth of the annotation
		 * @return the result of the processing, or {@code null} to continue
		 * searching for additional annotations
		 */
		T process(Annotation annotation, int metaDepth);

		/**
		 * Post-process the result returned by the {@link #process} method.
		 *
		 * <p>The {@code annotation} supplied to this method is an annotation
		 * that is present in the annotation hierarchy, between the initial
		 * {@link AnnotatedElement} and an invocation of {@link #process}
		 * that returned a non-null value.
		 *
		 * @param annotation the annotation to post-process
		 * @param result the result to post-process
		 */
		void postProcess(Annotation annotation, T result);
	}

	/**
	 * {@link Processor} that {@linkplain #process processes} annotations
	 * but does not {@link #postProcess} results.
	 * @since 4.2
	 */
	private abstract static class SimpleAnnotationProcessor<T> implements Processor<T> {

		/**
		 * <em>No-op</em>.
		 */
		@Override
		public final void postProcess(Annotation annotation, T result) {
			/* no-op */
		}
	}

	/**
	 * {@link Processor} that gets the {@code AnnotationAttributes} for the
	 * target annotation during the {@link #process} phase and then merges
	 * annotation attributes from lower levels in the annotation hierarchy
	 * during the {@link #postProcess} phase.
	 * @see AnnotationUtils#getAnnotationAttributes(Annotation)
	 * @since 4.2
	 */
	private static class MergedAnnotationAttributesProcessor implements Processor<AnnotationAttributes> {

		private final String annotationType;
		private final boolean classValuesAsString;
		private final boolean nestedAnnotationsAsMap;


		MergedAnnotationAttributesProcessor(String annotationType, boolean classValuesAsString, boolean nestedAnnotationsAsMap) {
			this.annotationType = annotationType;
			this.classValuesAsString = classValuesAsString;
			this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
		}

		@Override
		public AnnotationAttributes process(Annotation annotation, int metaDepth) {
			boolean found = annotation.annotationType().getName().equals(annotationType);
			return (found ? AnnotationUtils.getAnnotationAttributes(annotation, classValuesAsString, nestedAnnotationsAsMap) : null);
		}

		@Override
		public void postProcess(Annotation annotation, AnnotationAttributes attributes) {
			for (String key : attributes.keySet()) {
				if (!AnnotationUtils.VALUE.equals(key)) {
					Object value = AnnotationUtils.getValue(annotation, key);
					if (value != null) {
						attributes.put(key, AnnotationUtils.adaptValue(value, classValuesAsString, nestedAnnotationsAsMap));
					}
				}
			}
		}
	}

}
