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

package org.springframework.http.codec;

import org.springframework.core.codec.Encoder;

/**
 * Helps to configure a list of server-side HTTP message readers and writers
 * with support for built-in defaults and options to register additional custom
 * readers and writers via {@link #customCodecs()}.
 *
 * <p>The built-in defaults include basic data types such as various byte
 * representations, resources, strings, forms, but also others like JAXB2 and
 * Jackson 2 based on classpath detection. There are options to
 * {@link #defaultCodecs() override} some of the defaults or to have them
 * {@link #registerDefaults(boolean) turned off} completely.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface ServerCodecConfigurer extends CodecConfigurer {

	/**
	 * {@inheritDoc}
	 * <p>Overriden to return {@link ServerDefaultCodecsConfigurer}.
	 */
	@Override
	ServerDefaultCodecsConfigurer defaultCodecs();


	/**
	 * Creates a new instance of the {@code ServerCodecConfigurer}.
	 * @return the created instance
	 */
	static ServerCodecConfigurer create() {
		return new DefaultServerCodecConfigurer();
	}


	/**
	 * Extension of {@link DefaultCodecConfigurer} with extra server options.
	 */
	interface ServerDefaultCodecsConfigurer extends DefaultCodecsConfigurer {

		/**
		 * Configure the {@code Encoder} to use for Server-Sent Events.
		 * <p>By default the {@link #jackson2Encoder} override is used for SSE.
		 * @param encoder the encoder to use
		 */
		void serverSentEventEncoder(Encoder<?> encoder);

	}

}
