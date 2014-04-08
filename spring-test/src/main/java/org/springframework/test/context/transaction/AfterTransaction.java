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

package org.springframework.test.context.transaction;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * <p>Test annotation to indicate that the annotated {@code public void} method
 * should be executed <em>after</em> a transaction is ended for a test method
 * configured to run within a transaction via the {@code @Transactional} annotation.
 *
 * <p>The {@code @AfterTransaction} methods of superclasses will be executed
 * after those of the current class.
 *
 * <p>As of Spring Framework 4.0, this annotation may be used as a
 * <em>meta-annotation</em> to create custom <em>composed annotations</em>.
 *
 * @author Sam Brannen
 * @since 2.5
 * @see org.springframework.transaction.annotation.Transactional
 * @see BeforeTransaction
 */
@Documented
@Retention(RUNTIME)
@Target({ METHOD, ANNOTATION_TYPE })
public @interface AfterTransaction {
}
