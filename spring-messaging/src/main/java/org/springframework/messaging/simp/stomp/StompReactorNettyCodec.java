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
package org.springframework.messaging.simp.stomp;

import org.springframework.messaging.tcp.reactor.ReactorNettyCodec;

/**
 * {@code ReactorNettyCodec} that delegates to {@link StompDecoder} and
 * {@link StompEncoder}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
class StompReactorNettyCodec extends ReactorNettyCodec<byte[]> {

	public StompReactorNettyCodec() {
		this(new StompDecoder(), new StompEncoder());
	}

	public StompReactorNettyCodec(StompDecoder decoder) {
		this(decoder, new StompEncoder());
	}

	public StompReactorNettyCodec(StompDecoder decoder, StompEncoder encoder) {
		super(byteBuf -> decoder.decode(byteBuf.nioBuffer()),
				(byteBuf, message) -> byteBuf.writeBytes(encoder.encode(message)));
	}

}
