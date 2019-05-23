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

package org.springframework.util.unit;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link DataSize}.
 *
 * @author Stephane Nicoll
 */
public class DataSizeTests {

	@Test
	public void ofBytesToBytes() {
		assertThat(DataSize.ofBytes(1024).toBytes()).isEqualTo(1024);
	}

	@Test
	public void ofBytesToKilobytes() {
		assertThat(DataSize.ofBytes(1024).toKilobytes()).isEqualTo(1);
	}

	@Test
	public void ofKilobytesToKilobytes() {
		assertThat(DataSize.ofKilobytes(1024).toKilobytes()).isEqualTo(1024);
	}

	@Test
	public void ofKilobytesToMegabytes() {
		assertThat(DataSize.ofKilobytes(1024).toMegabytes()).isEqualTo(1);
	}

	@Test
	public void ofMegabytesToMegabytes() {
		assertThat(DataSize.ofMegabytes(1024).toMegabytes()).isEqualTo(1024);
	}

	@Test
	public void ofMegabytesToGigabytes() {
		assertThat(DataSize.ofMegabytes(2048).toGigabytes()).isEqualTo(2);
	}

	@Test
	public void ofGigabytesToGigabytes() {
		assertThat(DataSize.ofGigabytes(4096).toGigabytes()).isEqualTo(4096);
	}

	@Test
	public void ofGigabytesToTerabytes() {
		assertThat(DataSize.ofGigabytes(4096).toTerabytes()).isEqualTo(4);
	}

	@Test
	public void ofTerabytesToGigabytes() {
		assertThat(DataSize.ofTerabytes(1).toGigabytes()).isEqualTo(1024);
	}

	@Test
	public void ofWithBytesUnit() {
		assertThat(DataSize.of(10, DataUnit.BYTES)).isEqualTo(DataSize.ofBytes(10));
	}

	@Test
	public void ofWithKilobytesUnit() {
		assertThat(DataSize.of(20, DataUnit.KILOBYTES)).isEqualTo(DataSize.ofKilobytes(20));
	}

	@Test
	public void ofWithMegabytesUnit() {
		assertThat(DataSize.of(30, DataUnit.MEGABYTES)).isEqualTo(DataSize.ofMegabytes(30));
	}

	@Test
	public void ofWithGigabytesUnit() {
		assertThat(DataSize.of(40, DataUnit.GIGABYTES)).isEqualTo(DataSize.ofGigabytes(40));
	}

	@Test
	public void ofWithTerabytesUnit() {
		assertThat(DataSize.of(50, DataUnit.TERABYTES)).isEqualTo(DataSize.ofTerabytes(50));
	}

	@Test
	public void parseWithDefaultUnitUsesBytes() {
		assertThat(DataSize.parse("1024")).isEqualTo(DataSize.ofKilobytes(1));
	}

	@Test
	public void parseNegativeNumberWithDefaultUnitUsesBytes() {
		assertThat(DataSize.parse("-1")).isEqualTo(DataSize.ofBytes(-1));
	}

	@Test
	public void parseWithNullDefaultUnitUsesBytes() {
		assertThat(DataSize.parse("1024", null)).isEqualTo(DataSize.ofKilobytes(1));
	}

	@Test
	public void parseNegativeNumberWithNullDefaultUnitUsesBytes() {
		assertThat(DataSize.parse("-1024", null)).isEqualTo(DataSize.ofKilobytes(-1));
	}

	@Test
	public void parseWithCustomDefaultUnit() {
		assertThat(DataSize.parse("1", DataUnit.KILOBYTES)).isEqualTo(DataSize.ofKilobytes(1));
	}

	@Test
	public void parseNegativeNumberWithCustomDefaultUnit() {
		assertThat(DataSize.parse("-1", DataUnit.KILOBYTES)).isEqualTo(DataSize.ofKilobytes(-1));
	}

	@Test
	public void parseWithBytes() {
		assertThat(DataSize.parse("1024B")).isEqualTo(DataSize.ofKilobytes(1));
	}

	@Test
	public void parseWithNegativeBytes() {
		assertThat(DataSize.parse("-1024B")).isEqualTo(DataSize.ofKilobytes(-1));
	}

	@Test
	public void parseWithPositiveBytes() {
		assertThat(DataSize.parse("+1024B")).isEqualTo(DataSize.ofKilobytes(1));
	}

	@Test
	public void parseWithKilobytes() {
		assertThat(DataSize.parse("1KB")).isEqualTo(DataSize.ofBytes(1024));
	}

	@Test
	public void parseWithNegativeKilobytes() {
		assertThat(DataSize.parse("-1KB")).isEqualTo(DataSize.ofBytes(-1024));
	}

	@Test
	public void parseWithMegabytes() {
		assertThat(DataSize.parse("4MB")).isEqualTo(DataSize.ofMegabytes(4));
	}

	@Test
	public void parseWithNegativeMegabytes() {
		assertThat(DataSize.parse("-4MB")).isEqualTo(DataSize.ofMegabytes(-4));
	}

	@Test
	public void parseWithGigabytes() {
		assertThat(DataSize.parse("1GB")).isEqualTo(DataSize.ofMegabytes(1024));
	}

	@Test
	public void parseWithNegativeGigabytes() {
		assertThat(DataSize.parse("-1GB")).isEqualTo(DataSize.ofMegabytes(-1024));
	}

	@Test
	public void parseWithTerabytes() {
		assertThat(DataSize.parse("1TB")).isEqualTo(DataSize.ofTerabytes(1));
	}

	@Test
	public void parseWithNegativeTerabytes() {
		assertThat(DataSize.parse("-1TB")).isEqualTo(DataSize.ofTerabytes(-1));
	}

	@Test
	public void isNegativeWithPositive() {
		assertThat(DataSize.ofBytes(50).isNegative()).isFalse();
	}

	@Test
	public void isNegativeWithZero() {
		assertThat(DataSize.ofBytes(0).isNegative()).isFalse();
	}

	@Test
	public void isNegativeWithNegative() {
		assertThat(DataSize.ofBytes(-1).isNegative()).isTrue();
	}

	@Test
	public void toStringUsesBytes() {
		assertThat(DataSize.ofKilobytes(1).toString()).isEqualTo("1024B");
	}

	@Test
	public void toStringWithNegativeBytes() {
		assertThat(DataSize.ofKilobytes(-1).toString()).isEqualTo("-1024B");
	}

	@Test
	public void parseWithUnsupportedUnit() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				DataSize.parse("3WB"))
			.withMessage("'3WB' is not a valid data size");
	}

}
