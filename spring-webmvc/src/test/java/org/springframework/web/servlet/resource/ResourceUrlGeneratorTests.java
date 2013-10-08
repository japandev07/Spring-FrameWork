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

package org.springframework.web.servlet.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import static org.junit.Assert.*;


/**
 *
 * @author Jeremy Grelle
 * @author Rossen Stoyanchev
 */
public class ResourceUrlGeneratorTests {

	ResourceHttpRequestHandler handler;

	SimpleUrlHandlerMapping mapping;

	ResourceUrlGenerator generator;


	@Before
	public void setUp() {
		List<Resource> locations = new ArrayList<Resource>();
		locations.add(new ClassPathResource("test/", getClass()));
		locations.add(new ClassPathResource("testalternatepath/", getClass()));

		Map<String, ResourceHttpRequestHandler> urlMap = new HashMap<String, ResourceHttpRequestHandler>();
		handler = new ResourceHttpRequestHandler();
		handler.setLocations(locations);
		urlMap.put("/resources/**", handler);

		mapping = new SimpleUrlHandlerMapping();
		mapping.setUrlMap(urlMap);
	}

	private void initGenerator() {
		generator = new ResourceUrlGenerator();
		generator.setResourceHandlerMappings(Collections.singletonList(this.mapping));
	}

	@Test
	public void getStaticResourceUrl() {
		initGenerator();

		String url = generator.getResourceUrl("/resources/foo.css");
		assertEquals("/resources/foo.css", url);
	}

	@Test
	public void getFingerprintedResourceUrl() {
		List<ResourceResolver> resolvers = new ArrayList<ResourceResolver>();
		resolvers.add(new FingerprintResourceResolver());
		resolvers.add(new PathResourceResolver());
		handler.setResourceResolvers(resolvers);
		initGenerator();

		String url = generator.getResourceUrl("/resources/foo.css");
		assertEquals("/resources/foo-e36d2e05253c6c7085a91522ce43a0b4.css", url);
	}

	@Test
	public void getExtensionMappedResourceUrl() {
		List<ResourceResolver> resolvers = new ArrayList<ResourceResolver>();
		resolvers.add(new PathExtensionResourceResolver());
		resolvers.add(new PathResourceResolver());
		handler.setResourceResolvers(resolvers);
		initGenerator();

		String url = generator.getResourceUrl("/resources/zoo.css");
		assertEquals("/resources/zoo.css", url);
	}

}
