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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.init.ScriptUtils.ScriptStatementExecutor;

/**
 * Populates a database from SQL scripts defined in external resources.
 *
 * <p>Call {@link #addScript(Resource)} to add a SQL script location.
 * Call {@link #setSqlScriptEncoding(String)} to set the encoding for all added scripts.
 *
 * @author Keith Donald
 * @author Dave Syer
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Oliver Gierke
 * @author Sam Brannen
 * @author Chris Baldwin
 * @since 3.0
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
	 * Add a script to execute to populate the database.
	 * @param script the path to a SQL script
	 */
	public void addScript(Resource script) {
		this.scripts.add(script);
	}

	/**
	 * Set the scripts to execute to populate the database.
	 * @param scripts the scripts to execute
	 */
	public void setScripts(Resource... scripts) {
		this.scripts = Arrays.asList(scripts);
	}

	/**
	 * Specify the encoding for SQL scripts, if different from the platform encoding.
	 * Note setting this property has no effect on added scripts that are already
	 * {@link EncodedResource encoded resources}.
	 * @see #addScript(Resource)
	 */
	public void setSqlScriptEncoding(String sqlScriptEncoding) {
		this.sqlScriptEncoding = sqlScriptEncoding;
	}

	/**
	 * Specify the statement separator, if a custom one. Default is ";".
	 */
	public void setSeparator(String separator) {
		this.separator = separator;
	}

	/**
	 * Set the line prefix that identifies comments in the SQL script.
	 * Default is "--".
	 */
	public void setCommentPrefix(String commentPrefix) {
		this.commentPrefix = commentPrefix;
	}

	/**
	 * Set the block comment start delimiter in the SQL script.
	 * Default is "/*"
	 * @since 4.0.3
	 */
	public void setBlockCommentStartDelimiter(String blockCommentStartDelimiter) {
		this.blockCommentStartDelimiter = blockCommentStartDelimiter;		
	}
	
	/**
	 * Set the block comment end delimiter in the SQL script.
	 * Default is "*\/"
	 * @since 4.0.3
	 */
	public void setBlockCommentEndDelimiter(String blockCommentEndDelimiter) {
		this.blockCommentEndDelimiter = blockCommentEndDelimiter;
	}
	
	/**
	 * Flag to indicate that all failures in SQL should be logged but not cause a failure.
	 * Defaults to false.
	 */
	public void setContinueOnError(boolean continueOnError) {
		this.continueOnError = continueOnError;
	}

	/**
	 * Flag to indicate that a failed SQL {@code DROP} statement can be ignored.
	 * <p>This is useful for non-embedded databases whose SQL dialect does not support an
	 * {@code IF EXISTS} clause in a {@code DROP}. The default is false so that if the
	 * populator runs accidentally, it will fail fast when the script starts with a {@code DROP}.
	 */
	public void setIgnoreFailedDrops(boolean ignoreFailedDrops) {
		this.ignoreFailedDrops = ignoreFailedDrops;
	}


	@Override
	public void populate(Connection connection) throws SQLException {
		Statement statement = null;
		try {
			statement = connection.createStatement();
			final Statement stmt = statement; 
			for (Resource script : this.scripts) {
				ScriptUtils.executeSqlScript(
						new ScriptStatementExecutor() {
							
							@Override
							public int executeScriptStatement(String statement) throws DataAccessException {
								try {
									stmt.execute(statement);
									return stmt.getUpdateCount();
								}
								catch (SQLException e) {
									throw new UncategorizedSQLException(getClass().getName(), statement, e);
								}
							}
						}, 
						applyEncodingIfNecessary(script), this.continueOnError, this.ignoreFailedDrops, 
						this.commentPrefix, this.separator,	this.blockCommentStartDelimiter, 
						this.blockCommentEndDelimiter);
			}
		}
		finally {
			if (statement != null) {
				statement.close();
			}
		}
	}

	private EncodedResource applyEncodingIfNecessary(Resource script) {
		if (script instanceof EncodedResource) {
			return (EncodedResource) script;
		}
		else {
			return new EncodedResource(script, this.sqlScriptEncoding);
		}
	}
}
