/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.http.codec.protobuf;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.MimeType;

/**
 * A {@code Decoder} that reads {@link com.google.protobuf.Message}s using
 * <a href="https://developers.google.com/protocol-buffers/">Google Protocol Buffers</a>.
 *
 * <p>Flux deserialized via
 * {@link #decode(Publisher, ResolvableType, MimeType, Map)} are expected to use
 * <a href="https://developers.google.com/protocol-buffers/docs/techniques?hl=en#streaming">
 * delimited Protobuf messages</a> with the size of each message specified before
 * the message itself. Single values deserialized via
 * {@link #decodeToMono(Publisher, ResolvableType, MimeType, Map)} are expected
 * to use regular Protobuf message format (without the size prepended before
 * the message).
 *
 * <p>Notice that default instance of Protobuf message produces empty byte
 * array, so {@code Mono.just(Msg.getDefaultInstance())} sent over the network
 * will be deserialized as an empty {@link Mono}.
 *
 * <p>To generate {@code Message} Java classes, you need to install the
 * {@code protoc} binary.
 *
 * <p>This decoder requires Protobuf 3 or higher, and supports
 * {@code "application/x-protobuf"} and {@code "application/octet-stream"} with
 * the official {@code "com.google.protobuf:protobuf-java"} library.
 *
 * @author Sébastien Deleuze
 * @since 5.1
 * @see ProtobufEncoder
 */
public class ProtobufDecoder extends ProtobufCodecSupport implements Decoder<Message> {

	/** The default max size for aggregating messages. */
	protected static final int DEFAULT_MESSAGE_MAX_SIZE = 64 * 1024;

	private static final ConcurrentMap<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap<>();

	private final ExtensionRegistry extensionRegistry;

	private int maxMessageSize = DEFAULT_MESSAGE_MAX_SIZE;


	/**
	 * Construct a new {@code ProtobufDecoder}.
	 */
	public ProtobufDecoder() {
		this(ExtensionRegistry.newInstance());
	}

	/**
	 * Construct a new {@code ProtobufDecoder} with an initializer that allows the
	 * registration of message extensions.
	 * @param extensionRegistry a message extension registry
	 */
	public ProtobufDecoder(ExtensionRegistry extensionRegistry) {
		Assert.notNull(extensionRegistry, "ExtensionRegistry must not be null");
		this.extensionRegistry = extensionRegistry;
	}


	public void setMaxMessageSize(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}


	@Override
	public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
		return Message.class.isAssignableFrom(elementType.toClass()) && supportsMimeType(mimeType);
	}

	@Override
	public Flux<Message> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		return Flux.from(inputStream)
				.flatMapIterable(new MessageDecoderFunction(elementType, this.maxMessageSize));
	}

	@Override
	public Mono<Message> decodeToMono(Publisher<DataBuffer> inputStream, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		return DataBufferUtils.join(inputStream).map(dataBuffer -> {
					try {
						Message.Builder builder = getMessageBuilder(elementType.toClass());
						ByteBuffer buffer = dataBuffer.asByteBuffer();
						builder.mergeFrom(CodedInputStream.newInstance(buffer), this.extensionRegistry);
						return builder.build();
					}
					catch (IOException ex) {
						throw new DecodingException("I/O error while parsing input stream", ex);
					}
					catch (Exception ex) {
						throw new DecodingException("Could not read Protobuf message: " + ex.getMessage(), ex);
					}
					finally {
						DataBufferUtils.release(dataBuffer);
					}
				}
		);
	}

	/**
	 * Create a new {@code Message.Builder} instance for the given class.
	 * <p>This method uses a ConcurrentHashMap for caching method lookups.
	 */
	private static Message.Builder getMessageBuilder(Class<?> clazz) throws Exception {
		Method method = methodCache.get(clazz);
		if (method == null) {
			method = clazz.getMethod("newBuilder");
			methodCache.put(clazz, method);
		}
		return (Message.Builder) method.invoke(clazz);
	}

	@Override
	public List<MimeType> getDecodableMimeTypes() {
		return getMimeTypes();
	}


	private class MessageDecoderFunction implements Function<DataBuffer, Iterable<? extends Message>> {

		private final ResolvableType elementType;

		private final int maxMessageSize;

		@Nullable
		private DataBuffer output;

		private int messageBytesToRead;


		public MessageDecoderFunction(ResolvableType elementType, int maxMessageSize) {
			this.elementType = elementType;
			this.maxMessageSize = maxMessageSize;
		}


		@Override
		public Iterable<? extends Message> apply(DataBuffer input) {
			try {
				List<Message> messages = new ArrayList<>();
				int remainingBytesToRead;
				int chunkBytesToRead;

				do {
					if (this.output == null) {
						int firstByte = input.read();
						if (firstByte == -1) {
							throw new DecodingException("Cannot parse message size");
						}
						this.messageBytesToRead = CodedInputStream.readRawVarint32(firstByte, input.asInputStream());
						if (this.messageBytesToRead > this.maxMessageSize) {
							throw new DecodingException(
									"The number of bytes to read from the incoming stream " +
											"(" + this.messageBytesToRead + ") exceeds " +
											"the configured limit (" + this.maxMessageSize + ")");
						}
						this.output = input.factory().allocateBuffer(this.messageBytesToRead);
					}

					chunkBytesToRead = this.messageBytesToRead >= input.readableByteCount() ?
							input.readableByteCount() : this.messageBytesToRead;
					remainingBytesToRead = input.readableByteCount() - chunkBytesToRead;

					byte[] bytesToWrite = new byte[chunkBytesToRead];
					input.read(bytesToWrite, 0, chunkBytesToRead);
					this.output.write(bytesToWrite);
					this.messageBytesToRead -= chunkBytesToRead;

					if (this.messageBytesToRead == 0) {
						Message.Builder builder = getMessageBuilder(this.elementType.toClass());
						ByteBuffer buffer = this.output.asByteBuffer();
						builder.mergeFrom(CodedInputStream.newInstance(buffer), extensionRegistry);
						messages.add(builder.build());
						DataBufferUtils.release(this.output);
						this.output = null;
					}
				} while (remainingBytesToRead > 0);
				return messages;
			}
			catch (DecodingException ex) {
				throw ex;
			}
			catch (IOException ex) {
				throw new DecodingException("I/O error while parsing input stream", ex);
			}
			catch (Exception ex) {
				throw new DecodingException("Could not read Protobuf message: " + ex.getMessage(), ex);
			}
			finally {
				DataBufferUtils.release(input);
			}
		}
	}

}
