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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.test.generator.file.ResourceFile;
import org.springframework.aot.test.generator.file.ResourceFiles;
import org.springframework.aot.test.generator.file.SourceFile;
import org.springframework.aot.test.generator.file.SourceFiles;
import org.springframework.aot.test.generator.file.WritableContent;
import org.springframework.lang.Nullable;

/**
 * Utility that can be used to dynamically compile and test Java source code.
 *
 * @author Phillip Webb
 * @since 6.0
 * @see #forSystem()
 */
public final class TestCompiler {

	@Nullable
	private final ClassLoader classLoader;

	private final JavaCompiler compiler;

	private final SourceFiles sourceFiles;

	private final ResourceFiles resourceFiles;


	private TestCompiler(@Nullable ClassLoader classLoader, JavaCompiler compiler,
			SourceFiles sourceFiles, ResourceFiles resourceFiles) {
		this.classLoader = classLoader;
		this.compiler = compiler;
		this.sourceFiles = sourceFiles;
		this.resourceFiles = resourceFiles;
	}


	/**
	 * Return a new {@link TestCompiler} backed by the system java compiler.
	 * @return a new {@link TestCompiler} instance
	 */
	public static TestCompiler forSystem() {
		return forCompiler(ToolProvider.getSystemJavaCompiler());
	}

	/**
	 * Return a new {@link TestCompiler} backed by the given
	 * {@link JavaCompiler}.
	 * @param javaCompiler the java compiler to use
	 * @return a new {@link TestCompiler} instance
	 */
	public static TestCompiler forCompiler(JavaCompiler javaCompiler) {
		return new TestCompiler(null, javaCompiler, SourceFiles.none(),
				ResourceFiles.none());
	}

	/**
	 * Return a new {@link TestCompiler} instance with additional generated
	 * source and resource files.
	 * @param generatedFiles the generated files to add
	 * @return a new {@link TestCompiler} instance
	 */
	public TestCompiler withFiles(InMemoryGeneratedFiles generatedFiles) {
		List<SourceFile> sourceFiles = new ArrayList<>();
		generatedFiles.getGeneratedFiles(Kind.SOURCE).forEach((path,
				inputStreamSource) -> sourceFiles.add(SourceFile.of(inputStreamSource)));
		List<ResourceFile> resourceFiles = new ArrayList<>();
		generatedFiles.getGeneratedFiles(Kind.RESOURCE)
				.forEach((path, inputStreamSource) -> resourceFiles
						.add(ResourceFile.of(path, inputStreamSource)));
		return withSources(sourceFiles).withResources(resourceFiles);
	}

	/**
	 * Return a new {@link TestCompiler} instance with addition source files.
	 * @param sourceFiles the additional source files
	 * @return a new {@link TestCompiler} instance
	 */
	public TestCompiler withSources(SourceFile... sourceFiles) {
		return new TestCompiler(this.classLoader, this.compiler,
				this.sourceFiles.and(sourceFiles), this.resourceFiles);
	}

	/**
	 * Return a new {@link TestCompiler} instance with addition source files.
	 * @param sourceFiles the additional source files
	 * @return a new {@link TestCompiler} instance
	 */
	public TestCompiler withSources(Iterable<SourceFile> sourceFiles) {
		return new TestCompiler(this.classLoader, this.compiler,
				this.sourceFiles.and(sourceFiles), this.resourceFiles);
	}

	/**
	 * Return a new {@link TestCompiler} instance with addition source files.
	 * @param sourceFiles the additional source files
	 * @return a new {@link TestCompiler} instance
	 */
	public TestCompiler withSources(SourceFiles sourceFiles) {
		return new TestCompiler(this.classLoader, this.compiler,
				this.sourceFiles.and(sourceFiles), this.resourceFiles);
	}

	/**
	 * Return a new {@link TestCompiler} instance with addition resource files.
	 * @param resourceFiles the additional resource files
	 * @return a new {@link TestCompiler} instance
	 */
	public TestCompiler withResources(ResourceFile... resourceFiles) {
		return new TestCompiler(this.classLoader, this.compiler, this.sourceFiles,
				this.resourceFiles.and(resourceFiles));
	}

	/**
	 * Return a new {@link TestCompiler} instance with addition source files.
	 * @param resourceFiles the additional source files
	 * @return a new {@link TestCompiler} instance
	 */
	public TestCompiler withResources(Iterable<ResourceFile> resourceFiles) {
		return new TestCompiler(this.classLoader, this.compiler, this.sourceFiles,
				this.resourceFiles.and(resourceFiles));
	}

	/**
	 * Return a new {@link TestCompiler} instance with addition resource files.
	 * @param resourceFiles the additional resource files
	 * @return a new {@link TestCompiler} instance
	 */
	public TestCompiler withResources(ResourceFiles resourceFiles) {
		return new TestCompiler(this.classLoader, this.compiler, this.sourceFiles,
				this.resourceFiles.and(resourceFiles));
	}


	/**
	 * Compile content from this instance along with the additional provided
	 * content.
	 * @param content the additional content to compile
	 * @param compiled a consumed used to further assert the compiled code
	 * @throws CompilationException if source cannot be compiled
	 */
	public void compile(WritableContent content, Consumer<Compiled> compiled) {
		compile(SourceFile.of(content), compiled);
	}

	/**
	 * Compile content from this instance along with the additional provided
	 * source file.
	 * @param sourceFile the additional source file to compile
	 * @param compiled a consumed used to further assert the compiled code
	 * @throws CompilationException if source cannot be compiled
	 */
	public void compile(SourceFile sourceFile, Consumer<Compiled> compiled) {
		withSources(sourceFile).compile(compiled);
	}

	/**
	 * Compile content from this instance along with the additional provided
	 * source files.
	 * @param sourceFiles the additional source files to compile
	 * @param compiled a consumed used to further assert the compiled code
	 * @throws CompilationException if source cannot be compiled
	 */
	public void compile(SourceFiles sourceFiles, Consumer<Compiled> compiled) {
		withSources(sourceFiles).compile(compiled);
	}

	/**
	 * Compile content from this instance along with the additional provided
	 * source and resource files.
	 * @param sourceFiles the additional source files to compile
	 * @param resourceFiles the additional resource files to include
	 * @param compiled a consumed used to further assert the compiled code
	 * @throws CompilationException if source cannot be compiled
	 */
	public void compile(SourceFiles sourceFiles, ResourceFiles resourceFiles,
			Consumer<Compiled> compiled) {
		withSources(sourceFiles).withResources(resourceFiles).compile(compiled);
	}

	/**
	 * Compile content from this instance.
	 * @param compiled a consumed used to further assert the compiled code
	 * @throws CompilationException if source cannot be compiled
	 */
	public void compile(Consumer<Compiled> compiled) throws CompilationException {
		DynamicClassLoader dynamicClassLoader = compile();
		ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(dynamicClassLoader);
			compiled.accept(new Compiled(dynamicClassLoader, this.sourceFiles, this.resourceFiles));
		}
		catch (IllegalAccessError ex) {
			throw new IllegalAccessError(ex.getMessage() + ". " +
					"For non-public access ensure annotate your tests with @CompileWithTargetClassAccess");
		}
		finally {
			Thread.currentThread().setContextClassLoader(previousClassLoader);
		}
	}

	private DynamicClassLoader compile() {
		ClassLoader classLoaderToUse = (this.classLoader != null) ? this.classLoader
				: Thread.currentThread().getContextClassLoader();
		List<DynamicJavaFileObject> compilationUnits = this.sourceFiles.stream().map(
				DynamicJavaFileObject::new).toList();
		StandardJavaFileManager standardFileManager = this.compiler.getStandardFileManager(
				null, null, null);
		DynamicJavaFileManager fileManager = new DynamicJavaFileManager(
				standardFileManager, classLoaderToUse);
		if (!this.sourceFiles.isEmpty()) {
			Errors errors = new Errors();
			CompilationTask task = this.compiler.getTask(null, fileManager, errors, null,
					null, compilationUnits);
			boolean result = task.call();
			if (!result || errors.hasReportedErrors()) {
				throw new CompilationException(errors.toString(), this.sourceFiles, this.resourceFiles);
			}
		}
		return new DynamicClassLoader(classLoaderToUse, this.resourceFiles, fileManager.getClassFiles());
	}

	/**
	 * Print the contents of the source and resource files to the specified
	 * {@link PrintStream}.
	 * @param printStream the destination print stream
	 * @return this instance
	 */
	public TestCompiler printFiles(PrintStream printStream) {
		for (SourceFile sourceFile : this.sourceFiles) {
			printStream.append("---- source:   " + sourceFile.getPath() + "\n\n");
			printStream.append(sourceFile.getContent());
			printStream.append("\n\n");
		}
		for (ResourceFile resourceFile : this.resourceFiles) {
			printStream.append("---- resource: " + resourceFile.getPath() + "\n\n");
			printStream.append(resourceFile.getContent());
			printStream.append("\n\n");
		}
		return this;
	}


	/**
	 * {@link DiagnosticListener} used to collect errors.
	 */
	static class Errors implements DiagnosticListener<JavaFileObject> {

		private final StringBuilder message = new StringBuilder();

		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
				this.message.append("\n");
				this.message.append(diagnostic.getMessage(Locale.getDefault()));
				this.message.append(" ");
				this.message.append(diagnostic.getSource().getName());
				this.message.append(" ");
				this.message.append(diagnostic.getLineNumber()).append(":")
						.append(diagnostic.getColumnNumber());
			}
		}

		boolean hasReportedErrors() {
			return this.message.length() > 0;
		}

		@Override
		public String toString() {
			return this.message.toString();
		}

	}

}
