/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.reactive.function.server;

import java.util.Set;
import java.util.function.Function;

import reactor.core.publisher.Mono;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;

/**
 * Implementation of {@link RouterFunctions.Visitor} that creates a formatted string representation
 * of router functions.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
class ToStringVisitor implements RouterFunctions.Visitor, RequestPredicates.Visitor {

	private static final String NEW_LINE = System.getProperty("line.separator", "\\n");

	private final StringBuilder builder = new StringBuilder();

	private int indent = 0;

	@Nullable
	private String infix;

	// RouterFunctions.Visitor

	@Override
	public void startNested(RequestPredicate predicate) {
		indent();
		predicate.accept(this);
		this.builder.append(" => {");
		this.builder.append(NEW_LINE);
		this.indent++;
	}

	@Override
	public void endNested(RequestPredicate predicate) {
		this.indent--;
		indent();
		this.builder.append('}');
		this.builder.append(NEW_LINE);
	}

	@Override
	public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
		indent();
		predicate.accept(this);
		this.builder.append(" -> ");
		this.builder.append(handlerFunction);
		this.builder.append(NEW_LINE);
	}

	@Override
	public void resources(Function<ServerRequest, Mono<Resource>> lookupFunction) {
		indent();
		this.builder.append(lookupFunction);
		this.builder.append(NEW_LINE);
	}

	@Override
	public void unknown(RouterFunction<?> routerFunction) {
		indent();
		this.builder.append(routerFunction);
	}

	private void indent() {
		for (int i=0; i < this.indent; i++) {
			this.builder.append(' ');
		}
	}

	// RequestPredicates.Visitor

	@Override
	public void method(Set<HttpMethod> methods) {
		if (methods.size() == 1) {
			this.builder.append(methods.iterator().next());
		}
		else {
			this.builder.append(methods);
		}
		infix();
	}

	@Override
	public void path(String pattern) {
		this.builder.append(pattern);
		infix();
	}

	@Override
	public void pathExtension(String extension) {
		this.builder.append(String.format("*.%s", extension));
		infix();
	}

	@Override
	public void header(String name, String value) {
		this.builder.append(String.format("%s: %s", name, value));
		infix();
	}

	@Override
	public void queryParam(String name, String value) {
		this.builder.append(String.format("?%s == %s", name, value));
		infix();
	}

	@Override
	public void startAnd() {
		this.builder.append('(');
		this.infix = "&&";
	}

	@Override
	public void endAnd() {
		this.builder.append(')');
	}

	@Override
	public void startOr() {
		this.builder.append('(');
		this.infix = "||";
	}

	@Override
	public void endOr() {
		this.builder.append(')');
	}

	@Override
	public void startNegate() {
		this.builder.append("!(");

	}

	@Override
	public void endNegate() {
		this.builder.append(')');
	}

	@Override
	public void unknown(RequestPredicate predicate) {
		this.builder.append(predicate);
	}

	private void infix() {
		if (this.infix != null) {
			this.builder.append(' ');
			this.builder.append(this.infix);
			this.builder.append(' ');
			this.infix = null;
		}
	}


	@Override
	public String toString() {
		String result = this.builder.toString();
		if (result.endsWith(NEW_LINE)) {
			result = result.substring(0, result.length() - NEW_LINE.length());
		}
		return result;
	}
}
