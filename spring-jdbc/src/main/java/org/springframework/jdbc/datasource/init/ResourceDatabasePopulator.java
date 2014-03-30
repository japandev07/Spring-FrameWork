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

package org.springframework.jdbc.datasource.init;


import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;

/**
 * Populates, initializes, or cleans up a database using SQL scripts defined in
 * external resources.
 *
 * <ul>
 * <li>Call {@link #addScript} to add a single SQL script location.
 * <li>Call {@link #addScripts} to add multiple SQL script locations.
 * <li>Consult the setter methods in this class for further configuration options.
 * <li>Call {@link #populate} or {@link #execute} to initialize or clean up the
 * database using the configured scripts.
 * </ul>
 *
 * @author Keith Donald
 * @author Dave Syer
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Oliver Gierke
 * @author Sam Brannen
 * @author Chris Baldwin
 * @since 3.0
 * @see DatabasePopulatorUtils
 */
public class ResourceDatabasePopulator implements DatabasePopulator {

	private List<Resource> scripts = new ArrayList<Resource>();

	private String sqlScriptEncoding;

	private String separator = ScriptUtils.DEFAULT_STATEMENT_SEPARATOR;

	private String commentPrefix = ScriptUtils.DEFAULT_COMMENT_PREFIX;

	private String blockCommentStartDelimiter = ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER;

	private String blockCommentEndDelimiter = ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER;

	private boolean continueOnError = false;

	private boolean ignoreFailedDrops = false;


	/**
	 * Construct a new {@code ResourceDatabasePopulator} with default settings.
	 * @since 4.0.3
	 */
	public ResourceDatabasePopulator() {
		/* no-op */
	}

	/**
	 * Construct a new {@code ResourceDatabasePopulator} with default settings
	 * for the supplied scripts.
	 * @param scripts the scripts to execute to initialize or populate the database
	 * @since 4.0.3
	 */
	public ResourceDatabasePopulator(Resource... scripts) {
		this();
		this.scripts = Arrays.asList(scripts);
	}

	/**
	 * Construct a new {@code ResourceDatabasePopulator} with the supplied values.
	 * @param continueOnError flag to indicate that all failures in SQL should be
	 * logged but not cause a failure
	 * @param ignoreFailedDrops flag to indicate that a failed SQL {@code DROP}
	 * statement can be ignored
	 * @param sqlScriptEncoding the encoding for the supplied SQL scripts, if
	 * different from the platform encoding; may be {@code null}
	 * @param scripts the scripts to execute to initialize or populate the database
	 * @since 4.0.3
	 */
	public ResourceDatabasePopulator(boolean continueOnError, boolean ignoreFailedDrops, String sqlScriptEncoding,
			Resource... scripts) {
		this(scripts);
		this.continueOnError = continueOnError;
		this.ignoreFailedDrops = ignoreFailedDrops;
		this.sqlScriptEncoding = sqlScriptEncoding;
	}

	/**
	 * Add a script to execute to initialize or populate the database.
	 * @param script the path to an SQL script
	 */
	public void addScript(Resource script) {
		this.scripts.add(script);
	}

	/**
	 * Add multiple scripts to execute to initialize or populate the database.
	 * @param scripts the scripts to execute
	 */
	public void addScripts(Resource... scripts) {
		this.scripts.addAll(Arrays.asList(scripts));
	}

	/**
	 * Set the scripts to execute to initialize or populate the database,
	 * replacing any previously added scripts.
	 * @param scripts the scripts to execute
	 */
	public void setScripts(Resource... scripts) {
		this.scripts = Arrays.asList(scripts);
	}

	/**
	 * Specify the encoding for SQL scripts, if different from the platform encoding.
	 * @param sqlScriptEncoding the encoding used in scripts
	 * @see #addScript(Resource)
	 */
	public void setSqlScriptEncoding(String sqlScriptEncoding) {
		this.sqlScriptEncoding = sqlScriptEncoding;
	}

	/**
	 * Specify the statement separator, if a custom one.
	 * <p>Default is ";".
	 * @param separator the statement separator
	 */
	public void setSeparator(String separator) {
		this.separator = separator;
	}

	/**
	 * Set the prefix that identifies line comments within the SQL scripts.
	 * <p>Default is "--".
	 * @param commentPrefix the prefix for single-line comments
	 */
	public void setCommentPrefix(String commentPrefix) {
		this.commentPrefix = commentPrefix;
	}

	/**
	 * Set the start delimiter that identifies block comments within the SQL
	 * scripts.
	 * <p>Default is "/*".
	 * @param blockCommentStartDelimiter the start delimiter for block comments
	 * @since 4.0.3
	 * @see #setBlockCommentEndDelimiter
	 */
	public void setBlockCommentStartDelimiter(String blockCommentStartDelimiter) {
		this.blockCommentStartDelimiter = blockCommentStartDelimiter;
	}

	/**
	 * Set the end delimiter that identifies block comments within the SQL
	 * scripts.
	 * <p>Default is "*&#47;".
	 * @param blockCommentEndDelimiter the end delimiter for block comments
	 * @since 4.0.3
	 * @see #setBlockCommentStartDelimiter
	 */
	public void setBlockCommentEndDelimiter(String blockCommentEndDelimiter) {
		this.blockCommentEndDelimiter = blockCommentEndDelimiter;
	}

	/**
	 * Flag to indicate that all failures in SQL should be logged but not cause a failure.
	 * <p>Defaults to {@code false}.
	 * @param continueOnError {@code true} if script execution should continue on error
	 */
	public void setContinueOnError(boolean continueOnError) {
		this.continueOnError = continueOnError;
	}

	/**
	 * Flag to indicate that a failed SQL {@code DROP} statement can be ignored.
	 * <p>This is useful for non-embedded databases whose SQL dialect does not support an
	 * {@code IF EXISTS} clause in a {@code DROP} statement.
	 * <p>The default is {@code false} so that if the populator runs accidentally, it will
	 * fail fast if the script starts with a {@code DROP} statement.
	 * @param ignoreFailedDrops {@code true} if failed drop statements should be ignored
	 */
	public void setIgnoreFailedDrops(boolean ignoreFailedDrops) {
		this.ignoreFailedDrops = ignoreFailedDrops;
	}

	/**
	 * {@inheritDoc}
	 * @see #execute(DataSource)
	 */
	@Override
	public void populate(Connection connection) throws ScriptException {
		for (Resource script : this.scripts) {
			ScriptUtils.executeSqlScript(connection, encodeScript(script), this.continueOnError,
				this.ignoreFailedDrops, this.commentPrefix, this.separator, this.blockCommentStartDelimiter,
				this.blockCommentEndDelimiter);
		}
	}

	/**
	 * Execute this {@code DatabasePopulator} against the given {@link DataSource}.
	 * <p>Delegates to {@link DatabasePopulatorUtils#execute}.
	 * @param dataSource the {@code DataSource} to execute against
	 * @throws ScriptException if an error occurs
	 * @since 4.1
	 * @see #populate(Connection)
	 */
	public void execute(DataSource dataSource) throws ScriptException {
		DatabasePopulatorUtils.execute(this, dataSource);
	}

	/**
	 * {@link EncodedResource} is not a sub-type of {@link Resource}. Thus we always need
	 * to wrap each script resource in an encoded resource.
	 */
	private EncodedResource encodeScript(Resource script) {
		return new EncodedResource(script, this.sqlScriptEncoding);
	}

}
