/*
 * Copyright 2002-2019 the original author or authors.
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
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.lang.Nullable;

/**
 * A {@link AbstractMergedAnnotation} used as the implementation of
 * {@link MergedAnnotation#missing()}.
 *
 * @author Phillip Webb
 * @since 5.2
 * @param <A> the annotation type
 */
final class MissingMergedAnnotation<A extends Annotation>
		extends AbstractMergedAnnotation<A> {

	private static final MissingMergedAnnotation<?> INSTANCE = new MissingMergedAnnotation<>();


	private MissingMergedAnnotation() {
	}


	@Override
	public String getType() {
		throw new NoSuchElementException("Unable to get type for missing annotation");
	}

	@Override
	public boolean isPresent() {
		return false;
	}

	@Override
	@Nullable
	public Object getSource() {
		return null;
	}

	@Override
	@Nullable
	public MergedAnnotation<?> getParent() {
		return null;
	}

	@Override
	public int getDepth() {
		return -1;
	}

	@Override
	public int getAggregateIndex() {
		return -1;
	}

	public boolean hasNonDefaultValue(String attributeName) {
		throw new NoSuchElementException(
				"Unable to check non-default value for missing annotation");
	}

	@Override
	public boolean hasDefaultValue(String attributeName) {
		throw new NoSuchElementException(
				"Unable to check default value for missing annotation");
	}

	@Override
	public <T> Optional<T> getValue(String attributeName, Class<T> type) {
		return Optional.empty();
	}

	@Override
	public <T> Optional<T> getDefaultValue(@Nullable String attributeName, Class<T> type) {
		return Optional.empty();
	}

	@Override
	public MergedAnnotation<A> filterAttributes(Predicate<String> predicate) {
		return this;
	}

	@Override
	public MergedAnnotation<A> withNonMergedAttributes() {
		return this;
	}

	@Override
	public Map<String, Object> asMap(MapValues... options) {
		return Collections.emptyMap();
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T extends Map<String, Object>> T asMap(
			@Nullable Function<MergedAnnotation<?>, T> factory, MapValues... options) {
		if (factory != null) {
			return factory.apply(this);
		}
		return (T) ((Map) Collections.emptyMap());
	}

	@Override
	public String toString() {
		return "(missing)";
	}

	@Override
	public <T extends Annotation> MergedAnnotation<T> getAnnotation(String attributeName,
			Class<T> type) throws NoSuchElementException {

		throw new NoSuchElementException(
				"Unable to get attribute value for missing annotation");
	}

	@Override
	public <T extends Annotation> MergedAnnotation<T>[] getAnnotationArray(
			String attributeName, Class<T> type) throws NoSuchElementException {

		throw new NoSuchElementException(
				"Unable to get attribute value for missing annotation");
	}

	@Override
	protected <T> T getAttributeValue(String attributeName, Class<T> type) {
		throw new NoSuchElementException(
				"Unable to get attribute value for missing annotation");
	}

	protected A createSynthesized() {
		throw new NoSuchElementException("Unable to synthesize missing annotation");
	}

	@SuppressWarnings("unchecked")
	static <A extends Annotation> MergedAnnotation<A> getInstance() {
		return (MergedAnnotation<A>) INSTANCE;
	}

}
