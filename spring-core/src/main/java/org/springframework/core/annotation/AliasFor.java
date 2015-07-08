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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @AliasFor} is an annotation that is used to declare aliases for
 * annotation attributes.
 *
 * <h3>Usage Scenarios</h3>
 * <ul>
 * <li><strong>Aliases within an annotation</strong>: within a single
 * annotation, {@code @AliasFor} can be declared on a pair of attributes to
 * signal that they are interchangeable aliases for each other.</li>
 * <li><strong>Alias for attribute in meta-annotation</strong>: if the
 * {@link #annotation} attribute of {@code @AliasFor} is set to a different
 * annotation than the one that declares it, the {@link #attribute} is
 * interpreted as an alias for an attribute in a meta-annotation (i.e., an
 * explicit meta-annotation attribute override). This enables fine-grained
 * control over exactly which attributes are overridden within an annotation
 * hierarchy. In fact, with {@code @AliasFor} it is even possible to declare
 * an alias for the {@code value} attribute of a meta-annotation.</li>
 * </ul>
 *
 * <h3>Usage Requirements</h3>
 * <p>Like with any annotation in Java, the mere presence of {@code @AliasFor}
 * on its own will not enforce alias semantics. For alias semantics to be
 * enforced, annotations must be <em>loaded</em> via the utility methods in
 * {@link AnnotationUtils}. Behind the scenes, Spring will <em>synthesize</em>
 * an annotation by wrapping it in a dynamic proxy that transparently enforces
 * <em>attribute alias</em> semantics for annotation attributes that are
 * annotated with {@code @AliasFor}. Similarly, {@link AnnotatedElementUtils}
 * supports explicit meta-annotation attribute overrides when {@code @AliasFor}
 * is used within an annotation hierarchy. Typically you will not need to
 * manually synthesize annotations on your own since Spring will do that for
 * you transparently when looking up annotations on Spring-managed components.
 *
 * <h3>Implementation Requirements</h3>
 * <ul>
 * <li><strong>Aliases within an annotation</strong>:
 * <ol>
 * <li>Each attribute that makes up an aliased pair must be annotated with
 * {@code @AliasFor}, and the {@link #attribute} must reference the
 * <em>other</em> attribute in the pair.</li>
 * <li>Aliased attributes must declare the same return type.</li>
 * <li>Aliased attributes must declare a default value.</li>
 * <li>Aliased attributes must declare the same default value.</li>
 * <li>The {@link #annotation} attribute may remain set to the default,
 * although setting it to the declaring class for both attributes in the
 * pair is also valid.</li>
 * </ol>
 * </li>
 * <li><strong>Alias for attribute in meta-annotation</strong>:
 * <ol>
 * <li>The attribute that is an alias for an attribute in a meta-annotation
 * must be annotated with {@code @AliasFor}; the {@link #attribute} must
 * reference the aliased attribute in the meta-annotation; and the
 * {@link #annotation} must reference the meta-annotation.</li>
 * <li>Aliased attributes must declare the same return type.</li>
 * </ol>
 * </li>
 * </ul>
 *
 * <h3>Example: Aliases within an Annotation</h3>
 * <pre class="code"> public &#064;interface ContextConfiguration {
 *
 *    &#064;AliasFor(attribute = "locations")
 *    String[] value() default {};
 *
 *    &#064;AliasFor(attribute = "value")
 *    String[] locations() default {};
 *
 *    // ...
 * }</pre>
 *
 * <h3>Example: Alias for Attribute in Meta-annotation</h3>
 * <pre class="code"> &#064;ContextConfiguration
 * public &#064;interface MyTestConfig {
 *
 *    &#064;AliasFor(annotation = ContextConfiguration.class, attribute = "locations")
 *    String[] xmlFiles();
 * }</pre>
 *
 * <h3>Spring Annotations Supporting Attribute Aliases</h3>
 * <p>As of Spring Framework 4.2, several annotations within core Spring
 * have been updated to use {@code @AliasFor} to configure their internal
 * attribute aliases. Consult the Javadoc for individual annotations as well
 * as the reference manual for details.
 *
 * @author Sam Brannen
 * @since 4.2
 * @see AnnotatedElementUtils
 * @see AnnotationUtils
 * @see AnnotationUtils#synthesizeAnnotation(Annotation, java.lang.reflect.AnnotatedElement)
 * @see SynthesizedAnnotation
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface AliasFor {

	/**
	 * The name of the attribute that <em>this</em> attribute is an alias for.
	 */
	String attribute();

	/**
	 * The type of annotation in which the aliased {@link #attribute} is declared.
	 * <p>Defaults to {@link Annotation}, implying that the aliased attribute is
	 * declared in the same annotation as <em>this</em> attribute.
	 */
	Class<? extends Annotation> annotation() default Annotation.class;

}
