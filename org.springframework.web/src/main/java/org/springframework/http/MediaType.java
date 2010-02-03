/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.http;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.StringUtils;

/**
 * Represents an Internet Media Type, as defined in the HTTP specification.
 *
 * <p>Consists of a {@linkplain #getType() type} and a {@linkplain #getSubtype() subtype}. Also has functionality to
 * parse media types from a string using {@link #parseMediaType(String)}, or multiple comma-separated media types using
 * {@link #parseMediaTypes(String)}.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @see <a href="http://tools.ietf.org/html/rfc2616#section-3.7">HTTP 1.1, section 3.7</a>
 * @since 3.0
 */
public class MediaType implements Comparable<MediaType> {

	public static final MediaType ALL = new MediaType("*", "*");

	private static final String WILDCARD_TYPE = "*";

	private static final String PARAM_QUALITY_FACTORY = "q";

	private static final String PARAM_CHARSET = "charset";

	private final String type;

	private final String subtype;

	private final Map<String, String> parameters;

	/**
	 * Create a new {@link MediaType} for the given primary type.
	 *
	 * <p>The {@linkplain #getSubtype() subtype} is set to <code>&#42;</code>, parameters empty.
	 *
	 * @param type the primary type
	 */
	public MediaType(String type) {
		this(type, WILDCARD_TYPE);
	}

	/**
	 * Create a new {@link MediaType} for the given primary type and subtype. <p>The parameters are empty.
	 *
	 * @param type the primary type
	 * @param subtype the subtype
	 */
	public MediaType(String type, String subtype) {
		this(type, subtype, Collections.<String, String>emptyMap());
	}

	/**
	 * Create a new {@link MediaType} for the given type, subtype, and character set.
	 *
	 * @param type the primary type
	 * @param subtype the subtype
	 * @param charSet the character set
	 */
	public MediaType(String type, String subtype, Charset charSet) {
		this(type, subtype, Collections.singletonMap(PARAM_CHARSET, charSet.toString()));
	}

	/**
	 * Create a new {@link MediaType} for the given type, subtype, and quality value.
	 *
	 * @param type the primary type
	 * @param subtype the subtype
	 * @param qualityValue the quality value
	 */
	public MediaType(String type, String subtype, double qualityValue) {
		this(type, subtype, Collections.singletonMap(PARAM_QUALITY_FACTORY, Double.toString(qualityValue)));
	}

	/**
	 * Create a new {@link MediaType} for the given type, subtype, and parameters.
	 *
	 * @param type the primary type
	 * @param subtype the subtype
	 * @param parameters the parameters, mat be <code>null</code>
	 */
	public MediaType(String type, String subtype, Map<String, String> parameters) {
		Assert.hasText(type, "'type' must not be empty");
		Assert.hasText(subtype, "'subtype' must not be empty");
		Assert.doesNotContain(type, "/", "'type' must not contain /");
		Assert.doesNotContain(subtype, "/", "'subtype' must not contain /");
		this.type = type.toLowerCase(Locale.ENGLISH);
		this.subtype = subtype.toLowerCase(Locale.ENGLISH);
		if (!CollectionUtils.isEmpty(parameters)) {
			this.parameters = new LinkedCaseInsensitiveMap<String>(parameters.size(), Locale.ENGLISH);
			this.parameters.putAll(parameters);
		}
		else {
			this.parameters = Collections.emptyMap();
		}
	}

	/** Return the primary type. */
	public String getType() {
		return this.type;
	}

	/** Indicate whether the {@linkplain #getType() type} is the wildcard character <code>&#42;</code> or not. */
	public boolean isWildcardType() {
		return WILDCARD_TYPE.equals(type);
	}

	/** Return the subtype. */
	public String getSubtype() {
		return this.subtype;
	}

	/**
	 * Indicate whether the {@linkplain #getSubtype() subtype} is the wildcard character <code>&#42;</code> or not.
	 *
	 * @return whether the subtype is <code>&#42;</code>
	 */
	public boolean isWildcardSubtype() {
		return WILDCARD_TYPE.equals(subtype);
	}

	/**
	 * Return the character set, as indicated by a <code>charset</code> parameter, if any.
	 *
	 * @return the character set; or <code>null</code> if not available
	 */
	public Charset getCharSet() {
		String charSet = getParameter(PARAM_CHARSET);
		return (charSet != null ? Charset.forName(charSet) : null);
	}

	/**
	 * Return the quality value, as indicated by a <code>q</code> parameter, if any. Defaults to <code>1.0</code>.
	 *
	 * @return the quality factory
	 */
	public double getQualityValue() {
		String qualityFactory = getParameter(PARAM_QUALITY_FACTORY);
		return (qualityFactory != null ? Double.parseDouble(qualityFactory) : 1D);
	}

	/**
	 * Return a generic parameter value, given a parameter name.
	 *
	 * @param name the parameter name
	 * @return the parameter value; or <code>null</code> if not present
	 */
	public String getParameter(String name) {
		return this.parameters.get(name);
	}

	/**
	 * Indicate whether this {@link MediaType} includes the given media type.
	 *
	 * <p>For instance, {@code text/*} includes {@code text/plain}, {@code text/html}, and {@code application/*+xml}
	 * includes {@code application/soap+xml}, etc.
	 *
	 * @param other the reference media type with which to compare
	 * @return <code>true</code> if this media type includes the given media type; <code>false</code> otherwise
	 */
	public boolean includes(MediaType other) {
		if (this == other) {
			return true;
		}
		if (this.type.equals(other.type)) {
			if (this.subtype.equals(other.subtype) || isWildcardSubtype()) {
				return true;
			}
			// application/*+xml includes application/soap+xml
			int thisPlusIdx = this.subtype.indexOf('+');
			int otherPlusIdx = other.subtype.indexOf('+');
			if (thisPlusIdx != -1 && otherPlusIdx != -1) {
				String thisSubtypeNoSuffix = this.subtype.substring(0, thisPlusIdx);

				String thisSubtypeSuffix = this.subtype.substring(thisPlusIdx + 1);
				String otherSubtypeSuffix = other.subtype.substring(otherPlusIdx + 1);
				if (thisSubtypeSuffix.equals(otherSubtypeSuffix) && WILDCARD_TYPE.equals(thisSubtypeNoSuffix)) {
					return true;
				}
			}
		}
		return isWildcardType();
	}

	/**
	 * Compares this {@link MediaType} to another alphabetically.
	 *
	 * @param other media type to compare to
	 * @see #sortBySpecificity(List)
	 */
	public int compareTo(MediaType other) {
		String s1 = this.toString();
		String s2 = other.toString();
		return s1.compareToIgnoreCase(s2);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MediaType)) {
			return false;
		}
		MediaType otherType = (MediaType) other;
		return (this.type.equals(otherType.type) && this.subtype.equals(otherType.subtype) &&
				this.parameters.equals(otherType.parameters));
	}

	@Override
	public int hashCode() {
		int result = this.type.hashCode();
		result = 31 * result + this.subtype.hashCode();
		result = 31 * result + this.parameters.hashCode();
		return result;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		appendTo(builder);
		return builder.toString();
	}

	private void appendTo(StringBuilder builder) {
		builder.append(this.type);
		builder.append('/');
		builder.append(this.subtype);
		for (Map.Entry<String, String> entry : this.parameters.entrySet()) {
			builder.append(';');
			builder.append(entry.getKey());
			builder.append('=');
			builder.append(entry.getValue());
		}
	}

	/**
	 * Parse the given String into a single {@link MediaType}.
	 *
	 * @param mediaType the string to parse
	 * @return the media type
	 * @throws IllegalArgumentException if the string cannot be parsed
	 */
	public static MediaType parseMediaType(String mediaType) {
		Assert.hasLength(mediaType, "'mediaType' must not be empty");
		String[] parts = StringUtils.tokenizeToStringArray(mediaType, ";");

		String fullType = parts[0].trim();
		// java.net.HttpURLConnection returns a *; q=.2 Accept header
		if (WILDCARD_TYPE.equals(fullType)) {
			fullType = "*/*";
		}
		int subIndex = fullType.indexOf('/');
		String type = fullType.substring(0, subIndex);
		String subtype = fullType.substring(subIndex + 1, fullType.length());

		Map<String, String> parameters = null;
		if (parts.length > 1) {
			parameters = new LinkedHashMap<String, String>(parts.length - 1);
			for (int i = 1; i < parts.length; i++) {
				String part = parts[i];
				int eqIndex = part.indexOf('=');
				if (eqIndex != -1) {
					String name = part.substring(0, eqIndex);
					String value = part.substring(eqIndex + 1, part.length());
					parameters.put(name, value);
				}
			}
		}

		return new MediaType(type, subtype, parameters);
	}

	/**
	 * Parse the given, comma-seperated string into a list of {@link MediaType} objects. <p>This method can be used to
	 * parse an Accept or Content-Type header.
	 *
	 * @param mediaTypes the string to parse
	 * @return the list of media types
	 * @throws IllegalArgumentException if the string cannot be parsed
	 */
	public static List<MediaType> parseMediaTypes(String mediaTypes) {
		if (!StringUtils.hasLength(mediaTypes)) {
			return Collections.emptyList();
		}
		String[] tokens = mediaTypes.split(",\\s*");
		List<MediaType> result = new ArrayList<MediaType>(tokens.length);
		for (String token : tokens) {
			result.add(parseMediaType(token));
		}
		return result;
	}

	/**
	 * Return a string representation of the given list of {@link MediaType} objects.
	 *
	 * <p>This method can be used to for an {@code Accept} or {@code Content-Type} header.
	 *
	 * @param mediaTypes the string to parse
	 * @return the list of media types
	 * @throws IllegalArgumentException if the String cannot be parsed
	 */
	public static String toString(Collection<MediaType> mediaTypes) {
		StringBuilder builder = new StringBuilder();
		for (Iterator<MediaType> iterator = mediaTypes.iterator(); iterator.hasNext();) {
			MediaType mediaType = iterator.next();
			mediaType.appendTo(builder);
			if (iterator.hasNext()) {
				builder.append(", ");
			}
		}
		return builder.toString();
	}

	/**
	 * Sorts the given list of {@link MediaType} objects by specificity.
	 *
	 * <p>Given two media types:
	 * <ol>
	 *   <li>if either media type has a {@linkplain #isWildcardType() wildcard type}, then the media type without the
	 *   wildcard is ordered before the other.</li>
	 *   <li>if the two media types have different {@linkplain #getType() types}, then they are considered equal and
	 *   remain their current order.</li>
	 *   <li>if either media type has a {@linkplain #isWildcardSubtype() wildcard subtype}, then the media type without
	 *   the wildcard is sorted before the other.</li>
	 *   <li>if the two media types have different {@linkplain #getSubtype() subtypes}, then they are considered equal
	 *   and remain their current order.</li>
	 *   <li>if the two media types have different {@linkplain #getQualityValue() quality value}, then the media type
	 *   with the highest quality value is ordered before the other.</li>
	 *   <li>if the two media types have a different amount of {@linkplain #getParameter(String) parameters}, then the
	 *   media type with the most parameters is ordered before the other.</li>
	 * </ol>
	 *
	 * <p>For example:
	 * <blockquote>audio/basic &lt; audio/* &lt; *&#047;*</blockquote>
	 * <blockquote>audio/* &lt; audio/*;q=0.7; audio/*;q=0.3</blockquote>
	 * <blockquote>audio/basic;level=1 &lt; audio/basic</blockquote>
	 * <blockquote>audio/basic == text/html</blockquote>
	 * <blockquote>audio/basic == audio/wave</blockquote>
	 *
	 * @param mediaTypes the list of media types to be sorted
	 * @see <a href="http://tools.ietf.org/html/rfc2616#section-14.1">HTTP 1.1, section 14.1</a>
	 */
	public static void sortBySpecificity(List<MediaType> mediaTypes) {
		Assert.notNull(mediaTypes, "'mediaTypes' must not be null");
		if (mediaTypes.size() > 1) {
			Collections.sort(mediaTypes, SPECIFICITY_COMPARATOR);
		}
	}

	static final Comparator<MediaType> SPECIFICITY_COMPARATOR = new Comparator<MediaType>() {

		public int compare(MediaType mediaType1, MediaType mediaType2) {
			if (mediaType1.isWildcardType() && !mediaType2.isWildcardType()) { // */* < audio/*
				return 1;
			}
			else if (mediaType2.isWildcardType() && !mediaType1.isWildcardType()) { // audio/* > */*
				return -1;
			}
			else if (!mediaType1.getType().equals(mediaType2.getType())) { // audio/basic == text/html
				return 0;
			}
			else { // mediaType1.getType().equals(mediaType2.getType())
				if (mediaType1.isWildcardSubtype() && !mediaType2.isWildcardSubtype()) { // audio/* < audio/basic
					return 1;
				}
				else if (mediaType2.isWildcardSubtype() && !mediaType1.isWildcardSubtype()) { // audio/basic > audio/*
					return -1;
				}
				else if (!mediaType1.getSubtype().equals(mediaType2.getSubtype())) { // audio/basic == audio/wave
					return 0;
				}
				else { // mediaType2.getSubtype().equals(mediaType2.getSubtype())
					double quality1 = mediaType1.getQualityValue();
					double quality2 = mediaType2.getQualityValue();
					int qualityComparison = Double.compare(quality2, quality1);
					if (qualityComparison != 0) {
						return qualityComparison;  // audio/*;q=0.7 < audio/*;q=0.3
					} else {
						int paramsSize1 = mediaType1.parameters.size();
						int paramsSize2 = mediaType2.parameters.size();
						return (paramsSize2 < paramsSize1 ? -1 : (paramsSize2 == paramsSize1 ? 0 : 1)); // audio/basic;level=1 < audio/basic
					}
				}
			}
		}
	};

}
