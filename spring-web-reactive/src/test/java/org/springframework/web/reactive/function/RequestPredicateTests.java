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

package org.springframework.web.reactive.function;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Arjen Poutsma
 */
public class RequestPredicateTests {

	@Test
	public void and() throws Exception {
		RequestPredicate predicate1 = request -> true;
		RequestPredicate predicate2 = request -> true;
		RequestPredicate predicate3 = request -> false;

		MockRequest request = MockRequest.builder().build();
		assertTrue(predicate1.and(predicate2).test(request));
		assertTrue(predicate2.and(predicate1).test(request));
		assertFalse(predicate1.and(predicate3).test(request));
	}

	@Test
	public void negate() throws Exception {
		RequestPredicate predicate = request -> false;
		RequestPredicate negated = predicate.negate();

		MockRequest mockRequest = MockRequest.builder().build();
		assertTrue(negated.test(mockRequest));

		predicate = request -> true;
		negated = predicate.negate();

		assertFalse(negated.test(mockRequest));
	}

	@Test
	public void or() throws Exception {
		RequestPredicate predicate1 = request -> true;
		RequestPredicate predicate2 = request -> false;
		RequestPredicate predicate3 = request -> false;

		MockRequest request = MockRequest.builder().build();
		assertTrue(predicate1.or(predicate2).test(request));
		assertTrue(predicate2.or(predicate1).test(request));
		assertFalse(predicate2.or(predicate3).test(request));
	}
}