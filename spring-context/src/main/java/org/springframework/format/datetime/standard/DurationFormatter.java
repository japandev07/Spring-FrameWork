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

package org.springframework.format.datetime.standard;

import java.text.ParseException;
import java.time.Duration;
import java.util.Locale;

import org.springframework.format.Formatter;
import org.springframework.lang.UsesJava8;

/**
 * {@link Formatter} implementation for a JSR-310 {@link Duration},
 * following JSR-310's parsing rules for a Duration.
 *
 * @author Juergen Hoeller
 * @since 4.2.4
 * @see Duration#parse
 */
@UsesJava8
class DurationFormatter implements Formatter<Duration> {

	@Override
	public Duration parse(String text, Locale locale) throws ParseException {
		return Duration.parse(text);
	}

	@Override
	public String print(Duration object, Locale locale) {
		return object.toString();
	}

}
