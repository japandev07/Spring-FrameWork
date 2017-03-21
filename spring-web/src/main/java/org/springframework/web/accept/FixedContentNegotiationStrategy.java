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

package org.springframework.web.accept;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * A {@code ContentNegotiationStrategy} that returns a fixed content type.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public class FixedContentNegotiationStrategy implements ContentNegotiationStrategy {

	private static final Log logger = LogFactory.getLog(FixedContentNegotiationStrategy.class);

	private final List<MediaType> contentTypes;


	/**
	 * Create an instance with the given content type.
	 */
	public FixedContentNegotiationStrategy(MediaType... contentTypes) {
		this.contentTypes = Arrays.asList(contentTypes);
	}
	
	/**
	 * Create an instance with the given content type.
	 * 
	 * <p>
	 * List is ordered in the same manner as a "quality" parameter on incoming requests.
	 * If destinations which do not support any of the media types provided are present,
	 * end the list with {@link MediaType#ALL} to allow standard media type determination
	 */
	public FixedContentNegotiationStrategy(List<MediaType> contentTypes) {
		this.contentTypes = contentTypes;
	}


	@Override
	public List<MediaType> resolveMediaTypes(NativeWebRequest request) {
		if (logger.isDebugEnabled()) {
			logger.debug("Requested media types: " + this.contentTypes);
		}
		
		return this.contentTypes;
	}

}
