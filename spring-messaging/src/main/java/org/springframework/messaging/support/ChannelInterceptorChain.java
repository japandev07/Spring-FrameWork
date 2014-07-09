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

package org.springframework.messaging.support;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

/**
 * A convenience wrapper class for invoking a list of {@link ChannelInterceptor}s.
 *
 * @author Mark Fisher
 * @author Rossen Stoyanchev
 * @since 4.0
 */
class ChannelInterceptorChain {

	private static final Log logger = LogFactory.getLog(ChannelInterceptorChain.class);

	private final List<ChannelInterceptor> interceptors = new CopyOnWriteArrayList<ChannelInterceptor>();


	public boolean set(List<ChannelInterceptor> interceptors) {
		synchronized (this.interceptors) {
			this.interceptors.clear();
			return this.interceptors.addAll(interceptors);
		}
	}

	public boolean add(ChannelInterceptor interceptor) {
		return this.interceptors.add(interceptor);
	}

	public List<ChannelInterceptor> getInterceptors() {
		return Collections.unmodifiableList(this.interceptors);
	}


	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		for (ChannelInterceptor interceptor : this.interceptors) {
			message = interceptor.preSend(message, channel);
			if (message == null) {
				logger.debug("preSend returned null precluding send");
				return null;
			}
		}
		return message;
	}

	public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
		for (ChannelInterceptor interceptor : this.interceptors) {
			interceptor.postSend(message, channel, sent);
		}
	}

	public boolean preReceive(MessageChannel channel) {
		for (ChannelInterceptor interceptor : this.interceptors) {
			if (!interceptor.preReceive(channel)) {
				return false;
			}
		}
		return true;
	}

	public Message<?> postReceive(Message<?> message, MessageChannel channel) {
		for (ChannelInterceptor interceptor : this.interceptors) {
			message = interceptor.postReceive(message, channel);
			if (message == null) {
				return null;
			}
		}
		return message;
	}

}
