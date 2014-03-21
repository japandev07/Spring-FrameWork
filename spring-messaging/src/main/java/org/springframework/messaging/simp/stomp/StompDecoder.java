/*
 * Copyright 2002-2014 the original author or authors.
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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Decodes one or more STOMP frames from a {@link ByteBuffer}. If the buffer
 * contains any additional (incomplete) data, or perhaps not enough data to
 * form even one Message, the the buffer is reset and the value returned is
 * an empty list indicating that no more message can be read.
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
	 * Decodes one or more STOMP frames from the given {@code buffer} into a
	 * list of {@link Message}s.
	 *
	 * <p>If the given ByteBuffer contains partial STOMP frame content, or additional
	 * content with a partial STOMP frame, the buffer is reset and {@code null} is
	 * returned.
	 *
	 * @param buffer The buffer to decode the STOMP frame from
	 *
	 * @return the decoded messages or an empty list
	 */
	public List<Message<byte[]>> decode(ByteBuffer buffer) {
		return decode(buffer, new LinkedMultiValueMap<String, String>());
	}

	/**
	 * Decodes one or more STOMP frames from the given {@code buffer} into a
	 * list of {@link Message}s.
	 *
	 * <p>If the given ByteBuffer contains partial STOMP frame content, or additional
	 * content with a partial STOMP frame, the buffer is reset and {@code null} is
	 * returned.
	 *
	 * @param buffer The buffer to decode the STOMP frame from
	 * @param headers an empty map that will be filled with the successfully parsed
	 * 	headers of the last decoded message, or the last attempt at decoding an
	 *  (incomplete) STOMP frame. This can be useful for detecting 'content-length'.
	 *
	 * @return the decoded messages or an empty list
	 */
	public List<Message<byte[]>> decode(ByteBuffer buffer, MultiValueMap<String, String> headers) {
		List<Message<byte[]>> messages = new ArrayList<Message<byte[]>>();
		while (buffer.hasRemaining()) {
			headers.clear();
			Message<byte[]> m = decodeMessage(buffer, headers);
			if (m != null) {
				messages.add(m);
			}
			else {
				break;
			}
		}
		return messages;
	}

	/**
	 * Decode a single STOMP frame from the given {@code buffer} into a {@link Message}.
	 */
	private Message<byte[]> decodeMessage(ByteBuffer buffer, MultiValueMap<String, String> headers) {

		Message<byte[]> decodedMessage = null;
		skipLeadingEol(buffer);
		buffer.mark();

		String command = readCommand(buffer);
		if (command.length() > 0) {

			readHeaders(buffer, headers);
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
					logger.trace("Received incomplete frame. Resetting buffer.");
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


	/**
	 * Skip one ore more EOL characters at the start of the given ByteBuffer.
	 * Those are STOMP heartbeat frames.
	 */
	protected void skipLeadingEol(ByteBuffer buffer) {
		while (true) {
			if (!tryConsumeEndOfLine(buffer)) {
				break;
			}
		}
	}

	private String readCommand(ByteBuffer buffer) {
		ByteArrayOutputStream command = new ByteArrayOutputStream(256);
		while (buffer.remaining() > 0 && !tryConsumeEndOfLine(buffer)) {
			command.write(buffer.get());
		}
		return new String(command.toByteArray(), UTF8_CHARSET);
	}

	private void readHeaders(ByteBuffer buffer, MultiValueMap<String, String> headers) {
		while (true) {
			ByteArrayOutputStream headerStream = new ByteArrayOutputStream(256);
			while (buffer.remaining() > 0 && !tryConsumeEndOfLine(buffer)) {
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
	}

	private String unescape(String input) {
		return input.replaceAll("\\\\n", "\n")
				.replaceAll("\\\\r", "\r")
				.replaceAll("\\\\c", ":")
				.replaceAll("\\\\\\\\", "\\\\");
	}

	private byte[] readPayload(ByteBuffer buffer, MultiValueMap<String, String> headers) {
		Integer contentLength = getContentLength(headers);
		if (contentLength != null && contentLength >= 0) {
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
			ByteArrayOutputStream payload = new ByteArrayOutputStream(256);
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

	protected Integer getContentLength(MultiValueMap<String, String> headers) {
		if (headers.containsKey(StompHeaderAccessor.STOMP_CONTENT_LENGTH_HEADER)) {
			String rawContentLength = headers.getFirst(StompHeaderAccessor.STOMP_CONTENT_LENGTH_HEADER);
			try {
				return Integer.valueOf(rawContentLength);
			}
			catch (NumberFormatException ex) {
				logger.warn("Ignoring invalid content-length header value: '" + rawContentLength + "'");
			}
		}
		return null;
	}

	/**
	 * Try to read an EOL incrementing the buffer position if successful.
	 *
	 * @return whether an EOL was consumed
	 */
	private boolean tryConsumeEndOfLine(ByteBuffer buffer) {
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
