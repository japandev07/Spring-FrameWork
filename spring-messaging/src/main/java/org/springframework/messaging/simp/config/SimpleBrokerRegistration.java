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

package org.springframework.messaging.simp.config;

import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.scheduling.TaskScheduler;

/**
 * Registration class for configuring a {@link SimpleBrokerMessageHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class SimpleBrokerRegistration extends AbstractBrokerRegistration {

	private TaskScheduler taskScheduler;

	private long[] heartbeat;


	public SimpleBrokerRegistration(SubscribableChannel inChannel, MessageChannel outChannel, String[] prefixes) {
		super(inChannel, outChannel, prefixes);
	}


	/**
	 * Configure the {@link org.springframework.scheduling.TaskScheduler} to
	 * use for providing heartbeat support. Setting this property also sets the
	 * {@link #setHeartbeatValue heartbeatValue} to "10000, 10000".
	 * <p>By default this is not set.
	 * @since 4.2
	 */
	public SimpleBrokerRegistration setTaskScheduler(TaskScheduler taskScheduler) {
		this.taskScheduler = taskScheduler;
		return this;
	}

	/**
	 * Configure the value for the heartbeat settings. The first number
	 * represents how often the server will write or send a heartbeat.
	 * The second is how often the client should write. 0 means no heartbeats.
	 * <p>By default this is set to "0, 0" unless the {@link #setTaskScheduler
	 * taskScheduler} in which case the default becomes "10000,10000"
	 * (in milliseconds).
	 * @since 4.2
	 */
	public SimpleBrokerRegistration setHeartbeatValue(long[] heartbeat) {
		this.heartbeat = heartbeat;
		return this;
	}


	@Override
	protected SimpleBrokerMessageHandler getMessageHandler(SubscribableChannel brokerChannel) {
		SimpleBrokerMessageHandler handler = new SimpleBrokerMessageHandler(getClientInboundChannel(),
				getClientOutboundChannel(), brokerChannel, getDestinationPrefixes());
		if (this.taskScheduler != null) {
			handler.setTaskScheduler(this.taskScheduler);
		}
		if (this.heartbeat != null) {
			handler.setHeartbeatValue(this.heartbeat);
		}
		return handler;
	}

}
