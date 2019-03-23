/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import org.springframework.core.annotation.AnnotationTypeMapping.MirrorSets.MirrorSet;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Provides mapping information for a single annotation (or meta-annotation) in
 * the context of a root annotation type.
 *
 * @author Phillip Webb
 * @since 5.2
 * @see AnnotationTypeMappings
 */
final class AnnotationTypeMapping {

	@Nullable
	private final AnnotationTypeMapping parent;

	private final AnnotationTypeMapping root;

	private final int depth;

	private final Class<? extends Annotation> annotationType;

	@Nullable
	private final Annotation annotation;

	private final AttributeMethods attributes;

	private final MirrorSets mirrorSets;

	private final int[] aliasMappings;

	private final int[] conventionMappings;

	private final Map<Method, List<Method>> aliasedBy;

	private final Set<Method> claimedAliases = new HashSet<>();


	AnnotationTypeMapping(Class<? extends Annotation> annotationType) {
		this(null, annotationType, null);
	}

	AnnotationTypeMapping(AnnotationTypeMapping parent, Annotation annotation) {
		this(parent, annotation.annotationType(), annotation);
	}

	AnnotationTypeMapping(@Nullable AnnotationTypeMapping parent,
			Class<? extends Annotation> annotationType, @Nullable Annotation annotation) {

		this.parent = parent;
		this.root = parent != null ? parent.getRoot() : this;
		this.depth = parent == null ? 0 : parent.getDepth() + 1;
		this.annotationType = annotationType;
		this.annotation = annotation;
		this.attributes = AttributeMethods.forAnnotationType(annotationType);
		this.mirrorSets = new MirrorSets();
		this.aliasMappings = filledIntArray(this.attributes.size(), -1);
		this.conventionMappings = filledIntArray(this.attributes.size(), -1);
		this.aliasedBy = resolveAliasedForTargets();
		processAliases();
		addConventionMappings();
	}


	private Map<Method, List<Method>> resolveAliasedForTargets() {
		Map<Method, List<Method>> aliasedBy = new HashMap<>();
		for (int i = 0; i < this.attributes.size(); i++) {
			Method attribute = this.attributes.get(i);
			AliasFor aliasFor = AnnotationsScanner.getDeclaredAnnotation(attribute,
					AliasFor.class);
			if (aliasFor != null) {
				Method target = resolveAliasTarget(attribute, aliasFor);
				aliasedBy.computeIfAbsent(target, key -> new ArrayList<>()).add(
						attribute);
			}
		}
		return Collections.unmodifiableMap(aliasedBy);
	}

	private Method resolveAliasTarget(Method attribute, AliasFor aliasFor) {
		return resolveAliasTarget(attribute, aliasFor, true);
	}

	private Method resolveAliasTarget(Method attribute, AliasFor aliasFor, boolean checkAliasPair) {
		if (StringUtils.hasText(aliasFor.value()) &&
				StringUtils.hasText(aliasFor.attribute())) {
			throw new AnnotationConfigurationException(String.format(
					"In @AliasFor declared on %s, attribute 'attribute' and its alias "
							+ "'value' are present with values of '%s' and '%s', but "
							+ "only one is permitted.",
					AttributeMethods.describe(attribute), aliasFor.attribute(),
					aliasFor.value()));
		}
		Class<? extends Annotation> targetAnnotation = aliasFor.annotation();
		if (targetAnnotation == Annotation.class) {
			targetAnnotation = this.annotationType;
		}
		String targetAttributeName = aliasFor.attribute();
		if (!StringUtils.hasLength(targetAttributeName)) {
			targetAttributeName = aliasFor.value();
		}
		if (!StringUtils.hasLength(targetAttributeName)) {
			targetAttributeName = attribute.getName();
		}
		Method target = AttributeMethods.forAnnotationType(targetAnnotation)
				.get(targetAttributeName);
		if (target == null) {
			if (targetAnnotation == this.annotationType) {
				throw new AnnotationConfigurationException(String.format(
						"@AliasFor declaration on %s declares an "
								+ "alias for '%s' which is not present.",
						AttributeMethods.describe(attribute), targetAttributeName));
			}
			throw new AnnotationConfigurationException(String.format(
					"%s is declared as an @AliasFor nonexistent %s.",
					StringUtils.capitalize(AttributeMethods.describe(attribute)),
					AttributeMethods.describe(targetAnnotation, targetAttributeName)));
		}
		if (target == attribute) {
			throw new AnnotationConfigurationException(String.format(
					"@AliasFor declaration on %s points to itself. "
							+ "Specify 'annotation' to point to a same-named "
							+ "attribute on a meta-annotation.",
					AttributeMethods.describe(attribute)));
		}
		if (!isCompatibleReturnType(attribute.getReturnType(), target.getReturnType())) {
			throw new AnnotationConfigurationException(String.format(
					"Misconfigured aliases: %s and %s must declare the same return type.",
					AttributeMethods.describe(attribute),
					AttributeMethods.describe(target)));
		}
		if (isAliasPair(target) && checkAliasPair) {
			AliasFor targetAliasFor = target.getAnnotation(AliasFor.class);
			if (targetAliasFor == null) {
				throw new AnnotationConfigurationException(
						String.format("%s must be declared as an @AliasFor '%s'.",
								StringUtils.capitalize(AttributeMethods.describe(target)),
								attribute.getName()));
			}
			Method mirror = resolveAliasTarget(target, targetAliasFor, false);
			if (mirror != attribute) {
				throw new AnnotationConfigurationException(String.format(
						"%s must be declared as an @AliasFor '%s', not '%s'.",
						StringUtils.capitalize(AttributeMethods.describe(target)),
						attribute.getName(), mirror.getName()));
			}
		}
		return target;
	}

	private boolean isAliasPair(Method target) {
		return target.getDeclaringClass().equals(this.annotationType);
	}

	private boolean isCompatibleReturnType(Class<?> attributeType, Class<?> targetType) {
		return Objects.equals(attributeType, targetType) ||
				Objects.equals(attributeType, targetType.getComponentType());
	}

	private void processAliases() {
		List<Method> aliases = new ArrayList<>();
		for (int i = 0; i < this.attributes.size(); i++) {
			aliases.clear();
			aliases.add(this.attributes.get(i));
			collectAliases(aliases);
			if (aliases.size() > 1) {
				processAliases(aliases);
			}
		}
	}

	private void collectAliases(List<Method> aliases) {
		AnnotationTypeMapping mapping = this;
		while (mapping != null) {
			int size = aliases.size();
			for (int j = 0; j < size; j++) {
				List<Method> additional = mapping.aliasedBy.get(aliases.get(j));
				if (additional != null) {
					aliases.addAll(additional);
				}
			}
			mapping = mapping.parent;
		}
	}

	private void processAliases(List<Method> aliases) {
		int rootAttributeIndex = getFirstRootAttributeIndex(aliases);
		AnnotationTypeMapping mapping = this;
		while (mapping != null) {
			if (rootAttributeIndex != -1 && mapping != this.root) {
				for (int i = 0; i < mapping.attributes.size(); i++) {
					if (aliases.contains(mapping.attributes.get(i))) {
						mapping.aliasMappings[i] = rootAttributeIndex;
					}
				}
			}
			mapping.mirrorSets.updateFrom(aliases);
			mapping.claimedAliases.addAll(aliases);
			mapping = mapping.parent;
		}
	}

	private int getFirstRootAttributeIndex(Collection<Method> aliases) {
		AttributeMethods rootAttributes = this.root.getAttributes();
		for (int i = 0; i < rootAttributes.size(); i++) {
			if (aliases.contains(rootAttributes.get(i))) {
				return i;
			}
		}
		return -1;
	}

	private void addConventionMappings() {
		if (this.depth == 0) {
			return;
		}
		AttributeMethods rootAttributes = this.root.getAttributes();
		int[] mappings = this.conventionMappings;
		for (int i = 0; i < mappings.length; i++) {
			String name = this.attributes.get(i).getName();
			MirrorSet mirrors = getMirrorSets().getAssigned(i);
			int mapped = rootAttributes.indexOf(name);
			if (!MergedAnnotation.VALUE.equals(name) && mapped != -1) {
				mappings[i] = mapped;
				if (mirrors != null) {
					for (int j = 0; j < mirrors.size(); j++) {
						mappings[mirrors.getAttributeIndex(j)] = mapped;
					}
				}
			}
		}
	}

	/**
	 * Method called after all mappings have been set. At this point no further
	 * lookups from child mappings will occur.
	 */
	void afterAllMappingsSet() {
		validateAllAliasesClaimed();
		for (int i = 0; i < this.mirrorSets.size(); i++) {
			validateMirrorSet(this.mirrorSets.get(i));
		}
		this.claimedAliases.clear();
	}

	private void validateAllAliasesClaimed() {
		for (int i = 0; i < this.attributes.size(); i++) {
			Method attribute = this.attributes.get(i);
			AliasFor aliasFor = AnnotationsScanner.getDeclaredAnnotation(attribute, AliasFor.class);
			if (aliasFor != null && !this.claimedAliases.contains(attribute)) {
				Method target = resolveAliasTarget(attribute, aliasFor);
				throw new AnnotationConfigurationException(String.format(
						"@AliasFor declaration on %s declares an alias for %s which is not meta-present.",
						AttributeMethods.describe(attribute),
						AttributeMethods.describe(target)));
			}
		}
	}

	private void validateMirrorSet(MirrorSet mirrorSet) {
		Method firstAttribute = mirrorSet.get(0);
		Object firstDefaultValue = firstAttribute.getDefaultValue();
		for (int i = 1; i <= mirrorSet.size() - 1; i++) {
			Method mirrorAttribute = mirrorSet.get(i);
			Object mirrorDefaultValue = mirrorAttribute.getDefaultValue();
			if (firstDefaultValue == null || mirrorDefaultValue == null) {
				throw new AnnotationConfigurationException(String.format(
						"Misconfigured aliases: %s and %s must declare default values.",
						AttributeMethods.describe(firstAttribute),
						AttributeMethods.describe(mirrorAttribute)));
			}
			if (!ObjectUtils.nullSafeEquals(firstDefaultValue, mirrorDefaultValue)) {
				throw new AnnotationConfigurationException(String.format(
						"Misconfigured aliases: %s and %s must declare the same default value.",
						AttributeMethods.describe(firstAttribute),
						AttributeMethods.describe(mirrorAttribute)));
			}
		}
	}

	/**
	 * Return the root mapping.
	 * @return the root mapping
	 */
	AnnotationTypeMapping getRoot() {
		return this.root;
	}

	/**
	 * Return the parent mapping or {@code null}.
	 * @return the parent mapping
	 */
	@Nullable
	AnnotationTypeMapping getParent() {
		return this.parent;
	}

	/**
	 * Return the depth of this mapping.
	 * @return the depth of the mapping
	 */
	int getDepth() {
		return this.depth;
	}

	/**
	 * Return the type of the mapped annotation.
	 * @return the annotation type
	 */
	Class<? extends Annotation> getAnnotationType() {
		return this.annotationType;
	}

	/**
	 * Return the source annotation for this mapping. This will be the
	 * meta-annotation, or {@code null} if this is the root mapping.
	 * @return the source annotation of the mapping
	 */
	@Nullable
	Annotation getAnnotation() {
		return this.annotation;
	}

	/**
	 * Return the annotation attributes for the mapping annotation type.
	 * @return the attribute methods
	 */
	AttributeMethods getAttributes() {
		return this.attributes;
	}

	/**
	 * Return the related index of an alias mapped attribute, or {@code -1} if
	 * there is no mapping. The resulting value is the index of the attribute on
	 * the root annotation that can be invoked in order to obtain the actual
	 * value.
	 * @param attributeIndex the attribute index of the source attribute
	 * @return the mapped attribute index or {@code -1}
	 */
	int getAliasMapping(int attributeIndex) {
		return this.aliasMappings[attributeIndex];
	}

	/**
	 * Return the related index of a convention mapped attribute, or {@code -1}
	 * if there is no mapping. The resulting value is the index of the attribute
	 * on the root annotation that can be invoked in order to obtain the actual
	 * value.
	 * @param attributeIndex the attribute index of the source attribute
	 * @return the mapped attribute index or {@code -1}
	 */
	int getConventionMapping(int attributeIndex) {
		return this.conventionMappings[attributeIndex];
	}

	/**
	 * Return if the specified value is equivalent to the default value of the
	 * attribute at the given index.
	 * @param attributeIndex the attribute index of the source attribute
	 * @param value the value to check
	 * @param valueExtractor the value extractor used to extract value from any
	 * nested annotations
	 * @return {@code true} if the value is equivalent to the default value
	 */
	boolean isEquivalentToDefaultValue(int attributeIndex, Object value,
			BiFunction<Method, Object, Object> valueExtractor) {
		Method attribute = this.attributes.get(attributeIndex);
		return isEquivalentToDefaultValue(attribute, value, valueExtractor);
	}

	/**
	 * Return the mirror sets for this type mapping.
	 * @return the mirrorSets the attribute mirror sets.
	 */
	MirrorSets getMirrorSets() {
		return this.mirrorSets;
	}

	private static int[] filledIntArray(int size, int value) {
		int[] array = new int[size];
		Arrays.fill(array, value);
		return array;
	}

	private static boolean isEquivalentToDefaultValue(Method attribute, Object value,
			BiFunction<Method, Object, Object> valueExtractor) {
		return areEquivalent(attribute.getDefaultValue(), value, valueExtractor);
	}

	private static boolean areEquivalent(@Nullable Object value,
			@Nullable Object extractedValue,
			BiFunction<Method, Object, Object> valueExtractor) {
		if (ObjectUtils.nullSafeEquals(value, extractedValue)) {
			return true;
		}
		if (value instanceof Class && extractedValue instanceof String) {
			return areEquivalent((Class<?>) value, (String) extractedValue);
		}
		if (value instanceof Class[] && extractedValue instanceof String[]) {
			return areEquivalent((Class[]) value, (String[]) extractedValue);
		}
		if (value instanceof Annotation) {
			return areEquivalent((Annotation) value, extractedValue, valueExtractor);
		}
		return false;
	}

	private static boolean areEquivalent(Class<?>[] value, String[] extractedValue) {
		if (value.length != extractedValue.length) {
			return false;
		}
		for (int i = 0; i < value.length; i++) {
			if (!areEquivalent(value[i], extractedValue[i])) {
				return false;
			}
		}
		return true;
	}

	private static boolean areEquivalent(Class<?> value, String extractedValue) {
		return value.getName().equals(extractedValue);
	}

	private static boolean areEquivalent(Annotation value, @Nullable Object extractedValue,
			BiFunction<Method, Object, Object> valueExtractor) {
		AttributeMethods attributes = AttributeMethods.forAnnotationType(
				value.annotationType());
		for (int i = 0; i < attributes.size(); i++) {
			Method attribute = attributes.get(i);
			if (!areEquivalent(ReflectionUtils.invokeMethod(attribute, value),
					valueExtractor.apply(attribute, extractedValue), valueExtractor)) {
				return false;
			}
		}
		return true;
	}


	/**
	 * A collection of {@link MirrorSet} instances that provides details of all
	 * defined mirrors.
	 */
	class MirrorSets {

		private MirrorSet[] mirrorSets;

		private final MirrorSet[] assigned;


		MirrorSets() {
			this.assigned = new MirrorSet[attributes.size()];
			this.mirrorSets = new MirrorSet[0];
		}


		void updateFrom(Collection<Method> aliases) {
			MirrorSet mirrorSet = null;
			int size = 0;
			int last = -1;
			for (int i = 0; i < attributes.size(); i++) {
				Method attribute = attributes.get(i);
				if (aliases.contains(attribute)) {
					size++;
					if (size > 1) {
						if (mirrorSet == null) {
							mirrorSet = new MirrorSet();
							this.assigned[last] = mirrorSet;
						}
						this.assigned[i] = mirrorSet;
					}
					last = i;
				}
			}
			if (mirrorSet != null) {
				mirrorSet.update();
				LinkedHashSet<MirrorSet> unique = new LinkedHashSet<>(
						Arrays.asList(this.assigned));
				unique.remove(null);
				this.mirrorSets = unique.toArray(new MirrorSet[0]);
			}
		}

		int size() {
			return this.mirrorSets.length;
		}

		MirrorSet get(int index) {
			return this.mirrorSets[index];
		}

		@Nullable
		MirrorSet getAssigned(int attributeIndex) {
			return this.assigned[attributeIndex];
		}

		int[] resolve(@Nullable Object source, @Nullable Object annotation,
				BiFunction<Method, Object, Object> valueExtractor) {

			int[] result = new int[attributes.size()];
			for (int i = 0; i < result.length; i++) {
				result[i] = i;
			}
			for (int i = 0; i < size(); i++) {
				MirrorSet mirrorSet = get(i);
				int resolved = mirrorSet.resolve(source, annotation, valueExtractor);
				for (int j = 0; j < mirrorSet.size; j++) {
					result[mirrorSet.indexes[j]] = resolved;
				}
			}
			return result;
		}


		/**
		 * A single set of mirror attributes.
		 */
		class MirrorSet {

			private int size;

			private final int[] indexes = new int[attributes.size()];


			void update() {
				this.size = 0;
				Arrays.fill(this.indexes, -1);
				for (int i = 0; i < MirrorSets.this.assigned.length; i++) {
					if (MirrorSets.this.assigned[i] == this) {
						this.indexes[this.size] = i;
						this.size++;
					}
				}
			}

			<A> int resolve(@Nullable Object source, @Nullable A annotation,
					BiFunction<Method, Object, Object> valueExtractor) {

				int result = -1;
				Object lastValue = null;
				for (int i = 0; i < this.size; i++) {
					Method attribute = attributes.get(this.indexes[i]);
					Object value = valueExtractor.apply(attribute, annotation);
					boolean isDefaultValue = value == null || isEquivalentToDefaultValue(
							attribute, value, valueExtractor);
					if (isDefaultValue || ObjectUtils.nullSafeEquals(lastValue, value)) {
						continue;
					}
					if (lastValue != null &&
							!ObjectUtils.nullSafeEquals(lastValue, value)) {
						String on = (source != null) ? " declared on " + source : "";
						throw new AnnotationConfigurationException(String.format(
								"Different @AliasFor mirror values for annotation [%s]%s, "
										+ "attribute '%s' and its alias '%s' are declared with values of [%s] and [%s].",
								getAnnotationType().getName(), on,
								attributes.get(result).getName(),
								attribute.getName(),
								ObjectUtils.nullSafeToString(lastValue),
								ObjectUtils.nullSafeToString(value)));
					}
					result = this.indexes[i];
					lastValue = value;
				}
				return result;
			}

			int size() {
				return this.size;
			}

			Method get(int index) {
				int attributeIndex = this.indexes[index];
				return attributes.get(attributeIndex);
			}

			int getAttributeIndex(int index) {
				return this.indexes[index];
			}

		}

	}

}
