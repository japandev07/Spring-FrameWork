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

import org.springframework.messaging.Message;

import org.springframework.util.Assert;
import reactor.function.Consumer;
import reactor.function.Function;
import reactor.io.Buffer;
import reactor.io.encoding.Codec;

import java.nio.ByteBuffer;

/**
 * A Reactor TCP {@link Codec} for sending and receiving STOMP messages.
 *
 * @author Andy Wilkinson
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class Reactor11StompCodec implements Codec<Buffer, Message<byte[]>, Message<byte[]>> {

	private final StompDecoder stompDecoder;

	private final StompEncoder stompEncoder;

	private final Function<Message<byte[]>, Buffer> encodingFunction;


	public Reactor11StompCodec() {
		this(new StompEncoder(), new StompDecoder());
	}

	public Reactor11StompCodec(StompEncoder encoder, StompDecoder decoder) {
		Assert.notNull(encoder, "'encoder' is required");
		Assert.notNull(decoder, "'decoder' is required");
		this.stompEncoder = encoder;
		this.stompDecoder = decoder;
		this.encodingFunction = new EncodingFunction(this.stompEncoder);
	}

	@Override
	public Function<Buffer, Message<byte[]>> decoder(final Consumer<Message<byte[]>> messageConsumer) {
		return new DecodingFunction(this.stompDecoder, messageConsumer);
	}

	@Override
	public Function<Message<byte[]>, Buffer> encoder() {
		return this.encodingFunction;
	}


	private static class EncodingFunction implements Function<Message<byte[]>, Buffer> {

		private final StompEncoder encoder;

		private EncodingFunction(StompEncoder encoder) {
			this.encoder = encoder;
		}

		@Override
		public Buffer apply(Message<byte[]> message) {
			byte[] bytes = this.encoder.encode(message);
			return new Buffer(ByteBuffer.wrap(bytes));
		}
	}

	private static class DecodingFunction implements Function<Buffer, Message<byte[]>> {

		private final StompDecoder decoder;

		private final Consumer<Message<byte[]>> messageConsumer;

		public DecodingFunction(StompDecoder decoder, Consumer<Message<byte[]>> next) {
			this.decoder = decoder;
			this.messageConsumer = next;
		}

		@Override
		public Message<byte[]> apply(Buffer buffer) {
			for (Message<byte[]> message : this.decoder.decode(buffer.byteBuffer())) {
				this.messageConsumer.accept(message);
			}
			return null;
		}
	}
}
