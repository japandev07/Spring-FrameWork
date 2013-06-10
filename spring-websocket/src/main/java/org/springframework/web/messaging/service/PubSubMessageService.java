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

package org.springframework.web.messaging.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.messaging.GenericMessage;
import org.springframework.messaging.Message;
import org.springframework.web.messaging.converter.CompositeMessageConverter;
import org.springframework.web.messaging.converter.MessageConverter;
import org.springframework.web.messaging.event.EventBus;
import org.springframework.web.messaging.event.EventConsumer;
import org.springframework.web.messaging.event.EventRegistration;


/**
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class PubSubMessageService extends AbstractMessageService {

	private MessageConverter payloadConverter;

	private Map<String, List<EventRegistration>> subscriptionsBySession =
			new ConcurrentHashMap<String, List<EventRegistration>>();


	public PubSubMessageService(EventBus reactor) {
		super(reactor);
		this.payloadConverter = new CompositeMessageConverter(null);
	}


	public void setMessageConverters(List<MessageConverter> converters) {
		this.payloadConverter = new CompositeMessageConverter(converters);
	}

	@Override
	protected void processMessage(Message<?> message) {

		if (logger.isDebugEnabled()) {
			logger.debug("Message received: " + message);
		}

		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put("destination", message.getHeaders().get("destination"));

		MediaType contentType = (MediaType) message.getHeaders().get("content-type");
		headers.put("content-type", contentType);

		try {
			// Convert to byte[] payload before the fan-out
			byte[] payload = payloadConverter.convertToPayload(message.getPayload(), contentType);
			message = new GenericMessage<byte[]>(payload, headers);

			getEventBus().send(getPublishKey(message), message);
		}
		catch (Exception ex) {
			logger.error("Failed to publish " + message, ex);
		}
	}

	private String getPublishKey(Message<?> message) {
		return "destination:" + (String) message.getHeaders().get("destination");
	}

	@Override
	protected void processSubscribe(Message<?> message) {
		if (logger.isDebugEnabled()) {
			logger.debug("Subscribe " + message);
		}
		final String replyKey = (String) message.getHeaders().getReplyChannel();
		EventRegistration registration = getEventBus().registerConsumer(getPublishKey(message),
				new EventConsumer<Message<?>>() {
					@Override
					public void accept(Message<?> message) {
						getEventBus().send(replyKey, message);
					}
				});

		addSubscription((String) message.getHeaders().get("sessionId"), registration);
	}

	private void addSubscription(String sessionId, EventRegistration registration) {
		List<EventRegistration> list = this.subscriptionsBySession.get(sessionId);
		if (list == null) {
			list = new ArrayList<EventRegistration>();
			this.subscriptionsBySession.put(sessionId, list);
		}
		list.add(registration);
	}

	@Override
	public void processDisconnect(Message<?> message) {
		String sessionId = (String) message.getHeaders().get("sessionId");
		removeSubscriptions(sessionId);
	}

	@Override
	protected void processClientConnectionClosed(String sessionId) {
		removeSubscriptions(sessionId);
	}

	private void removeSubscriptions(String sessionId) {
		List<EventRegistration> registrations = this.subscriptionsBySession.remove(sessionId);
		if (logger.isTraceEnabled()) {
			logger.trace("Cancelling " + registrations.size() + " subscriptions for session=" + sessionId);
		}
		for (EventRegistration registration : registrations) {
			registration.cancel();
		}
	}

}
