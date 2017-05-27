/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.lang.Nullable;

/**
 * A simple utility class for Base64 encoding and decoding.
 *
 * <p>Adapts to Java 8's {@link java.util.Base64} in a convenience fashion.
 *
 * @author Juergen Hoeller
 * @author Gary Russell
 * @since 4.1
 * @see java.util.Base64
 */
public abstract class Base64Utils {

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;


	/**
	 * Base64-encode the given byte array.
	 * @param src the original byte array (may be {@code null})
	 * @return the encoded byte array (or {@code null} if the input was {@code null})
	 * @throws IllegalStateException if Base64 encoding between byte arrays is not
	 * supported, i.e. neither Java 8 nor Apache Commons Codec is present at runtime
	 */
	@Nullable
	public static byte[] encode(@Nullable byte[] src) {
		if (src == null || src.length == 0) {
			return src;
		}
		return Base64.getEncoder().encode(src);
	}

	/**
	 * Base64-decode the given byte array.
	 * @param src the encoded byte array (may be {@code null})
	 * @return the original byte array (or {@code null} if the input was {@code null})
	 * @throws IllegalStateException if Base64 encoding between byte arrays is not
	 * supported, i.e. neither Java 8 nor Apache Commons Codec is present at runtime
	 */
	@Nullable
	public static byte[] decode(@Nullable byte[] src) {
		if (src == null || src.length == 0) {
			return src;
		}
		return Base64.getDecoder().decode(src);
	}

	/**
	 * Base64-encode the given byte array using the RFC 4648
	 * "URL and Filename Safe Alphabet".
	 * @param src the original byte array (may be {@code null})
	 * @return the encoded byte array (or {@code null} if the input was {@code null})
	 * @throws IllegalStateException if Base64 encoding between byte arrays is not
	 * supported, i.e. neither Java 8 nor Apache Commons Codec is present at runtime
	 * @since 4.2.4
	 */
	@Nullable
	public static byte[] encodeUrlSafe(@Nullable byte[] src) {
		if (src == null || src.length == 0) {
			return src;
		}
		return Base64.getUrlEncoder().encode(src);
	}

	/**
	 * Base64-decode the given byte array using the RFC 4648
	 * "URL and Filename Safe Alphabet".
	 * @param src the encoded byte array (may be {@code null})
	 * @return the original byte array (or {@code null} if the input was {@code null})
	 * @throws IllegalStateException if Base64 encoding between byte arrays is not
	 * supported, i.e. neither Java 8 nor Apache Commons Codec is present at runtime
	 * @since 4.2.4
	 */
	@Nullable
	public static byte[] decodeUrlSafe(@Nullable byte[] src) {
		if (src == null || src.length == 0) {
			return src;
		}
		return Base64.getUrlDecoder().decode(src);
	}

	/**
	 * Base64-encode the given byte array to a String.
	 * @param src the original byte array (may be {@code null})
	 * @return the encoded byte array as a UTF-8 String
	 * (or {@code null} if the input was {@code null})
	 */
	@Nullable
	public static String encodeToString(@Nullable byte[] src) {
		if (src == null) {
			return null;
		}
		if (src.length == 0) {
			return "";
		}
		return new String(encode(src), DEFAULT_CHARSET);
	}

	/**
	 * Base64-decode the given byte array from an UTF-8 String.
	 * @param src the encoded UTF-8 String (may be {@code null})
	 * @return the original byte array (or {@code null} if the input was {@code null})
	 */
	@Nullable
	public static byte[] decodeFromString(@Nullable String src) {
		if (src == null) {
			return null;
		}
		if (src.isEmpty()) {
			return new byte[0];
		}
		return decode(src.getBytes(DEFAULT_CHARSET));
	}

	/**
	 * Base64-encode the given byte array to a String using the RFC 4648
	 * "URL and Filename Safe Alphabet".
	 * @param src the original byte array (may be {@code null})
	 * @return the encoded byte array as a UTF-8 String
	 * (or {@code null} if the input was {@code null})
	 * @throws IllegalStateException if Base64 encoding between byte arrays is not
	 * supported, i.e. neither Java 8 nor Apache Commons Codec is present at runtime
	 */
	@Nullable
	public static String encodeToUrlSafeString(@Nullable byte[] src) {
		return new String(encodeUrlSafe(src), DEFAULT_CHARSET);
	}

	/**
	 * Base64-decode the given byte array from an UTF-8 String using the RFC 4648
	 * "URL and Filename Safe Alphabet".
	 * @param src the encoded UTF-8 String (may be {@code null})
	 * @return the original byte array (or {@code null} if the input was {@code null})
	 * @throws IllegalStateException if Base64 encoding between byte arrays is not
	 * supported, i.e. neither Java 8 nor Apache Commons Codec is present at runtime
	 */
	@Nullable
	public static byte[] decodeFromUrlSafeString(@Nullable String src) {
		return decodeUrlSafe(src.getBytes(DEFAULT_CHARSET));
	}

}
