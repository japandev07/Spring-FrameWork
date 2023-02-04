/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.validation.beanvalidation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ConstructorDescriptor;
import jakarta.validation.metadata.MethodDescriptor;
import jakarta.validation.metadata.MethodType;
import jakarta.validation.metadata.ParameterDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * AOT {@code BeanRegistrationAotProcessor} that adds additional hints
 * required for {@link ConstraintValidator}s.
 *
 * @author Sebastien Deleuze
 * @since 6.0.5
 */
class BeanValidationBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

	private static final boolean isBeanValidationPresent = ClassUtils.isPresent(
			"jakarta.validation.Validation", BeanValidationBeanRegistrationAotProcessor.class.getClassLoader());

	@Nullable
	@Override
	public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
		if (isBeanValidationPresent) {
			return BeanValidationDelegate.processAheadOfTime(registeredBean);
		}
		return null;
	}

	private static class BeanValidationDelegate {

		private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

		@Nullable
		public static BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
			BeanDescriptor descriptor = validator.getConstraintsForClass(registeredBean.getBeanClass());
			Set<ConstraintDescriptor<?>> constraintDescriptors = new HashSet<>();
			for (MethodDescriptor methodDescriptor : descriptor.getConstrainedMethods(MethodType.NON_GETTER, MethodType.GETTER)) {
				for (ParameterDescriptor parameterDescriptor : methodDescriptor.getParameterDescriptors()) {
					constraintDescriptors.addAll(parameterDescriptor.getConstraintDescriptors());
				}
			}
			for (ConstructorDescriptor constructorDescriptor : descriptor.getConstrainedConstructors()) {
				for (ParameterDescriptor parameterDescriptor : constructorDescriptor.getParameterDescriptors()) {
					constraintDescriptors.addAll(parameterDescriptor.getConstraintDescriptors());
				}
			}
			for (PropertyDescriptor propertyDescriptor : descriptor.getConstrainedProperties()) {
				constraintDescriptors.addAll(propertyDescriptor.getConstraintDescriptors());
			}
			if (!constraintDescriptors.isEmpty()) {
				return new BeanValidationBeanRegistrationAotContribution(constraintDescriptors);
			}
			return null;
		}

	}

	private static class BeanValidationBeanRegistrationAotContribution implements BeanRegistrationAotContribution {

		private final Collection<ConstraintDescriptor<?>> constraintDescriptors;

		public BeanValidationBeanRegistrationAotContribution(Collection<ConstraintDescriptor<?>> constraintDescriptors) {
			this.constraintDescriptors = constraintDescriptors;
		}

		@Override
		public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
			for (ConstraintDescriptor<?> constraintDescriptor : this.constraintDescriptors) {
				for (Class<?> constraintValidatorClass : constraintDescriptor.getConstraintValidatorClasses()) {
					generationContext.getRuntimeHints().reflection().registerType(constraintValidatorClass,
							MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
				}
			}
		}
	}

}
