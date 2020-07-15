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

package org.springframework.scheduling.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.joda.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.scheduling.TriggerContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Unit tests for {@link CronTrigger}.
 *
 * @author Dave Syer
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Sam Brannen
 */
class CronTriggerTests {

	private final Calendar calendar = new GregorianCalendar();

	private void setUp(LocalDateTime localDateTime, TimeZone timeZone) {
		this.calendar.setTimeZone(timeZone);
		this.calendar.setTime(localDateTime.toDate());
		roundup(this.calendar);
	}


	@ParameterizedCronTriggerTest
	void testMatchAll(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("* * * * * *", timeZone);
		TriggerContext context = getTriggerContext(localDateTime.toDate());
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testMatchLastSecond(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("* * * * * *", timeZone);
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.set(Calendar.SECOND, 58);
		assertMatchesNextSecond(trigger, calendar);
	}

	@ParameterizedCronTriggerTest
	void testMatchSpecificSecond(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("10 * * * * *", timeZone);
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.set(Calendar.SECOND, 9);
		assertMatchesNextSecond(trigger, calendar);
	}

	@ParameterizedCronTriggerTest
	void testIncrementSecondByOne(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("11 * * * * *", timeZone);
		this.calendar.set(Calendar.SECOND, 10);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.SECOND, 1);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementSecondWithPreviousExecutionTooEarly(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("11 * * * * *", timeZone);
		this.calendar.set(Calendar.SECOND, 11);
		SimpleTriggerContext context = new SimpleTriggerContext();
		context.update(this.calendar.getTime(), new Date(this.calendar.getTimeInMillis() - 100),
				new Date(this.calendar.getTimeInMillis() - 90));
		this.calendar.add(Calendar.MINUTE, 1);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementSecondAndRollover(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("10 * * * * *", timeZone);
		this.calendar.set(Calendar.SECOND, 11);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.SECOND, 59);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testSecondRange(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("10-15 * * * * *", timeZone);
		this.calendar.set(Calendar.SECOND, 9);
		assertMatchesNextSecond(trigger, this.calendar);
		this.calendar.set(Calendar.SECOND, 14);
		assertMatchesNextSecond(trigger, this.calendar);
	}

	@ParameterizedCronTriggerTest
	void testIncrementMinute(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 * * * * *", timeZone);
		this.calendar.set(Calendar.MINUTE, 10);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.MINUTE, 1);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context1 = getTriggerContext(localDate);
		localDate = trigger.nextExecutionTime(context1);
		assertThat(localDate).isEqualTo(this.calendar.getTime());
		this.calendar.add(Calendar.MINUTE, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		localDate = trigger.nextExecutionTime(context2);
		assertThat(localDate).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementMinuteByOne(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 11 * * * *", timeZone);
		this.calendar.set(Calendar.MINUTE, 10);
		TriggerContext context = getTriggerContext(this.calendar.getTime());
		this.calendar.add(Calendar.MINUTE, 1);
		this.calendar.set(Calendar.SECOND, 0);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementMinuteAndRollover(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 10 * * * *", timeZone);
		this.calendar.set(Calendar.MINUTE, 11);
		this.calendar.set(Calendar.SECOND, 0);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.MINUTE, 59);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementHour(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 * * * *", timeZone);
		this.calendar.set(Calendar.MONTH, 9);
		this.calendar.set(Calendar.DAY_OF_MONTH, 30);
		this.calendar.set(Calendar.HOUR_OF_DAY, 11);
		this.calendar.set(Calendar.MINUTE, 1);
		this.calendar.set(Calendar.SECOND, 0);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.HOUR_OF_DAY, 12);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual).isEqualTo(this.calendar.getTime());
		this.calendar.set(Calendar.HOUR_OF_DAY, 13);
		TriggerContext context2 = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context2)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementHourAndRollover(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 * * * *", timeZone);
		this.calendar.set(Calendar.MONTH, 9);
		this.calendar.set(Calendar.DAY_OF_MONTH, 10);
		this.calendar.set(Calendar.HOUR_OF_DAY, 23);
		this.calendar.set(Calendar.MINUTE, 1);
		this.calendar.set(Calendar.SECOND, 0);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.DAY_OF_MONTH, 11);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual).isEqualTo(this.calendar.getTime());
		this.calendar.set(Calendar.HOUR_OF_DAY, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context2)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementDayOfMonth(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 * * *", timeZone);
		this.calendar.set(Calendar.DAY_OF_MONTH, 1);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.DAY_OF_MONTH, 1);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual1 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		assertThat(this.calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(2);
		this.calendar.add(Calendar.DAY_OF_MONTH, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual).isEqualTo(this.calendar.getTime());
		assertThat(this.calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(3);
	}

	@ParameterizedCronTriggerTest
	void testIncrementDayOfMonthByOne(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("* * * 10 * *", timeZone);
		this.calendar.set(Calendar.DAY_OF_MONTH, 9);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.DAY_OF_MONTH, 1);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementDayOfMonthAndRollover(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("* * * 10 * *", timeZone);
		this.calendar.set(Calendar.DAY_OF_MONTH, 11);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.MONTH, 1);
		this.calendar.set(Calendar.DAY_OF_MONTH, 10);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testDailyTriggerInShortMonth(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 * * *", timeZone);
		this.calendar.set(Calendar.MONTH, 8); // September: 30 days
		this.calendar.set(Calendar.DAY_OF_MONTH, 30);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.MONTH, 9); // October
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		this.calendar.set(Calendar.DAY_OF_MONTH, 1);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual).isEqualTo(this.calendar.getTime());
		this.calendar.set(Calendar.DAY_OF_MONTH, 2);
		TriggerContext context2 = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context2)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testDailyTriggerInLongMonth(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 * * *", timeZone);
		this.calendar.set(Calendar.MONTH, 7); // August: 31 days and not a daylight saving boundary
		this.calendar.set(Calendar.DAY_OF_MONTH, 30);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		this.calendar.set(Calendar.DAY_OF_MONTH, 31);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual).isEqualTo(this.calendar.getTime());
		this.calendar.set(Calendar.MONTH, 8); // September
		this.calendar.set(Calendar.DAY_OF_MONTH, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context2)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testDailyTriggerOnDaylightSavingBoundary(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 * * *", timeZone);
		this.calendar.set(Calendar.MONTH, 9); // October: 31 days and a daylight saving boundary in CET
		this.calendar.set(Calendar.DAY_OF_MONTH, 30);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		this.calendar.set(Calendar.DAY_OF_MONTH, 31);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual).isEqualTo(this.calendar.getTime());
		this.calendar.set(Calendar.MONTH, 10); // November
		this.calendar.set(Calendar.DAY_OF_MONTH, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context2)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementMonth(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 1 * *", timeZone);
		this.calendar.set(Calendar.MONTH, 9);
		this.calendar.set(Calendar.DAY_OF_MONTH, 30);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.DAY_OF_MONTH, 1);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		this.calendar.set(Calendar.MONTH, 10);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual).isEqualTo(this.calendar.getTime());
		this.calendar.set(Calendar.MONTH, 11);
		TriggerContext context2 = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context2)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementMonthAndRollover(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 1 * *", timeZone);
		this.calendar.set(Calendar.MONTH, 11);
		this.calendar.set(Calendar.DAY_OF_MONTH, 31);
		this.calendar.set(Calendar.YEAR, 2010);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.DAY_OF_MONTH, 1);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		this.calendar.set(Calendar.MONTH, 0);
		this.calendar.set(Calendar.YEAR, 2011);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual).isEqualTo(this.calendar.getTime());
		this.calendar.set(Calendar.MONTH, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context2)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testMonthlyTriggerInLongMonth(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 31 * *", timeZone);
		this.calendar.set(Calendar.MONTH, 9);
		this.calendar.set(Calendar.DAY_OF_MONTH, 30);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.DAY_OF_MONTH, 31);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testMonthlyTriggerInShortMonth(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 1 * *", timeZone);
		this.calendar.set(Calendar.MONTH, 9);
		this.calendar.set(Calendar.DAY_OF_MONTH, 30);
		Date localDate = this.calendar.getTime();
		this.calendar.set(Calendar.MONTH, 10);
		this.calendar.set(Calendar.DAY_OF_MONTH, 1);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testIncrementDayOfWeekByOne(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("* * * * * 2", timeZone);
		this.calendar.set(Calendar.DAY_OF_WEEK, 2);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.DAY_OF_WEEK, 1);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
		assertThat(this.calendar.get(Calendar.DAY_OF_WEEK)).isEqualTo(Calendar.TUESDAY);
	}

	@ParameterizedCronTriggerTest
	void testIncrementDayOfWeekAndRollover(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("* * * * * 2", timeZone);
		this.calendar.set(Calendar.DAY_OF_WEEK, 4);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.DAY_OF_MONTH, 6);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(this.calendar.getTime());
		assertThat(this.calendar.get(Calendar.DAY_OF_WEEK)).isEqualTo(Calendar.TUESDAY);
	}

	@ParameterizedCronTriggerTest
	void testSpecificMinuteSecond(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("55 5 * * * *", timeZone);
		this.calendar.set(Calendar.MINUTE, 4);
		this.calendar.set(Calendar.SECOND, 54);
		Date localDate = this.calendar.getTime();
		TriggerContext context1 = getTriggerContext(localDate);
		this.calendar.add(Calendar.MINUTE, 1);
		this.calendar.set(Calendar.SECOND, 55);
		Object actual1 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		this.calendar.add(Calendar.HOUR, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testSpecificHourSecond(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("55 * 10 * * *", timeZone);
		this.calendar.set(Calendar.HOUR_OF_DAY, 9);
		this.calendar.set(Calendar.SECOND, 54);
		Date localDate = this.calendar.getTime();
		TriggerContext context1 = getTriggerContext(localDate);
		this.calendar.add(Calendar.HOUR_OF_DAY, 1);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 55);
		Object actual1 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		this.calendar.add(Calendar.MINUTE, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testSpecificMinuteHour(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("* 5 10 * * *", timeZone);
		this.calendar.set(Calendar.MINUTE, 4);
		this.calendar.set(Calendar.HOUR_OF_DAY, 9);
		Date localDate = this.calendar.getTime();
		this.calendar.add(Calendar.MINUTE, 1);
		this.calendar.add(Calendar.HOUR_OF_DAY, 1);
		this.calendar.set(Calendar.SECOND, 0);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual1 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		// next trigger is in one second because second is wildcard
		this.calendar.add(Calendar.SECOND, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testSpecificDayOfMonthSecond(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("55 * * 3 * *", timeZone);
		this.calendar.set(Calendar.DAY_OF_MONTH, 2);
		this.calendar.set(Calendar.SECOND, 54);
		Date localDate = this.calendar.getTime();
		TriggerContext context1 = getTriggerContext(localDate);
		this.calendar.add(Calendar.DAY_OF_MONTH, 1);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 55);
		Object actual1 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		this.calendar.add(Calendar.MINUTE, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testSpecificDate(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("* * * 3 11 *", timeZone);
		this.calendar.set(Calendar.DAY_OF_MONTH, 2);
		this.calendar.set(Calendar.MONTH, 9);
		Date localDate = this.calendar.getTime();
		TriggerContext context1 = getTriggerContext(localDate);
		this.calendar.add(Calendar.DAY_OF_MONTH, 1);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MONTH, 10); // 10=November
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		Object actual1 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		this.calendar.add(Calendar.SECOND, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testNonExistentSpecificDate(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		// TODO: maybe try and detect this as a special case in parser?
		CronTrigger trigger = new CronTrigger("0 0 0 31 6 *", timeZone);
		this.calendar.set(Calendar.DAY_OF_MONTH, 10);
		this.calendar.set(Calendar.MONTH, 2);
		Date localDate = this.calendar.getTime();
		TriggerContext context1 = getTriggerContext(localDate);
		assertThat(trigger.nextExecutionTime(context1)).isNull();
	}

	@ParameterizedCronTriggerTest
	void testLeapYearSpecificDate(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 0 29 2 *", timeZone);
		this.calendar.set(Calendar.YEAR, 2007);
		this.calendar.set(Calendar.DAY_OF_MONTH, 10);
		this.calendar.set(Calendar.MONTH, 1); // 2=February
		Date localDate = this.calendar.getTime();
		TriggerContext context1 = getTriggerContext(localDate);
		this.calendar.set(Calendar.YEAR, 2008);
		this.calendar.set(Calendar.DAY_OF_MONTH, 29);
		this.calendar.set(Calendar.HOUR_OF_DAY, 0);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		Object actual1 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		this.calendar.add(Calendar.YEAR, 4);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testWeekDaySequence(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 0 7 ? * MON-FRI", timeZone);
		// This is a Saturday
		this.calendar.set(2009, 8, 26);
		Date localDate = this.calendar.getTime();
		// 7 am is the trigger time
		this.calendar.set(Calendar.HOUR_OF_DAY, 7);
		this.calendar.set(Calendar.MINUTE, 0);
		this.calendar.set(Calendar.SECOND, 0);
		// Add two days because we start on Saturday
		this.calendar.add(Calendar.DAY_OF_MONTH, 2);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual2 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual2).isEqualTo(this.calendar.getTime());
		// Next day is a week day so add one
		this.calendar.add(Calendar.DAY_OF_MONTH, 1);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual1 = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		this.calendar.add(Calendar.DAY_OF_MONTH, 1);
		TriggerContext context3 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context3);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testDayOfWeekIndifferent(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("* * * 2 * *", timeZone);
		CronTrigger trigger2 = new CronTrigger("* * * 2 * ?", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testSecondIncrementer(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("57,59 * * * * *", timeZone);
		CronTrigger trigger2 = new CronTrigger("57/2 * * * * *", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testSecondIncrementerWithRange(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("1,3,5 * * * * *", timeZone);
		CronTrigger trigger2 = new CronTrigger("1-6/2 * * * * *", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testHourIncrementer(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("* * 4,8,12,16,20 * * *", timeZone);
		CronTrigger trigger2 = new CronTrigger("* * 4/4 * * *", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testDayNames(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("* * * * * 0-6", timeZone);
		CronTrigger trigger2 = new CronTrigger("* * * * * TUE,WED,THU,FRI,SAT,SUN,MON", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testSundayIsZero(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("* * * * * 0", timeZone);
		CronTrigger trigger2 = new CronTrigger("* * * * * SUN", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testSundaySynonym(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("* * * * * 0", timeZone);
		CronTrigger trigger2 = new CronTrigger("* * * * * 7", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testMonthNames(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("* * * * 1-12 *", timeZone);
		CronTrigger trigger2 = new CronTrigger("* * * * FEB,JAN,MAR,APR,MAY,JUN,JUL,AUG,SEP,OCT,NOV,DEC *", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testMonthNamesMixedCase(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("* * * * 2 *", timeZone);
		CronTrigger trigger2 = new CronTrigger("* * * * Feb *", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testSecondInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("77 * * * * *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testSecondRangeInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("44-77 * * * * *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testMinuteInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("* 77 * * * *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testMinuteRangeInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("* 44-77 * * * *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testHourInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("* * 27 * * *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testHourRangeInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("* * 23-28 * * *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testDayInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("* * * 45 * *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testDayRangeInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("* * * 28-45 * *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testMonthInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("0 0 0 25 13 ?", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testMonthInvalidTooSmall(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("0 0 0 25 0 ?", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testDayOfMonthInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("0 0 0 32 12 ?", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testMonthRangeInvalid(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		assertThatIllegalArgumentException().isThrownBy(() -> new CronTrigger("* * * * 11-13 *", timeZone));
	}

	@ParameterizedCronTriggerTest
	void testWhitespace(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger1 = new CronTrigger("*  *  * *  1 *", timeZone);
		CronTrigger trigger2 = new CronTrigger("* * * * 1 *", timeZone);
		assertThat(trigger2).isEqualTo(trigger1);
	}

	@ParameterizedCronTriggerTest
	void testMonthSequence(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		CronTrigger trigger = new CronTrigger("0 30 23 30 1/3 ?", timeZone);
		this.calendar.set(2010, 11, 30);
		Date localDate = this.calendar.getTime();
		// set expected next trigger time
		this.calendar.set(Calendar.HOUR_OF_DAY, 23);
		this.calendar.set(Calendar.MINUTE, 30);
		this.calendar.set(Calendar.SECOND, 0);
		this.calendar.add(Calendar.MONTH, 1);
		TriggerContext context1 = getTriggerContext(localDate);
		Object actual2 = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual2).isEqualTo(this.calendar.getTime());
		// Next trigger is 3 months latter
		this.calendar.add(Calendar.MONTH, 3);
		TriggerContext context2 = getTriggerContext(localDate);
		Object actual1 = localDate = trigger.nextExecutionTime(context2);
		assertThat(actual1).isEqualTo(this.calendar.getTime());
		// Next trigger is 3 months latter
		this.calendar.add(Calendar.MONTH, 3);
		TriggerContext context3 = getTriggerContext(localDate);
		Object actual = localDate = trigger.nextExecutionTime(context3);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	@ParameterizedCronTriggerTest
	void testDaylightSavingMissingHour(LocalDateTime localDateTime, TimeZone timeZone) {
		setUp(localDateTime, timeZone);

		// This trigger has to be somewhere in between 2am and 3am
		CronTrigger trigger = new CronTrigger("0 10 2 * * *", timeZone);
		this.calendar.set(Calendar.DAY_OF_MONTH, 31);
		this.calendar.set(Calendar.MONTH, Calendar.MARCH);
		this.calendar.set(Calendar.YEAR, 2013);
		this.calendar.set(Calendar.HOUR_OF_DAY, 1);
		this.calendar.set(Calendar.SECOND, 54);
		Date localDate = this.calendar.getTime();
		TriggerContext context1 = getTriggerContext(localDate);
		if (timeZone.equals(TimeZone.getTimeZone("CET"))) {
			// Clocks go forward an hour so 2am doesn't exist in CET for this localDateTime
			this.calendar.add(Calendar.DAY_OF_MONTH, 1);
		}
		this.calendar.add(Calendar.HOUR_OF_DAY, 1);
		this.calendar.set(Calendar.MINUTE, 10);
		this.calendar.set(Calendar.SECOND, 0);
		Object actual = localDate = trigger.nextExecutionTime(context1);
		assertThat(actual).isEqualTo(this.calendar.getTime());
	}

	private static void roundup(Calendar calendar) {
		calendar.add(Calendar.SECOND, 1);
		calendar.set(Calendar.MILLISECOND, 0);
	}

	private static void assertMatchesNextSecond(CronTrigger trigger, Calendar calendar) {
		Date localDateTime = calendar.getTime();
		roundup(calendar);
		TriggerContext context = getTriggerContext(localDateTime);
		assertThat(trigger.nextExecutionTime(context)).isEqualTo(calendar.getTime());
	}

	private static TriggerContext getTriggerContext(Date lastCompletionTime) {
		SimpleTriggerContext context = new SimpleTriggerContext();
		context.update(null, null, lastCompletionTime);
		return context;
	}


	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@ParameterizedTest(name = "[{index}] localDateTime[{0}], time zone[{1}]")
	@MethodSource("parameters")
	@interface ParameterizedCronTriggerTest {
	}

	static Stream<Arguments> parameters() {
		return Stream.of(
			arguments(LocalDateTime.now(), TimeZone.getTimeZone("PST")),
			arguments(LocalDateTime.now(), TimeZone.getTimeZone("CET"))
		);
	}

}
