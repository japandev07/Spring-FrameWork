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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * General utility methods for working with annotations, handling meta-annotations,
 * bridge methods (which the compiler generates for generic declarations) as well
 * as super methods (for optional <em>annotation inheritance</em>).
 *
 * <p>Note that most of the features of this class are not provided by the
 * JDK's introspection facilities themselves.
 *
 * <p>As a general rule for runtime-retained annotations (e.g. for transaction
 * control, authorization, or service exposure), always use the lookup methods
 * on this class (e.g., {@link #findAnnotation(Method, Class)},
 * {@link #getAnnotation(Method, Class)}, and {@link #getAnnotations(Method)})
 * instead of the plain annotation lookup methods in the JDK. You can still
 * explicitly choose between a <em>get</em> lookup on the given class level only
 * ({@link #getAnnotation(Method, Class)}) and a <em>find</em> lookup in the entire
 * inheritance hierarchy of the given method ({@link #findAnnotation(Method, Class)}).
 *
 * <h3>Meta-annotation Support</h3>
 * <p>Most {@code find*()} methods and some {@code get*()} methods in this class
 * provide support for meta-annotations. Consult the Javadoc for each method in
 * this class for details. For support for meta-annotations with
 * <em>attribute overrides</em> in <em>composed annotations</em>, use
 * {@link AnnotatedElementUtils} instead.
 *
 * <h3>Search Scope</h3>
 * <p>The search algorithms used by methods in this class stop searching for
 * an annotation once the first annotation of the specified type has been
 * found. As a consequence, additional annotations of the specified type will
 * be silently ignored.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Mark Fisher
 * @author Chris Beams
 * @author Phillip Webb
 * @since 2.0
 * @see java.lang.reflect.AnnotatedElement#getAnnotations()
 * @see java.lang.reflect.AnnotatedElement#getAnnotation(Class)
 * @see java.lang.reflect.AnnotatedElement#getDeclaredAnnotations()
 */
public abstract class AnnotationUtils {

	/** The attribute name for annotations with a single element */
	public static final String VALUE = "value";


	private static final Map<AnnotationCacheKey, Annotation> findAnnotationCache =
			new ConcurrentReferenceHashMap<AnnotationCacheKey, Annotation>(256);

	private static final Map<Class<?>, Boolean> annotatedInterfaceCache =
			new ConcurrentReferenceHashMap<Class<?>, Boolean>(256);

	private static transient Log logger;


	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied
	 * annotation: either the given annotation itself or a direct meta-annotation
	 * thereof.
	 * <p>Note that this method supports only a single level of meta-annotations.
	 * For support for arbitrary levels of meta-annotations, use one of the
	 * {@code find*()} methods instead.
	 * @param ann the Annotation to check
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.0
	 */
	@SuppressWarnings("unchecked")
	public static <A extends Annotation> A getAnnotation(Annotation ann, Class<A> annotationType) {
		if (annotationType.isInstance(ann)) {
			return (A) ann;
		}
		try {
			return ann.annotationType().getAnnotation(annotationType);
		}
		catch (Exception ex) {
			// Assuming nested Class values not resolvable within annotation attributes...
			logIntrospectionFailure(ann.annotationType(), ex);
			return null;
		}
	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied
	 * {@link AnnotatedElement}, where the {@code AnnotatedElement} is either
	 * directly annotated or meta-annotated with the {@code annotationType}.
	 * <p>Note that this method supports only a single level of meta-annotations.
	 * For support for arbitrary levels of meta-annotations, use
	 * {@link #findAnnotation(AnnotatedElement, Class)} instead.
	 * @param annotatedElement the {@code AnnotatedElement} from which to get the annotation
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 3.1
	 */
	public static <A extends Annotation> A getAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
		try {
			A ann = annotatedElement.getAnnotation(annotationType);
			if (ann == null) {
				for (Annotation metaAnn : annotatedElement.getAnnotations()) {
					ann = metaAnn.annotationType().getAnnotation(annotationType);
					if (ann != null) {
						break;
					}
				}
			}
			return ann;
		}
		catch (Exception ex) {
			// Assuming nested Class values not resolvable within annotation attributes...
			logIntrospectionFailure(annotatedElement, ex);
			return null;
		}
	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the
	 * supplied {@link Method}, where the method is either directly annotated
	 * or meta-annotated with the {@code annotationType}.
	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>Note that this method supports only a single level of meta-annotations.
	 * For support for arbitrary levels of meta-annotations, use
	 * {@link #findAnnotation(Method, Class)} instead.
	 * @param method the method to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @return the first matching annotation, or {@code null} if not found
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 * @see #getAnnotation(AnnotatedElement, Class)
	 */
	public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
		Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
		return getAnnotation((AnnotatedElement) resolvedMethod, annotationType);
	}

	/**
	 * Get all {@link Annotation Annotations} that are <em>present</em> on the
	 * supplied {@link AnnotatedElement}.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * @param annotatedElement the Method, Constructor or Field to retrieve annotations from
	 * @return the annotations found, an empty array, or {@code null} if not
	 * resolvable (e.g. because nested Class values in annotation attributes
	 * failed to resolve at runtime)
	 * @since 4.0.8
	 */
	public static Annotation[] getAnnotations(AnnotatedElement annotatedElement) {
		try {
			return annotatedElement.getAnnotations();
		}
		catch (Exception ex) {
			// Assuming nested Class values not resolvable within annotation attributes...
			logIntrospectionFailure(annotatedElement, ex);
		}
		return null;
	}

	/**
	 * Get all {@link Annotation Annotations} that are <em>present</em on the
	 * supplied {@link Method}.
	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * @param method the Method to retrieve annotations from
	 * @return the annotations found, an empty array, or {@code null} if not
	 * resolvable (e.g. because nested Class values in annotation attributes
	 * failed to resolve at runtime)
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 * @see AnnotatedElement#getAnnotations()
	 */
	public static Annotation[] getAnnotations(Method method) {
		try {
			return BridgeMethodResolver.findBridgedMethod(method).getAnnotations();
		}
		catch (Exception ex) {
			// Assuming nested Class values not resolvable within annotation attributes...
			logIntrospectionFailure(method, ex);
		}
		return null;
	}

	/**
	 * Get the possibly repeating {@link Annotation}s of {@code annotationType}
	 * from the supplied {@link Method}.
	 * <p>Deals with both a single direct annotation and repeating annotations
	 * nested within a containing annotation.
	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>directly present</em> on the supplied method.
	 * @param method the method to look for annotations on
	 * @param containerAnnotationType the class of the container that holds the annotations
	 * @param annotationType the annotation type to look for
	 * @return the annotations found or an empty set; never {@code null}
	 * @since 4.0
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 * @see java.lang.annotation.Repeatable
	 */
	public static <A extends Annotation> Set<A> getRepeatableAnnotation(Method method,
			Class<? extends Annotation> containerAnnotationType, Class<A> annotationType) {

		Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
		return getRepeatableAnnotation((AnnotatedElement) resolvedMethod, containerAnnotationType, annotationType);
	}

	/**
	 * Get the possibly repeating {@link Annotation}s of {@code annotationType} from the
	 * supplied {@link AnnotatedElement}.
	 * <p>Deals with both a single direct annotation and repeating annotations
	 * nested within a containing annotation.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>directly present</em> on the supplied element.
	 * @param annotatedElement the element to look for annotations on
	 * @param containerAnnotationType the class of the container that holds the annotations
	 * @param annotationType the annotation type to look for
	 * @return the annotations found or an empty set; never {@code null}
	 * @since 4.0
	 * @see java.lang.annotation.Repeatable
	 */
	public static <A extends Annotation> Set<A> getRepeatableAnnotation(AnnotatedElement annotatedElement,
			Class<? extends Annotation> containerAnnotationType, Class<A> annotationType) {

		try {
			if (annotatedElement.getAnnotations().length > 0) {
				return new AnnotationCollector<A>(containerAnnotationType, annotationType).getResult(annotatedElement);
			}
		}
		catch (Exception ex) {
			// Assuming nested Class values not resolvable within annotation attributes...
			logIntrospectionFailure(annotatedElement, ex);
		}
		return Collections.emptySet();
	}

	/**
	 * Find a single {@link Annotation} of {@code annotationType} on the
	 * supplied {@link AnnotatedElement}.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>directly present</em> on the supplied element.
	 * <p><strong>Warning</strong>: this method operates generically on
	 * annotated elements. In other words, this method does not execute
	 * specialized search algorithms for classes or methods. If you require
	 * the more specific semantics of {@link #findAnnotation(Class, Class)}
	 * or {@link #findAnnotation(Method, Class)}, invoke one of those methods
	 * instead.
	 * @param annotatedElement the {@code AnnotatedElement} on which to find the annotation
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.2
	 */
	public static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
		// Do NOT store result in the findAnnotationCache since doing so could break
		// findAnnotation(Class, Class) and findAnnotation(Method, Class).
		return findAnnotation(annotatedElement, annotationType, new HashSet<Annotation>());
	}

	/**
	 * Perform the search algorithm for {@link #findAnnotation(AnnotatedElement, Class)}
	 * avoiding endless recursion by tracking which annotations have already
	 * been <em>visited</em>.
	 * @param annotatedElement the {@code AnnotatedElement} on which to find the annotation
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @param visited the set of annotations that have already been visited
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.2
	 */
	@SuppressWarnings("unchecked")
	private static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType, Set<Annotation> visited) {
		Assert.notNull(annotatedElement, "AnnotatedElement must not be null");
		try {
			Annotation[] anns = annotatedElement.getDeclaredAnnotations();
			for (Annotation ann : anns) {
				if (ann.annotationType().equals(annotationType)) {
					return (A) ann;
				}
			}
			for (Annotation ann : anns) {
				if (!isInJavaLangAnnotationPackage(ann) && visited.add(ann)) {
					A annotation = findAnnotation((AnnotatedElement) ann.annotationType(), annotationType, visited);
					if (annotation != null) {
						return annotation;
					}
				}
			}
		}
		catch (Exception ex) {
			// Assuming nested Class values not resolvable within annotation attributes...
			logIntrospectionFailure(annotatedElement, ex);
		}
		return null;
	}

	/**
	 * Find a single {@link Annotation} of {@code annotationType} on the supplied
	 * {@link Method}, traversing its super methods (i.e., from superclasses and
	 * interfaces) if no annotation can be found on the given method itself.
	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>directly present</em> on the method.
	 * <p>Annotations on methods are not inherited by default, so we need to handle
	 * this explicitly.
	 * @param method the method to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @return the first matching annotation, or {@code null} if not found
	 * @see #getAnnotation(Method, Class)
	 */
	@SuppressWarnings("unchecked")
	public static <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
		AnnotationCacheKey cacheKey = new AnnotationCacheKey(method, annotationType);
		A result = (A) findAnnotationCache.get(cacheKey);

		if (result == null) {
			Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
			result = findAnnotation((AnnotatedElement) resolvedMethod, annotationType);

			if (result == null) {
				result = searchOnInterfaces(method, annotationType, method.getDeclaringClass().getInterfaces());
			}

			Class<?> clazz = method.getDeclaringClass();
			while (result == null) {
				clazz = clazz.getSuperclass();
				if (clazz == null || clazz.equals(Object.class)) {
					break;
				}
				try {
					Method equivalentMethod = clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
					Method resolvedEquivalentMethod = BridgeMethodResolver.findBridgedMethod(equivalentMethod);
					result = findAnnotation((AnnotatedElement) resolvedEquivalentMethod, annotationType);
				}
				catch (NoSuchMethodException ex) {
					// No equivalent method found
				}
				if (result == null) {
					result = searchOnInterfaces(method, annotationType, clazz.getInterfaces());
				}
			}

			if (result != null) {
				findAnnotationCache.put(cacheKey, result);
			}
		}

		return result;
	}

	private static <A extends Annotation> A searchOnInterfaces(Method method, Class<A> annotationType, Class<?>... ifcs) {
		A annotation = null;
		for (Class<?> iface : ifcs) {
			if (isInterfaceWithAnnotatedMethods(iface)) {
				try {
					Method equivalentMethod = iface.getMethod(method.getName(), method.getParameterTypes());
					annotation = getAnnotation(equivalentMethod, annotationType);
				}
				catch (NoSuchMethodException ex) {
					// Skip this interface - it doesn't have the method...
				}
				if (annotation != null) {
					break;
				}
			}
		}
		return annotation;
	}

	static boolean isInterfaceWithAnnotatedMethods(Class<?> iface) {
		Boolean flag = annotatedInterfaceCache.get(iface);
		if (flag != null) {
			return flag.booleanValue();
		}
		Boolean found = Boolean.FALSE;
		for (Method ifcMethod : iface.getMethods()) {
			try {
				if (ifcMethod.getAnnotations().length > 0) {
					found = Boolean.TRUE;
					break;
				}
			}
			catch (Exception ex) {
				// Assuming nested Class values not resolvable within annotation attributes...
				logIntrospectionFailure(ifcMethod, ex);
			}
		}
		annotatedInterfaceCache.put(iface, found);
		return found.booleanValue();
	}

	/**
	 * Find a single {@link Annotation} of {@code annotationType} on the
	 * supplied {@link Class}, traversing its interfaces, annotations, and
	 * superclasses if the annotation is not <em>present</em> on the given class
	 * itself.
	 * <p>This method explicitly handles class-level annotations which are not
	 * declared as {@link java.lang.annotation.Inherited inherited} <em>as well
	 * as meta-annotations and annotations on interfaces</em>.
	 * <p>The algorithm operates as follows:
	 * <ol>
	 * <li>Search for the annotation on the given class and return it if found.
	 * <li>Recursively search through all annotations that the given class declares.
	 * <li>Recursively search through all interfaces that the given class declares.
	 * <li>Recursively search through the superclass hierarchy of the given class.
	 * </ol>
	 * <p>Note: in this context, the term <em>recursively</em> means that the search
	 * process continues by returning to step #1 with the current interface,
	 * annotation, or superclass as the class to look for annotations on.
	 * @param clazz the class to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * @return the first matching annotation, or {@code null} if not found
	 */
	@SuppressWarnings("unchecked")
	public static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType) {
		AnnotationCacheKey cacheKey = new AnnotationCacheKey(clazz, annotationType);
		A result = (A) findAnnotationCache.get(cacheKey);
		if (result == null) {
			result = findAnnotation(clazz, annotationType, new HashSet<Annotation>());
			if (result != null) {
				findAnnotationCache.put(cacheKey, result);
			}
		}
		return result;
	}

	/**
	 * Perform the search algorithm for {@link #findAnnotation(Class, Class)},
	 * avoiding endless recursion by tracking which annotations have already
	 * been <em>visited</em>.
	 * @param clazz the class to look for annotations on
	 * @param annotationType the type of annotation to look for
	 * @param visited the set of annotations that have already been visited
	 * @return the first matching annotation, or {@code null} if not found
	 */
	@SuppressWarnings("unchecked")
	private static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType, Set<Annotation> visited) {
		Assert.notNull(clazz, "Class must not be null");

		try {
			Annotation[] anns = clazz.getDeclaredAnnotations();
			for (Annotation ann : anns) {
				if (ann.annotationType().equals(annotationType)) {
					return (A) ann;
				}
			}
			for (Annotation ann : anns) {
				if (!isInJavaLangAnnotationPackage(ann) && visited.add(ann)) {
					A annotation = findAnnotation(ann.annotationType(), annotationType, visited);
					if (annotation != null) {
						return annotation;
					}
				}
			}
		}
		catch (Exception ex) {
			// Assuming nested Class values not resolvable within annotation attributes...
			logIntrospectionFailure(clazz, ex);
			return null;
		}

		for (Class<?> ifc : clazz.getInterfaces()) {
			A annotation = findAnnotation(ifc, annotationType, visited);
			if (annotation != null) {
				return annotation;
			}
		}

		Class<?> superclass = clazz.getSuperclass();
		if (superclass == null || superclass.equals(Object.class)) {
			return null;
		}
		return findAnnotation(superclass, annotationType, visited);
	}

	/**
	 * Find the first {@link Class} in the inheritance hierarchy of the specified {@code clazz}
	 * (including the specified {@code clazz} itself) which declares an annotation for the
	 * specified {@code annotationType}, or {@code null} if not found. If the supplied
	 * {@code clazz} is {@code null}, {@code null} will be returned.
	 * <p>If the supplied {@code clazz} is an interface, only the interface itself will be checked;
	 * the inheritance hierarchy for interfaces will not be traversed.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * <p>The standard {@link Class} API does not provide a mechanism for determining which class
	 * in an inheritance hierarchy actually declares an {@link Annotation}, so we need to handle
	 * this explicitly.
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @param clazz the class on which to check for the annotation (may be {@code null})
	 * @return the first {@link Class} in the inheritance hierarchy of the specified {@code clazz}
	 * which declares an annotation of the specified {@code annotationType}, or {@code null}
	 * if not found
	 * @see Class#isAnnotationPresent(Class)
	 * @see Class#getDeclaredAnnotations()
	 * @see #findAnnotationDeclaringClassForTypes(List, Class)
	 * @see #isAnnotationDeclaredLocally(Class, Class)
	 */
	public static Class<?> findAnnotationDeclaringClass(Class<? extends Annotation> annotationType, Class<?> clazz) {
		Assert.notNull(annotationType, "Annotation type must not be null");
		if (clazz == null || clazz.equals(Object.class)) {
			return null;
		}
		if (isAnnotationDeclaredLocally(annotationType, clazz)) {
			return clazz;
		}
		return findAnnotationDeclaringClass(annotationType, clazz.getSuperclass());
	}

	/**
	 * Find the first {@link Class} in the inheritance hierarchy of the specified
	 * {@code clazz} (including the specified {@code clazz} itself) which declares
	 * at least one of the specified {@code annotationTypes}, or {@code null} if
	 * none of the specified annotation types could be found.
	 * <p>If the supplied {@code clazz} is {@code null}, {@code null} will be
	 * returned.
	 * <p>If the supplied {@code clazz} is an interface, only the interface itself
	 * will be checked; the inheritance hierarchy for interfaces will not be traversed.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * <p>The standard {@link Class} API does not provide a mechanism for determining
	 * which class in an inheritance hierarchy actually declares one of several
	 * candidate {@linkplain Annotation annotations}, so we need to handle this
	 * explicitly.
	 * @param annotationTypes the list of Class objects corresponding to the
	 * annotation types
	 * @param clazz the Class object corresponding to the class on which to check
	 * for the annotations, or {@code null}
	 * @return the first {@link Class} in the inheritance hierarchy of the specified
	 * {@code clazz} which declares an annotation of at least one of the specified
	 * {@code annotationTypes}, or {@code null} if not found
	 * @since 3.2.2
	 * @see Class#isAnnotationPresent(Class)
	 * @see Class#getDeclaredAnnotations()
	 * @see #findAnnotationDeclaringClass(Class, Class)
	 * @see #isAnnotationDeclaredLocally(Class, Class)
	 */
	public static Class<?> findAnnotationDeclaringClassForTypes(List<Class<? extends Annotation>> annotationTypes, Class<?> clazz) {
		Assert.notEmpty(annotationTypes, "The list of annotation types must not be empty");
		if (clazz == null || clazz.equals(Object.class)) {
			return null;
		}
		for (Class<? extends Annotation> annotationType : annotationTypes) {
			if (isAnnotationDeclaredLocally(annotationType, clazz)) {
				return clazz;
			}
		}
		return findAnnotationDeclaringClassForTypes(annotationTypes, clazz.getSuperclass());
	}

	/**
	 * Determine whether an annotation of the specified {@code annotationType} is
	 * declared locally (i.e., <em>directly present</em>) on the supplied
	 * {@code clazz}. The supplied {@link Class} may represent any type.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * <p>Note: This method does <strong>not</strong> determine if the annotation is
	 * {@linkplain java.lang.annotation.Inherited inherited}. For greater clarity
	 * regarding inherited annotations, consider using
	 * {@link #isAnnotationInherited(Class, Class)} instead.
	 * @param annotationType the Class object corresponding to the annotation type
	 * @param clazz the Class object corresponding to the class on which to check for the annotation
	 * @return {@code true} if an annotation of the specified {@code annotationType}
	 * is <em>directly present</em> on the supplied {@code clazz}
	 * @see java.lang.Class#getDeclaredAnnotations()
	 * @see java.lang.Class#getDeclaredAnnotation(Class)
	 * @see #isAnnotationInherited(Class, Class)
	 */
	public static boolean isAnnotationDeclaredLocally(Class<? extends Annotation> annotationType, Class<?> clazz) {
		Assert.notNull(annotationType, "Annotation type must not be null");
		Assert.notNull(clazz, "Class must not be null");
		boolean declaredLocally = false;
		try {
			for (Annotation ann : clazz.getDeclaredAnnotations()) {
				if (ann.annotationType().equals(annotationType)) {
					declaredLocally = true;
					break;
				}
			}
		}
		catch (Exception ex) {
			// Assuming nested Class values not resolvable within annotation attributes...
			logIntrospectionFailure(clazz, ex);
		}
		return declaredLocally;
	}

	/**
	 * Determine whether an annotation of the specified {@code annotationType} is <em>present</em>
	 * on the supplied {@code clazz} and is {@linkplain java.lang.annotation.Inherited inherited}
	 * (i.e., not declared locally for the class).
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * <p>If the supplied {@code clazz} is an interface, only the interface itself will be checked.
	 * In accordance with standard meta-annotation semantics, the inheritance hierarchy for interfaces
	 * will not be traversed. See the {@linkplain java.lang.annotation.Inherited Javadoc} for the
	 * {@code @Inherited} meta-annotation for further details regarding annotation inheritance.
	 * @param annotationType the Class object corresponding to the annotation type
	 * @param clazz the Class object corresponding to the class on which to check for the annotation
	 * @return {@code true} if an annotation of the specified {@code annotationType} is present
	 * on the supplied {@code clazz} and is <em>inherited</em>
	 * @see Class#isAnnotationPresent(Class)
	 * @see #isAnnotationDeclaredLocally(Class, Class)
	 */
	public static boolean isAnnotationInherited(Class<? extends Annotation> annotationType, Class<?> clazz) {
		Assert.notNull(annotationType, "Annotation type must not be null");
		Assert.notNull(clazz, "Class must not be null");
		return (clazz.isAnnotationPresent(annotationType) && !isAnnotationDeclaredLocally(annotationType, clazz));
	}

	/**
	 * Determine if the supplied {@link Annotation} is defined in the core JDK
	 * {@code java.lang.annotation} package.
	 * @param annotation the annotation to check (never {@code null})
	 * @return {@code true} if the annotation is in the {@code java.lang.annotation} package
	 */
	public static boolean isInJavaLangAnnotationPackage(Annotation annotation) {
		Assert.notNull(annotation, "Annotation must not be null");
		return isInJavaLangAnnotationPackage(annotation.annotationType().getName());
	}

	/**
	 * Determine if the {@link Annotation} with the supplied name is defined
	 * in the core JDK {@code java.lang.annotation} package.
	 * @param annotationType the name of the annotation type to check (never {@code null} or empty)
	 * @return {@code true} if the annotation is in the {@code java.lang.annotation} package
	 * @since 4.2
	 */
	public static boolean isInJavaLangAnnotationPackage(String annotationType) {
		Assert.hasText(annotationType, "annotationType must not be null or empty");
		return annotationType.startsWith("java.lang.annotation");
	}

	/**
	 * Retrieve the given annotation's attributes as a {@link Map}, preserving all
	 * attribute types.
	 * <p>Equivalent to calling {@link #getAnnotationAttributes(Annotation, boolean, boolean)}
	 * with the {@code classValuesAsString} and {@code nestedAnnotationsAsMap} parameters
	 * set to {@code false}.
	 * <p>Note: This method actually returns an {@link AnnotationAttributes} instance.
	 * However, the {@code Map} signature has been preserved for binary compatibility.
	 * @param annotation the annotation to retrieve the attributes for
	 * @return the Map of annotation attributes, with attribute names as keys and
	 * corresponding attribute values as values; never {@code null}
	 * @see #getAnnotationAttributes(Annotation, boolean, boolean)
	 */
	public static Map<String, Object> getAnnotationAttributes(Annotation annotation) {
		return getAnnotationAttributes(annotation, false, false);
	}

	/**
	 * Retrieve the given annotation's attributes as a {@link Map}.
	 * <p>Equivalent to calling {@link #getAnnotationAttributes(Annotation, boolean, boolean)}
	 * with the {@code nestedAnnotationsAsMap} parameter set to {@code false}.
	 * <p>Note: This method actually returns an {@link AnnotationAttributes} instance.
	 * However, the {@code Map} signature has been preserved for binary compatibility.
	 * @param annotation the annotation to retrieve the attributes for
	 * @param classValuesAsString whether to turn Class references into Strings (for
	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata})
	 * or to preserve them as Class references
	 * @return the Map of annotation attributes, with attribute names as keys and
	 * corresponding attribute values as values; never {@code null}
	 * @see #getAnnotationAttributes(Annotation, boolean, boolean)
	 */
	public static Map<String, Object> getAnnotationAttributes(Annotation annotation, boolean classValuesAsString) {
		return getAnnotationAttributes(annotation, classValuesAsString, false);
	}

	/**
	 * Retrieve the given annotation's attributes as an {@link AnnotationAttributes}
	 * map structure.
	 * <p>This method provides fully recursive annotation reading capabilities on par with
	 * the reflection-based {@link org.springframework.core.type.StandardAnnotationMetadata}.
	 * @param annotation the annotation to retrieve the attributes for
	 * @param classValuesAsString whether to convert Class references into Strings (for
	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata})
	 * or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to turn nested Annotation instances into
	 * {@link AnnotationAttributes} maps (for compatibility with
	 * {@link org.springframework.core.type.AnnotationMetadata}) or to preserve them as
	 * Annotation instances
	 * @return the annotation attributes (a specialized Map) with attribute names as keys
	 * and corresponding attribute values as values; never {@code null}
	 * @since 3.1.1
	 */
	public static AnnotationAttributes getAnnotationAttributes(Annotation annotation, boolean classValuesAsString,
			boolean nestedAnnotationsAsMap) {

		AnnotationAttributes attrs = new AnnotationAttributes();
		Method[] methods = annotation.annotationType().getDeclaredMethods();
		for (Method method : methods) {
			if (method.getParameterTypes().length == 0 && method.getReturnType() != void.class) {
				try {
					ReflectionUtils.makeAccessible(method);
					Object value = method.invoke(annotation);
					attrs.put(method.getName(), adaptValue(value, classValuesAsString, nestedAnnotationsAsMap));
				}
				catch (Exception ex) {
					throw new IllegalStateException("Could not obtain annotation attribute values", ex);
				}
			}
		}
		return attrs;
	}

	/**
	 * Adapt the given value according to the given class and nested annotation settings.
	 * @param value the annotation attribute value
	 * @param classValuesAsString whether to turn Class references into Strings (for
	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata})
	 * or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to turn nested Annotation instances into
	 * {@link AnnotationAttributes} maps (for compatibility with
	 * {@link org.springframework.core.type.AnnotationMetadata}) or to preserve them as
	 * Annotation instances
	 * @return the adapted value, or the original value if no adaptation is needed
	 */
	static Object adaptValue(Object value, boolean classValuesAsString, boolean nestedAnnotationsAsMap) {
		if (classValuesAsString) {
			if (value instanceof Class) {
				value = ((Class<?>) value).getName();
			}
			else if (value instanceof Class[]) {
				Class<?>[] clazzArray = (Class<?>[]) value;
				String[] newValue = new String[clazzArray.length];
				for (int i = 0; i < clazzArray.length; i++) {
					newValue[i] = clazzArray[i].getName();
				}
				value = newValue;
			}
		}
		if (nestedAnnotationsAsMap && value instanceof Annotation) {
			return getAnnotationAttributes((Annotation) value, classValuesAsString, true);
		}
		else if (nestedAnnotationsAsMap && value instanceof Annotation[]) {
			Annotation[] realAnnotations = (Annotation[]) value;
			AnnotationAttributes[] mappedAnnotations = new AnnotationAttributes[realAnnotations.length];
			for (int i = 0; i < realAnnotations.length; i++) {
				mappedAnnotations[i] = getAnnotationAttributes(realAnnotations[i], classValuesAsString, true);
			}
			return mappedAnnotations;
		}
		else {
			return value;
		}
	}

	/**
	 * Retrieve the <em>value</em> of the {@code value} attribute of a
	 * single-element Annotation, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the value
	 * @return the attribute value, or {@code null} if not found
	 * @see #getValue(Annotation, String)
	 */
	public static Object getValue(Annotation annotation) {
		return getValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>value</em> of a named attribute, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the attribute value, or {@code null} if not found
	 * @see #getValue(Annotation)
	 */
	public static Object getValue(Annotation annotation, String attributeName) {
		if (annotation == null || !StringUtils.hasLength(attributeName)) {
			return null;
		}
		try {
			Method method = annotation.annotationType().getDeclaredMethod(attributeName);
			ReflectionUtils.makeAccessible(method);
			return method.invoke(annotation);
		}
		catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Retrieve the <em>default value</em> of the {@code value} attribute
	 * of a single-element Annotation, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the default value
	 * @return the default value, or {@code null} if not found
	 * @see #getDefaultValue(Annotation, String)
	 */
	public static Object getDefaultValue(Annotation annotation) {
		return getDefaultValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>default value</em> of a named attribute, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the default value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the default value of the named attribute, or {@code null} if not found
	 * @see #getDefaultValue(Class, String)
	 */
	public static Object getDefaultValue(Annotation annotation, String attributeName) {
		if (annotation == null) {
			return null;
		}
		return getDefaultValue(annotation.annotationType(), attributeName);
	}

	/**
	 * Retrieve the <em>default value</em> of the {@code value} attribute
	 * of a single-element Annotation, given the {@link Class annotation type}.
	 * @param annotationType the <em>annotation type</em> for which the default value should be retrieved
	 * @return the default value, or {@code null} if not found
	 * @see #getDefaultValue(Class, String)
	 */
	public static Object getDefaultValue(Class<? extends Annotation> annotationType) {
		return getDefaultValue(annotationType, VALUE);
	}

	/**
	 * Retrieve the <em>default value</em> of a named attribute, given the
	 * {@link Class annotation type}.
	 * @param annotationType the <em>annotation type</em> for which the default value should be retrieved
	 * @param attributeName the name of the attribute value to retrieve.
	 * @return the default value of the named attribute, or {@code null} if not found
	 * @see #getDefaultValue(Annotation, String)
	 */
	public static Object getDefaultValue(Class<? extends Annotation> annotationType, String attributeName) {
		if (annotationType == null || !StringUtils.hasLength(attributeName)) {
			return null;
		}
		try {
			return annotationType.getDeclaredMethod(attributeName).getDefaultValue();
		}
		catch (Exception ex) {
			return null;
		}
	}


	/**
	 * Log an introspection failure (in particular {@code TypeNotPresentExceptions}) -
	 * before moving on, pretending there were no annotations on this specific element.
	 * @param element the element that we tried to introspect annotations on
	 * @param ex the exception that we encountered
	 */
	static void logIntrospectionFailure(AnnotatedElement element, Exception ex) {
		Log loggerToUse = logger;
		if (loggerToUse == null) {
			loggerToUse = LogFactory.getLog(AnnotationUtils.class);
			logger = loggerToUse;
		}
		if (element instanceof Class && Annotation.class.isAssignableFrom((Class<?>) element)) {
			// Meta-annotation lookup on an annotation type
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to introspect meta-annotations on [" + element + "]: " + ex);
			}
		}
		else {
			// Direct annotation lookup on regular Class, Method, Field
			if (loggerToUse.isInfoEnabled()) {
				logger.info("Failed to introspect annotations on [" + element + "]: " + ex);
			}

		}
	}


	/**
	 * Cache key for the AnnotatedElement cache.
	 */
	private static class AnnotationCacheKey {

		private final AnnotatedElement element;

		private final Class<? extends Annotation> annotationType;

		public AnnotationCacheKey(AnnotatedElement element, Class<? extends Annotation> annotationType) {
			this.element = element;
			this.annotationType = annotationType;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof AnnotationCacheKey)) {
				return false;
			}
			AnnotationCacheKey otherKey = (AnnotationCacheKey) other;
			return (this.element.equals(otherKey.element) &&
					ObjectUtils.nullSafeEquals(this.annotationType, otherKey.annotationType));
		}

		@Override
		public int hashCode() {
			return (this.element.hashCode() * 29 + this.annotationType.hashCode());
		}
	}


	private static class AnnotationCollector<A extends Annotation> {

		private final Class<? extends Annotation> containerAnnotationType;

		private final Class<A> annotationType;

		private final Set<AnnotatedElement> visited = new HashSet<AnnotatedElement>();

		private final Set<A> result = new LinkedHashSet<A>();

		public AnnotationCollector(Class<? extends Annotation> containerAnnotationType, Class<A> annotationType) {
			this.containerAnnotationType = containerAnnotationType;
			this.annotationType = annotationType;
		}

		public Set<A> getResult(AnnotatedElement element) {
			process(element);
			return Collections.unmodifiableSet(this.result);
		}

		@SuppressWarnings("unchecked")
		private void process(AnnotatedElement element) {
			if (this.visited.add(element)) {
				try {
					for (Annotation ann : element.getAnnotations()) {
						Class<? extends Annotation> currentAnnotationType = ann.annotationType();
						if (ObjectUtils.nullSafeEquals(this.annotationType, currentAnnotationType)) {
							this.result.add((A) ann);
						}
						else if (ObjectUtils.nullSafeEquals(this.containerAnnotationType, currentAnnotationType)) {
							this.result.addAll(getValue(ann));
						}
						else if (!isInJavaLangAnnotationPackage(ann)) {
							process(currentAnnotationType);
						}
					}
				}
				catch (Exception ex) {
					logIntrospectionFailure(element, ex);
				}
			}
		}

		@SuppressWarnings("unchecked")
		private List<A> getValue(Annotation annotation) {
			try {
				Method method = annotation.annotationType().getDeclaredMethod("value");
				ReflectionUtils.makeAccessible(method);
				return Arrays.asList((A[]) method.invoke(annotation));
			}
			catch (Exception ex) {
				// Unable to read value from repeating annotation container -> ignore it.
				return Collections.emptyList();
			}
		}
	}

}
