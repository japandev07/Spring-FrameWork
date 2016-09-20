/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.core.io.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import org.springframework.util.Assert;

/**
 * Utility class for working with {@link DataBuffer}s.
 *
 * @author Arjen Poutsma
 * @author Brian Clozel
 * @since 5.0
 */
public abstract class DataBufferUtils {

	private static final Consumer<ReadableByteChannel> CLOSE_CONSUMER = channel -> {
		try {
			if (channel != null) {
				channel.close();
			}
		}
		catch (IOException ex) {
		}
	};


	/**
	 * Read the given {@code InputStream} into a {@code Flux} of
	 * {@code DataBuffer}s. Closes the input stream when the flux is terminated.
	 * @param inputStream the input stream to read from
	 * @param dataBufferFactory the factory to create data buffers with
	 * @param bufferSize the maximum size of the data buffers
	 * @return a flux of data buffers read from the given channel
	 */
	public static Flux<DataBuffer> read(InputStream inputStream,
			DataBufferFactory dataBufferFactory, int bufferSize) {

		Assert.notNull(inputStream, "InputStream must not be null");
		Assert.notNull(dataBufferFactory, "DataBufferFactory must not be null");

		ReadableByteChannel channel = Channels.newChannel(inputStream);
		return read(channel, dataBufferFactory, bufferSize);
	}

	/**
	 * Read the given {@code ReadableByteChannel} into a {@code Flux} of
	 * {@code DataBuffer}s. Closes the channel when the flux is terminated.
	 * @param channel the channel to read from
	 * @param dataBufferFactory the factory to create data buffers with
	 * @param bufferSize the maximum size of the data buffers
	 * @return a flux of data buffers read from the given channel
	 */
	public static Flux<DataBuffer> read(ReadableByteChannel channel,
			DataBufferFactory dataBufferFactory, int bufferSize) {

		Assert.notNull(channel, "ReadableByteChannel must not be null");
		Assert.notNull(dataBufferFactory, "DataBufferFactory must not be null");

		return Flux.generate(() -> channel,
				new ReadableByteChannelGenerator(dataBufferFactory, bufferSize),
				CLOSE_CONSUMER);
	}

	/**
	 * Relay buffers from the given {@link Publisher} until the total
	 * {@linkplain DataBuffer#readableByteCount() byte count} reaches
	 * the given maximum byte count, or until the publisher is complete.
	 * @param publisher the publisher to filter
	 * @param maxByteCount the maximum byte count
	 * @return a flux whose maximum byte count is {@code maxByteCount}
	 */
	public static Flux<DataBuffer> takeUntilByteCount(Publisher<DataBuffer> publisher, long maxByteCount) {
		Assert.notNull(publisher, "Publisher must not be null");
		Assert.isTrue(maxByteCount >= 0, "'maxByteCount' must be a positive number");
		AtomicLong byteCountDown = new AtomicLong(maxByteCount);

		return Flux.from(publisher).
				takeWhile(dataBuffer -> {
					int delta = -dataBuffer.readableByteCount();
					long currentCount = byteCountDown.getAndAdd(delta);
					return currentCount >= 0;
				}).
				map(dataBuffer -> {
					long currentCount = byteCountDown.get();
					if (currentCount >= 0) {
						return dataBuffer;
					}
					else {
						// last buffer
						int size = (int) (currentCount + dataBuffer.readableByteCount());
						return dataBuffer.slice(0, size);
					}
				});
	}

	/**
	 * Skip buffers from the given {@link Publisher} until the total
	 * {@linkplain DataBuffer#readableByteCount() byte count} reaches
	 * the given maximum byte count, or until the publisher is complete.
	 * @param publisher the publisher to filter
	 * @param maxByteCount the maximum byte count
	 * @return a flux with the remaining part of the given publisher
	 */
	public static Flux<DataBuffer> skipUntilByteCount(Publisher<DataBuffer> publisher, long maxByteCount) {
		Assert.notNull(publisher, "Publisher must not be null");
		Assert.isTrue(maxByteCount >= 0, "'maxByteCount' must be a positive number");
		AtomicLong byteCountDown = new AtomicLong(maxByteCount);

		return Flux.from(publisher).
				skipUntil(dataBuffer -> {
					int delta = -dataBuffer.readableByteCount();
					long currentCount = byteCountDown.addAndGet(delta);
					if(currentCount < 0) {
						return true;
					} else {
						DataBufferUtils.release(dataBuffer);
						return false;
					}
				}).
				map(dataBuffer -> {
					long currentCount = byteCountDown.get();
					// slice first buffer, then let others flow through
					if (currentCount < 0) {
						int skip = (int) (currentCount + dataBuffer.readableByteCount());
						byteCountDown.set(0);
						return dataBuffer.slice(skip, dataBuffer.readableByteCount() - skip);
					}
					return dataBuffer;
				});
	}

	/**
	 * Retain the given data buffer, it it is a {@link PooledDataBuffer}.
	 * @param dataBuffer the data buffer to retain
	 * @return the retained buffer
	 */
	@SuppressWarnings("unchecked")
	public static <T extends DataBuffer> T retain(T dataBuffer) {
		if (dataBuffer instanceof PooledDataBuffer) {
			return (T) ((PooledDataBuffer) dataBuffer).retain();
		}
		else {
			return dataBuffer;
		}
	}

	/**
	 * Release the given data buffer, if it is a {@link PooledDataBuffer}.
	 * @param dataBuffer the data buffer to release
	 * @return {@code true} if the buffer was released; {@code false} otherwise.
	 */
	public static boolean release(DataBuffer dataBuffer) {
		if (dataBuffer instanceof PooledDataBuffer) {
			return ((PooledDataBuffer) dataBuffer).release();
		}
		return false;
	}


	private static class ReadableByteChannelGenerator
			implements BiFunction<ReadableByteChannel, SynchronousSink<DataBuffer>, ReadableByteChannel> {

		private final DataBufferFactory dataBufferFactory;

		private final int chunkSize;

		public ReadableByteChannelGenerator(DataBufferFactory dataBufferFactory, int chunkSize) {
			this.dataBufferFactory = dataBufferFactory;
			this.chunkSize = chunkSize;
		}

		@Override
		public ReadableByteChannel apply(ReadableByteChannel channel, SynchronousSink<DataBuffer> sub) {
			try {
				ByteBuffer byteBuffer = ByteBuffer.allocate(chunkSize);
				int read;
				if ((read = channel.read(byteBuffer)) >= 0) {
					byteBuffer.flip();
					boolean release = true;
					DataBuffer dataBuffer = this.dataBufferFactory.allocateBuffer(read);
					try {
						dataBuffer.write(byteBuffer);
						release = false;
						sub.next(dataBuffer);
					}
					finally {
						if (release) {
							release(dataBuffer);
						}
					}
				}
				else {
					sub.complete();
				}
			}
			catch (IOException ex) {
				sub.error(ex);
			}
			return channel;
		}
	}

}
