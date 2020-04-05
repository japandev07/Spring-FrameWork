/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.Map;

/**
 * Decoder for {@link ByteBuf ByteBufs}.
 *
 * @author Vladislav Kisel
 * @since 5.3
 */
public class ByteBufDecoder extends AbstractDataBufferDecoder<ByteBuf> {

	public ByteBufDecoder() {
		super(MimeTypeUtils.ALL);
	}


	@Override
	public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
		return (ByteBuf.class.isAssignableFrom(elementType.toClass()) &&
				super.canDecode(elementType, mimeType));
	}

	@Override
	public ByteBuf decode(DataBuffer dataBuffer, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		// Copies the dataBuffer if needed only
		ByteBuf byteBuf;
		if (dataBuffer instanceof NettyDataBuffer) {
			byteBuf = ((NettyDataBuffer) dataBuffer).getNativeBuffer();
		} else {
			byteBuf = Unpooled.wrappedBuffer(dataBuffer.asByteBuffer());
			DataBufferUtils.release(dataBuffer);
		}

		if (logger.isDebugEnabled()) {
			logger.debug(Hints.getLogPrefix(hints) + "Read " + byteBuf.readableBytes() + " bytes");
		}
		return byteBuf;
	}

}
