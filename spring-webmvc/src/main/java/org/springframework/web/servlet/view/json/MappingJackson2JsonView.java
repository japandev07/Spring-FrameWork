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

package org.springframework.web.servlet.view.json;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.View;

/**
 * Spring MVC {@link View} that renders JSON content by serializing the model for the current request
 * using <a href="http://jackson.codehaus.org/">Jackson 2's</a> {@link ObjectMapper}.
 *
 * <p>By default, the entire contents of the model map (with the exception of framework-specific classes)
 * will be encoded as JSON. If the model contains only one key, you can have it extracted encoded as JSON
 * alone via  {@link #setExtractValueFromSingleKeyModel}.
 *
 * <p>The default constructor uses the default configuration provided by {@link Jackson2ObjectMapperBuilder}.
 *
 * <p>Compatible with Jackson 2.1 and higher.
 *
 * @author Jeremy Grelle
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 3.1.2
 */
public class MappingJackson2JsonView extends AbstractJackson2View {

	/**
	 * Default content type: "application/json".
	 * Overridable through {@link #setContentType}.
	 */
	public static final String DEFAULT_CONTENT_TYPE = "application/json";

	/**
	 * Default content type for JSONP: "application/javascript".
	 */
	public static final String DEFAULT_JSONP_CONTENT_TYPE = "application/javascript";

	private String jsonPrefix;

	private Set<String> modelKeys;

	private boolean extractValueFromSingleKeyModel = false;

	private Set<String> jsonpParameterNames = new LinkedHashSet<String>(Arrays.asList("jsonp", "callback"));


	/**
	 * Construct a new {@code MappingJackson2JsonView} using default configuration
	 * provided by {@link Jackson2ObjectMapperBuilder} and setting the content type
	 * to {@code application/json}.
	 */
	public MappingJackson2JsonView() {
		super(Jackson2ObjectMapperBuilder.json().build(), DEFAULT_CONTENT_TYPE);
	}


	/**
	 * Specify a custom prefix to use for this view's JSON output.
	 * Default is none.
	 * @see #setPrefixJson
	 */
	public void setJsonPrefix(String jsonPrefix) {
		this.jsonPrefix = jsonPrefix;
	}

	/**
	 * Indicates whether the JSON output by this view should be prefixed with <tt>"{} && "</tt>.
	 * Default is {@code false}.
	 * <p>Prefixing the JSON string in this manner is used to help prevent JSON Hijacking.
	 * The prefix renders the string syntactically invalid as a script so that it cannot be hijacked.
	 * This prefix does not affect the evaluation of JSON, but if JSON validation is performed
	 * on the string, the prefix would need to be ignored.
	 * @see #setJsonPrefix
	 */
	public void setPrefixJson(boolean prefixJson) {
		this.jsonPrefix = (prefixJson ? "{} && " : null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setModelKey(String modelKey) {
		this.modelKeys = Collections.singleton(modelKey);
	}

	/**
	 * Set the attributes in the model that should be rendered by this view.
	 * When set, all other model attributes will be ignored.
	 */
	public void setModelKeys(Set<String> modelKeys) {
		this.modelKeys = modelKeys;
	}

	/**
	 * Return the attributes in the model that should be rendered by this view.
	 */
	public final Set<String> getModelKeys() {
		return this.modelKeys;
	}

	/**
	 * Set the attributes in the model that should be rendered by this view.
	 * When set, all other model attributes will be ignored.
	 * @deprecated use {@link #setModelKeys(Set)} instead
	 */
	@Deprecated
	public void setRenderedAttributes(Set<String> renderedAttributes) {
		this.modelKeys = renderedAttributes;
	}

	/**
	 * Return the attributes in the model that should be rendered by this view.
	 * @deprecated use {@link #getModelKeys()} instead
	 */
	@Deprecated
	public final Set<String> getRenderedAttributes() {
		return this.modelKeys;
	}

	/**
	 * Set whether to serialize models containing a single attribute as a map or whether to
	 * extract the single value from the model and serialize it directly.
	 * <p>The effect of setting this flag is similar to using {@code MappingJackson2HttpMessageConverter}
	 * with an {@code @ResponseBody} request-handling method.
	 * <p>Default is {@code false}.
	 */
	public void setExtractValueFromSingleKeyModel(boolean extractValueFromSingleKeyModel) {
		this.extractValueFromSingleKeyModel = extractValueFromSingleKeyModel;
	}

	/**
	 * Set JSONP request parameter names. Each time a request has one of those
	 * parameters, the resulting JSON will be wrapped into a function named as
	 * specified by the JSONP request parameter value.
	 * <p>The parameter names configured by default are "jsonp" and "callback".
	 * @since 4.1
	 * @see <a href="http://en.wikipedia.org/wiki/JSONP">JSONP Wikipedia article</a>
	 */
	public void setJsonpParameterNames(Set<String> jsonpParameterNames) {
		this.jsonpParameterNames = jsonpParameterNames;
	}

	private String getJsonpParameterValue(HttpServletRequest request) {
		if (this.jsonpParameterNames != null) {
			for (String name : this.jsonpParameterNames) {
				String value = request.getParameter(name);
				if (!StringUtils.isEmpty(value)) {
					return value;
				}
			}
		}
		return null;
	}

	/**
	 * Filter out undesired attributes from the given model.
	 * The return value can be either another {@link Map} or a single value object.
	 * <p>The default implementation removes {@link BindingResult} instances and entries
	 * not included in the {@link #setRenderedAttributes renderedAttributes} property.
	 * @param model the model, as passed on to {@link #renderMergedOutputModel}
	 * @return the value to be rendered
	 */
	@Override
	protected Object filterModel(Map<String, Object> model) {
		Map<String, Object> result = new HashMap<String, Object>(model.size());
		Set<String> modelKeys = (!CollectionUtils.isEmpty(this.modelKeys) ? this.modelKeys : model.keySet());
		for (Map.Entry<String, Object> entry : model.entrySet()) {
			if (!(entry.getValue() instanceof BindingResult) && modelKeys.contains(entry.getKey()) &&
					!entry.getKey().equals(JsonView.class.getName())) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return (this.extractValueFromSingleKeyModel && result.size() == 1 ? result.values().iterator().next() : result);
	}

	@Override
	protected Object filterAndWrapModel(Map<String, Object> model, HttpServletRequest request) {
		Object value = super.filterAndWrapModel(model, request);
		String jsonpParameterValue = getJsonpParameterValue(request);
		if (jsonpParameterValue != null) {
			if (value instanceof MappingJacksonValue) {
				((MappingJacksonValue) value).setJsonpFunction(jsonpParameterValue);
			}
			else {
				MappingJacksonValue container = new MappingJacksonValue(value);
				container.setJsonpFunction(jsonpParameterValue);
				value = container;
			}
		}
		return value;
	}

	@Override
	protected void writePrefix(JsonGenerator generator, Object object) throws IOException {
		if (this.jsonPrefix != null) {
			generator.writeRaw(this.jsonPrefix);
		}
		String jsonpFunction = null;
		if (object instanceof MappingJacksonValue) {
			jsonpFunction = ((MappingJacksonValue)object).getJsonpFunction();
		}
		if (jsonpFunction != null) {
			generator.writeRaw(jsonpFunction + "(" );
		}
	}

	@Override
	protected void writeSuffix(JsonGenerator generator, Object object) throws IOException {
		String jsonpFunction = null;
		if (object instanceof MappingJacksonValue) {
			jsonpFunction = ((MappingJacksonValue)object).getJsonpFunction();
		}
		if (jsonpFunction != null) {
			generator.writeRaw(");");
		}
	}

	@Override
	protected void setResponseContentType(HttpServletRequest request, HttpServletResponse response) {
		if (getJsonpParameterValue(request) != null) {
			response.setContentType(DEFAULT_JSONP_CONTENT_TYPE);
		}
		else {
			super.setResponseContentType(request, response);
		}
	}

}
