/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.reactive.support;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.util.ObjectUtils;

/**
 * {@link org.springframework.web.WebApplicationInitializer WebApplicationInitializer}
 * to register a {@code DispatcherHandler}, wrapping it in a
 * {@link ServletHttpHandlerAdapter}, and use Java-based Spring configuration.
 *
 * <p>Concrete implementations are required to implement {@link #getConfigClasses()}.
 * Further template and customization methods are provided by
 * {@link AbstractDispatcherHandlerInitializer}.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public abstract class AbstractAnnotationConfigDispatcherHandlerInitializer
		extends AbstractDispatcherHandlerInitializer {


	/**
	 * {@inheritDoc}
	 * <p>This implementation creates an {@link AnnotationConfigApplicationContext},
	 * providing it the annotated classes returned by {@link #getConfigClasses()}.
	 */
	@Override
	protected ApplicationContext createApplicationContext() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		Class<?>[] configClasses = getConfigClasses();
		if (!ObjectUtils.isEmpty(configClasses)) {
			context.register(configClasses);
		}
		return context;
	}

	/**
	 * Specify {@code @Configuration} and/or {@code @Component} classes for
	 * the {@linkplain #createApplicationContext() application context}.
	 * @return the configuration for the application context
	 */
	protected abstract Class<?>[] getConfigClasses();

}
