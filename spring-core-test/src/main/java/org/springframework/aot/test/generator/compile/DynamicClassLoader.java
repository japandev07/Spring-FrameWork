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

package org.springframework.aot.test.generator.compile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;

import org.springframework.aot.test.generator.file.ResourceFile;
import org.springframework.aot.test.generator.file.ResourceFiles;
import org.springframework.aot.test.generator.file.SourceFile;
import org.springframework.aot.test.generator.file.SourceFiles;
import org.springframework.lang.Nullable;

/**
 * {@link ClassLoader} used to expose dynamically generated content.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @since 6.0
 */
public class DynamicClassLoader extends ClassLoader {

	private static final Logger logger = System.getLogger(DynamicClassLoader.class.getName());


	private final SourceFiles sourceFiles;

	private final ResourceFiles resourceFiles;

	private final Map<String, DynamicClassFileObject> classFiles;

	private final ClassLoader sourceLoader;


	public DynamicClassLoader(ClassLoader sourceLoader, SourceFiles sourceFiles,
			ResourceFiles resourceFiles, Map<String, DynamicClassFileObject> classFiles) {
		super(sourceLoader.getParent());
		this.sourceLoader = sourceLoader;
		this.sourceFiles = sourceFiles;
		this.resourceFiles = resourceFiles;
		this.classFiles = classFiles;
	}


	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		DynamicClassFileObject classFile = this.classFiles.get(name);
		if (classFile != null) {
			return defineClass(name, classFile);
		}
		try {
			Class<?> fromSourceLoader = this.sourceLoader.loadClass(name);
			if (Modifier.isPublic(fromSourceLoader.getModifiers())) {
				return fromSourceLoader;
			}
		}
		catch (Exception ex) {
			// Continue
		}
		try (InputStream classStream = this.sourceLoader.getResourceAsStream(name.replace(".", "/") + ".class")) {
			byte[] bytes = classStream.readAllBytes();
			return defineClass(name, bytes, 0, bytes.length, null);
		}
		catch (IOException ex) {
			throw new ClassNotFoundException(name);
		}
	}

	private Class<?> defineClass(String name, DynamicClassFileObject classFile) {
		byte[] bytes = classFile.getBytes();
		SourceFile sourceFile = this.sourceFiles.get(name);
		if (sourceFile != null && sourceFile.getTarget() != null) {
			try {
				Lookup lookup = MethodHandles.privateLookupIn(sourceFile.getTarget(),
						MethodHandles.lookup());
				return lookup.defineClass(bytes);
			}
			catch (IllegalAccessException ex) {
				logger.log(Level.WARNING,
						"Unable to define class using MethodHandles Lookup, "
								+ "only public methods and classes will be accessible");
			}
		}
		return defineClass(name, bytes, 0, bytes.length, null);
	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		URL resource = findResource(name);
		if (resource != null) {
			return new SingletonEnumeration<>(resource);
		}
		return super.findResources(name);
	}

	@Override
	@Nullable
	protected URL findResource(String name) {
		ResourceFile file = this.resourceFiles.get(name);
		if (file != null) {
			try {
				return new URL(null, "resource:///" + file.getPath(),
						new ResourceFileHandler(file));
			}
			catch (MalformedURLException ex) {
				throw new IllegalStateException(ex);
			}
		}
		return super.findResource(name);
	}


	private static class SingletonEnumeration<E> implements Enumeration<E> {

		@Nullable
		private E element;


		SingletonEnumeration(@Nullable E element) {
			this.element = element;
		}


		@Override
		public boolean hasMoreElements() {
			return this.element != null;
		}

		@Override
		@Nullable
		public E nextElement() {
			E next = this.element;
			this.element = null;
			return next;
		}

	}


	private static class ResourceFileHandler extends URLStreamHandler {

		private final ResourceFile file;


		ResourceFileHandler(ResourceFile file) {
			this.file = file;
		}


		@Override
		protected URLConnection openConnection(URL url) throws IOException {
			return new ResourceFileConnection(url, this.file);
		}

	}


	private static class ResourceFileConnection extends URLConnection {

		private final ResourceFile file;


		protected ResourceFileConnection(URL url, ResourceFile file) {
			super(url);
			this.file = file;
		}


		@Override
		public void connect() throws IOException {
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return new ByteArrayInputStream(
					this.file.getContent().getBytes(StandardCharsets.UTF_8));

		}

	}

}
