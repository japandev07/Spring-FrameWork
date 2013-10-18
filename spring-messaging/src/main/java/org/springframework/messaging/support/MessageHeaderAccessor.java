/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.messaging.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

/**
 * A base class for read/write access to {@link MessageHeaders}. Supports creation of new
 * headers or modification of existing message headers.
 *
 * <p>Sub-classes can provide additional typed getters and setters for convenient access
 * to specific headers. Getters and setters should delegate to {@link #getHeader(String)}
 * or {@link #setHeader(String, Object)} respectively. At the end {@link #toMap()} can be
 * used to obtain the resulting headers.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class MessageHeaderAccessor {

	protected Log logger = LogFactory.getLog(getClass());


	// wrapped read-only message headers
	private final MessageHeaders originalHeaders;

	// header updates
	private final Map<String, Object> headers = new HashMap<String, Object>(4);


	/**
	 * A constructor for creating new message headers.
	 */
	public MessageHeaderAccessor() {
		this.originalHeaders = null;
	}

	/**
	 * A constructor for accessing and modifying existing message headers.
	 */
	public MessageHeaderAccessor(Message<?> message) {
		this.originalHeaders = (message != null) ? message.getHeaders() : null;
	}


	/**
	 * Return a header map including original, wrapped headers (if any) plus additional
	 * header updates made through accessor methods.
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> result = new HashMap<String, Object>();
		if (this.originalHeaders != null) {
			result.putAll(this.originalHeaders);
		}
		for (String key : this.headers.keySet()) {
			Object value = this.headers.get(key);
			if (value == null) {
				result.remove(key);
			}
			else {
				result.put(key, value);
			}
		}
		return result;
	}

	public boolean isModified() {
		return (!this.headers.isEmpty());
	}

	public Object getHeader(String headerName) {
		if (this.headers.containsKey(headerName)) {
			return this.headers.get(headerName);
		}
		else if (this.originalHeaders != null) {
			return this.originalHeaders.get(headerName);
		}
		return null;
	}

	/**
	 * Set the value for the given header name. If the provided value is {@code null} the
	 * header will be removed.
	 */
	public void setHeader(String name, Object value) {
		Assert.isTrue(!isReadOnly(name), "The '" + name + "' header is read-only.");
		verifyType(name, value);
		if (!ObjectUtils.nullSafeEquals(value, getHeader(name))) {
			this.headers.put(name, value);
		}
	}

	protected boolean isReadOnly(String headerName) {
		return MessageHeaders.ID.equals(headerName) || MessageHeaders.TIMESTAMP.equals(headerName);
	}

	/**
	 * Set the value for the given header name only if the header name is not already associated with a value.
	 */
	public void setHeaderIfAbsent(String name, Object value) {
		if (getHeader(name) == null) {
			setHeader(name, value);
		}
	}

	/**
	 * Removes all headers provided via array of 'headerPatterns'. As the name suggests
	 * the array may contain simple matching patterns for header names. Supported pattern
	 * styles are: "xxx*", "*xxx", "*xxx*" and "xxx*yyy".
	 */
	public void removeHeaders(String... headerPatterns) {
		List<String> headersToRemove = new ArrayList<String>();
		for (String pattern : headerPatterns) {
			if (StringUtils.hasLength(pattern)){
				if (pattern.contains("*")){
					headersToRemove.addAll(getMatchingHeaderNames(pattern, this.headers));
					headersToRemove.addAll(getMatchingHeaderNames(pattern, this.originalHeaders));
				}
				else {
					headersToRemove.add(pattern);
				}
			}
		}
		for (String headerToRemove : headersToRemove) {
			removeHeader(headerToRemove);
		}
	}

	private List<String> getMatchingHeaderNames(String pattern, Map<String, Object> headers) {
		List<String> matchingHeaderNames = new ArrayList<String>();
		if (headers != null) {
			for (Map.Entry<String, Object> header: headers.entrySet()) {
				if (PatternMatchUtils.simpleMatch(pattern,  header.getKey())) {
					matchingHeaderNames.add(header.getKey());
				}
			}
		}
		return matchingHeaderNames;
	}

	/**
	 * Remove the value for the given header name.
	 */
	public void removeHeader(String headerName) {
		if (StringUtils.hasLength(headerName) && !isReadOnly(headerName)) {
			setHeader(headerName, null);
		}
	}

	/**
	 * Copy the name-value pairs from the provided Map. This operation will overwrite any
	 * existing values. Use { {@link #copyHeadersIfAbsent(Map)} to avoid overwriting
	 * values.
	 */
	public void copyHeaders(Map<String, ?> headersToCopy) {
		if (headersToCopy != null) {
			Set<String> keys = headersToCopy.keySet();
			for (String key : keys) {
				if (!isReadOnly(key)) {
					setHeader(key, headersToCopy.get(key));
				}
			}
		}
	}

	/**
	 * Copy the name-value pairs from the provided Map. This operation will <em>not</em>
	 * overwrite any existing values.
	 */
	public void copyHeadersIfAbsent(Map<String, ?> headersToCopy) {
		if (headersToCopy != null) {
			Set<String> keys = headersToCopy.keySet();
			for (String key : keys) {
				if (!this.isReadOnly(key)) {
					setHeaderIfAbsent(key, headersToCopy.get(key));
				}
			}
		}
	}

	public UUID getId() {
		return (UUID) getHeader(MessageHeaders.ID);
	}

	public Long getTimestamp() {
		return (Long) getHeader(MessageHeaders.TIMESTAMP);
	}

	public void setReplyChannel(MessageChannel replyChannel) {
		setHeader(MessageHeaders.REPLY_CHANNEL, replyChannel);
	}

	public Object getReplyChannel() {
        return getHeader(MessageHeaders.REPLY_CHANNEL);
    }

	public void setReplyChannelName(String replyChannelName) {
		setHeader(MessageHeaders.REPLY_CHANNEL, replyChannelName);
	}

	public void setErrorChannel(MessageChannel errorChannel) {
		setHeader(MessageHeaders.ERROR_CHANNEL, errorChannel);
	}

    public Object getErrorChannel() {
        return getHeader(MessageHeaders.ERROR_CHANNEL);
    }

	public void setErrorChannelName(String errorChannelName) {
		setHeader(MessageHeaders.ERROR_CHANNEL, errorChannelName);
	}

    public MimeType getContentType() {
        return (MimeType) getHeader(MessageHeaders.CONTENT_TYPE);
    }

	public void setContentType(MimeType contentType) {
		setHeader(MessageHeaders.CONTENT_TYPE, contentType);
	}


	@Override
	public String toString() {
		return getClass().getSimpleName() + " [originalHeaders=" + this.originalHeaders
				+ ", updated headers=" + this.headers + "]";
	}

	protected void verifyType(String headerName, Object headerValue) {
        if (headerName != null && headerValue != null) {
        	if (MessageHeaders.ERROR_CHANNEL.equals(headerName)
                    || MessageHeaders.REPLY_CHANNEL.endsWith(headerName)) {
                Assert.isTrue(headerValue instanceof MessageChannel || headerValue instanceof String, "The '"
                        + headerName + "' header value must be a MessageChannel or String.");
            }
        }
    }
}
