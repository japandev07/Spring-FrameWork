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

package org.springframework.aot;

import org.springframework.core.NativeDetector;
import org.springframework.core.SpringProperties;

/**
 * Determine if AOT-processed optimizations must be used rather than the
 * regular runtime. Strictly for internal use within the framework.
 *
 * @author Stephane Nicoll
 * @since 6.0
 */
public abstract class AotDetector {

	/**
	 * System property that indicates the application should run with AOT
	 * generated artifacts. If such optimizations are not available, it is
	 * recommended to throw an exception rather than falling back to the
	 * regular runtime behavior.
	 */
	public static final String AOT_ENABLED = "spring.aot.enabled";

	/**
	 * Return whether AOT optimizations must be considered at runtime. This
	 * is mandatory in a native image but can be triggered on the JVM using
	 * the {@value AOT_ENABLED} spring property.
	 * @return whether AOT optimizations must be considered
	 */
	public static boolean useGeneratedArtifacts() {
		return (NativeDetector.inNativeImage() || SpringProperties.getFlag(AOT_ENABLED));
	}

}
