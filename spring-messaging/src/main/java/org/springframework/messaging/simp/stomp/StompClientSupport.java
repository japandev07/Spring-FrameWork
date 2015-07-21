/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.messaging.simp.stomp;

import java.util.Arrays;

import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;

/**
 * Base class for STOMP client implementations.
 *
 * <p>Subclasses can connect over WebSocket or TCP using any library.
 * When creating a new connection a sub-class can create an instance of
 * {@link DefaultStompSession} which extends
 * {@link org.springframework.messaging.tcp.TcpConnectionHandler
 * TcpConnectionHandler} whose lifecycle methods the sub-class must then invoke.
 *
 * <p>In effect {@code TcpConnectionHandler} and {@code TcpConnection} are the
 * contracts any sub-class must adapt to while using {@link StompEncoder} and
 * {@link StompDecoder} to encode and decode STOMP messages.
 *
 * @author Rossen Stoyanchev
 * @since 4.2
 */
public abstract class StompClientSupport {

	private MessageConverter messageConverter = new SimpleMessageConverter();

	private TaskScheduler taskScheduler;

	private long[] defaultHeartbeat = new long[] {10000, 10000};

	private long receiptTimeLimit = 15 * 1000;


	/**
	 * Set the {@link MessageConverter} to use to convert the payload of incoming
	 * and outgoing messages to and from {@code byte[]} based on object type
	 * and the "content-type" header.
	 * <p>By default, {@link SimpleMessageConverter} is configured.
	 * @param messageConverter the message converter to use
	 */
	public void setMessageConverter(MessageConverter messageConverter) {
		Assert.notNull(messageConverter, "'messageConverter' must not be null");
		this.messageConverter = messageConverter;
	}

	/**
	 * Return the configured {@link MessageConverter}.
	 */
	public MessageConverter getMessageConverter() {
		return this.messageConverter;
	}

	/**
	 * Configure a scheduler to use for heartbeats and for receipt tracking.
	 * <p><strong>Note:</strong> some transports have built-in support to work
	 * with heartbeats and therefore do not require a TaskScheduler.
	 * Receipts however, if needed, do require a TaskScheduler to be configured.
	 * <p>By default, this is not set.
	 */
	public void setTaskScheduler(TaskScheduler taskScheduler) {
		this.taskScheduler = taskScheduler;
	}

	/**
	 * The configured TaskScheduler.
	 */
	public TaskScheduler getTaskScheduler() {
		return this.taskScheduler;
	}

	/**
	 * Configure the default value for the "heart-beat" header of the STOMP
	 * CONNECT frame. The first number represents how often the client will write
	 * or send a heart-beat. The second is how often the server should write.
	 * A value of 0 means no heart-beats.
	 * <p>By default this is set to "10000,10000" but sub-classes may override
	 * that default and for example set it to "0,0" if they require a
	 * TaskScheduler to be configured first.
	 * @param heartbeat the value for the CONNECT "heart-beat" header
	 * @see <a href="http://stomp.github.io/stomp-specification-1.2.html#Heart-beating">
	 * http://stomp.github.io/stomp-specification-1.2.html#Heart-beating</a>
	 */
	public void setDefaultHeartbeat(long[] heartbeat) {
		Assert.notNull(heartbeat);
		Assert.isTrue(heartbeat[0] >= 0 && heartbeat[1] >=0 , "Invalid heart-beat: "  + Arrays.toString(heartbeat));
		this.defaultHeartbeat = heartbeat;
	}

	/**
	 * Return the configured default heart-beat value, never {@code null}.
	 */
	public long[] getDefaultHeartbeat() {
		return this.defaultHeartbeat;
	}

	/**
	 * Whether heartbeats are enabled. Returns {@code false} if
	 * {@link #setDefaultHeartbeat defaultHeartbeat} is set to "0,0", and
	 * {@code true} otherwise.
	 */
	public boolean isDefaultHeartbeatEnabled() {
		return (getDefaultHeartbeat() != null && getDefaultHeartbeat()[0] != 0 && getDefaultHeartbeat()[1] != 0);
	}

	/**
	 * Configure the number of milliseconds before a receipt is considered expired.
	 * <p>By default set to 15,000 (15 seconds).
	 */
	public void setReceiptTimeLimit(long receiptTimeLimit) {
		Assert.isTrue(receiptTimeLimit > 0);
		this.receiptTimeLimit = receiptTimeLimit;
	}

	/**
	 * Return the configured receipt time limit.
	 */
	public long getReceiptTimeLimit() {
		return this.receiptTimeLimit;
	}


	/**
	 * Factory method for create and configure a new session.
	 * @param connectHeaders headers for the STOMP CONNECT frame
	 * @param handler the handler for the STOMP session
	 * @return the created session
	 */
	protected ConnectionHandlingStompSession createSession(StompHeaders connectHeaders, StompSessionHandler handler) {
		connectHeaders = processConnectHeaders(connectHeaders);
		DefaultStompSession session = new DefaultStompSession(handler, connectHeaders);
		session.setMessageConverter(getMessageConverter());
		session.setTaskScheduler(getTaskScheduler());
		session.setReceiptTimeLimit(getReceiptTimeLimit());
		return session;
	}

	/**
	 * Further initialize the StompHeaders, for example setting the heart-beat
	 * header if necessary.
	 * @param connectHeaders the headers to modify
	 * @return the modified headers
	 */
	protected StompHeaders processConnectHeaders(StompHeaders connectHeaders) {
		connectHeaders = (connectHeaders != null ? connectHeaders : new StompHeaders());
		if (connectHeaders.getHeartbeat() == null) {
			connectHeaders.setHeartbeat(getDefaultHeartbeat());
		}
		return connectHeaders;
	}

}
