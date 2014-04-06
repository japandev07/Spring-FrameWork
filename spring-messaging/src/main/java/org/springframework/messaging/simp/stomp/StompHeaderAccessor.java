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

package org.springframework.messaging.simp.stomp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * A {@code MessageHeaderAccessor} to use when creating a {@code Message} from a
 * decoded STOMP frame, or when encoding a {@code Message} to a STOMP frame.
 *
 * <p>When created from STOMP frame content, the actual STOMP headers are stored
 * in the native header sub-map managed by the parent class
 * {@link org.springframework.messaging.support.NativeMessageHeaderAccessor}
 * while the parent class
 * {@link org.springframework.messaging.simp.SimpMessageHeaderAccessor} manages
 * common processing headers some of which are based on STOMP headers (e.g.
 * destination, content-type, etc).
 *
 * <p>An instance of this class can also be created by wrapping an existing
 * {@code Message}. That message may have been created with the more generic
 * {@link org.springframework.messaging.simp.SimpMessageHeaderAccessor} in
 * which case STOMP headers are created from common processing headers.
 * In this case it is also necessary to invoke either
 * {@link #updateStompCommandAsClientMessage()} or
 * {@link #updateStompCommandAsServerMessage()} if sending a message and
 * depending on whether a message is sent to a client or the message broker.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class StompHeaderAccessor extends SimpMessageHeaderAccessor {

	private static final StompHeaderAccessorFactory factory = new DefaultStompHeaderAccessorFactory();

	private static final AtomicLong messageIdCounter = new AtomicLong();

	private static final long[] DEFAULT_HEARTBEAT = new long[] {0, 0};

	// STOMP header names

	public static final String STOMP_ID_HEADER = "id";

	public static final String STOMP_HOST_HEADER = "host";

	public static final String STOMP_ACCEPT_VERSION_HEADER = "accept-version";

	public static final String STOMP_MESSAGE_ID_HEADER = "message-id";

	public static final String STOMP_RECEIPT_HEADER = "receipt"; // any client frame except CONNECT

	public static final String STOMP_RECEIPT_ID_HEADER = "receipt-id"; // RECEIPT frame

	public static final String STOMP_SUBSCRIPTION_HEADER = "subscription";

	public static final String STOMP_VERSION_HEADER = "version";

	public static final String STOMP_MESSAGE_HEADER = "message";

	public static final String STOMP_ACK_HEADER = "ack";

	public static final String STOMP_NACK_HEADER = "nack";

	public static final String STOMP_LOGIN_HEADER = "login";

	public static final String STOMP_PASSCODE_HEADER = "passcode";

	public static final String STOMP_DESTINATION_HEADER = "destination";

	public static final String STOMP_CONTENT_TYPE_HEADER = "content-type";

	public static final String STOMP_CONTENT_LENGTH_HEADER = "content-length";

	public static final String STOMP_HEARTBEAT_HEADER = "heart-beat";

	// Other header names

	private static final String COMMAND_HEADER = "stompCommand";

	private static final String CREDENTIALS_HEADER = "stompCredentials";


	/**
	 * A constructor for creating message headers from a parsed STOMP frame.
	 */
	StompHeaderAccessor(StompCommand command, Map<String, List<String>> externalSourceHeaders) {
		super(command.getMessageType(), externalSourceHeaders);
		setHeader(COMMAND_HEADER, command);
		updateSimpMessageHeadersFromStompHeaders();
	}

	/**
	 * A constructor for accessing and modifying existing message headers.
	 * Note that the message headers may not have been created from a STOMP frame
	 * but may have rather originated from using the more generic
	 * {@link org.springframework.messaging.simp.SimpMessageHeaderAccessor}.
	 */
	StompHeaderAccessor(Message<?> message) {
		super(message);
		updateStompHeadersFromSimpMessageHeaders();
	}

	StompHeaderAccessor() {
		super(SimpMessageType.HEARTBEAT, null);
	}

	void updateSimpMessageHeadersFromStompHeaders() {
		if (getNativeHeaders() == null) {
			return;
		}
		String value = getFirstNativeHeader(STOMP_DESTINATION_HEADER);
		if (value != null) {
			super.setDestination(value);
		}
		value = getFirstNativeHeader(STOMP_CONTENT_TYPE_HEADER);
		if (value != null) {
			super.setContentType(MimeTypeUtils.parseMimeType(value));
		}
		StompCommand command = getCommand();
		if (StompCommand.MESSAGE.equals(command)) {
			value = getFirstNativeHeader(STOMP_SUBSCRIPTION_HEADER);
			if (value != null) {
				super.setSubscriptionId(value);
			}
		}
		else if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.UNSUBSCRIBE.equals(command)) {
			value = getFirstNativeHeader(STOMP_ID_HEADER);
			if (value != null) {
				super.setSubscriptionId(value);
			}
		}
		else if (StompCommand.CONNECT.equals(command)) {
			protectPasscode();
		}
	}

	private void updateStompHeadersFromSimpMessageHeaders() {
		if (getDestination() != null) {
			setNativeHeader(STOMP_DESTINATION_HEADER, getDestination());
		}
		if (getContentType() != null) {
			setNativeHeader(STOMP_CONTENT_TYPE_HEADER, getContentType().toString());
		}
		trySetStompHeaderForSubscriptionId();
	}

	/**
	 * Create an instance for the given STOMP command.
	 */
	public static StompHeaderAccessor create(StompCommand command) {
		return factory.create(command);
	}

	/**
	 * Create an instance for the given STOMP command and headers.
	 */
	public static StompHeaderAccessor create(StompCommand command, Map<String, List<String>> headers) {
		return factory.create(command, headers);
	}

	/**
	 * Create headers for a heartbeat. While a STOMP heartbeat frame does not
	 * have headers, a session id is needed for processing purposes at a minimum.
	 */
	public static StompHeaderAccessor createForHeartbeat() {
		return factory.createForHeartbeat();
	}

	/**
	 * Create an instance from the payload and headers of the given Message.
	 */
	public static StompHeaderAccessor wrap(Message<?> message) {
		return factory.wrap(message);
	}


	@Override
	protected MessageHeaderAccessor createAccessor(Message<?> message) {
		return factory.wrap(message);
	}

	Map<String, List<String>> getNativeHeaders() {
		@SuppressWarnings("unchecked")
		Map<String, List<String>> map = (Map<String, List<String>>) getHeader(NATIVE_HEADERS);
		return (map != null ? map : Collections.<String, List<String>>emptyMap());
	}

	public StompCommand updateStompCommandAsClientMessage() {

		Assert.state(SimpMessageType.MESSAGE.equals(getMessageType()), "Unexpected message type " + getMessage());

		if (getCommand() == null) {
			setHeader(COMMAND_HEADER, StompCommand.SEND);
		}
		else if (!getCommand().equals(StompCommand.SEND)) {
			throw new IllegalStateException("Unexpected STOMP command " + getCommand());
		}

		return getCommand();
	}

	public void updateStompCommandAsServerMessage() {

		Assert.state(SimpMessageType.MESSAGE.equals(getMessageType()), "Unexpected message type " + getMessage());

		StompCommand command = getCommand();
		if ((command == null) || StompCommand.SEND.equals(command)) {
			setHeader(COMMAND_HEADER, StompCommand.MESSAGE);
		}
		else if (!StompCommand.MESSAGE.equals(command)) {
			throw new IllegalStateException("Unexpected STOMP command " + command);
		}

		trySetStompHeaderForSubscriptionId();

		if (getMessageId() == null) {
			String messageId = getSessionId() + "-" + messageIdCounter.getAndIncrement();
			setNativeHeader(STOMP_MESSAGE_ID_HEADER, messageId);
		}
	}

	/**
	 * @return the STOMP command, or {@code null} if not yet set.
	 */
	public StompCommand getCommand() {
		return (StompCommand) getHeader(COMMAND_HEADER);
	}

	/**
	 * A static alternative for access to the STOMP command.
	 */
	public static StompCommand getCommand(Map<String, Object> headers) {
		return (StompCommand) headers.get(COMMAND_HEADER);
	}

	public Set<String> getAcceptVersion() {
		String rawValue = getFirstNativeHeader(STOMP_ACCEPT_VERSION_HEADER);
		return (rawValue != null) ? StringUtils.commaDelimitedListToSet(rawValue) : Collections.<String>emptySet();
	}

	public boolean isHeartbeat() {
		return (SimpMessageType.HEARTBEAT == getMessageType());
	}

	public void setAcceptVersion(String acceptVersion) {
		setNativeHeader(STOMP_ACCEPT_VERSION_HEADER, acceptVersion);
	}

	public void setHost(String host) {
		setNativeHeader(STOMP_HOST_HEADER, host);
	}

	public String getHost() {
		return getFirstNativeHeader(STOMP_HOST_HEADER);
	}

	@Override
	public void setDestination(String destination) {
		super.setDestination(destination);
		setNativeHeader(STOMP_DESTINATION_HEADER, destination);
	}

	public long[] getHeartbeat() {
		String rawValue = getFirstNativeHeader(STOMP_HEARTBEAT_HEADER);
		if (!StringUtils.hasText(rawValue)) {
			return Arrays.copyOf(DEFAULT_HEARTBEAT, 2);
		}
		String[] rawValues = StringUtils.commaDelimitedListToStringArray(rawValue);
		return new long[] { Long.valueOf(rawValues[0]), Long.valueOf(rawValues[1])};
	}

	public void setContentType(MimeType contentType) {
		super.setContentType(contentType);
		setNativeHeader(STOMP_CONTENT_TYPE_HEADER, contentType.toString());
	}

	@Override
	public void setSubscriptionId(String subscriptionId) {
		super.setSubscriptionId(subscriptionId);
		trySetStompHeaderForSubscriptionId();
	}

	private void trySetStompHeaderForSubscriptionId() {
		String subscriptionId = getSubscriptionId();
		if (subscriptionId != null) {
			if (getCommand() != null && StompCommand.MESSAGE.equals(getCommand())) {
				setNativeHeader(STOMP_SUBSCRIPTION_HEADER, subscriptionId);
			}
			else {
				SimpMessageType messageType = getMessageType();
				if (SimpMessageType.SUBSCRIBE.equals(messageType) || SimpMessageType.UNSUBSCRIBE.equals(messageType)) {
					setNativeHeader(STOMP_ID_HEADER, subscriptionId);
				}
			}
		}
	}

	public Integer getContentLength() {
		if (containsNativeHeader(STOMP_CONTENT_LENGTH_HEADER)) {
			return Integer.valueOf(getFirstNativeHeader(STOMP_CONTENT_LENGTH_HEADER));
		}
		return null;
	}

	public static Integer getContentLength(Map<String, List<String>> nativeHeaders) {
		if (nativeHeaders.containsKey(STOMP_CONTENT_LENGTH_HEADER)) {
			List<String> values = nativeHeaders.get(STOMP_CONTENT_LENGTH_HEADER);
			String value = (values != null ? values.get(0) : null);
			return Integer.valueOf(value);
		}
		return null;
	}

	public void setContentLength(int contentLength) {
		setNativeHeader(STOMP_CONTENT_LENGTH_HEADER, String.valueOf(contentLength));
	}

	public void setHeartbeat(long cx, long cy) {
		setNativeHeader(STOMP_HEARTBEAT_HEADER, StringUtils.arrayToCommaDelimitedString(new Object[]{cx, cy}));
	}

	public void setAck(String ack) {
		setNativeHeader(STOMP_ACK_HEADER, ack);
	}

	public String getAck() {
		return getFirstNativeHeader(STOMP_ACK_HEADER);
	}

	public void setNack(String nack) {
		setNativeHeader(STOMP_NACK_HEADER, nack);
	}

	public String getNack() {
		return getFirstNativeHeader(STOMP_NACK_HEADER);
	}

	public void setLogin(String login) {
		setNativeHeader(STOMP_LOGIN_HEADER, login);
	}

	public String getLogin() {
		return getFirstNativeHeader(STOMP_LOGIN_HEADER);
	}


	public void setPasscode(String passcode) {
		setNativeHeader(STOMP_PASSCODE_HEADER, passcode);
		protectPasscode();
	}

	private void protectPasscode() {
		String value = getFirstNativeHeader(STOMP_PASSCODE_HEADER);
		if (value != null && !"PROTECTED".equals(value)) {
			setHeader(CREDENTIALS_HEADER, new StompPasscode(value));
			setNativeHeader(STOMP_PASSCODE_HEADER, "PROTECTED");
		}
	}

	/**
	 * @return the passcode header value or {@code null}.
	 */
	public String getPasscode() {
		StompPasscode credentials = (StompPasscode) getHeader(CREDENTIALS_HEADER);
		return (credentials != null ? credentials.passcode : null);
	}

	/**
	 * A static alternative for access to the passcode header.
	 */
	public static String getPasscode(Map<String, Object> headers) {
		StompPasscode credentials = (StompPasscode) headers.get(CREDENTIALS_HEADER);
		return (credentials != null ? credentials.passcode : null);
	}

	public void setReceiptId(String receiptId) {
		setNativeHeader(STOMP_RECEIPT_ID_HEADER, receiptId);
	}

	public String getReceiptId() {
		return getFirstNativeHeader(STOMP_RECEIPT_ID_HEADER);
	}

	public void setReceipt(String receiptId) {
		setNativeHeader(STOMP_RECEIPT_HEADER, receiptId);
	}

	public String getReceipt() {
		return getFirstNativeHeader(STOMP_RECEIPT_HEADER);
	}

	public String getMessage() {
		return getFirstNativeHeader(STOMP_MESSAGE_HEADER);
	}

	public void setMessage(String content) {
		setNativeHeader(STOMP_MESSAGE_HEADER, content);
	}

	public String getMessageId() {
		return getFirstNativeHeader(STOMP_MESSAGE_ID_HEADER);
	}

	public void setMessageId(String id) {
		setNativeHeader(STOMP_MESSAGE_ID_HEADER, id);
	}

	public String getVersion() {
		return getFirstNativeHeader(STOMP_VERSION_HEADER);
	}

	public void setVersion(String version) {
		setNativeHeader(STOMP_VERSION_HEADER, version);
	}


	private static class StompPasscode {

		private final String passcode;

		public StompPasscode(String passcode) {
			this.passcode = passcode;
		}

		@Override
		public String toString() {
			return "[PROTECTED]";
		}
	}
}
