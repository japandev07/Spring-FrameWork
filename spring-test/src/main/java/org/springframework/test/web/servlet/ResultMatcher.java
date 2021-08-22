/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.test.web.servlet;

/**
 * A {@code ResultMatcher} matches the result of an executed request against
 * some expectation.
 *
 * <p>See static factory methods in
 * {@link org.springframework.test.web.servlet.result.MockMvcResultMatchers
 * MockMvcResultMatchers}.
 *
 * <h3>Example Using Status and Content Result Matchers</h3>
 *
 * <pre class="code">
 * import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
 * import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
 * import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
 *
 * // ...
 *
 * WebApplicationContext wac = ...;
 *
 * MockMvc mockMvc = webAppContextSetup(wac).build();
 *
 * mockMvc.perform(get("/form"))
 *   .andExpect(status().isOk())
 *   .andExpect(content().mimeType(MediaType.APPLICATION_JSON));
 * </pre>
 *
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @author Michał Rowicki
 * @since 3.2
 */
@FunctionalInterface
public interface ResultMatcher {

	/**
	 * Assert the result of an executed request.
	 * @param result the result of the executed request
	 * @throws Exception if a failure occurs
	 */
	void match(MvcResult result) throws Exception;


	/**
	 * Static method for matching with an array of result matchers.
	 * @param matchers the matchers
	 * @since 5.1
	 */
	static ResultMatcher matchAll(ResultMatcher... matchers) {
		return result -> {
			for (ResultMatcher matcher : matchers) {
				matcher.match(result);
			}
		};
	}

	/**
	 * Static method for matching with an array of result matchers whose assertion
	 * failures are caught and stored. Once all matchers have been called, if any
	 * failures occurred, an {@link AssertionError} will be thrown containing the
	 * error messages of all assertion failures.
	 * @param matchers the matchers
	 * @since 5.3.10
	 */
	static ResultMatcher matchAllSoftly(ResultMatcher... matchers) {
		return result -> {
			String message = "";
			for (ResultMatcher matcher : matchers) {
				try {
					matcher.match(result);
				}
				catch (Error | Exception ex) {
					if (!message.isEmpty()) {
						message += System.lineSeparator();
					}
					message += ex.getMessage();
				}
			}
			if (!message.isEmpty()) {
				throw new AssertionError(message);
			}
		};
	}

}
