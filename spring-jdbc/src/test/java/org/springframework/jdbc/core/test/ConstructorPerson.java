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

package org.springframework.jdbc.core.test;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author Juergen Hoeller
 */
public class ConstructorPerson {

	private String name;

	private long age;

	private java.util.Date birth_date;

	private BigDecimal balance;


	public ConstructorPerson(String name, long age, Date birth_date, BigDecimal balance) {
		this.name = name;
		this.age = age;
		this.birth_date = birth_date;
		this.balance = balance;
	}


	public String name() {
		return name;
	}

	public long age() {
		return age;
	}

	public Date birth_date() {
		return birth_date;
	}

	public BigDecimal balance() {
		return balance;
	}

}
