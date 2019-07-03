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

package org.springframework.messaging.simp;

import java.security.Principal;
import java.util.Collections;
import java.util.function.Consumer;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for SimpMessageHeaderAccessor.
 *
 * @author Rossen Stoyanchev
 */
public class SimpMessageHeaderAccessorTests {


	@Test
	public void getShortLogMessage() {
		assertThat(SimpMessageHeaderAccessor.create().getShortLogMessage("p"))
				.isEqualTo("MESSAGE session=null payload=p");
	}

	@Test
	public void getLogMessageWithValuesSet() {
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
		accessor.setDestination("/destination");
		accessor.setSubscriptionId("subscription");
		accessor.setSessionId("session");
		accessor.setUser(new TestPrincipal("user"));
		accessor.setSessionAttributes(Collections.<String, Object>singletonMap("key", "value"));

		assertThat(accessor.getShortLogMessage("p"))
				.isEqualTo(("MESSAGE destination=/destination subscriptionId=subscription " +
						"session=session user=user attributes[1] payload=p"));
	}

	@Test
	public void getDetailedLogMessageWithValuesSet() {
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
		accessor.setDestination("/destination");
		accessor.setSubscriptionId("subscription");
		accessor.setSessionId("session");
		accessor.setUser(new TestPrincipal("user"));
		accessor.setSessionAttributes(Collections.<String, Object>singletonMap("key", "value"));
		accessor.setNativeHeader("nativeKey", "nativeValue");

		assertThat(accessor.getDetailedLogMessage("p"))
				.isEqualTo(("MESSAGE destination=/destination subscriptionId=subscription " +
						"session=session user=user attributes={key=value} nativeHeaders=" +
						"{nativeKey=[nativeValue]} payload=p"));
	}

	@Test
	public void userChangeCallback() {
		UserCallback userCallback = new UserCallback();
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
		accessor.setUserChangeCallback(userCallback);

		Principal user1 = mock(Principal.class);
		accessor.setUser(user1);
		assertThat(userCallback.getUser()).isEqualTo(user1);

		Principal user2 = mock(Principal.class);
		accessor.setUser(user2);
		assertThat(userCallback.getUser()).isEqualTo(user2);
	}


	private static class UserCallback implements Consumer<Principal> {

		private Principal user;


		public Principal getUser() {
			return this.user;
		}

		@Override
		public void accept(Principal principal) {
			this.user = principal;
		}
	}

}
