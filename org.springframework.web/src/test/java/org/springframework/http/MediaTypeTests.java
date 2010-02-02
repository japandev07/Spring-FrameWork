/*
 * Copyright 2002-2010 the original author or authors.
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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.Random;

import static org.junit.Assert.*;
import org.junit.Test;

/** @author Arjen Poutsma */
public class MediaTypeTests {

	@Test
	public void includes() throws Exception {
		MediaType textPlain = new MediaType("text", "plain");
		assertTrue("Equal types is not inclusive", textPlain.includes(textPlain));
		MediaType allText = new MediaType("text");
		assertTrue("All subtypes is not inclusive", allText.includes(textPlain));
		assertFalse("All subtypes is not inclusive", textPlain.includes(allText));
		assertTrue("All types is not inclusive", MediaType.ALL.includes(textPlain));
		assertFalse("All types is not inclusive", textPlain.includes(MediaType.ALL));

		MediaType applicationSoapXml = new MediaType("application", "soap+xml");
		MediaType applicationWildcardXml = new MediaType("application", "*+xml");

		assertTrue(applicationSoapXml.includes(applicationSoapXml));
		assertTrue(applicationWildcardXml.includes(applicationWildcardXml));

		assertTrue(applicationWildcardXml.includes(applicationSoapXml));
		assertFalse(applicationSoapXml.includes(applicationWildcardXml));
	}

	@Test
	public void testToString() throws Exception {
		MediaType mediaType = new MediaType("text", "plain", 0.7);
		String result = mediaType.toString();
		assertEquals("Invalid toString() returned", "text/plain;q=0.7", result);
	}

	@Test(expected= IllegalArgumentException.class)
	public void slashInType() {
		new MediaType("text/plain");
	}

	@Test(expected= IllegalArgumentException.class)
	public void slashInSubtype() {
		new MediaType("text", "/");
	}
	
	@Test
	public void getDefaultQualityValue() {
		MediaType mediaType = new MediaType("text", "plain");
		assertEquals("Invalid quality value", 1, mediaType.getQualityValue(), 0D);
	}

	@Test
	public void parseMediaType() throws Exception {
		String s = "audio/*; q=0.2";
		MediaType mediaType = MediaType.parseMediaType(s);
		assertEquals("Invalid type", "audio", mediaType.getType());
		assertEquals("Invalid subtype", "*", mediaType.getSubtype());
		assertEquals("Invalid quality factor", 0.2D, mediaType.getQualityValue(), 0D);
	}

	@Test
	public void parseCharset() throws Exception {
		String s = "text/html; charset=iso-8859-1";
		MediaType mediaType = MediaType.parseMediaType(s);
		assertEquals("Invalid type", "text", mediaType.getType());
		assertEquals("Invalid subtype", "html", mediaType.getSubtype());
		assertEquals("Invalid charset", Charset.forName("ISO-8859-1"), mediaType.getCharSet());
	}

	@Test
	public void parseURLConnectionMediaType() throws Exception {
		String s = "*; q=.2";
		MediaType mediaType = MediaType.parseMediaType(s);
		assertEquals("Invalid type", "*", mediaType.getType());
		assertEquals("Invalid subtype", "*", mediaType.getSubtype());
		assertEquals("Invalid quality factor", 0.2D, mediaType.getQualityValue(), 0D);
	}

	@Test
	public void parseMediaTypes() throws Exception {
		String s = "text/plain; q=0.5, text/html, text/x-dvi; q=0.8, text/x-c";
		List<MediaType> mediaTypes = MediaType.parseMediaTypes(s);
		assertNotNull("No media types returned", mediaTypes);
		assertEquals("Invalid amount of media types", 4, mediaTypes.size());

		mediaTypes = MediaType.parseMediaTypes(null);
		assertNotNull("No media types returned", mediaTypes);
		assertEquals("Invalid amount of media types", 0, mediaTypes.size());
	}

	@Test
	public void specificityComparator() throws Exception {
		MediaType audioBasic = new MediaType("audio", "basic");
		MediaType audioWave = new MediaType("audio", "wave");
		MediaType audio = new MediaType("audio");
		MediaType audio03 = new MediaType("audio", "*", 0.3);
		MediaType audio07 = new MediaType("audio", "*", 0.7);
		MediaType audioBasicLevel = new MediaType("audio", "basic", Collections.singletonMap("level", "1"));
		MediaType textHtml = new MediaType("text", "html");
		MediaType all = MediaType.ALL;

		Comparator<MediaType> comp = MediaType.SPECIFICITY_COMPARATOR;

		// equal
		assertEquals("Invalid comparison result", 0, comp.compare(audioBasic,audioBasic));
		assertEquals("Invalid comparison result", 0, comp.compare(audio, audio));
		assertEquals("Invalid comparison result", 0, comp.compare(audio07, audio07));
		assertEquals("Invalid comparison result", 0, comp.compare(audio03, audio03));
		assertEquals("Invalid comparison result", 0, comp.compare(audioBasicLevel, audioBasicLevel));

		// specific to unspecific
		assertTrue("Invalid comparison result", comp.compare(audioBasic, audio) < 0);
		assertTrue("Invalid comparison result", comp.compare(audioBasic, all) < 0);
		assertTrue("Invalid comparison result", comp.compare(audio, all) < 0);

		// unspecific to specific
		assertTrue("Invalid comparison result", comp.compare(audio, audioBasic) > 0);
		assertTrue("Invalid comparison result", comp.compare(all, audioBasic) > 0);
		assertTrue("Invalid comparison result", comp.compare(all, audio) > 0);

		// qualifiers
		assertTrue("Invalid comparison result", comp.compare(audio, audio07) < 0);
		assertTrue("Invalid comparison result", comp.compare(audio07, audio) > 0);
		assertTrue("Invalid comparison result", comp.compare(audio07, audio03) < 0);
		assertTrue("Invalid comparison result", comp.compare(audio03, audio07) > 0);
		assertTrue("Invalid comparison result", comp.compare(audio03, all) < 0);
		assertTrue("Invalid comparison result", comp.compare(all, audio03) > 0);

		// other parameters
		assertTrue("Invalid comparison result", comp.compare(audioBasic, audioBasicLevel) > 0);
		assertTrue("Invalid comparison result", comp.compare(audioBasicLevel, audioBasic) < 0);

		// different types
		assertEquals("Invalid comparison result", 0, comp.compare(audioBasic, textHtml));
		assertEquals("Invalid comparison result", 0, comp.compare(textHtml, audioBasic));

		// different subtypes
		assertEquals("Invalid comparison result", 0, comp.compare(audioBasic, audioWave));
		assertEquals("Invalid comparison result", 0, comp.compare(audioWave, audioBasic));
	}

	@Test
	public void sortBySpecificityRelated() {
		MediaType audioBasic = new MediaType("audio", "basic");
		MediaType audio = new MediaType("audio");
		MediaType audio03 = new MediaType("audio", "*", 0.3);
		MediaType audio07 = new MediaType("audio", "*", 0.7);
		MediaType audioBasicLevel = new MediaType("audio", "basic", Collections.singletonMap("level", "1"));
		MediaType all = MediaType.ALL;

		List<MediaType> expected = new ArrayList<MediaType>();
		expected.add(audioBasicLevel);
		expected.add(audioBasic);
		expected.add(audio);
		expected.add(audio07);
		expected.add(audio03);
		expected.add(all);

		List<MediaType> result = new ArrayList<MediaType>(expected);
		Random rnd = new Random();
		// shuffle & sort 10 times
		for (int i = 0; i < 10; i++) {
			Collections.shuffle(result, rnd);
			MediaType.sortBySpecificity(result);

			for (int j = 0; j < result.size(); j++) {
				assertSame("Invalid media type at " + j, expected.get(j), result.get(j));
			}
		}
	}

	@Test
	public void sortBySpecificityUnrelated() {
		MediaType audioBasic = new MediaType("audio", "basic");
		MediaType audioWave = new MediaType("audio", "wave");
		MediaType textHtml = new MediaType("text", "html");

		List<MediaType> expected = new ArrayList<MediaType>();
		expected.add(textHtml);
		expected.add(audioBasic);
		expected.add(audioWave);

		List<MediaType> result = new ArrayList<MediaType>(expected);
		MediaType.sortBySpecificity(result);

		for (int i = 0; i < result.size(); i++) {
			assertSame("Invalid media type at " + i, expected.get(i), result.get(i));
		}

	}

}
