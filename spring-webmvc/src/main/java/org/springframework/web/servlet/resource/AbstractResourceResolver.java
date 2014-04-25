/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.web.servlet.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.Resource;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Base class for {@link org.springframework.web.servlet.resource.ResourceResolver}
 * implementations.
 *
 * @author Rossen Stoyanchev
 * @since 4.1
 */
public abstract class AbstractResourceResolver implements ResourceResolver {

	protected final Log logger = LogFactory.getLog(getClass());


	@Override
	public Resource resolveResource(HttpServletRequest request, String requestPath,
			List<? extends Resource> locations, ResourceResolverChain chain) {

		if (logger.isTraceEnabled()) {
			logger.trace("Resolving resource: requestPath=\"" + requestPath + "\"");
		}
		return resolveResourceInternal(request, requestPath, locations, chain);
	}

	protected abstract Resource resolveResourceInternal(HttpServletRequest request, String requestPath,
			List<? extends Resource> locations, ResourceResolverChain chain);

	@Override
	public String resolveUrlPath(String resourceUrlPath, List<? extends Resource> locations,
			ResourceResolverChain chain) {

		if (logger.isTraceEnabled()) {
			logger.trace("Resolving public URL for path=\"" + resourceUrlPath + "\"");
		}

		return resolveUrlPathInternal(resourceUrlPath, locations, chain);
	}

	protected abstract String resolveUrlPathInternal(String resourceUrlPath,
			List<? extends Resource> locations, ResourceResolverChain chain);

}
