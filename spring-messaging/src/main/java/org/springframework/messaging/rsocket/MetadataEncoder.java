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
package org.springframework.messaging.rsocket;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.rsocket.metadata.CompositeMetadataFlyweight;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.ObjectUtils;

/**
 * Helps to collect metadata values and mime types, and encode them.
 *
 * @author Rossen Stoyanchev
 * @since 5.2
 */
final class MetadataEncoder {

	/** For route variable replacement. */
	private static final Pattern VARS_PATTERN = Pattern.compile("\\{([^/]+?)\\}");


	private final MimeType metadataMimeType;

	private final RSocketStrategies strategies;

	private final boolean isComposite;

	private final ByteBufAllocator allocator;

	@Nullable
	private String route;

	private final Map<Object, MimeType> metadata = new LinkedHashMap<>(4);


	MetadataEncoder(MimeType metadataMimeType, RSocketStrategies strategies) {
		Assert.notNull(metadataMimeType, "'metadataMimeType' is required");
		Assert.notNull(strategies, "RSocketStrategies is required");
		this.metadataMimeType = metadataMimeType;
		this.strategies = strategies;
		this.isComposite = metadataMimeType.equals(MetadataExtractor.COMPOSITE_METADATA);
		this.allocator = bufferFactory() instanceof NettyDataBufferFactory ?
				((NettyDataBufferFactory) bufferFactory()).getByteBufAllocator() : ByteBufAllocator.DEFAULT;
	}


	private DataBufferFactory bufferFactory() {
		return this.strategies.dataBufferFactory();
	}


	/**
	 * Set the route to a remote handler as described in
	 * {@link RSocketRequester#route(String, Object...)}.
	 */
	public MetadataEncoder route(String route, Object... routeVars) {
		this.route = expand(route, routeVars);
		assertMetadataEntryCount();
		return this;
	}

	private static String expand(String route, Object... routeVars) {
		if (ObjectUtils.isEmpty(routeVars)) {
			return route;
		}
		StringBuffer sb = new StringBuffer();
		int index = 0;
		Matcher matcher = VARS_PATTERN.matcher(route);
		while (matcher.find()) {
			Assert.isTrue(index < routeVars.length, () -> "No value for variable '" + matcher.group(1) + "'");
			String value = routeVars[index].toString();
			value = value.contains(".") ? value.replaceAll("\\.", "%2E") : value;
			matcher.appendReplacement(sb, value);
			index++;
		}
		return sb.toString();
	}

	private void assertMetadataEntryCount() {
		if (!this.isComposite) {
			int count = this.route != null ? this.metadata.size() + 1 : this.metadata.size();
			Assert.isTrue(count < 2, "Composite metadata required for multiple metadata entries.");
		}
	}

	/**
	 * Add a metadata entry. If called more than once or in addition to route,
	 * composite metadata must be in use.
	 */
	public MetadataEncoder metadata(Object metadata, @Nullable MimeType mimeType) {
		if (this.isComposite) {
			Assert.notNull(mimeType, "MimeType is required for composite metadata entries.");
		}
		else if (mimeType == null) {
			mimeType = this.metadataMimeType;
		}
		else if (!this.metadataMimeType.equals(mimeType)) {
			throw new IllegalArgumentException("Mime type is optional (may be null) " +
					"but was provided and does not match the connection metadata mime type.");
		}
		this.metadata.put(metadata, mimeType);
		assertMetadataEntryCount();
		return this;
	}

	/**
	 * Add route and/or metadata, both optional.
	 */
	public MetadataEncoder metadataAndOrRoute(@Nullable Map<Object, MimeType> metadata,
			@Nullable String route, @Nullable Object[] vars) {

		if (route != null) {
			this.route = expand(route, vars != null ? vars : new Object[0]);
		}
		if (!CollectionUtils.isEmpty(metadata)) {
			for (Map.Entry<Object, MimeType> entry : metadata.entrySet()) {
				metadata(entry.getKey(), entry.getValue());
			}
		}
		assertMetadataEntryCount();
		return this;
	}


	/**
	 * Encode the collected metadata entries to a {@code DataBuffer}.
	 * @see PayloadUtils#createPayload(DataBuffer, DataBuffer)
	 */
	public DataBuffer encode() {
		Map<Object, MimeType> mergedMetadata = mergeRouteAndMetadata();
		if (this.isComposite) {
			CompositeByteBuf composite = this.allocator.compositeBuffer();
			try {
				mergedMetadata.forEach((value, mimeType) -> {
					DataBuffer buffer = encodeEntry(value, mimeType);
					CompositeMetadataFlyweight.encodeAndAddMetadata(
							composite, this.allocator, mimeType.toString(), PayloadUtils.asByteBuf(buffer));
				});
				if (bufferFactory() instanceof NettyDataBufferFactory) {
					return ((NettyDataBufferFactory) bufferFactory()).wrap(composite);
				}
				else {
					DataBuffer buffer = bufferFactory().wrap(composite.nioBuffer());
					composite.release();
					return buffer;
				}
			}
			catch (Throwable ex) {
				composite.release();
				throw ex;
			}
		}
		else {
			Assert.isTrue(mergedMetadata.size() == 1, "Composite metadata required for multiple entries");
			Map.Entry<Object, MimeType> entry = mergedMetadata.entrySet().iterator().next();
			if (!this.metadataMimeType.equals(entry.getValue())) {
				throw new IllegalArgumentException(
						"Connection configured for metadata mime type " +
								"'" + this.metadataMimeType + "', but actual is `" + mergedMetadata + "`");
			}
			return encodeEntry(entry.getKey(), entry.getValue());
		}
	}

	private Map<Object, MimeType> mergeRouteAndMetadata() {
		if (this.route == null) {
			return this.metadata;
		}

		MimeType routeMimeType = this.metadataMimeType.equals(MetadataExtractor.COMPOSITE_METADATA) ?
				MetadataExtractor.ROUTING : this.metadataMimeType;

		Object routeValue = this.route;
		if (routeMimeType.equals(MetadataExtractor.ROUTING)) {
			// TODO: use rsocket-core API when available
			routeValue = bufferFactory().wrap(this.route.getBytes(StandardCharsets.UTF_8));
		}

		Map<Object, MimeType> result = new LinkedHashMap<>();
		result.put(routeValue, routeMimeType);
		result.putAll(this.metadata);
		return result;
	}

	@SuppressWarnings("unchecked")
	private <T> DataBuffer encodeEntry(Object metadata, MimeType mimeType) {
		if (metadata instanceof DataBuffer) {
			return (DataBuffer) metadata;
		}
		ResolvableType type = ResolvableType.forInstance(metadata);
		Encoder<T> encoder = this.strategies.encoder(type, mimeType);
		Assert.notNull(encoder, () -> "No encoder for metadata " + metadata + ", mimeType '" + mimeType + "'");
		return encoder.encodeValue((T) metadata, bufferFactory(), type, mimeType, Collections.emptyMap());
	}

}
