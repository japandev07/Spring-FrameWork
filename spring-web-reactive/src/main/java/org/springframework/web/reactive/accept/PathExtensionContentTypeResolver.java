/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.reactive.accept;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.WebUtils;

/**
 * A {@link RequestedContentTypeResolver} that extracts the file extension from the
 * request path and uses that as the media type lookup key.
 *
 * <p>If the file extension is not found in the explicit registrations provided
 * to the constructor, the Java Activation Framework (JAF) is used as a fallback
 * mechanism. The presence of the JAF is detected and enabled automatically but
 * the {@link #setUseJaf(boolean)} property may be set to false.
 *
 * @author Rossen Stoyanchev
 */
public class PathExtensionContentTypeResolver extends AbstractMappingContentTypeResolver {

	private boolean useJaf = true;

	private boolean ignoreUnknownExtensions = true;


	/**
	 * Create an instance with the given map of file extensions and media types.
	 */
	public PathExtensionContentTypeResolver(Map<String, MediaType> mediaTypes) {
		super(mediaTypes);
	}

	/**
	 * Create an instance without any mappings to start with. Mappings may be added
	 * later on if any extensions are resolved through the Java Activation framework.
	 */
	public PathExtensionContentTypeResolver() {
		super(null);
	}


	/**
	 * Whether to use the Java Activation Framework to look up file extensions.
	 * <p>By default this is set to "true" but depends on JAF being present.
	 */
	public void setUseJaf(boolean useJaf) {
		this.useJaf = useJaf;
	}

	/**
	 * Whether to ignore requests with unknown file extension. Setting this to
	 * {@code false} results in {@code HttpMediaTypeNotAcceptableException}.
	 * <p>By default this is set to {@code true}.
	 */
	public void setIgnoreUnknownExtensions(boolean ignoreUnknownExtensions) {
		this.ignoreUnknownExtensions = ignoreUnknownExtensions;
	}


	@Override
	protected String extractKey(ServerWebExchange exchange) {
		String path = exchange.getRequest().getURI().getRawPath();
		String filename = WebUtils.extractFullFilenameFromUrlPath(path);
		String extension = StringUtils.getFilenameExtension(filename);
		return (StringUtils.hasText(extension)) ? extension.toLowerCase(Locale.ENGLISH) : null;
	}

	@Override
	protected MediaType handleNoMatch(String key) throws NotAcceptableStatusException {
		if (this.useJaf) {
			Optional<MimeType> mimeType = MimeTypeUtils.getMimeType("file." + key);
			MediaType mediaType = mimeType.map(MediaType::toMediaType).orElse(null);
			if (mediaType != null && !MediaType.APPLICATION_OCTET_STREAM.equals(mediaType)) {
				return mediaType;
			}
		}
		if (!this.ignoreUnknownExtensions) {
			throw new NotAcceptableStatusException(getMediaTypes());
		}
		return null;
	}

	/**
	 * A public method exposing the knowledge of the path extension resolver to
	 * determine the media type for a given {@link Resource}. First it checks
	 * the explicitly registered mappings and then falls back on JAF.
	 * @param resource the resource
	 * @return the MediaType for the extension or {@code null}.
	 */
	public MediaType resolveMediaTypeForResource(Resource resource) {
		Assert.notNull(resource);
		MediaType mediaType = null;
		String filename = resource.getFilename();
		String extension = StringUtils.getFilenameExtension(filename);
		if (extension != null) {
			mediaType = getMediaType(extension);
		}
		if (mediaType == null) {
			mediaType = MimeTypeUtils.getMimeType(filename).map(MediaType::toMediaType).orElse(null);
		}
		if (MediaType.APPLICATION_OCTET_STREAM.equals(mediaType)) {
			mediaType = null;
		}
		return mediaType;
	}

}
