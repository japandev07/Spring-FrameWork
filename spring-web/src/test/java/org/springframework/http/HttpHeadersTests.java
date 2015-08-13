/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.hamcrest.Matchers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link org.springframework.http.HttpHeaders}.
 *
 * @author Arjen Poutsma
 * @author Sebastien Deleuze
 */
public class HttpHeadersTests {

	private final HttpHeaders headers = new HttpHeaders();


	@Test
	public void accept() {
		MediaType mediaType1 = new MediaType("text", "html");
		MediaType mediaType2 = new MediaType("text", "plain");
		List<MediaType> mediaTypes = new ArrayList<MediaType>(2);
		mediaTypes.add(mediaType1);
		mediaTypes.add(mediaType2);
		headers.setAccept(mediaTypes);
		assertEquals("Invalid Accept header", mediaTypes, headers.getAccept());
		assertEquals("Invalid Accept header", "text/html, text/plain", headers.getFirst("Accept"));
	}

	@Test  // SPR-9655
	public void acceptIPlanet() {
		headers.add("Accept", "text/html");
		headers.add("Accept", "text/plain");
		List<MediaType> expected = Arrays.asList(new MediaType("text", "html"), new MediaType("text", "plain"));
		assertEquals("Invalid Accept header", expected, headers.getAccept());
	}

	@Test
	public void acceptCharsets() {
		Charset charset1 = Charset.forName("UTF-8");
		Charset charset2 = Charset.forName("ISO-8859-1");
		List<Charset> charsets = new ArrayList<Charset>(2);
		charsets.add(charset1);
		charsets.add(charset2);
		headers.setAcceptCharset(charsets);
		assertEquals("Invalid Accept header", charsets, headers.getAcceptCharset());
		assertEquals("Invalid Accept header", "utf-8, iso-8859-1", headers.getFirst("Accept-Charset"));
	}

	@Test
	public void acceptCharsetWildcard() {
		headers.set("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
		assertEquals("Invalid Accept header", Arrays.asList(Charset.forName("ISO-8859-1"), Charset.forName("UTF-8")),
				headers.getAcceptCharset());
	}

	@Test
	public void allow() {
		EnumSet<HttpMethod> methods = EnumSet.of(HttpMethod.GET, HttpMethod.POST);
		headers.setAllow(methods);
		assertEquals("Invalid Allow header", methods, headers.getAllow());
		assertEquals("Invalid Allow header", "GET,POST", headers.getFirst("Allow"));
	}

	@Test
	public void contentLength() {
		long length = 42L;
		headers.setContentLength(length);
		assertEquals("Invalid Content-Length header", length, headers.getContentLength());
		assertEquals("Invalid Content-Length header", "42", headers.getFirst("Content-Length"));
	}

	@Test
	public void contentType() {
		MediaType contentType = new MediaType("text", "html", Charset.forName("UTF-8"));
		headers.setContentType(contentType);
		assertEquals("Invalid Content-Type header", contentType, headers.getContentType());
		assertEquals("Invalid Content-Type header", "text/html;charset=UTF-8", headers.getFirst("Content-Type"));
	}

	@Test
	public void location() throws URISyntaxException {
		URI location = new URI("http://www.example.com/hotels");
		headers.setLocation(location);
		assertEquals("Invalid Location header", location, headers.getLocation());
		assertEquals("Invalid Location header", "http://www.example.com/hotels", headers.getFirst("Location"));
	}

	@Test
	public void eTag() {
		String eTag = "\"v2.6\"";
		headers.setETag(eTag);
		assertEquals("Invalid ETag header", eTag, headers.getETag());
		assertEquals("Invalid ETag header", "\"v2.6\"", headers.getFirst("ETag"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void illegalETag() {
		String eTag = "v2.6";
		headers.setETag(eTag);
		assertEquals("Invalid ETag header", eTag, headers.getETag());
		assertEquals("Invalid ETag header", "\"v2.6\"", headers.getFirst("ETag"));
	}

	@Test
	public void ifNoneMatch() {
		String ifNoneMatch = "\"v2.6\"";
		headers.setIfNoneMatch(ifNoneMatch);
		assertEquals("Invalid If-None-Match header", ifNoneMatch, headers.getIfNoneMatch().get(0));
		assertEquals("Invalid If-None-Match header", "\"v2.6\"", headers.getFirst("If-None-Match"));
	}

	@Test
	public void ifNoneMatchList() {
		String ifNoneMatch1 = "\"v2.6\"";
		String ifNoneMatch2 = "\"v2.7\"";
		List<String> ifNoneMatchList = new ArrayList<String>(2);
		ifNoneMatchList.add(ifNoneMatch1);
		ifNoneMatchList.add(ifNoneMatch2);
		headers.setIfNoneMatch(ifNoneMatchList);
		assertEquals("Invalid If-None-Match header", ifNoneMatchList, headers.getIfNoneMatch());
		assertEquals("Invalid If-None-Match header", "\"v2.6\", \"v2.7\"", headers.getFirst("If-None-Match"));
	}

	@Test
	public void date() {
		Calendar calendar = new GregorianCalendar(2008, 11, 18, 11, 20);
		calendar.setTimeZone(TimeZone.getTimeZone("CET"));
		long date = calendar.getTimeInMillis();
		headers.setDate(date);
		assertEquals("Invalid Date header", date, headers.getDate());
		assertEquals("Invalid Date header", "Thu, 18 Dec 2008 10:20:00 GMT", headers.getFirst("date"));

		// RFC 850
		headers.set("Date", "Thu, 18 Dec 2008 10:20:00 GMT");
		assertEquals("Invalid Date header", date, headers.getDate());
	}

	@Test(expected = IllegalArgumentException.class)
	public void dateInvalid() {
		headers.set("Date", "Foo Bar Baz");
		headers.getDate();
	}

	@Test
	public void dateOtherLocale() {
		Locale defaultLocale = Locale.getDefault();
		try {
			Locale.setDefault(new Locale("nl", "nl"));
			Calendar calendar = new GregorianCalendar(2008, 11, 18, 11, 20);
			calendar.setTimeZone(TimeZone.getTimeZone("CET"));
			long date = calendar.getTimeInMillis();
			headers.setDate(date);
			assertEquals("Invalid Date header", "Thu, 18 Dec 2008 10:20:00 GMT", headers.getFirst("date"));
			assertEquals("Invalid Date header", date, headers.getDate());
		}
		finally {
			Locale.setDefault(defaultLocale);
		}
	}

	@Test
	public void lastModified() {
		Calendar calendar = new GregorianCalendar(2008, 11, 18, 11, 20);
		calendar.setTimeZone(TimeZone.getTimeZone("CET"));
		long date = calendar.getTimeInMillis();
		headers.setLastModified(date);
		assertEquals("Invalid Last-Modified header", date, headers.getLastModified());
		assertEquals("Invalid Last-Modified header", "Thu, 18 Dec 2008 10:20:00 GMT",
				headers.getFirst("last-modified"));
	}

	@Test
	public void expires() {
		Calendar calendar = new GregorianCalendar(2008, 11, 18, 11, 20);
		calendar.setTimeZone(TimeZone.getTimeZone("CET"));
		long date = calendar.getTimeInMillis();
		headers.setExpires(date);
		assertEquals("Invalid Expires header", date, headers.getExpires());
		assertEquals("Invalid Expires header", "Thu, 18 Dec 2008 10:20:00 GMT", headers.getFirst("expires"));
	}

	// SPR-10648 (example is from INT-3063)

	@Test
	public void expiresInvalidDate() {
		headers.set("Expires", "-1");
		assertEquals(-1, headers.getExpires());
	}

	@Test
	public void ifModifiedSince() {
		Calendar calendar = new GregorianCalendar(2008, 11, 18, 11, 20);
		calendar.setTimeZone(TimeZone.getTimeZone("CET"));
		long date = calendar.getTimeInMillis();
		headers.setIfModifiedSince(date);
		assertEquals("Invalid If-Modified-Since header", date, headers.getIfModifiedSince());
		assertEquals("Invalid If-Modified-Since header", "Thu, 18 Dec 2008 10:20:00 GMT",
				headers.getFirst("if-modified-since"));
	}

	@Test
	public void pragma() {
		String pragma = "no-cache";
		headers.setPragma(pragma);
		assertEquals("Invalid Pragma header", pragma, headers.getPragma());
		assertEquals("Invalid Pragma header", "no-cache", headers.getFirst("pragma"));
	}

	@Test
	public void cacheControl() {
		String cacheControl = "no-cache";
		headers.setCacheControl(cacheControl);
		assertEquals("Invalid Cache-Control header", cacheControl, headers.getCacheControl());
		assertEquals("Invalid Cache-Control header", "no-cache", headers.getFirst("cache-control"));
	}

	@Test
	public void contentDisposition() {
		headers.setContentDispositionFormData("name", null);
		assertEquals("Invalid Content-Disposition header", "form-data; name=\"name\"",
				headers.getFirst("Content-Disposition"));

		headers.setContentDispositionFormData("name", "filename");
		assertEquals("Invalid Content-Disposition header", "form-data; name=\"name\"; filename=\"filename\"",
				headers.getFirst("Content-Disposition"));
	}

	@Test  // SPR-11917
	public void getAllowEmptySet() {
		headers.setAllow(Collections.<HttpMethod> emptySet());
		assertThat(headers.getAllow(), Matchers.emptyCollectionOf(HttpMethod.class));
	}

	@Test
	public void accessControlAllowCredentials() {
		assertFalse(headers.getAccessControlAllowCredentials());
		headers.setAccessControlAllowCredentials(false);
		assertFalse(headers.getAccessControlAllowCredentials());
		headers.setAccessControlAllowCredentials(true);
		assertTrue(headers.getAccessControlAllowCredentials());
	}

	@Test
	public void accessControlAllowHeaders() {
		List<String> allowedHeaders = headers.getAccessControlAllowHeaders();
		assertThat(allowedHeaders, Matchers.emptyCollectionOf(String.class));
		headers.setAccessControlAllowHeaders(Arrays.asList("header1", "header2"));
		allowedHeaders = headers.getAccessControlAllowHeaders();
		assertEquals(allowedHeaders, Arrays.asList("header1", "header2"));
	}

	@Test
	public void accessControlAllowMethods() {
		List<HttpMethod> allowedMethods = headers.getAccessControlAllowMethods();
		assertThat(allowedMethods, Matchers.emptyCollectionOf(HttpMethod.class));
		headers.setAccessControlAllowMethods(Arrays.asList(HttpMethod.GET, HttpMethod.POST));
		allowedMethods = headers.getAccessControlAllowMethods();
		assertEquals(allowedMethods, Arrays.asList(HttpMethod.GET, HttpMethod.POST));
	}

	@Test
	public void accessControlAllowOrigin() {
		assertNull(headers.getAccessControlAllowOrigin());
		headers.setAccessControlAllowOrigin("*");
		assertEquals("*", headers.getAccessControlAllowOrigin());
	}

	@Test
	public void accessControlExposeHeaders() {
		List<String> exposedHeaders = headers.getAccessControlExposeHeaders();
		assertThat(exposedHeaders, Matchers.emptyCollectionOf(String.class));
		headers.setAccessControlExposeHeaders(Arrays.asList("header1", "header2"));
		exposedHeaders = headers.getAccessControlExposeHeaders();
		assertEquals(exposedHeaders, Arrays.asList("header1", "header2"));
	}

	@Test
	public void accessControlMaxAge() {
		assertEquals(-1, headers.getAccessControlMaxAge());
		headers.setAccessControlMaxAge(3600);
		assertEquals(3600, headers.getAccessControlMaxAge());
	}

	@Test
	public void accessControlRequestHeaders() {
		List<String> requestHeaders = headers.getAccessControlRequestHeaders();
		assertThat(requestHeaders, Matchers.emptyCollectionOf(String.class));
		headers.setAccessControlRequestHeaders(Arrays.asList("header1", "header2"));
		requestHeaders = headers.getAccessControlRequestHeaders();
		assertEquals(requestHeaders, Arrays.asList("header1", "header2"));
	}

	@Test
	public void accessControlRequestMethod() {
		assertNull(headers.getAccessControlRequestMethod());
		headers.setAccessControlRequestMethod(HttpMethod.POST);
		assertEquals(HttpMethod.POST, headers.getAccessControlRequestMethod());
	}

}
