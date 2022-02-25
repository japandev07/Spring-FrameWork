/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.web.bind;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;

/**
 * Exception to be thrown when validation on an argument annotated with {@code @Valid} fails.
 * Extends {@link BindException} as of 5.3.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
@SuppressWarnings("serial")
public class MethodArgumentNotValidException extends BindException implements ErrorResponse {

	private final MethodParameter parameter;

	private final ProblemDetail body;


	/**
	 * Constructor for {@link MethodArgumentNotValidException}.
	 * @param parameter the parameter that failed validation
	 * @param bindingResult the results of the validation
	 */
	public MethodArgumentNotValidException(MethodParameter parameter, BindingResult bindingResult) {
		super(bindingResult);
		this.parameter = parameter;
		this.body = ProblemDetail.forRawStatusCode(getRawStatusCode()).withDetail(initMessage(parameter));
	}


	@Override
	public int getRawStatusCode() {
		return HttpStatus.BAD_REQUEST.value();
	}

	@Override
	public ProblemDetail getBody() {
		return this.body;
	}

	/**
	 * Return the method parameter that failed validation.
	 */
	public final MethodParameter getParameter() {
		return this.parameter;
	}

	@Override
	public String getMessage() {
		return initMessage(this.parameter);
	}

	private String initMessage(MethodParameter parameter) {
		StringBuilder sb = new StringBuilder("Validation failed for argument [")
				.append(parameter.getParameterIndex()).append("] in ")
				.append(parameter.getExecutable().toGenericString());
		BindingResult bindingResult = getBindingResult();
		if (bindingResult.getErrorCount() > 1) {
			sb.append(" with ").append(bindingResult.getErrorCount()).append(" errors");
		}
		sb.append(": ");
		for (ObjectError error : bindingResult.getAllErrors()) {
			sb.append('[').append(error).append("] ");
		}
		return sb.toString();
	}

}
