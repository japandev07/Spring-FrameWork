/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.jms.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.messaging.handler.annotation.MessageMapping;

/**
 * Annotation that marks a method to be the target of a JMS message
 * listener on the specified {@link #destination()}. The {@link #containerFactory()}
 * identifies the {@link org.springframework.jms.config.JmsListenerContainerFactory
 * JmsListenerContainerFactory} to use to build the jms listener container. If not
 * set, a <em>default</em> container factory is assumed to be available with a bean
 * name of {@code jmsListenerContainerFactory} unless an explicit default has been
 * provided through configuration.
 *
 * <p>Processing of {@code @JmsListener} annotations is performed by
 * registering a {@link JmsListenerAnnotationBeanPostProcessor}. This can be
 * done manually or, more conveniently, through the {@code <jms:annotation-driven/>}
 * element or {@link EnableJms} annotation.
 *
 * <p>Annotated methods are allowed to have flexible signatures similar to what
 * {@link MessageMapping} provides, that is
 * <ul>
 * <li>{@link javax.jms.Session} to get access to the JMS session</li>
 * <li>{@link javax.jms.Message} or one if subclass to get access to the raw JMS message</li>
 * <li>{@link org.springframework.messaging.Message} to use the messaging abstraction counterpart</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Payload @Payload}-annotated method
 * arguments including the support of validation</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Header @Header}-annotated method
 * arguments to extract a specific header value, including standard JMS headers defined by
 * {@link org.springframework.jms.support.JmsHeaders JmsHeaders}</li>
 * <li>{@link org.springframework.messaging.handler.annotation.Headers @Headers}-annotated
 * argument that must also be assignable to {@link java.util.Map} for getting access to all
 * headers.</li>
 * <li>{@link org.springframework.messaging.MessageHeaders MessageHeaders} arguments for
 * getting access to all headers.</li>
 * <li>{@link org.springframework.messaging.support.MessageHeaderAccessor MessageHeaderAccessor}
 * or {@link org.springframework.jms.support.JmsMessageHeaderAccessor JmsMessageHeaderAccessor}
 * for convenient access to all method arguments.</li>
 * </ul>
 *
 * <p>Annotated method may have a non {@code void} return type. When they do, the result of the
 * method invocation is sent as a JMS reply to the destination defined by either the
 * {@code JMSReplyTO} header of the incoming message. When this value is not set, a default
 * destination can be provided by adding @{@link org.springframework.messaging.handler.annotation.SendTo
 * SendTo} to the method declaration.
 *
 * @author Stephane Nicoll
 * @since 4.1
 * @see EnableJms
 * @see JmsListenerAnnotationBeanPostProcessor
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@MessageMapping
@Documented
public @interface JmsListener {

	/**
	 * The unique identifier of the container managing this endpoint.
	 * <p>if none is specified an auto-generated one is provided.
	 * @see org.springframework.jms.config.JmsListenerEndpointRegistry#getListenerContainer(String)
	 */
	String id() default "";

	/**
	 * The bean name of the {@link org.springframework.jms.config.JmsListenerContainerFactory}
	 * to use to create the message listener container responsible to serve this endpoint.
	 * <p>If not specified, the default container factory is used, if any.
	 */
	String containerFactory() default "";

	/**
	 * The destination name for this listener, resolved through the container-wide
	 * {@link org.springframework.jms.support.destination.DestinationResolver} strategy.
	 */
	String destination();

	/**
	 * The name for the durable subscription, if any.
	 */
	String subscription() default "";

	/**
	 * The JMS message selector expression, if any
	 * <p>See the JMS specification for a detailed definition of selector expressions.
	 */
	String selector() default "";

	/**
	 * The concurrency for the listener, if any.
	 * <p>The concurrency limits can be a "lower-upper" String, e.g. "5-10", or a simple
	 * upper limit String, e.g. "10" (the lower limit will be 1 in this case).
	 * <p>The underlying container may or may not support all features. For instance, it
	 * may not be able to scale: in that case only the upper value is used.
	 */
	String concurrency() default "";

}
