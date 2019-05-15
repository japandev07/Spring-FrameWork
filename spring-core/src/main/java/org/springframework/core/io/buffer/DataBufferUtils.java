/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.io.buffer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Utility class for working with {@link DataBuffer DataBuffers}.
 *
 * @author Arjen Poutsma
 * @author Brian Clozel
 * @since 5.0
 */
public abstract class DataBufferUtils {

	private static final Consumer<DataBuffer> RELEASE_CONSUMER = DataBufferUtils::release;


	//---------------------------------------------------------------------
	// Reading
	//---------------------------------------------------------------------

	/**
	 * Obtain a {@link InputStream} from the given supplier, and read it into a
	 * {@code Flux} of {@code DataBuffer}s. Closes the input stream when the
	 * Flux is terminated.
	 * @param inputStreamSupplier the supplier for the input stream to read from
	 * @param bufferFactory the factory to create data buffers with
	 * @param bufferSize the maximum size of the data buffers
	 * @return a Flux of data buffers read from the given channel
	 */
	public static Flux<DataBuffer> readInputStream(
			Callable<InputStream> inputStreamSupplier, DataBufferFactory bufferFactory, int bufferSize) {

		Assert.notNull(inputStreamSupplier, "'inputStreamSupplier' must not be null");
		return readByteChannel(() -> Channels.newChannel(inputStreamSupplier.call()), bufferFactory, bufferSize);
	}

	/**
	 * Obtain a {@link ReadableByteChannel} from the given supplier, and read
	 * it into a {@code Flux} of {@code DataBuffer}s. Closes the channel when
	 * the Flux is terminated.
	 * @param channelSupplier the supplier for the channel to read from
	 * @param bufferFactory the factory to create data buffers with
	 * @param bufferSize the maximum size of the data buffers
	 * @return a Flux of data buffers read from the given channel
	 */
	public static Flux<DataBuffer> readByteChannel(
			Callable<ReadableByteChannel> channelSupplier, DataBufferFactory bufferFactory, int bufferSize) {

		Assert.notNull(channelSupplier, "'channelSupplier' must not be null");
		Assert.notNull(bufferFactory, "'dataBufferFactory' must not be null");
		Assert.isTrue(bufferSize > 0, "'bufferSize' must be > 0");

		return Flux.using(channelSupplier,
				channel -> Flux.generate(new ReadableByteChannelGenerator(channel, bufferFactory, bufferSize)),
				DataBufferUtils::closeChannel);

		// No doOnDiscard as operators used do not cache
	}

	/**
	 * Obtain a {@code AsynchronousFileChannel} from the given supplier, and read
	 * it into a {@code Flux} of {@code DataBuffer}s. Closes the channel when
	 * the Flux is terminated.
	 * @param channelSupplier the supplier for the channel to read from
	 * @param bufferFactory the factory to create data buffers with
	 * @param bufferSize the maximum size of the data buffers
	 * @return a Flux of data buffers read from the given channel
	 */
	public static Flux<DataBuffer> readAsynchronousFileChannel(
			Callable<AsynchronousFileChannel> channelSupplier, DataBufferFactory bufferFactory, int bufferSize) {

		return readAsynchronousFileChannel(channelSupplier, 0, bufferFactory, bufferSize);
	}

	/**
	 * Obtain a {@code AsynchronousFileChannel} from the given supplier, and
	 * read it into a {@code Flux} of {@code DataBuffer}s, starting at the given
	 * position. Closes the channel when the Flux is terminated.
	 * @param channelSupplier the supplier for the channel to read from
	 * @param position the position to start reading from
	 * @param bufferFactory the factory to create data buffers with
	 * @param bufferSize the maximum size of the data buffers
	 * @return a Flux of data buffers read from the given channel
	 */
	public static Flux<DataBuffer> readAsynchronousFileChannel(
			Callable<AsynchronousFileChannel> channelSupplier, long position,
			DataBufferFactory bufferFactory, int bufferSize) {

		Assert.notNull(channelSupplier, "'channelSupplier' must not be null");
		Assert.notNull(bufferFactory, "'dataBufferFactory' must not be null");
		Assert.isTrue(position >= 0, "'position' must be >= 0");
		Assert.isTrue(bufferSize > 0, "'bufferSize' must be > 0");

		Flux<DataBuffer> flux = Flux.using(channelSupplier,
				channel -> Flux.create(sink -> {
					ReadCompletionHandler handler =
							new ReadCompletionHandler(channel, sink, position, bufferFactory, bufferSize);
					sink.onDispose(handler::dispose);
					DataBuffer dataBuffer = bufferFactory.allocateBuffer(bufferSize);
					ByteBuffer byteBuffer = dataBuffer.asByteBuffer(0, bufferSize);
					channel.read(byteBuffer, position, dataBuffer, handler);
				}),
				channel -> {
					// Do not close channel from here, rather wait for the current read callback
					// and then complete after releasing the DataBuffer.
				});

		return flux.doOnDiscard(PooledDataBuffer.class, DataBufferUtils::release);
	}

	/**
	 * Read the given {@code Resource} into a {@code Flux} of {@code DataBuffer}s.
	 * <p>If the resource is a file, it is read into an
	 * {@code AsynchronousFileChannel} and turned to {@code Flux} via
	 * {@link #readAsynchronousFileChannel(Callable, DataBufferFactory, int)} or else
	 * fall back to {@link #readByteChannel(Callable, DataBufferFactory, int)}.
	 * Closes the channel when the flux is terminated.
	 * @param resource the resource to read from
	 * @param bufferFactory the factory to create data buffers with
	 * @param bufferSize the maximum size of the data buffers
	 * @return a Flux of data buffers read from the given channel
	 */
	public static Flux<DataBuffer> read(Resource resource, DataBufferFactory bufferFactory, int bufferSize) {
		return read(resource, 0, bufferFactory, bufferSize);
	}

	/**
	 * Read the given {@code Resource} into a {@code Flux} of {@code DataBuffer}s
	 * starting at the given position.
	 * <p>If the resource is a file, it is read into an
	 * {@code AsynchronousFileChannel} and turned to {@code Flux} via
	 * {@link #readAsynchronousFileChannel(Callable, DataBufferFactory, int)} or else
	 * fall back on {@link #readByteChannel(Callable, DataBufferFactory, int)}.
	 * Closes the channel when the flux is terminated.
	 * @param resource the resource to read from
	 * @param position the position to start reading from
	 * @param bufferFactory the factory to create data buffers with
	 * @param bufferSize the maximum size of the data buffers
	 * @return a Flux of data buffers read from the given channel
	 */
	public static Flux<DataBuffer> read(
			Resource resource, long position, DataBufferFactory bufferFactory, int bufferSize) {

		try {
			if (resource.isFile()) {
				File file = resource.getFile();
				return readAsynchronousFileChannel(
						() -> AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ),
						position, bufferFactory, bufferSize);
			}
		}
		catch (IOException ignore) {
			// fallback to resource.readableChannel(), below
		}
		Flux<DataBuffer> result = readByteChannel(resource::readableChannel, bufferFactory, bufferSize);
		return position == 0 ? result : skipUntilByteCount(result, position);
	}


	//---------------------------------------------------------------------
	// Writing
	//---------------------------------------------------------------------

	/**
	 * Write the given stream of {@link DataBuffer DataBuffers} to the given
	 * {@code OutputStream}. Does <strong>not</strong> close the output stream
	 * when the flux is terminated, and does <strong>not</strong>
	 * {@linkplain #release(DataBuffer) release} the data buffers in the source.
	 * If releasing is required, then subscribe to the returned {@code Flux}
	 * with a {@link #releaseConsumer()}.
	 * <p>Note that the writing process does not start until the returned
	 * {@code Flux} is subscribed to.
	 * @param source the stream of data buffers to be written
	 * @param outputStream the output stream to write to
	 * @return a Flux containing the same buffers as in {@code source}, that
	 * starts the writing process when subscribed to, and that publishes any
	 * writing errors and the completion signal
	 */
	public static Flux<DataBuffer> write(Publisher<DataBuffer> source, OutputStream outputStream) {
		Assert.notNull(source, "'source' must not be null");
		Assert.notNull(outputStream, "'outputStream' must not be null");

		WritableByteChannel channel = Channels.newChannel(outputStream);
		return write(source, channel);
	}

	/**
	 * Write the given stream of {@link DataBuffer DataBuffers} to the given
	 * {@code WritableByteChannel}. Does <strong>not</strong> close the channel
	 * when the flux is terminated, and does <strong>not</strong>
	 * {@linkplain #release(DataBuffer) release} the data buffers in the source.
	 * If releasing is required, then subscribe to the returned {@code Flux}
	 * with a {@link #releaseConsumer()}.
	 * <p>Note that the writing process does not start until the returned
	 * {@code Flux} is subscribed to.
	 * @param source the stream of data buffers to be written
	 * @param channel the channel to write to
	 * @return a Flux containing the same buffers as in {@code source}, that
	 * starts the writing process when subscribed to, and that publishes any
	 * writing errors and the completion signal
	 */
	public static Flux<DataBuffer> write(Publisher<DataBuffer> source, WritableByteChannel channel) {
		Assert.notNull(source, "'source' must not be null");
		Assert.notNull(channel, "'channel' must not be null");

		Flux<DataBuffer> flux = Flux.from(source);
		return Flux.create(sink -> {
			WritableByteChannelSubscriber subscriber = new WritableByteChannelSubscriber(sink, channel);
			sink.onDispose(subscriber);
			flux.subscribe(subscriber);
		});
	}

	/**
	 * Write the given stream of {@link DataBuffer DataBuffers} to the given
	 * {@code AsynchronousFileChannel}. Does <strong>not</strong> close the
	 * channel when the flux is terminated, and does <strong>not</strong>
	 * {@linkplain #release(DataBuffer) release} the data buffers in the source.
	 * If releasing is required, then subscribe to the returned {@code Flux}
	 * with a {@link #releaseConsumer()}.
	 * <p>Note that the writing process does not start until the returned
	 * {@code Flux} is subscribed to.
	 * @param source the stream of data buffers to be written
	 * @param channel the channel to write to
	 * @return a Flux containing the same buffers as in {@code source}, that
	 * starts the writing process when subscribed to, and that publishes any
	 * writing errors and the completion signal
	 * @since 5.0.10
	 */
	public static Flux<DataBuffer> write(Publisher<DataBuffer> source, AsynchronousFileChannel channel) {
		return write(source, channel, 0);
	}

	/**
	 * Write the given stream of {@link DataBuffer DataBuffers} to the given
	 * {@code AsynchronousFileChannel}. Does <strong>not</strong> close the channel
	 * when the flux is terminated, and does <strong>not</strong>
	 * {@linkplain #release(DataBuffer) release} the data buffers in the source.
	 * If releasing is required, then subscribe to the returned {@code Flux} with a
	 * {@link #releaseConsumer()}.
	 * <p>Note that the writing process does not start until the returned
	 * {@code Flux} is subscribed to.
	 * @param source the stream of data buffers to be written
	 * @param channel the channel to write to
	 * @param position file position write write is to begin; must be non-negative
	 * @return a flux containing the same buffers as in {@code source}, that
	 * starts the writing process when subscribed to, and that publishes any
	 * writing errors and the completion signal
	 */
	public static Flux<DataBuffer> write(
			Publisher<DataBuffer> source, AsynchronousFileChannel channel, long position) {

		Assert.notNull(source, "'source' must not be null");
		Assert.notNull(channel, "'channel' must not be null");
		Assert.isTrue(position >= 0, "'position' must be >= 0");

		Flux<DataBuffer> flux = Flux.from(source);
		return Flux.create(sink -> {
			WriteCompletionHandler handler = new WriteCompletionHandler(sink, channel, position);
			sink.onDispose(handler);
			flux.subscribe(handler);
		});
	}

	static void closeChannel(@Nullable Channel channel) {
		if (channel != null && channel.isOpen()) {
			try {
				channel.close();
			}
			catch (IOException ignored) {
			}
		}
	}


	//---------------------------------------------------------------------
	// Various
	//---------------------------------------------------------------------

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

		AtomicLong countDown = new AtomicLong(maxByteCount);
		return Flux.from(publisher)
				.map(buffer -> {
					long remainder = countDown.addAndGet(-buffer.readableByteCount());
					if (remainder < 0) {
						int length = buffer.readableByteCount() + (int) remainder;
						return buffer.slice(0, length);
					}
					else {
						return buffer;
					}
				})
				.takeUntil(buffer -> countDown.get() <= 0);

		// No doOnDiscard as operators used do not cache (and drop) buffers
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

		return Flux.defer(() -> {
			AtomicLong countDown = new AtomicLong(maxByteCount);
			return Flux.from(publisher)
					.skipUntil(buffer -> {
						long remainder = countDown.addAndGet(-buffer.readableByteCount());
						return remainder < 0;
					})
					.map(buffer -> {
						long remainder = countDown.get();
						if (remainder < 0) {
							countDown.set(0);
							int start = buffer.readableByteCount() + (int)remainder;
							int length = (int) -remainder;
							return buffer.slice(start, length);
						}
						else {
							return buffer;
						}
					});
		}).doOnDiscard(PooledDataBuffer.class, DataBufferUtils::release);
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
	 * Release the given data buffer, if it is a {@link PooledDataBuffer} and
	 * has been {@linkplain PooledDataBuffer#isAllocated() allocated}.
	 * @param dataBuffer the data buffer to release
	 * @return {@code true} if the buffer was released; {@code false} otherwise.
	 */
	public static boolean release(@Nullable DataBuffer dataBuffer) {
		if (dataBuffer instanceof PooledDataBuffer) {
			PooledDataBuffer pooledDataBuffer = (PooledDataBuffer) dataBuffer;
			if (pooledDataBuffer.isAllocated()) {
				return pooledDataBuffer.release();
			}
		}
		return false;
	}

	/**
	 * Return a consumer that calls {@link #release(DataBuffer)} on all
	 * passed data buffers.
	 */
	public static Consumer<DataBuffer> releaseConsumer() {
		return RELEASE_CONSUMER;
	}

	/**
	 * Return a new {@code DataBuffer} composed from joining together the given
	 * {@code dataBuffers} elements. Depending on the {@link DataBuffer} type,
	 * the returned buffer may be a single buffer containing all data of the
	 * provided buffers, or it may be a zero-copy, composite with references to
	 * the given buffers.
	 * <p>If {@code dataBuffers} produces an error or if there is a cancel
	 * signal, then all accumulated buffers will be
	 * {@linkplain #release(DataBuffer) released}.
	 * <p>Note that the given data buffers do <strong>not</strong> have to be
	 * released. They will be released as part of the returned composite.
	 * @param dataBuffers the data buffers that are to be composed
	 * @return a buffer that is composed from the {@code dataBuffers} argument
	 * @since 5.0.3
	 */
	public static Mono<DataBuffer> join(Publisher<DataBuffer> dataBuffers) {
		Assert.notNull(dataBuffers, "'dataBuffers' must not be null");

		if (dataBuffers instanceof Mono) {
			return (Mono<DataBuffer>) dataBuffers;
		}

		return Flux.from(dataBuffers)
				.collectList()
				.filter(list -> !list.isEmpty())
				.map(list -> list.get(0).factory().join(list))
				.doOnDiscard(PooledDataBuffer.class, DataBufferUtils::release);
	}

	/**
	 * Return a {@link Matcher} for the given delimiters. The matcher can be used to find the
	 * delimiters in data buffers.
	 * @param delimiter the delimiter bytes to find
	 * @return the matcher
	 * @since 5.2
	 */
	public static Matcher matcher(byte[] delimiter) {
		Assert.isTrue(delimiter.length > 0, "Delimiter must not be empty");
		return new KnuthMorrisPrattMatcher(delimiter);
	}

	/**
	 * Splits the given stream of data buffers around the given delimiter.
	 * The returned flux contains data buffers that are terminated by the given delimiter,
	 * though the delimiter itself is removed.
	 * @param dataBuffers the input stream of data buffers
	 * @param delimiter the delimiting byte array
	 * @return the flux of data buffers created by splitting the given data buffers around the
	 * given delimiter
	 * @since 5.2
	 */
	public static Flux<DataBuffer> split(Publisher<DataBuffer> dataBuffers, byte[] delimiter) {
		return split(dataBuffers, delimiter, true);
	}

	/**
	 * Splits the given stream of data buffers around the given delimiter.
	 * The returned flux contains data buffers that are terminated by the given delimiter,
	 * which is included when {@code stripDelimiter} is {@code false}.
	 * @param dataBuffers the input stream of data buffers
	 * @param delimiter the delimiter bytes
	 * @param stripDelimiter whether to include the delimiter at the end of each resulting buffer
	 * @return the flux of data buffers created by splitting the given data buffers around the
	 * given delimiter
	 * @since 5.2
	 */
	public static Flux<DataBuffer> split(Publisher<DataBuffer> dataBuffers, byte[] delimiter,
			boolean stripDelimiter) {

		return split(dataBuffers, new byte[][]{delimiter}, stripDelimiter);
	}

	/**
	 * Splits the given stream of data buffers around the given delimiters.
	 * The returned flux contains data buffers that are terminated by any of the given delimiters,
	 * which are included when {@code stripDelimiter} is {@code false}.
	 * @param dataBuffers the input stream of data buffers
	 * @param delimiters the delimiters, one per element
	 * @param stripDelimiter whether to include the delimiters at the end of each resulting buffer
	 * @return the flux of data buffers created by splitting the given data buffers around the
	 * given delimiters
	 * @since 5.2
	 */
	public static Flux<DataBuffer> split(Publisher<DataBuffer> dataBuffers, byte[][] delimiters,
			boolean stripDelimiter) {
		Assert.notNull(dataBuffers, "DataBuffers must not be null");
		Assert.isTrue(delimiters.length > 0, "Delimiter must not be empty");

		Matcher[] matchers = matchers(delimiters);

		return Flux.from(dataBuffers)
				.flatMap(buffer -> endFrameAfterDelimiter(buffer, matchers))
				.bufferUntil(buffer -> buffer instanceof EndFrameBuffer)
				.map(buffers -> joinAndStrip(buffers, stripDelimiter))
				.doOnDiscard(PooledDataBuffer.class, DataBufferUtils::release);
	}

	private static Matcher[] matchers(byte[][] delimiters) {
		Assert.isTrue(delimiters.length > 0, "Delimiters must not be empty");
		Matcher[] result = new Matcher[delimiters.length];
		for (int i = 0; i < delimiters.length; i++) {
			result[i] = matcher(delimiters[i]);
		}
		return result;
	}

	/**
	 * Finds the {@link Matcher} with the first match and longest delimiter, and inserts a
	 * {@link EndFrameBuffer} just after its match.
	 *
	 * @param dataBuffer the buffer to find delimiters in
	 * @param matchers used to find the first delimiters
	 * @return a flux of buffers, containing {@link EndFrameBuffer} after each delimiter that was
	 * found in {@code dataBuffer}. Returns  Flux, because returning List (w/ flatMapIterable)
	 * results in memory leaks due to pre-fetching.
	 */
	private static Flux<DataBuffer> endFrameAfterDelimiter(DataBuffer dataBuffer, Matcher[] matchers) {
		List<DataBuffer> result = new ArrayList<>();
		do {
			int matchedEndIdx = Integer.MAX_VALUE;
			byte[] matchedDelimiter = new byte[0];
			for (Matcher matcher : matchers) {
				int endIdx = matcher.match(dataBuffer);
				if (endIdx != -1 &&
						endIdx <= matchedEndIdx &&
						matcher.delimiter().length > matchedDelimiter.length) {
					matchedEndIdx = endIdx;
					matchedDelimiter = matcher.delimiter();
				}
			}
			if (matchedDelimiter.length > 0) {
				int readPosition = dataBuffer.readPosition();
				int length = matchedEndIdx + 1 - readPosition ;
				result.add(dataBuffer.retainedSlice(readPosition, length));
				result.add(new EndFrameBuffer(matchedDelimiter));
				dataBuffer.readPosition(matchedEndIdx + 1);

				for (Matcher matcher : matchers) {
					matcher.reset();
				}
			}
			else {
				result.add(retain(dataBuffer));
				break;
			}
		}
		while (dataBuffer.readableByteCount() > 0);

		DataBufferUtils.release(dataBuffer);
		return Flux.fromIterable(result);
	}

	/**
	 * Joins the given list of buffers. If the list ends with a {@link EndFrameBuffer}, it is
	 * removed. If {@code stripDelimiter} is {@code true} and the resulting buffer ends with
	 * a delimiter, it is removed.
	 * @param dataBuffers the data buffers to join
	 * @param stripDelimiter whether to strip the delimiter
	 * @return
	 */
	private static DataBuffer joinAndStrip(List<DataBuffer> dataBuffers,
			boolean stripDelimiter) {

		Assert.state(!dataBuffers.isEmpty(), "DataBuffers should not be empty");

		byte[] matchingDelimiter = null;

		int lastIdx = dataBuffers.size() - 1;
		DataBuffer lastBuffer = dataBuffers.get(lastIdx);
		if (lastBuffer instanceof EndFrameBuffer) {
			matchingDelimiter = ((EndFrameBuffer) lastBuffer).delimiter();
			dataBuffers.remove(lastIdx);
		}

		DataBuffer result = dataBuffers.get(0).factory().join(dataBuffers);

		if (stripDelimiter && matchingDelimiter != null) {
			result.writePosition(result.writePosition() - matchingDelimiter.length);
		}
		return result;
	}


	/**
	 * Defines an object that matches a data buffer against a delimiter.
	 * @since 5.2
	 * @see #match(DataBuffer)
	 */
	public interface Matcher {

		/**
		 * Returns the position of the final matching delimiter byte that matches the given buffer,
		 * or {@code -1} if not found.
		 * @param dataBuffer the buffer in which to search for the delimiter
		 * @return the position of the final matching delimiter, or {@code -1} if not found.
		 */
		int match(DataBuffer dataBuffer);

		/**
		 * Return the delimiter used for this matcher.
		 * @return the delimiter
		 */
		byte[] delimiter();

		/**
		 * Resets the state of this matcher.
		 */
		void reset();

	}


	private static class ReadableByteChannelGenerator implements Consumer<SynchronousSink<DataBuffer>> {

		private final ReadableByteChannel channel;

		private final DataBufferFactory dataBufferFactory;

		private final int bufferSize;

		public ReadableByteChannelGenerator(
				ReadableByteChannel channel, DataBufferFactory dataBufferFactory, int bufferSize) {

			this.channel = channel;
			this.dataBufferFactory = dataBufferFactory;
			this.bufferSize = bufferSize;
		}

		@Override
		public void accept(SynchronousSink<DataBuffer> sink) {
			boolean release = true;
			DataBuffer dataBuffer = this.dataBufferFactory.allocateBuffer(this.bufferSize);
			try {
				int read;
				ByteBuffer byteBuffer = dataBuffer.asByteBuffer(0, dataBuffer.capacity());
				if ((read = this.channel.read(byteBuffer)) >= 0) {
					dataBuffer.writePosition(read);
					release = false;
					sink.next(dataBuffer);
				}
				else {
					sink.complete();
				}
			}
			catch (IOException ex) {
				sink.error(ex);
			}
			finally {
				if (release) {
					release(dataBuffer);
				}
			}
		}
	}


	private static class ReadCompletionHandler implements CompletionHandler<Integer, DataBuffer> {

		private final AsynchronousFileChannel channel;

		private final FluxSink<DataBuffer> sink;

		private final DataBufferFactory dataBufferFactory;

		private final int bufferSize;

		private final AtomicLong position;

		private final AtomicBoolean disposed = new AtomicBoolean();

		public ReadCompletionHandler(AsynchronousFileChannel channel,
				FluxSink<DataBuffer> sink, long position, DataBufferFactory dataBufferFactory, int bufferSize) {

			this.channel = channel;
			this.sink = sink;
			this.position = new AtomicLong(position);
			this.dataBufferFactory = dataBufferFactory;
			this.bufferSize = bufferSize;
		}

		@Override
		public void completed(Integer read, DataBuffer dataBuffer) {
			if (read != -1 && !this.disposed.get()) {
				long pos = this.position.addAndGet(read);
				dataBuffer.writePosition(read);
				this.sink.next(dataBuffer);
				// onNext may have led to onCancel (e.g. downstream takeUntil)
				if (this.disposed.get()) {
					complete();
				}
				else {
					DataBuffer newDataBuffer = this.dataBufferFactory.allocateBuffer(this.bufferSize);
					ByteBuffer newByteBuffer = newDataBuffer.asByteBuffer(0, this.bufferSize);
					this.channel.read(newByteBuffer, pos, newDataBuffer, this);
				}
			}
			else {
				release(dataBuffer);
				complete();
			}
		}

		private void complete() {
			this.sink.complete();
			closeChannel(this.channel);
		}

		@Override
		public void failed(Throwable exc, DataBuffer dataBuffer) {
			release(dataBuffer);
			this.sink.error(exc);
			closeChannel(this.channel);
		}

		public void dispose() {
			this.disposed.set(true);
		}
	}


	private static class WritableByteChannelSubscriber extends BaseSubscriber<DataBuffer> {

		private final FluxSink<DataBuffer> sink;

		private final WritableByteChannel channel;

		public WritableByteChannelSubscriber(FluxSink<DataBuffer> sink, WritableByteChannel channel) {
			this.sink = sink;
			this.channel = channel;
		}

		@Override
		protected void hookOnSubscribe(Subscription subscription) {
			request(1);
		}

		@Override
		protected void hookOnNext(DataBuffer dataBuffer) {
			try {
				ByteBuffer byteBuffer = dataBuffer.asByteBuffer();
				while (byteBuffer.hasRemaining()) {
					this.channel.write(byteBuffer);
				}
				this.sink.next(dataBuffer);
				request(1);
			}
			catch (IOException ex) {
				this.sink.next(dataBuffer);
				this.sink.error(ex);
			}
		}

		@Override
		protected void hookOnError(Throwable throwable) {
			this.sink.error(throwable);
		}

		@Override
		protected void hookOnComplete() {
			this.sink.complete();
		}
	}


	private static class WriteCompletionHandler extends BaseSubscriber<DataBuffer>
			implements CompletionHandler<Integer, ByteBuffer> {

		private final FluxSink<DataBuffer> sink;

		private final AsynchronousFileChannel channel;

		private final AtomicBoolean completed = new AtomicBoolean();

		private final AtomicReference<Throwable> error = new AtomicReference<>();

		private final AtomicLong position;

		private final AtomicReference<DataBuffer> dataBuffer = new AtomicReference<>();

		public WriteCompletionHandler(
				FluxSink<DataBuffer> sink, AsynchronousFileChannel channel, long position) {

			this.sink = sink;
			this.channel = channel;
			this.position = new AtomicLong(position);
		}

		@Override
		protected void hookOnSubscribe(Subscription subscription) {
			request(1);
		}

		@Override
		protected void hookOnNext(DataBuffer value) {
			if (!this.dataBuffer.compareAndSet(null, value)) {
				throw new IllegalStateException();
			}
			ByteBuffer byteBuffer = value.asByteBuffer();
			this.channel.write(byteBuffer, this.position.get(), byteBuffer, this);
		}

		@Override
		protected void hookOnError(Throwable throwable) {
			this.error.set(throwable);

			if (this.dataBuffer.get() == null) {
				this.sink.error(throwable);
			}
		}

		@Override
		protected void hookOnComplete() {
			this.completed.set(true);

			if (this.dataBuffer.get() == null) {
				this.sink.complete();
			}
		}

		@Override
		public void completed(Integer written, ByteBuffer byteBuffer) {
			long pos = this.position.addAndGet(written);
			if (byteBuffer.hasRemaining()) {
				this.channel.write(byteBuffer, pos, byteBuffer, this);
				return;
			}
			sinkDataBuffer();

			Throwable throwable = this.error.get();
			if (throwable != null) {
				this.sink.error(throwable);
			}
			else if (this.completed.get()) {
				this.sink.complete();
			}
			else {
				request(1);
			}
		}

		@Override
		public void failed(Throwable exc, ByteBuffer byteBuffer) {
			sinkDataBuffer();
			this.sink.error(exc);
		}

		private void sinkDataBuffer() {
			DataBuffer dataBuffer = this.dataBuffer.get();
			Assert.state(dataBuffer != null, "DataBuffer should not be null");
			this.sink.next(dataBuffer);
			this.dataBuffer.set(null);
		}
	}

	/**
	 * Implementation of {@link Matcher} that uses the Knuth-Morris-Pratt algorithm.
	 *
	 * @see <a href="https://www.nayuki.io/page/knuth-morris-pratt-string-matching">Knuth-Morris-Pratt string matching</a>
	 */
	private static class KnuthMorrisPrattMatcher implements Matcher {

		private final byte[] delimiter;

		private final int[] table;

		private int matches = 0;


		public KnuthMorrisPrattMatcher(byte[] delimiter) {
			this.delimiter = Arrays.copyOf(delimiter, delimiter.length);
			this.table = longestSuffixPrefixTable(delimiter);
		}

		private static int[] longestSuffixPrefixTable(byte[] delimiter) {
			int[] result = new int[delimiter.length];
			result[0] = 0;
			for (int i = 1; i < delimiter.length; i++) {
				int j = result[i - 1];
				while (j > 0 && delimiter[i] != delimiter[j]) {
					j = result[j - 1];
				}
				if (delimiter[i] == delimiter[j]) {
					j++;
				}
				result[i] = j;
			}
			return result;
		}

		@Override
		public int match(DataBuffer dataBuffer) {
			for (int i = dataBuffer.readPosition(); i < dataBuffer.writePosition(); i++) {
				byte b = dataBuffer.getByte(i);

				while (this.matches > 0 && b != this.delimiter[this.matches]) {
					this.matches = this.table[this.matches - 1];
				}

				if (b == this.delimiter[this.matches]) {
					this.matches++;
					if (this.matches == this.delimiter.length) {
						reset();
						return i;
					}
				}
			}
			return -1;
		}

		@Override
		public byte[] delimiter() {
			return Arrays.copyOf(this.delimiter, this.delimiter.length);
		}

		@Override
		public void reset() {
			this.matches = 0;
		}
	}


	private static class EndFrameBuffer implements DataBuffer {

		private static final DataBuffer BUFFER = new DefaultDataBufferFactory().wrap(new byte[0]);

		private byte[] delimiter;


		public EndFrameBuffer(byte[] delimiter) {
			this.delimiter = delimiter;
		}

		public byte[] delimiter() {
			return this.delimiter;
		}

		@Override
		public DataBufferFactory factory() {
			return BUFFER.factory();
		}

		@Override
		public int indexOf(IntPredicate predicate, int fromIndex) {
			return BUFFER.indexOf(predicate, fromIndex);
		}

		@Override
		public int lastIndexOf(IntPredicate predicate, int fromIndex) {
			return BUFFER.lastIndexOf(predicate, fromIndex);
		}

		@Override
		public int readableByteCount() {
			return BUFFER.readableByteCount();
		}

		@Override
		public int writableByteCount() {
			return BUFFER.writableByteCount();
		}

		@Override
		public int capacity() {
			return BUFFER.capacity();
		}

		@Override
		public DataBuffer capacity(int capacity) {
			return BUFFER.capacity(capacity);
		}

		@Override
		public DataBuffer ensureCapacity(int capacity) {
			return BUFFER.ensureCapacity(capacity);
		}

		@Override
		public int readPosition() {
			return BUFFER.readPosition();
		}

		@Override
		public DataBuffer readPosition(int readPosition) {
			return BUFFER.readPosition(readPosition);
		}

		@Override
		public int writePosition() {
			return BUFFER.writePosition();
		}

		@Override
		public DataBuffer writePosition(int writePosition) {
			return BUFFER.writePosition(writePosition);
		}

		@Override
		public byte getByte(int index) {
			return BUFFER.getByte(index);
		}

		@Override
		public byte read() {
			return BUFFER.read();
		}

		@Override
		public DataBuffer read(byte[] destination) {
			return BUFFER.read(destination);
		}

		@Override
		public DataBuffer read(byte[] destination, int offset, int length) {
			return BUFFER.read(destination, offset, length);
		}

		@Override
		public DataBuffer write(byte b) {
			return BUFFER.write(b);
		}

		@Override
		public DataBuffer write(byte[] source) {
			return BUFFER.write(source);
		}

		@Override
		public DataBuffer write(byte[] source, int offset, int length) {
			return BUFFER.write(source, offset, length);
		}

		@Override
		public DataBuffer write(DataBuffer... buffers) {
			return BUFFER.write(buffers);
		}

		@Override
		public DataBuffer write(ByteBuffer... buffers) {
			return BUFFER.write(buffers);
		}

		@Override
		public DataBuffer write(CharSequence charSequence, Charset charset) {
			return BUFFER.write(charSequence, charset);
		}

		@Override
		public DataBuffer slice(int index, int length) {
			return BUFFER.slice(index, length);
		}

		@Override
		public DataBuffer retainedSlice(int index, int length) {
			return BUFFER.retainedSlice(index, length);
		}

		@Override
		public ByteBuffer asByteBuffer() {
			return BUFFER.asByteBuffer();
		}

		@Override
		public ByteBuffer asByteBuffer(int index, int length) {
			return BUFFER.asByteBuffer(index, length);
		}

		@Override
		public InputStream asInputStream() {
			return BUFFER.asInputStream();
		}

		@Override
		public InputStream asInputStream(boolean releaseOnClose) {
			return BUFFER.asInputStream(releaseOnClose);
		}

		@Override
		public OutputStream asOutputStream() {
			return BUFFER.asOutputStream();
		}
	}

}
