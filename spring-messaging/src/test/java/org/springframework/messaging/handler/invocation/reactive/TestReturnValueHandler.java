/*
 * Copyright 2002-2019 the original author or authors.
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
package org.springframework.messaging.handler.invocation.reactive;

import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * Return value handler that simply stores the last return value.
 * @author Rossen Stoyanchev
 */
public class TestReturnValueHandler implements HandlerMethodReturnValueHandler {

	@Nullable
	private Object lastReturnValue;


	@Nullable
	public Object getLastReturnValue() {
		return this.lastReturnValue;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return true;
	}

	@Override
	public Mono<Void> handleReturnValue(@Nullable Object value, MethodParameter returnType, Message<?> message) {
		this.lastReturnValue = value;
		return Mono.empty();
	}

}
