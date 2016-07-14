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
package org.springframework.web.server.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Mono;

import org.springframework.web.server.WebSession;

/**
 * Simple Map-based storage for {@link WebSession} instances.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class InMemoryWebSessionStore implements WebSessionStore {

	private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();


	@Override
	public Mono<Void> storeSession(WebSession session) {
		this.sessions.put(session.getId(), session);
		return Mono.empty();
	}

	@Override
	public Mono<WebSession> retrieveSession(String id) {
		return (this.sessions.containsKey(id) ? Mono.just(this.sessions.get(id)) : Mono.empty());
	}

	@Override
	public Mono<Void> removeSession(String id) {
		this.sessions.remove(id);
		return Mono.empty();
	}

}
