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

package org.springframework.aot.nativex.feature;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.hosted.Feature;

/**
 * GraalVM {@link Feature} that substitutes boolean field values that match a certain pattern
 * with values pre-computed AOT without causing class build-time initialization.
 *
 * @author Sebastien Deleuze
 * @author Phillip Webb
 * @since 6.0
 */
class PreComputeFieldFeature implements Feature {

	private static Pattern[] patterns = {
			Pattern.compile(Pattern.quote("org.springframework.core.NativeDetector#imageCode")),
			Pattern.compile(Pattern.quote("org.springframework.") + ".*#.*Present"),
			Pattern.compile(Pattern.quote("org.springframework.") + ".*#.*PRESENT"),
			Pattern.compile(Pattern.quote("reactor.") + ".*#.*Available")
	};

	private final ThrowawayClassLoader throwawayClassLoader = new ThrowawayClassLoader(PreComputeFieldFeature.class.getClassLoader());

	@Override
	public void beforeAnalysis(BeforeAnalysisAccess access) {
		access.registerSubtypeReachabilityHandler(this::iterateFields, Object.class);
	}

	/* This method is invoked for every type that is reachable. */
	private void iterateFields(DuringAnalysisAccess access, Class<?> subtype) {
		try {
			for (Field field : subtype.getDeclaredFields()) {
				int modifiers = field.getModifiers();
				if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) || field.isEnumConstant() ||
						(field.getType() != boolean.class && field.getType() != Boolean.class)) {
					continue;
				}
				String fieldIdentifier = field.getDeclaringClass().getName() + "#" + field.getName();
				for (Pattern pattern : patterns) {
					if (pattern.matcher(fieldIdentifier).matches()) {
						try {
							Object fieldValue = provideFieldValue(field);
							access.registerFieldValueTransformer(field, (receiver, originalValue) -> fieldValue);
							System.out.println("Field " + fieldIdentifier + " set to " + fieldValue + " at build time");
						}
						catch (Throwable ex) {
							System.out.println("Field " + fieldIdentifier + " will be evaluated at runtime due to this error during build time evaluation: " + ex.getMessage());
						}
					}
				}
			}
		}
		catch (NoClassDefFoundError ex) {
			// Skip classes that have not all their field types in the classpath
		}
	}

	/* This method is invoked when the field value is written to the image heap or the field is constant folded. */
	private Object provideFieldValue(Field field) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
		Class<?> throwawayClass = this.throwawayClassLoader.loadClass(field.getDeclaringClass().getName());
		Field throwawayField = throwawayClass.getDeclaredField(field.getName());
		throwawayField.setAccessible(true);
		return throwawayField.get(null);
	}

}
