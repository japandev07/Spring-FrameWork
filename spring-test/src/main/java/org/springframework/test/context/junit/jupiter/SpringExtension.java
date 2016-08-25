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

package org.springframework.test.context.junit.jupiter;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ContainerExecutionCondition;
import org.junit.jupiter.api.extension.ContainerExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestExecutionCondition;
import org.junit.jupiter.api.extension.TestExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContextManager;
import org.springframework.util.Assert;

/**
 * {@code SpringExtension} integrates the <em>Spring TestContext Framework</em>
 * into JUnit 5's <em>Jupiter</em> programming model.
 *
 * <p>To use this class, simply annotate a JUnit Jupiter based test class with
 * {@code @ExtendWith(SpringExtension.class)}.
 *
 * @author Sam Brannen
 * @author Tadaya Tsuyukubo
 * @since 5.0
 * @see org.springframework.test.context.junit.jupiter.SpringJUnitConfig
 * @see org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig
 * @see org.springframework.test.context.TestContextManager
 * @see DisabledIf
 */
public class SpringExtension implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor,
		BeforeEachCallback, AfterEachCallback, BeforeTestExecutionCallback, AfterTestExecutionCallback,
		ParameterResolver,
		ContainerExecutionCondition, TestExecutionCondition {

	/**
	 * {@link Namespace} in which {@code TestContextManagers} are stored, keyed
	 * by test class.
	 */
	private static final Namespace namespace = Namespace.create(SpringExtension.class);

	private static final ConditionEvaluationResult TEST_ENABLED = ConditionEvaluationResult.enabled(
			"@DisabledIf condition didn't match");

	private static final Log logger = LogFactory.getLog(SpringExtension.class);


	/**
	 * Delegates to {@link TestContextManager#beforeTestClass}.
	 */
	@Override
	public void beforeAll(ContainerExtensionContext context) throws Exception {
		getTestContextManager(context).beforeTestClass();
	}

	/**
	 * Delegates to {@link TestContextManager#afterTestClass}.
	 */
	@Override
	public void afterAll(ContainerExtensionContext context) throws Exception {
		try {
			getTestContextManager(context).afterTestClass();
		}
		finally {
			context.getStore(namespace).remove(context.getTestClass().get());
		}
	}

	/**
	 * Delegates to {@link TestContextManager#prepareTestInstance}.
	 */
	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
		getTestContextManager(context).prepareTestInstance(testInstance);
	}

	/**
	 * Delegates to {@link TestContextManager#beforeTestMethod}.
	 */
	@Override
	public void beforeEach(TestExtensionContext context) throws Exception {
		Object testInstance = context.getTestInstance();
		Method testMethod = context.getTestMethod().get();
		getTestContextManager(context).beforeTestMethod(testInstance, testMethod);
	}

	/**
	 * Delegates to {@link TestContextManager#beforeTestExecution}.
	 */
	@Override
	public void beforeTestExecution(TestExtensionContext context) throws Exception {
		Object testInstance = context.getTestInstance();
		Method testMethod = context.getTestMethod().get();
		getTestContextManager(context).beforeTestExecution(testInstance, testMethod);
	}

	/**
	 * Delegates to {@link TestContextManager#afterTestExecution}.
	 */
	@Override
	public void afterTestExecution(TestExtensionContext context) throws Exception {
		Object testInstance = context.getTestInstance();
		Method testMethod = context.getTestMethod().get();
		Throwable testException = context.getTestException().orElse(null);
		getTestContextManager(context).afterTestExecution(testInstance, testMethod, testException);
	}

	/**
	 * Delegates to {@link TestContextManager#afterTestMethod}.
	 */
	@Override
	public void afterEach(TestExtensionContext context) throws Exception {
		Object testInstance = context.getTestInstance();
		Method testMethod = context.getTestMethod().get();
		Throwable testException = context.getTestException().orElse(null);
		getTestContextManager(context).afterTestMethod(testInstance, testMethod, testException);
	}

	/**
	 * Determine if the value for the {@link Parameter} in the supplied
	 * {@link ParameterContext} should be autowired from the test's
	 * {@link ApplicationContext}.
	 * <p>Returns {@code true} if the parameter is declared in a {@link Constructor}
	 * that is annotated with {@link Autowired @Autowired} and otherwise delegates
	 * to {@link ParameterAutowireUtils#isAutowirable}.
	 * <p><strong>WARNING</strong>: if the parameter is declared in a {@code Constructor}
	 * that is annotated with {@code @Autowired}, Spring will assume the responsibility
	 * for resolving all parameters in the constructor. Consequently, no other
	 * registered {@link ParameterResolver} will be able to resolve parameters.
	 *
	 * @see #resolve
	 * @see ParameterAutowireUtils#isAutowirable
	 */
	@Override
	public boolean supports(ParameterContext parameterContext, ExtensionContext extensionContext) {
		Parameter parameter = parameterContext.getParameter();
		Executable executable = parameter.getDeclaringExecutable();
		return (executable instanceof Constructor && AnnotatedElementUtils.hasAnnotation(executable, Autowired.class))
				|| ParameterAutowireUtils.isAutowirable(parameter);
	}

	/**
	 * Resolve a value for the {@link Parameter} in the supplied
	 * {@link ParameterContext} by retrieving the corresponding dependency
	 * from the test's {@link ApplicationContext}.
	 * <p>Delegates to {@link ParameterAutowireUtils#resolveDependency}.
	 * @see #supports
	 * @see ParameterAutowireUtils#resolveDependency
	 */
	@Override
	public Object resolve(ParameterContext parameterContext, ExtensionContext extensionContext) {
		Parameter parameter = parameterContext.getParameter();
		Class<?> testClass = extensionContext.getTestClass().get();
		ApplicationContext applicationContext = getApplicationContext(extensionContext);
		return ParameterAutowireUtils.resolveDependency(parameter, testClass, applicationContext);
	}

	@Override
	public ConditionEvaluationResult evaluate(ContainerExtensionContext context) {
		return evaluateDisabledIf(context);
	}

	@Override
	public ConditionEvaluationResult evaluate(TestExtensionContext context) {
		return evaluateDisabledIf(context);
	}

	private ConditionEvaluationResult evaluateDisabledIf(ExtensionContext extensionContext) {
		Optional<AnnotatedElement> element = extensionContext.getElement();
		if (!element.isPresent()) {
			return TEST_ENABLED;
		}

		DisabledIf disabledIf = AnnotatedElementUtils.findMergedAnnotation(element.get(), DisabledIf.class);
		if (disabledIf == null) {
			return TEST_ENABLED;
		}

		String condition = disabledIf.condition();
		if (condition.trim().length() == 0) {
			return TEST_ENABLED;
		}

		ApplicationContext applicationContext = getApplicationContext(extensionContext);
		if (!(applicationContext instanceof ConfigurableApplicationContext)) {
			return TEST_ENABLED;
		}

		ConfigurableBeanFactory configurableBeanFactory = ((ConfigurableApplicationContext) applicationContext)
				.getBeanFactory();
		BeanExpressionResolver expressionResolver = configurableBeanFactory.getBeanExpressionResolver();
		BeanExpressionContext beanExpressionContext = new BeanExpressionContext(configurableBeanFactory, null);

		Object result = expressionResolver
				.evaluate(configurableBeanFactory.resolveEmbeddedValue(condition), beanExpressionContext);

		if (result == null || !Boolean.valueOf(result.toString())) {
			return TEST_ENABLED;
		}

		String reason = disabledIf.reason();
		if (reason.trim().length() == 0) {
			String testTarget = extensionContext.getTestMethod().map(Method::getName)
					.orElseGet(() -> extensionContext.getTestClass().get().getSimpleName());
			reason = String.format("%s is disabled. condition=%s", testTarget, condition);
		}

		logger.info(String.format("%s is disabled. reason=%s", element.get(), reason));
		return ConditionEvaluationResult.disabled(reason);

	}

	/**
	 * Get the {@link ApplicationContext} associated with the supplied
	 * {@code ExtensionContext}.
	 * @param context the current {@code ExtensionContext}; never {@code null}
	 * @return the application context
	 * @throws IllegalStateException if an error occurs while retrieving the
	 * application context
	 * @see org.springframework.test.context.TestContext#getApplicationContext()
	 */
	private ApplicationContext getApplicationContext(ExtensionContext context) {
		return getTestContextManager(context).getTestContext().getApplicationContext();
	}

	/**
	 * Get the {@link TestContextManager} associated with the supplied
	 * {@code ExtensionContext}.
	 * @return the {@code TestContextManager}; never {@code null}
	 */
	private TestContextManager getTestContextManager(ExtensionContext context) {
		Assert.notNull(context, "ExtensionContext must not be null");
		Class<?> testClass = context.getTestClass().get();
		Store store = context.getStore(namespace);
		return store.getOrComputeIfAbsent(testClass, TestContextManager::new, TestContextManager.class);
	}

}
