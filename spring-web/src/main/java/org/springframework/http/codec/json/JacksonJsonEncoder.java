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

package org.springframework.http.codec.json;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * Encode from an {@code Object} stream to a byte stream of JSON objects.
 *
 * @author Sebastien Deleuze
 * @author Arjen Poutsma
 * @see JacksonJsonDecoder
 */
public class JacksonJsonEncoder extends AbstractEncoder<Object> {

	private static final ByteBuffer START_ARRAY_BUFFER = ByteBuffer.wrap(new byte[]{'['});

	private static final ByteBuffer SEPARATOR_BUFFER = ByteBuffer.wrap(new byte[]{','});

	private static final ByteBuffer END_ARRAY_BUFFER = ByteBuffer.wrap(new byte[]{']'});


	private final ObjectMapper mapper;


	public JacksonJsonEncoder() {
		this(new ObjectMapper());
	}

	public JacksonJsonEncoder(ObjectMapper mapper) {
		super(new MimeType("application", "json", StandardCharsets.UTF_8),
				new MimeType("application", "*+json", StandardCharsets.UTF_8));
		Assert.notNull(mapper, "'mapper' must not be null");

		this.mapper = mapper;
	}


	@Override
	public Flux<DataBuffer> encode(Publisher<?> inputStream, DataBufferFactory bufferFactory,
			ResolvableType elementType, MimeType mimeType, Object... hints) {

		Assert.notNull(inputStream, "'inputStream' must not be null");
		Assert.notNull(bufferFactory, "'bufferFactory' must not be null");
		Assert.notNull(elementType, "'elementType' must not be null");

		if (inputStream instanceof Mono) {
			return Flux.from(inputStream).map(value -> encodeValue(value, bufferFactory, elementType));
		}

		Mono<DataBuffer> startArray = Mono.just(bufferFactory.wrap(START_ARRAY_BUFFER));
		Mono<DataBuffer> endArray = Mono.just(bufferFactory.wrap(END_ARRAY_BUFFER));

		Flux<DataBuffer> array = Flux.from(inputStream)
				.flatMap(value -> {
					DataBuffer arraySeparator = bufferFactory.wrap(SEPARATOR_BUFFER);
					return Flux.just(encodeValue(value, bufferFactory, elementType), arraySeparator);
				});

		return Flux.concat(startArray, array.skipLast(1), endArray);
	}

	private DataBuffer encodeValue(Object value, DataBufferFactory bufferFactory, ResolvableType type) {
		TypeFactory typeFactory = this.mapper.getTypeFactory();
		JavaType javaType = typeFactory.constructType(type.getType());
		ObjectWriter writer = this.mapper.writerFor(javaType);

		DataBuffer buffer = bufferFactory.allocateBuffer();
		OutputStream outputStream = buffer.asOutputStream();

		try {
			writer.writeValue(outputStream, value);
		}
		catch (IOException e) {
			throw new CodecException("Error while writing the data", e);
		}

		return buffer;
	}

}
