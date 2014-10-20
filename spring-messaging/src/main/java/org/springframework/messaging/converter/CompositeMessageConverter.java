/*
 * Copyright 2002-2014 the original  author or authors.
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

package org.springframework.messaging.converter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;

/**
 * A {@link MessageConverter} that delegates to a list of other converters
 * to be invoked until one of them returns a non-null result.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class CompositeMessageConverter implements MessageConverter {

	private final List<MessageConverter> converters;


	/**
	 * Create an instance with the given converters.
	 */
	public CompositeMessageConverter(Collection<MessageConverter> converters) {
		Assert.notEmpty(converters, "Converters must not be empty");
		this.converters = new ArrayList<MessageConverter>(converters);
	}

	public List<MessageConverter> getConverters() {
		return this.converters;
	}


	@Override
	public Object fromMessage(Message<?> message, Class<?> targetClass) {
		for (MessageConverter converter : this.converters) {
			Object result = converter.fromMessage(message, targetClass);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	public Message<?> toMessage(Object payload, MessageHeaders headers) {
		for (MessageConverter converter : this.converters) {
			Message<?> result = converter.toMessage(payload, headers);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return "CompositeMessageConverter[converters=" + this.converters + "]";
	}

}
