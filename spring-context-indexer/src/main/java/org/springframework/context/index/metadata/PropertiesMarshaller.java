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

package org.springframework.context.index.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Marshaller to write {@link CandidateComponentsMetadata} as properties.
 *
 * @author Stephane Nicoll
 * @since 5.0
 */
public class PropertiesMarshaller {

	public void write(CandidateComponentsMetadata metadata, OutputStream out)
			throws IOException {

		Properties props = new Properties();
		metadata.getItems().forEach(m -> props.put(m.getType(), String.join(",", m.getStereotypes())));
		props.store(out, "");
	}

	public CandidateComponentsMetadata read(InputStream in) throws IOException {
		CandidateComponentsMetadata result = new CandidateComponentsMetadata();
		Properties props = new Properties();
		props.load(in);
		for (Map.Entry<Object, Object> entry : props.entrySet()) {
			String type = (String) entry.getKey();
			Set<String> candidates = new HashSet<>(Arrays.asList(((String) entry.getValue()).split(",")));
			result.add(new ItemMetadata(type, candidates));
		}
		return result;
	}

}
