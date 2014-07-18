/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.messaging.simp;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

/**
 * A {@link Scope} implementation exposing the attributes of a SiMP session
 * (e.g. WebSocket session).
 *
 * <p>Relies on a thread-bound {@link SimpAttributes} instance exported by
 * {@link org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 4.1
 */
public class SimpSessionScope implements Scope {

	@Override
	public Object get(String name, ObjectFactory<?> objectFactory) {
		SimpAttributes simpAttributes = SimpAttributesContextHolder.currentAttributes();
		Object value = simpAttributes.getAttribute(name);
		if (value != null) {
			return value;
		}
		synchronized (simpAttributes.getSessionMutex()) {
			value = simpAttributes.getAttribute(name);
			if (value == null) {
				value = objectFactory.getObject();
				simpAttributes.setAttribute(name, value);
			}
			return value;
		}
	}

	@Override
	public Object remove(String name) {
		SimpAttributes simpAttributes = SimpAttributesContextHolder.currentAttributes();
		synchronized (simpAttributes.getSessionMutex()) {
			Object value = simpAttributes.getAttribute(name);
			if (value != null) {
				simpAttributes.removeAttribute(name);
				return value;
			}
			else {
				return null;
			}
		}
	}

	@Override
	public void registerDestructionCallback(String name, Runnable callback) {
		SimpAttributesContextHolder.currentAttributes().registerDestructionCallback(name, callback);
	}

	@Override
	public Object resolveContextualObject(String key) {
		return null;
	}

	@Override
	public String getConversationId() {
		return SimpAttributesContextHolder.currentAttributes().getSessionId();
	}

}
