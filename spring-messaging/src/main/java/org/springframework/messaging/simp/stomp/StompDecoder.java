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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Decodes STOMP frames from a {@link ByteBuffer}. If the buffer does not contain
 * enough data to form a complete STOMP frame, the buffer is reset and the value
 * returned is {@code null} indicating that no message could be read.
 *
 * @author Andy Wilkinson
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class StompDecoder {

	private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

	private static final byte[] HEARTBEAT_PAYLOAD = new byte[] {'\n'};

	private final Log logger = LogFactory.getLog(StompDecoder.class);


	/**
	 * Decodes a STOMP frame in the given {@code buffer} into a {@link Message}.
	 * If the given ByteBuffer contains partial STOMP frame content, the method
	 * resets the buffer and returns {@code null}.
	 *
	 * @param buffer The buffer to decode the frame from
	 *
	 * @return The decoded message or {@code null}
	 */
	public Message<byte[]> decode(ByteBuffer buffer) {

		Message<byte[]> decodedMessage = null;

		skipLeadingEol(buffer);
		buffer.mark();

		String command = readCommand(buffer);

		if (command.length() > 0) {
			MultiValueMap<String, String> headers = readHeaders(buffer);
			byte[] payload = readPayload(buffer, headers);

			if (payload != null) {
				StompCommand stompCommand = StompCommand.valueOf(command);
				if ((payload.length > 0) && (!stompCommand.isBodyAllowed())) {
					throw new StompConversionException(stompCommand + " shouldn't have but " +
							"has a payload with length=" + payload.length + ", headers=" + headers);
				}
				decodedMessage = MessageBuilder.withPayload(payload)
						.setHeaders(StompHeaderAccessor.create(stompCommand, headers)).build();
				if (logger.isDebugEnabled()) {
					logger.debug("Decoded " + decodedMessage);
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Received incomplete frame. Resetting buffer");
				}
				buffer.reset();
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Decoded heartbeat");
			}
			decodedMessage = MessageBuilder.withPayload(HEARTBEAT_PAYLOAD).setHeaders(
					StompHeaderAccessor.create(SimpMessageType.HEARTBEAT)).build();
		}
		return decodedMessage;
	}

	private void skipLeadingEol(ByteBuffer buffer) {
		while (true) {
			if (!isEol(buffer)) {
				break;
			}
		}
	}

	private String readCommand(ByteBuffer buffer) {
		ByteArrayOutputStream command = new ByteArrayOutputStream();
		while (buffer.remaining() > 0 && !isEol(buffer)) {
			command.write(buffer.get());
		}
		return new String(command.toByteArray(), UTF8_CHARSET);
	}

	private MultiValueMap<String, String> readHeaders(ByteBuffer buffer) {
		MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
		while (true) {
			ByteArrayOutputStream headerStream = new ByteArrayOutputStream();
			while (buffer.remaining() > 0 && !isEol(buffer)) {
				headerStream.write(buffer.get());
			}
			if (headerStream.size() > 0) {
				String header = new String(headerStream.toByteArray(), UTF8_CHARSET);
				int colonIndex = header.indexOf(':');
				if ((colonIndex <= 0) || (colonIndex == header.length() - 1)) {
					if (buffer.remaining() > 0) {
						throw new StompConversionException(
								"Illegal header: '" + header + "'. A header must be of the form <name>:<value>");
					}
				}
				else {
					String headerName = unescape(header.substring(0, colonIndex));
					String headerValue = unescape(header.substring(colonIndex + 1));
					headers.add(headerName,  headerValue);
				}
			}
			else {
				break;
			}
		}
		return headers;
	}

	private String unescape(String input) {
		return input.replaceAll("\\\\n", "\n")
				.replaceAll("\\\\r", "\r")
				.replaceAll("\\\\c", ":")
				.replaceAll("\\\\\\\\", "\\\\");
	}

	private byte[] readPayload(ByteBuffer buffer, MultiValueMap<String, String> headers) {
		String contentLengthString = headers.getFirst("content-length");
		if (contentLengthString != null) {
			int contentLength = Integer.valueOf(contentLengthString);
			if (buffer.remaining() > contentLength) {
				byte[] payload = new byte[contentLength];
				buffer.get(payload);
				if (buffer.get() != 0) {
					throw new StompConversionException("Frame must be terminated with a null octet");
				}
				return payload;
			}
			else {
				return null;
			}
		}
		else {
			ByteArrayOutputStream payload = new ByteArrayOutputStream();
			while (buffer.remaining() > 0) {
				byte b = buffer.get();
				if (b == 0) {
					return payload.toByteArray();
				}
				else {
					payload.write(b);
				}
			}
		}
		return null;
	}

	private boolean isEol(ByteBuffer buffer) {
		if (buffer.remaining() > 0) {
			byte b = buffer.get();
			if (b == '\n') {
				return true;
			}
			else if (b == '\r') {
				if (buffer.remaining() > 0 && buffer.get() == '\n') {
					return true;
				}
				else {
					throw new StompConversionException("'\\r' must be followed by '\\n'");
				}
			}
			buffer.position(buffer.position() - 1);
		}
		return false;
	}
}