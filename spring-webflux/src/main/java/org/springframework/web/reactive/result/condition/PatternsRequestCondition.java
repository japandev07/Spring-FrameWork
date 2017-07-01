/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.reactive.result.condition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.server.reactive.PathContainer;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * A logical disjunction (' || ') request condition that matches a request
 * against a set of URL path patterns.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 5.0
 */
public final class PatternsRequestCondition extends AbstractRequestCondition<PatternsRequestCondition> {

	private final Set<PathPattern> patterns;

	private final PathPatternParser parser;


	/**
	 * Creates a new instance with the given URL patterns.
	 * Each pattern is prepended with "/" if not already.
	 * @param patterns 0 or more URL patterns; if 0 the condition will match to every request.
	 */
	public PatternsRequestCondition(String... patterns) {
		this(patterns, null);
	}

	/**
	 * Creates a new instance with the given URL patterns.
	 * Each pattern that is not empty and does not start with "/" is pre-pended with "/".
	 * @param patterns the URL patterns to use; if 0, the condition will match to every request.
	 * @param patternParser for parsing string patterns
	 */
	public PatternsRequestCondition(String[] patterns, PathPatternParser patternParser) {
		this(Arrays.asList(patterns), patternParser);
	}

	/**
	 * Private constructor accepting a collection of raw patterns.
	 */
	private PatternsRequestCondition(Collection<String> patterns, PathPatternParser parser) {
		this.parser = (parser != null ? parser : new PathPatternParser());
		this.patterns = toSortedSet(patterns.stream().map(pattern -> parse(pattern, this.parser)));
	}

	private static PathPattern parse(String pattern, PathPatternParser parser) {
		if (StringUtils.hasText(pattern) && !pattern.startsWith("/")) {
			pattern = "/" + pattern;
		}
		return parser.parse(pattern);
	}

	private static Set<PathPattern> toSortedSet(Stream<PathPattern> stream) {
		return Collections.unmodifiableSet(stream.sorted()
				.collect(Collectors.toCollection(() -> new TreeSet<>(getPatternComparator()))));
	}

	private static Comparator<PathPattern> getPatternComparator() {
		return (p1, p2) -> {
			int index = p1.compareTo(p2);
			return (index != 0 ? index : p1.getPatternString().compareTo(p2.getPatternString()));
		};
	}

	/**
	 * Private constructor accepting a list of path patterns.
	 */
	private PatternsRequestCondition(List<PathPattern> patterns, PathPatternParser patternParser) {
		this.patterns = toSortedSet(patterns.stream());
		this.parser = patternParser;
	}

	public Set<PathPattern> getPatterns() {
		return this.patterns;
	}

	@Override
	protected Collection<PathPattern> getContent() {
		return this.patterns;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	/**
	 * Returns a new instance with URL patterns from the current instance ("this") and
	 * the "other" instance as follows:
	 * <ul>
	 * <li>If there are patterns in both instances, combine the patterns in "this" with
	 * the patterns in "other" using {@link PathPattern#combine(PathPattern)}.
	 * <li>If only one instance has patterns, use them.
	 * <li>If neither instance has patterns, use an empty String (i.e. "").
	 * </ul>
	 */
	@Override
	public PatternsRequestCondition combine(PatternsRequestCondition other) {
		List<PathPattern> combined = new ArrayList<>();
		if (!this.patterns.isEmpty() && !other.patterns.isEmpty()) {
			for (PathPattern pattern1 : this.patterns) {
				for (PathPattern pattern2 : other.patterns) {
					combined.add(pattern1.combine(pattern2));
				}
			}
		}
		else if (!this.patterns.isEmpty()) {
			combined.addAll(this.patterns);
		}
		else if (!other.patterns.isEmpty()) {
			combined.addAll(other.patterns);
		}
		else {
			combined.add(this.parser.parse(""));
		}
		return new PatternsRequestCondition(combined, this.parser);
	}

	/**
	 * Checks if any of the patterns match the given request and returns an instance
	 * that is guaranteed to contain matching patterns, sorted.
	 * @param exchange the current exchange
	 * @return the same instance if the condition contains no patterns;
	 * or a new condition with sorted matching patterns;
	 * or {@code null} if no patterns match.
	 */
	@Override
	@Nullable
	public PatternsRequestCondition getMatchingCondition(ServerWebExchange exchange) {
		if (this.patterns.isEmpty()) {
			return this;
		}
		SortedSet<PathPattern> matches = getMatchingPatterns(exchange);
		return matches.isEmpty() ? null :
				new PatternsRequestCondition(new ArrayList<PathPattern>(matches), this.parser);
	}

	/**
	 * Find the patterns matching the given lookup path. Invoking this method should
	 * yield results equivalent to those of calling
	 * {@link #getMatchingCondition(ServerWebExchange)}.
	 * This method is provided as an alternative to be used if no request is available
	 * (e.g. introspection, tooling, etc).
	 * @param exchange the current exchange
	 * @return a sorted set of matching patterns sorted with the closest match first
	 */
	private SortedSet<PathPattern> getMatchingPatterns(ServerWebExchange exchange) {
		PathContainer lookupPath = exchange.getRequest().getPath().pathWithinApplication();
		return patterns.stream()
				.filter(pattern -> pattern.matches(lookupPath))
				.collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * Compare the two conditions based on the URL patterns they contain.
	 * Patterns are compared one at a time, from top to bottom. If all compared
	 * patterns match equally, but one instance has more patterns, it is
	 * considered a closer match.
	 * <p>It is assumed that both instances have been obtained via
	 * {@link #getMatchingCondition(ServerWebExchange)} to ensure they
	 * contain only patterns that match the request and are sorted with
	 * the best matches on top.
	 */
	@Override
	public int compareTo(PatternsRequestCondition other, ServerWebExchange exchange) {
		Iterator<PathPattern> iterator = this.patterns.iterator();
		Iterator<PathPattern> iteratorOther = other.getPatterns().iterator();
		while (iterator.hasNext() && iteratorOther.hasNext()) {
			int result = iterator.next().compareTo(iteratorOther.next());
			if (result != 0) {
				return result;
			}
		}
		if (iterator.hasNext()) {
			return -1;
		}
		else if (iteratorOther.hasNext()) {
			return 1;
		}
		else {
			return 0;
		}
	}

}
