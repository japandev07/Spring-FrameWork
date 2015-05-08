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

package org.springframework.test.context.junit4.rules;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit4.statements.ProfileValueChecker;
import org.springframework.test.context.junit4.statements.RunAfterTestClassCallbacks;
import org.springframework.test.context.junit4.statements.RunBeforeTestClassCallbacks;

/**
 * {@code SpringClassRule} is a custom JUnit {@link TestRule} that provides
 * <em>class-level</em> functionality of the <em>Spring TestContext Framework</em>
 * to standard JUnit tests by means of the {@link TestContextManager} and associated
 * support classes and annotations.
 *
 * <p>In contrast to the {@link org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * SpringJUnit4ClassRunner}, Spring's rule-based JUnit support has the advantage
 * that it is independent of any {@link org.junit.runner.Runner Runner} and
 * can therefore be combined with existing alternative runners like JUnit's
 * {@code Parameterized} or third-party runners such as the {@code MockitoJUnitRunner}.
 *
 * <p>In order to achieve the same functionality as the {@code SpringJUnit4ClassRunner},
 * however, a {@code SpringClassRule} must be combined with a {@link SpringMethodRule},
 * since {@code SpringClassRule} only provides the class-level features of the
 * {@code SpringJUnit4ClassRunner}.
 *
 * <h3>Example Usage</h3>
 * <pre><code> public class ExampleSpringIntegrationTest {
 *
 *    &#064;ClassRule
 *    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();
 *
 *    &#064;Rule
 *    public final SpringMethodRule springMethodRule = new SpringMethodRule(this);
 *
 *    // ...
 * }</code></pre>
 *
 * <p>The following list constitutes all annotations currently supported directly
 * or indirectly by {@code SpringClassRule}. <em>(Note that additional annotations
 * may be supported by various
 * {@link org.springframework.test.context.TestExecutionListener TestExecutionListener} or
 * {@link org.springframework.test.context.TestContextBootstrapper TestContextBootstrapper}
 * implementations.)</em>
 *
 * <ul>
 * <li>{@link org.springframework.test.annotation.ProfileValueSourceConfiguration @ProfileValueSourceConfiguration}</li>
 * <li>{@link org.springframework.test.annotation.IfProfileValue @IfProfileValue}</li>
 * </ul>
 *
 * <p><strong>NOTE:</strong> This class requires JUnit 4.9 or higher.
 *
 * @author Sam Brannen
 * @author Philippe Marschall
 * @since 4.2
 * @see #apply(Statement, Description)
 * @see SpringMethodRule
 * @see org.springframework.test.context.TestContextManager
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 */
public class SpringClassRule implements TestRule {

	private static final Log logger = LogFactory.getLog(SpringClassRule.class);

	/**
	 * This field is {@code volatile} since a {@code SpringMethodRule} can
	 * potentially access it from a different thread, depending on the type
	 * of JUnit runner in use.
	 */
	private volatile TestContextManager testContextManager;


	/**
	 * Create a new {@link TestContextManager} for the supplied test class.
	 * <p>Can be overridden by subclasses.
	 * @param clazz the test class to be managed
	 */
	protected TestContextManager createTestContextManager(Class<?> clazz) {
		return new TestContextManager(clazz);
	}

	/**
	 * Get the {@link TestContextManager} associated with this rule.
	 * <p>Will be {@code null} until the {@link #apply} method is invoked
	 * by a JUnit runner.
	 */
	protected final TestContextManager getTestContextManager() {
		return this.testContextManager;
	}

	/**
	 * Apply <em>class-level</em> functionality of the <em>Spring TestContext
	 * Framework</em> to the supplied {@code base} statement.
	 *
	 * <p>Specifically, this method creates the {@link TestContextManager} used
	 * by this rule and its associated {@link SpringMethodRule} and invokes the
	 * {@link TestContextManager#beforeTestClass() beforeTestClass()} and
	 * {@link TestContextManager#afterTestClass() afterTestClass()} methods
	 * on the {@code TestContextManager}.
	 *
	 * <p>In addition, this method checks whether the test is enabled in
	 * the current execution environment. This prevents classes with a
	 * non-matching {@code @IfProfileValue} annotation from running altogether,
	 * even skipping the execution of {@code beforeTestClass()} methods
	 * in {@code TestExecutionListeners}.
	 *
	 * @param base the base {@code Statement} that this rule should be applied to
	 * @param description a {@code Description} of the current test execution
	 * @return a statement that wraps the supplied {@code base} with class-level
	 * functionality of the Spring TestContext Framework
	 * @see #createTestContextManager
	 * @see #withBeforeTestClassCallbacks
	 * @see #withAfterTestClassCallbacks
	 * @see #withProfileValueCheck
	 */
	@Override
	public Statement apply(final Statement base, final Description description) {
		Class<?> testClass = description.getTestClass();

		if (logger.isDebugEnabled()) {
			logger.debug("Applying SpringClassRule to test class [" + testClass.getName() + "].");
		}

		validateSpringMethodRuleConfiguration(testClass);

		this.testContextManager = createTestContextManager(testClass);

		Statement statement = base;
		statement = withBeforeTestClassCallbacks(statement);
		statement = withAfterTestClassCallbacks(statement);
		statement = withProfileValueCheck(testClass, statement);
		return statement;
	}

	/**
	 * Wrap the supplied {@code statement} with a {@code RunBeforeTestClassCallbacks} statement.
	 * @see RunBeforeTestClassCallbacks
	 */
	protected Statement withBeforeTestClassCallbacks(Statement statement) {
		return new RunBeforeTestClassCallbacks(statement, getTestContextManager());
	}

	/**
	 * Wrap the supplied {@code statement} with a {@code RunAfterTestClassCallbacks} statement.
	 * @see RunAfterTestClassCallbacks
	 */
	protected Statement withAfterTestClassCallbacks(Statement statement) {
		return new RunAfterTestClassCallbacks(statement, getTestContextManager());
	}

	/**
	 * Wrap the supplied {@code statement} with a {@code ProfileValueChecker} statement.
	 * @see ProfileValueChecker
	 */
	protected Statement withProfileValueCheck(Class<?> testClass, Statement statement) {
		return new ProfileValueChecker(statement, testClass, null);
	}

	private void validateSpringMethodRuleConfiguration(Class<?> testClass) {
		Field ruleField = null;

		for (Field field : testClass.getFields()) {
			int modifiers = field.getModifiers();
			if (!Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
					&& (SpringMethodRule.class.isAssignableFrom(field.getType()))) {
				ruleField = field;
				break;
			}
		}

		if (ruleField == null) {
			throw new IllegalStateException(String.format(
				"Failed to find 'public SpringMethodRule' field in test class [%s]. "
						+ "Consult the Javadoc for SpringClassRule for details.", testClass.getName()));
		}

		if (!ruleField.isAnnotationPresent(Rule.class)) {
			throw new IllegalStateException(String.format(
				"SpringMethodRule field [%s] must be annotated with JUnit's @Rule annotation. "
						+ "Consult the Javadoc for SpringClassRule for details.", ruleField));
		}
	}

}
