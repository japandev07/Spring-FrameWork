/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.context.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.ApplicationEvent;
import org.springframework.core.annotation.AliasFor;

/**
 * Annotation that marks a method as a listener for application events.
 *
 * <p>The method must declare one (and only one) parameter that reflects the
 * event type to listen to. Alternatively, this annotation may refer to the
 * event type(s) using the {@link #classes} attribute. Events can be
 * {@link ApplicationEvent} instances as well as arbitrary objects.
 *
 * <p>Processing of {@code @EventListener} annotations is performed via the
 * {@link EventListenerMethodProcessor} that is registered automatically
 * when using Java config or via the {@code <context:annotation-driven/>}
 * XML element.
 *
 * <p>Annotated methods may have a non-{@code void} return type. When they
 * do, the result of the method invocation is sent as a new event. If the
 * return type is either an array or a collection, each element is sent as
 * a new event.
 *
 * <p>It is also possible to define the order in which listeners for a
 * certain event are invoked. To do so, add a regular
 * {@link org.springframework.core.annotation.Order @Order} annotation
 * alongside this annotation.
 *
 * <p>While it is possible to define any arbitrary exception types, checked
 * exceptions will be wrapped in a {@link java.lang.reflect.UndeclaredThrowableException}
 * so that the caller only handles runtime exceptions.
 *
 * @author Stephane Nicoll
 * @since 4.2
 * @see EventListenerMethodProcessor
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {

	/**
	 * Alias for {@link #classes}.
	 */
	@AliasFor(attribute = "classes")
	Class<?>[] value() default {};

	/**
	 * The event classes that this listener handles.
	 * <p>When this attribute is specified with one value, the method parameter
	 * may or may not be specified. When this attribute is specified with more
	 * than one value, the method must not have a parameter.
	 */
	@AliasFor(attribute = "value")
	Class<?>[] classes() default {};

	/**
	 * Spring Expression Language (SpEL) attribute used for making the event
	 * handling conditional.
	 * <p>Default is "", meaning the event is always handled.
	 */
	String condition() default "";

}