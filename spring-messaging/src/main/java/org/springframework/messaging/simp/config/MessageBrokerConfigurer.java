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

package org.springframework.messaging.simp.config;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.handler.AbstractBrokerMessageHandler;
import org.springframework.util.Assert;

/**
 * A helper class for configuring message broker options.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class MessageBrokerConfigurer {

	private final MessageChannel webSocketResponseChannel;

	private SimpleBrokerRegistration simpleBroker;

	private StompBrokerRelayRegistration stompRelay;

	private String[] annotationMethodDestinationPrefixes;

	private String userDestinationPrefix;


	public MessageBrokerConfigurer(MessageChannel webSocketResponseChannel) {
		Assert.notNull(webSocketResponseChannel);
		this.webSocketResponseChannel = webSocketResponseChannel;
	}

	/**
	 * Enable a simple message broker and configure one or more prefixes to filter
	 * destinations targeting the broker (e.g. destinations prefixed with "/topic").
	 */
	public SimpleBrokerRegistration enableSimpleBroker(String... destinationPrefixes) {
		this.simpleBroker = new SimpleBrokerRegistration(this.webSocketResponseChannel, destinationPrefixes);
		return this.simpleBroker;
	}

	/**
	 * Enable a STOMP broker relay and configure the destination prefixes supported by the
	 * message broker. Check the STOMP documentation of the message broker for supported
	 * destinations.
	 */
	public StompBrokerRelayRegistration enableStompBrokerRelay(String... destinationPrefixes) {
		this.stompRelay = new StompBrokerRelayRegistration(this.webSocketResponseChannel, destinationPrefixes);
		return this.stompRelay;
	}

	/**
	 * Configure one or more prefixes to filter destinations targeting annotated
	 * application methods. For example destinations prefixed with "/app" may be processed
	 * by annotated application methods while other destinations may target the message
	 * broker (e.g. "/topic", "/queue").
	 * <p>
	 * When messages are processed, the matching prefix is removed from the destination in
	 * order to form the lookup path. This means annotations should not contain the
	 * destination prefix.
	 * <p>
	 * Prefixes that do not have a trailing slash will have one automatically appended.
	 */
	public MessageBrokerConfigurer setAnnotationMethodDestinationPrefixes(String... destinationPrefixes) {
		this.annotationMethodDestinationPrefixes = destinationPrefixes;
		return this;
	}

	/**
	 * Configure the prefix used to identify user destinations. User destinations
	 * provide the ability for a user to subscribe to queue names unique to their
	 * session as well as for others to send messages to those unique,
	 * user-specific queues.
	 * <p>
	 * For example when a user attempts to subscribe to "/user/queue/position-updates",
	 * the destination may be translated to "/queue/position-updatesi9oqdfzo" yielding a
	 * unique queue name that does not collide with any other user attempting to do the same.
	 * Subsequently when messages are sent to "/user/{username}/queue/position-updates",
	 * the destination is translated to "/queue/position-updatesi9oqdfzo".
	 * <p>
	 * The default prefix used to identify such destinations is "/user/".
	 */
	public MessageBrokerConfigurer setUserDestinationPrefix(String destinationPrefix) {
		this.userDestinationPrefix = destinationPrefix;
		return this;
	}


	protected AbstractBrokerMessageHandler getSimpleBroker() {
		initSimpleBrokerIfNecessary();
		return (this.simpleBroker != null) ? this.simpleBroker.getMessageHandler() : null;
	}

	protected void initSimpleBrokerIfNecessary() {
		if ((this.simpleBroker == null) && (this.stompRelay == null)) {
			this.simpleBroker = new SimpleBrokerRegistration(this.webSocketResponseChannel, null);
		}
	}

	protected AbstractBrokerMessageHandler getStompBrokerRelay() {
		return (this.stompRelay != null) ? this.stompRelay.getMessageHandler() : null;
	}

	protected Collection<String> getAnnotationMethodDestinationPrefixes() {
		return (this.annotationMethodDestinationPrefixes != null)
				? Arrays.asList(this.annotationMethodDestinationPrefixes) : null;
	}

	protected String getUserDestinationPrefix() {
		return this.userDestinationPrefix;
	}
}
