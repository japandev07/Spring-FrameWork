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

package org.springframework.core.codec;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Encode from a {@code CharSequence} stream to a bytes stream.
 *
 * @author Sebastien Deleuze
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 5.0
 * @see StringDecoder
 */
public final class CharSequenceEncoder extends AbstractEncoder<CharSequence> {

	/**
	 * The default charset used by the encoder.
	 */
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;


	private CharSequenceEncoder(MimeType... mimeTypes) {
		super(mimeTypes);
	}


	@Override
	public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
		Class<?> clazz = elementType.toClass();
		return super.canEncode(elementType, mimeType) && CharSequence.class.isAssignableFrom(clazz);
	}

	@Override
	public Flux<DataBuffer> encode(Publisher<? extends CharSequence> inputStream,
			DataBufferFactory bufferFactory, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		Charset charset = getCharset(mimeType);

		return Flux.from(inputStream).map(charSequence -> {
			if (!Hints.isLoggingSuppressed(hints)) {
				LogFormatUtils.traceDebug(logger, traceOn -> {
					String formatted = LogFormatUtils.formatValue(charSequence, !traceOn);
					return Hints.getLogPrefix(hints) + "Writing " + formatted;
				});
			}
			CharBuffer charBuffer = CharBuffer.wrap(charSequence);
			ByteBuffer byteBuffer = charset.encode(charBuffer);
			return bufferFactory.wrap(byteBuffer);
		});
	}

	private Charset getCharset(@Nullable MimeType mimeType) {
		if (mimeType != null && mimeType.getCharset() != null) {
			return mimeType.getCharset();
		}
		else {
			return DEFAULT_CHARSET;
		}
	}


	/**
	 * Create a {@code CharSequenceEncoder} that supports only "text/plain".
	 */
	public static CharSequenceEncoder textPlainOnly() {
		return new CharSequenceEncoder(new MimeType("text", "plain", DEFAULT_CHARSET));
	}

	/**
	 * Create a {@code CharSequenceEncoder} that supports all MIME types.
	 */
	public static CharSequenceEncoder allMimeTypes() {
		return new CharSequenceEncoder(new MimeType("text", "plain", DEFAULT_CHARSET), MimeTypeUtils.ALL);
	}

}
