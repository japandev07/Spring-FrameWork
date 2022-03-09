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

package org.springframework.aot.test.generator.file;

import org.assertj.core.api.AssertProvider;

/**
 * {@link DynamicFile} that holds resource file content and provides
 * {@link ResourceFileAssert} support.
 *
 * @author Phillip Webb
 * @since 6.0
 */
public final class ResourceFile extends DynamicFile
		implements AssertProvider<ResourceFileAssert> {


	private ResourceFile(String path, String content) {
		super(path, content);
	}


	/**
	 * Factory method to create a new {@link ResourceFile} from the given
	 * {@link CharSequence}.
	 * @param path the relative path of the file or {@code null} to have the
	 * path deduced
	 * @param charSequence a file containing the source contents
	 * @return a {@link ResourceFile} instance
	 */
	public static ResourceFile of(String path, CharSequence charSequence) {
		return new ResourceFile(path, charSequence.toString());
	}

	/**
	 * Factory method to create a new {@link SourceFile} from the given
	 * {@link WritableContent}.
	 * @param path the relative path of the file or {@code null} to have the
	 * path deduced
	 * @param writableContent the content to write to the file
	 * @return a {@link SourceFile} instance
	 */
	public static ResourceFile of(String path, WritableContent writableContent) {
		return new ResourceFile(path, toString(writableContent));
	}

	/**
	 * AssertJ {@code assertThat} support.
	 * @deprecated use {@code assertThat(sourceFile)} rather than calling this
	 * method directly.
	 */
	@Override
	@Deprecated
	public ResourceFileAssert assertThat() {
		return new ResourceFileAssert(this);
	}

}
