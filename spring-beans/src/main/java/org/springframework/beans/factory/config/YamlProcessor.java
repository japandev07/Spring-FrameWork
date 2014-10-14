/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.beans.factory.config;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.parser.ParserException;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Base class for Yaml factories.
 *
 * @author Dave Syer
 * @since 4.1
 */
public abstract class YamlProcessor {

	private final Log logger = LogFactory.getLog(getClass());

	private ResolutionMethod resolutionMethod = ResolutionMethod.OVERRIDE;

	private Resource[] resources = new Resource[0];

	private List<DocumentMatcher> documentMatchers = Collections.emptyList();

	private boolean matchDefault = true;


	/**
	 * A map of document matchers allowing callers to selectively use only
	 * some of the documents in a YAML resource. In YAML documents are
	 * separated by <code>---<code> lines, and each document is converted
	 * to properties before the match is made. E.g.
	 *
	 * <pre class="code">
	 * environment: dev
	 * url: http://dev.bar.com
	 * name: Developer Setup
	 * ---
	 * environment: prod
	 * url:http://foo.bar.com
	 * name: My Cool App
	 * </pre>
	 *
	 * when mapped with
	 * <code>documentMatchers = YamlProcessor.mapMatcher({"environment": "prod"})</code>
	 * would end up as
	 *
	 * <pre class="code">
	 * environment=prod
	 * url=http://foo.bar.com
	 * name=My Cool App
	 * url=http://dev.bar.com
	 * </pre>
	 * @param matchers a map of keys to value patterns (regular expressions)
	 */
	public void setDocumentMatchers(DocumentMatcher... matchers) {
		this.documentMatchers = Arrays.asList(matchers);
	}

	/**
	 * Flag indicating that a document for which all the
	 * {@link #setDocumentMatchers(DocumentMatcher...) document matchers} abstain will
	 * nevertheless match.
	 * @param matchDefault the flag to set (default true)
	 */
	public void setMatchDefault(boolean matchDefault) {
		this.matchDefault = matchDefault;
	}

	/**
	 * Method to use for resolving resources. Each resource will be converted to a Map, so
	 * this property is used to decide which map entries to keep in the final output from
	 * this factory.
	 * @param resolutionMethod the resolution method to set (defaults to
	 * {@link ResolutionMethod#OVERRIDE}).
	 */
	public void setResolutionMethod(ResolutionMethod resolutionMethod) {
		Assert.notNull(resolutionMethod, "ResolutionMethod must not be null");
		this.resolutionMethod = resolutionMethod;
	}

	/**
	 * Set locations of YAML {@link Resource resources} to be loaded.
	 * @see ResolutionMethod
	 */
	public void setResources(Resource... resources) {
		this.resources = resources;
	}


	/**
	 * Provide an opportunity for subclasses to process the Yaml parsed from the supplied
	 * resources. Each resource is parsed in turn and the documents inside checked against
	 * the {@link #setDocumentMatchers(DocumentMatcher...) matchers}. If a document
	 * matches it is passed into the callback, along with its representation as
	 * Properties. Depending on the {@link #setResolutionMethod(ResolutionMethod)} not all
	 * of the documents will be parsed.
	 * @param callback a callback to delegate to once matching documents are found
	 */
	protected void process(MatchCallback callback) {
		Yaml yaml = new Yaml(new StrictMapAppenderConstructor());
		for (Resource resource : this.resources) {
			boolean found = process(callback, yaml, resource);
			if (this.resolutionMethod == ResolutionMethod.FIRST_FOUND && found) {
				return;
			}
		}
	}

	private boolean process(MatchCallback callback, Yaml yaml, Resource resource) {
		int count = 0;
		try {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Loading from YAML: " + resource);
			}
			for (Object object : yaml.loadAll(resource.getInputStream())) {
				if (object != null && process(asMap(object), callback)) {
					count++;
					if (this.resolutionMethod == ResolutionMethod.FIRST_FOUND) {
						break;
					}
				}
			}
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Loaded " + count + " document" + (count > 1 ? "s" : "") +
						" from YAML resource: " + resource);
			}
		}
		catch (IOException ex) {
			handleProcessError(resource, ex);
		}
		return (count > 0);
	}

	private void handleProcessError(Resource resource, IOException ex) {
		if (this.resolutionMethod != ResolutionMethod.FIRST_FOUND &&
				this.resolutionMethod != ResolutionMethod.OVERRIDE_AND_IGNORE) {
			throw new IllegalStateException(ex);
		}
		if (this.logger.isWarnEnabled()) {
			this.logger.warn("Could not load map from " + resource + ": " + ex.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> asMap(Object object) {
		// YAML can have numbers as keys
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (!(object instanceof Map)) {
			// A document can be a text literal
			result.put("document", object);
			return result;
		}

		Map<Object, Object> map = (Map<Object, Object>) object;
		for (Entry<Object, Object> entry : map.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Map) {
				value = asMap(value);
			}
			Object key = entry.getKey();
			if (key instanceof CharSequence) {
				result.put(key.toString(), value);
			}
			else {
				// It has to be a map key in this case
				result.put("[" + key.toString() + "]", value);
			}
		}
		return result;
	}

	private boolean process(Map<String, Object> map, MatchCallback callback) {
		Properties properties = new Properties();
		assignProperties(properties, map, null);

		if (this.documentMatchers.isEmpty()) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Merging document (no matchers set)" + map);
			}
			callback.process(properties, map);
			return true;
		}

		MatchStatus result = MatchStatus.ABSTAIN;
		for (DocumentMatcher matcher : this.documentMatchers) {
			MatchStatus match = matcher.matches(properties);
			result = MatchStatus.getMostSpecific(match, result);
			if (match == MatchStatus.FOUND) {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug("Matched document with document matcher: " + properties);
				}
				callback.process(properties, map);
				return true;
			}
		}

		if (result == MatchStatus.ABSTAIN && this.matchDefault) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Matched document with default matcher: " + map);
			}
			callback.process(properties, map);
			return true;
		}

		this.logger.debug("Unmatched document");
		return false;
	}

	private void assignProperties(Properties properties, Map<String, Object> input, String path) {
		for (Entry<String, Object> entry : input.entrySet()) {
			String key = entry.getKey();
			if (StringUtils.hasText(path)) {
				if (key.startsWith("[")) {
					key = path + key;
				}
				else {
					key = path + "." + key;
				}
			}
			Object value = entry.getValue();
			if (value instanceof String) {
				properties.put(key, value);
			}
			else if (value instanceof Map) {
				// Need a compound key
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) value;
				assignProperties(properties, map, key);
			}
			else if (value instanceof Collection) {
				// Need a compound key
				@SuppressWarnings("unchecked")
				Collection<Object> collection = (Collection<Object>) value;
				int count = 0;
				for (Object object : collection) {
					assignProperties(properties,
							Collections.singletonMap("[" + (count++) + "]", object), key);
				}
			}
			else {
				properties.put(key, value == null ? "" : value);
			}
		}
	}


	/**
	 * Callback interface used to process properties in a resulting map.
	 */
	public interface MatchCallback {

		/**
		 * Process the properties.
		 * @param properties the properties to process
		 * @param map a mutable result map
		 */
		void process(Properties properties, Map<String, Object> map);
	}


	/**
	 * Strategy interface used to test if properties match.
	 */
	public interface DocumentMatcher {

		/**
		 * Test if the given properties match.
		 * @param properties the properties to test
		 * @return the status of the match.
		 */
		MatchStatus matches(Properties properties);
	}


	/**
	 * Status returned from {@link DocumentMatcher#matches(java.util.Properties)}
	 */
	public static enum MatchStatus {

		/**
		 * A match was found.
		 */
		FOUND,

		/**
		 * No match was found.
		 */
		NOT_FOUND,

		/**
		 * The matcher should not be considered.
		 */
		ABSTAIN;

		/**
		 * Compare two {@link MatchStatus} items, returning the most specific status.
		 */
		public static MatchStatus getMostSpecific(MatchStatus a, MatchStatus b) {
			return (a.ordinal() < b.ordinal() ? a : b);
		}
	}


	/**
	 * Method to use for resolving resources.
	 */
	public static enum ResolutionMethod {

		/**
		 * Replace values from earlier in the list.
		 */
		OVERRIDE,

		/**
		 * Replace values from earlier in the list, ignoring any failures.
		 */
		OVERRIDE_AND_IGNORE,

		/**
		 * Take the first resource in the list that exists and use just that.
		 */
		FIRST_FOUND
	}


	/**
	 * A specialized {@link Constructor} that checks for duplicate keys.
	 */
	private static class StrictMapAppenderConstructor extends Constructor {

		public 	StrictMapAppenderConstructor() {
			super();
		}

		@Override
		protected Map<Object, Object> constructMapping(MappingNode node) {
			try {
				return super.constructMapping(node);
			} catch (IllegalStateException e) {
				throw new ParserException("while parsing MappingNode",
						node.getStartMark(), e.getMessage(), node.getEndMark());
			}
		}

		@Override
		protected Map<Object, Object> createDefaultMap() {
			final Map<Object, Object> delegate = super.createDefaultMap();
			return new AbstractMap<Object, Object>() {
				@Override
				public Object put(Object key, Object value) {
					if (delegate.containsKey(key)) {
						throw new IllegalStateException("duplicate key: " + key);
					}
					return delegate.put(key, value);
				}
				@Override
				public Set<Entry<Object, Object>> entrySet() {
					return delegate.entrySet();
				}
			};
		}

	}

}
