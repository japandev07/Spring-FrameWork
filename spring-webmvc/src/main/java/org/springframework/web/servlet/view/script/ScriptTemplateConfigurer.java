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

package org.springframework.web.servlet.view.script;

import java.nio.charset.Charset;

import javax.script.ScriptEngine;

/**
 * An implementation of Spring MVC's {@link ScriptTemplateConfig} for creating
 * a {@code ScriptEngine} for use in a web application.
 *
 * <pre class="code">
 *
 * // Add the following to an &#64;Configuration class
 *
 * &#64;Bean
 * public ScriptTemplateConfigurer mustacheConfigurer() {
 *
 *    ScriptTemplateConfigurer configurer = new ScriptTemplateConfigurer();
 *    configurer.setEngineName("nashorn");
 *    configurer.setScripts("mustache.js");
 *    configurer.setRenderObject("Mustache");
 *    configurer.setRenderFunction("render");
 *    return configurer;
 * }
 * </pre>
 *
 * <p>It is possible to use non thread-safe script engines and templating libraries, like
 * Handlebars or React running on Nashorn, by setting the
 * {@link #setSharedEngine(Boolean) sharedEngine} property to {@code false}.
 *
 * @author Sebastien Deleuze
 * @since 4.2
 * @see ScriptTemplateView
 */
public class ScriptTemplateConfigurer implements ScriptTemplateConfig {

	private ScriptEngine engine;

	private String engineName;

	private String[] scripts;

	private String renderObject;

	private String renderFunction;

	private Charset charset;

	private String resourceLoaderPath;

	private Boolean sharedEngine;

	/**
	 * Set the {@link ScriptEngine} to use by the view.
	 * The script engine must implement {@code Invocable}.
	 * You must define {@code engine} or {@code engineName}, not both.
	 *
	 * <p>When the {@code sharedEngine} flag is set to {@code false}, you should not specify
	 * the script engine with this setter, but with the {@link #setEngineName(String)}
	 * one (since it implies multiple lazy instanciations of the script engine).
	 *
	 * @see #setEngineName(String)
	 */
	public void setEngine(ScriptEngine engine) {
		this.engine = engine;
	}

	@Override
	public ScriptEngine getEngine() {
		return this.engine;
	}

	/**
	 * Set the engine name that will be used to instantiate the {@link ScriptEngine}.
	 * The script engine must implement {@code Invocable}.
	 * You must define {@code engine} or {@code engineName}, not both.
	 * @see #setEngine(ScriptEngine)
	 */
	public void setEngineName(String engineName) {
		this.engineName = engineName;
	}

	@Override
	public String getEngineName() {
		return this.engineName;
	}

	/**
	 * Set the scripts to be loaded by the script engine (library or user provided).
	 * Since {@code resourceLoaderPath} default value is "classpath:", you can load easily
	 * any script available on the classpath.
	 *
	 * For example, in order to use a Javascript library available as a WebJars dependency
	 * and a custom "render.js" file, you should call
	 * {@code configurer.setScripts("/META-INF/resources/webjars/library/version/library.js",
	 * "com/myproject/script/render.js");}.
	 *
	 * @see #setResourceLoaderPath(String)
	 * @see <a href="http://www.webjars.org">WebJars</a>
	 */
	public void setScripts(String... scriptNames) {
		this.scripts = scriptNames;
	}

	@Override
	public String[] getScripts() {
		return this.scripts;
	}

	/**
	 * Set the object where belongs the render function (optional).
	 * For example, in order to call {@code Mustache.render()}, {@code renderObject}
	 * should be set to {@code "Mustache"} and {@code renderFunction} to {@code "render"}.
	 */
	public void setRenderObject(String renderObject) {
		this.renderObject = renderObject;
	}

	@Override
	public String getRenderObject() {
		return this.renderObject;
	}

	/**
	 * Set the render function name (mandatory). This function will be called with the
	 * following parameters:
	 * <ol>
	 *     <li>{@code template}: the view template content (String)</li>
	 *     <li>{@code model}: the view model (Map)</li>
	 * </ol>
	 */
	public void setRenderFunction(String renderFunction) {
		this.renderFunction = renderFunction;
	}

	@Override
	public String getRenderFunction() {
		return this.renderFunction;
	}

	/**
	 * Set the charset used to read script and template files.
	 * ({@code UTF-8} by default).
	 */
	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	@Override
	public Charset getCharset() {
		return this.charset;
	}

	/**
	 * Set the resource loader path(s) via a Spring resource location.
	 * Accepts multiple locations as a comma-separated list of paths.
	 * Standard URLs like "file:" and "classpath:" and pseudo URLs are supported
	 * as understood by Spring's {@link org.springframework.core.io.ResourceLoader}.
	 * Relative paths are allowed when running in an ApplicationContext.
	 * Default is "classpath:".
	 */
	public void setResourceLoaderPath(String resourceLoaderPath) {
		this.resourceLoaderPath = resourceLoaderPath;
	}

	@Override
	public String getResourceLoaderPath() {
		return this.resourceLoaderPath;
	}

	/**
	 * When set to {@code false}, use thread-local {@link ScriptEngine} instances instead
	 * of one single shared instance. This flag should be set to {@code false} for those
	 * using non thread-safe script engines and templating libraries, like Handlebars or
	 * React running on Nashorn for example.
	 *
	 * <p>When this flag is set to {@code false}, the script engine must be specified using
	 * {@link #setEngineName(String)}. Using {@link #setEngine(ScriptEngine)} is not
	 * possible because multiple instances of the script engine need to be created lazily
	 * (one per thread).
	 * @see <a href="http://docs.oracle.com/javase/8/docs/api/javax/script/ScriptEngineFactory.html#getParameter-java.lang.String-">THREADING ScriptEngine parameter<a/>
	 */
	public void setSharedEngine(Boolean sharedEngine) {
		this.sharedEngine = sharedEngine;
	}

	@Override
	public Boolean isShareEngine() {
		return this.sharedEngine;
	}

}
