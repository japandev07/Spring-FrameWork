/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.r2dbc.connection.init;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Generic utility methods for working with SQL scripts.
 * <p>Mainly for internal use within the framework.
 *
 * @author Thomas Risberg
 * @author Sam Brannen
 * @author Juergen Hoeller
 * @author Keith Donald
 * @author Dave Syer
 * @author Chris Beams
 * @author Oliver Gierke
 * @author Chris Baldwin
 * @author Nicolas Debeissat
 * @author Phillip Webb
 * @author Mark Paluch
 * @since 5.3
 */
public abstract class ScriptUtils {

	/**
	 * Default statement separator within SQL scripts: {@code ";"}.
	 */
	public static final String DEFAULT_STATEMENT_SEPARATOR = ";";

	/**
	 * Fallback statement separator within SQL scripts: {@code "\n"}.
	 * <p>Used if neither a custom separator nor the
	 * {@link #DEFAULT_STATEMENT_SEPARATOR} is present in a given script.
	 */
	public static final String FALLBACK_STATEMENT_SEPARATOR = "\n";

	/**
	 * End of file (EOF) SQL statement separator: {@code "^^^ END OF SCRIPT ^^^"}.
	 * <p>This value may be supplied as the {@code separator} to {@link
	 * #executeSqlScript(Connection, EncodedResource, DataBufferFactory, boolean, boolean, String[], String, String, String)}
	 * to denote that an SQL script contains a single statement (potentially
	 * spanning multiple lines) with no explicit statement separator. Note that
	 * such a script should not actually contain this value; it is merely a
	 * <em>virtual</em> statement separator.
	 */
	public static final String EOF_STATEMENT_SEPARATOR = "^^^ END OF SCRIPT ^^^";

	/**
	 * Default prefix for single-line comments within SQL scripts: {@code "--"}.
	 */
	public static final String DEFAULT_COMMENT_PREFIX = "--";

	/**
	 * Default prefixes for single-line comments within SQL scripts: {@code ["--"]}.
	 */
	public static final String[] DEFAULT_COMMENT_PREFIXES = {DEFAULT_COMMENT_PREFIX};

	/**
	 * Default start delimiter for block comments within SQL scripts: {@code "/*"}.
	 */
	public static final String DEFAULT_BLOCK_COMMENT_START_DELIMITER = "/*";

	/**
	 * Default end delimiter for block comments within SQL scripts: <code>"*&#47;"</code>.
	 */
	public static final String DEFAULT_BLOCK_COMMENT_END_DELIMITER = "*/";


	private static final Log logger = LogFactory.getLog(ScriptUtils.class);

	// utility constructor
	private ScriptUtils() {}

	/**
	 * Split an SQL script into separate statements delimited by the provided
	 * separator character. Each individual statement will be added to the
	 * provided {@code List}.
	 * <p>Within the script, {@value #DEFAULT_COMMENT_PREFIX} will be used as the
	 * comment prefix; any text beginning with the comment prefix and extending to
	 * the end of the line will be omitted from the output. Similarly,
	 * {@value #DEFAULT_BLOCK_COMMENT_START_DELIMITER} and
	 * {@value #DEFAULT_BLOCK_COMMENT_END_DELIMITER} will be used as the
	 * <em>start</em> and <em>end</em> block comment delimiters: any text enclosed
	 * in a block comment will be omitted from the output. In addition, multiple
	 * adjacent whitespace characters will be collapsed into a single space.
	 * @param script the SQL script
	 * @param separator character separating each statement (typically a ';')
	 * @param statements the list that will contain the individual statements
	 * @throws ScriptException if an error occurred while splitting the SQL script
	 * @see #splitSqlScript(String, String, List)
	 * @see #splitSqlScript(EncodedResource, String, String, String, String, String, List)
	 */
	public static void splitSqlScript(String script, char separator, List<String> statements) throws ScriptException {
		splitSqlScript(script, String.valueOf(separator), statements);
	}

	/**
	 * Split an SQL script into separate statements delimited by the provided
	 * separator string. Each individual statement will be added to the
	 * provided {@code List}.
	 * <p>Within the script, {@value #DEFAULT_COMMENT_PREFIX} will be used as the
	 * comment prefix; any text beginning with the comment prefix and extending to
	 * the end of the line will be omitted from the output. Similarly,
	 * {@value #DEFAULT_BLOCK_COMMENT_START_DELIMITER} and
	 * {@value #DEFAULT_BLOCK_COMMENT_END_DELIMITER} will be used as the
	 * <em>start</em> and <em>end</em> block comment delimiters: any text enclosed
	 * in a block comment will be omitted from the output. In addition, multiple
	 * adjacent whitespace characters will be collapsed into a single space.
	 * @param script the SQL script
	 * @param separator text separating each statement
	 * (typically a ';' or newline character)
	 * @param statements the list that will contain the individual statements
	 * @throws ScriptException if an error occurred while splitting the SQL script
	 * @see #splitSqlScript(String, char, List)
	 * @see #splitSqlScript(EncodedResource, String, String, String, String, String, List)
	 */
	public static void splitSqlScript(String script, String separator, List<String> statements) throws ScriptException {
		splitSqlScript(null, script, separator, DEFAULT_COMMENT_PREFIX, DEFAULT_BLOCK_COMMENT_START_DELIMITER,
				DEFAULT_BLOCK_COMMENT_END_DELIMITER, statements);
	}

	/**
	 * Split an SQL script into separate statements delimited by the provided
	 * separator string. Each individual statement will be added to the provided
	 * {@code List}.
	 * <p>Within the script, the provided {@code commentPrefix} will be honored:
	 * any text beginning with the comment prefix and extending to the end of the
	 * line will be omitted from the output. Similarly, the provided
	 * {@code blockCommentStartDelimiter} and {@code blockCommentEndDelimiter}
	 * delimiters will be honored: any text enclosed in a block comment will be
	 * omitted from the output. In addition, multiple adjacent whitespace characters
	 * will be collapsed into a single space.
	 * @param resource the resource from which the script was read
	 * @param script the SQL script
	 * @param separator text separating each statement
	 * (typically a ';' or newline character)
	 * @param commentPrefix the prefix that identifies SQL line comments
	 * (typically "--")
	 * @param blockCommentStartDelimiter the <em>start</em> block comment delimiter;
	 * never {@code null} or empty
	 * @param blockCommentEndDelimiter the <em>end</em> block comment delimiter;
	 * never {@code null} or empty
	 * @param statements the list that will contain the individual statements
	 * @throws ScriptException if an error occurred while splitting the SQL script
	 */
	public static void splitSqlScript(@Nullable EncodedResource resource, String script,
			String separator, String commentPrefix, String blockCommentStartDelimiter,
			String blockCommentEndDelimiter, List<String> statements) throws ScriptException {

		Assert.hasText(commentPrefix, "'commentPrefix' must not be null or empty");
		splitSqlScript(resource, script, separator, new String[] { commentPrefix },
				blockCommentStartDelimiter, blockCommentEndDelimiter, statements);
	}

	/**
	 * Split an SQL script into separate statements delimited by the provided
	 * separator string. Each individual statement will be added to the provided
	 * {@code List}.
	 * <p>Within the script, the provided {@code commentPrefixes} will be honored:
	 * any text beginning with one of the comment prefixes and extending to the
	 * end of the line will be omitted from the output. Similarly, the provided
	 * {@code blockCommentStartDelimiter} and {@code blockCommentEndDelimiter}
	 * delimiters will be honored: any text enclosed in a block comment will be
	 * omitted from the output. In addition, multiple adjacent whitespace characters
	 * will be collapsed into a single space.
	 * @param resource the resource from which the script was read
	 * @param script the SQL script
	 * @param separator text separating each statement
	 * (typically a ';' or newline character)
	 * @param commentPrefixes the prefixes that identify SQL line comments
	 * (typically "--")
	 * @param blockCommentStartDelimiter the <em>start</em> block comment delimiter;
	 * never {@code null} or empty
	 * @param blockCommentEndDelimiter the <em>end</em> block comment delimiter;
	 * never {@code null} or empty
	 * @param statements the list that will contain the individual statements
	 * @throws ScriptException if an error occurred while splitting the SQL script
	 */
	public static void splitSqlScript(@Nullable EncodedResource resource, String script,
			String separator, String[] commentPrefixes, String blockCommentStartDelimiter,
			String blockCommentEndDelimiter, List<String> statements) throws ScriptException {

		Assert.hasText(script, "'script' must not be null or empty");
		Assert.notNull(separator, "'separator' must not be null");
		Assert.notEmpty(commentPrefixes, "'commentPrefixes' must not be null or empty");
		for (String commentPrefix : commentPrefixes) {
			Assert.hasText(commentPrefix, "'commentPrefixes' must not contain null or empty elements");
		}
		Assert.hasText(blockCommentStartDelimiter, "'blockCommentStartDelimiter' must not be null or empty");
		Assert.hasText(blockCommentEndDelimiter, "'blockCommentEndDelimiter' must not be null or empty");

		StringBuilder sb = new StringBuilder();
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		boolean inEscape = false;

		for (int i = 0; i < script.length(); i++) {
			char c = script.charAt(i);
			if (inEscape) {
				inEscape = false;
				sb.append(c);
				continue;
			}
			// MySQL style escapes
			if (c == '\\') {
				inEscape = true;
				sb.append(c);
				continue;
			}
			if (!inDoubleQuote && (c == '\'')) {
				inSingleQuote = !inSingleQuote;
			}
			else if (!inSingleQuote && (c == '"')) {
				inDoubleQuote = !inDoubleQuote;
			}
			if (!inSingleQuote && !inDoubleQuote) {
				if (script.startsWith(separator, i)) {
					// We've reached the end of the current statement
					if (sb.length() > 0) {
						statements.add(sb.toString());
						sb = new StringBuilder();
					}
					i += separator.length() - 1;
					continue;
				}
				else if (startsWithAny(script, commentPrefixes, i)) {
					// Skip over any content from the start of the comment to the EOL
					int indexOfNextNewline = script.indexOf('\n', i);
					if (indexOfNextNewline > i) {
						i = indexOfNextNewline;
						continue;
					}
					else {
						// If there's no EOL, we must be at the end of the script, so stop here.
						break;
					}
				}
				else if (script.startsWith(blockCommentStartDelimiter, i)) {
					// Skip over any block comments
					int indexOfCommentEnd = script.indexOf(blockCommentEndDelimiter, i);
					if (indexOfCommentEnd > i) {
						i = indexOfCommentEnd + blockCommentEndDelimiter.length() - 1;
						continue;
					}
					else {
						throw new ScriptParseException(
								"Missing block comment end delimiter: " + blockCommentEndDelimiter, resource);
					}
				}
				else if (c == ' ' || c == '\r' || c == '\n' || c == '\t') {
					// Avoid multiple adjacent whitespace characters
					if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
						c = ' ';
					}
					else {
						continue;
					}
				}
			}
			sb.append(c);
		}

		if (StringUtils.hasText(sb)) {
			statements.add(sb.toString());
		}
	}

	/**
	 * Read a script from the given resource, using "{@code --}" as the comment prefix
	 * and "{@code ;}" as the statement separator, and build a String containing the lines.
	 * @param resource the {@code EncodedResource} to be read
	 * @return {@code String} containing the script lines
	 */
	public static Mono<String> readScript(EncodedResource resource, DataBufferFactory dataBufferFactory) {
		return readScript(resource, dataBufferFactory, DEFAULT_COMMENT_PREFIXES, DEFAULT_STATEMENT_SEPARATOR,
				DEFAULT_BLOCK_COMMENT_END_DELIMITER);
	}

	/**
	 * Read a script from the provided resource, using the supplied comment prefixes
	 * and statement separator, and build a {@code String} containing the lines.
	 * <p>Lines <em>beginning</em> with one of the comment prefixes are excluded
	 * from the results; however, line comments anywhere else &mdash; for example,
	 * within a statement &mdash; will be included in the results.
	 * @param resource the {@code EncodedResource} containing the script
	 * to be processed
	 * @param commentPrefixes the prefixes that identify comments in the SQL script
	 * (typically "--")
	 * @param separator the statement separator in the SQL script (typically ";")
	 * @param blockCommentEndDelimiter the <em>end</em> block comment delimiter
	 * @return a {@link Mono} of {@link String} containing the script lines that
	 * completes once the resource was loaded
	 */
	private static Mono<String> readScript(EncodedResource resource, DataBufferFactory dataBufferFactory,
			@Nullable String[] commentPrefixes, @Nullable String separator, @Nullable String blockCommentEndDelimiter) {

		return DataBufferUtils.join(DataBufferUtils.read(resource.getResource(), dataBufferFactory, 8192))
				.handle((it, sink) -> {

					try (InputStream is = it.asInputStream()) {

						InputStreamReader in = resource.getCharset() != null ? new InputStreamReader(is, resource.getCharset())
								: new InputStreamReader(is);
						LineNumberReader lnr = new LineNumberReader(in);
						String script = readScript(lnr, commentPrefixes, separator, blockCommentEndDelimiter);

						sink.next(script);
						sink.complete();
					}
					catch (Exception ex) {
						sink.error(ex);
					}
					finally {
						DataBufferUtils.release(it);
					}
				});
	}

	/**
	 * Read a script from the provided {@code LineNumberReader}, using the supplied
	 * comment prefix and statement separator, and build a {@code String} containing
	 * the lines.
	 * <p>Lines <em>beginning</em> with the comment prefix are excluded from the
	 * results; however, line comments anywhere else &mdash; for example, within
	 * a statement &mdash; will be included in the results.
	 * @param lineNumberReader the {@code LineNumberReader} containing the script
	 * to be processed
	 * @param lineCommentPrefix the prefix that identifies comments in the SQL script
	 * (typically "--")
	 * @param separator the statement separator in the SQL script (typically ";")
	 * @param blockCommentEndDelimiter the <em>end</em> block comment delimiter
	 * @return a {@code String} containing the script lines
	 * @throws IOException in case of I/O errors
	 */
	public static String readScript(LineNumberReader lineNumberReader, @Nullable String lineCommentPrefix,
			@Nullable String separator, @Nullable String blockCommentEndDelimiter) throws IOException {
		String[] lineCommentPrefixes = (lineCommentPrefix != null) ? new String[] { lineCommentPrefix } : null;
		return readScript(lineNumberReader, lineCommentPrefixes, separator, blockCommentEndDelimiter);
	}

	/**
	 * Read a script from the provided {@code LineNumberReader}, using the supplied
	 * comment prefixes and statement separator, and build a {@code String} containing
	 * the lines.
	 * <p>Lines <em>beginning</em> with one of the comment prefixes are excluded
	 * from the results; however, line comments anywhere else &mdash; for example,
	 * within a statement &mdash; will be included in the results.
	 * @param lineNumberReader the {@code LineNumberReader} containing the script
	 * to be processed
	 * @param lineCommentPrefixes the prefixes that identify comments in the SQL script
	 * (typically "--")
	 * @param separator the statement separator in the SQL script (typically ";")
	 * @param blockCommentEndDelimiter the <em>end</em> block comment delimiter
	 * @return a {@code String} containing the script lines
	 * @throws IOException in case of I/O errors
	 */
	public static String readScript(LineNumberReader lineNumberReader, @Nullable String[] lineCommentPrefixes,
			@Nullable String separator, @Nullable String blockCommentEndDelimiter) throws IOException {

		String currentStatement = lineNumberReader.readLine();
		StringBuilder scriptBuilder = new StringBuilder();
		while (currentStatement != null) {
			if ((blockCommentEndDelimiter != null && currentStatement.contains(blockCommentEndDelimiter)) ||
				(lineCommentPrefixes != null && !startsWithAny(currentStatement, lineCommentPrefixes, 0))) {
				if (scriptBuilder.length() > 0) {
					scriptBuilder.append('\n');
				}
				scriptBuilder.append(currentStatement);
			}
			currentStatement = lineNumberReader.readLine();
		}
		appendSeparatorToScriptIfNecessary(scriptBuilder, separator);
		return scriptBuilder.toString();
	}

	private static void appendSeparatorToScriptIfNecessary(StringBuilder scriptBuilder, @Nullable String separator) {
		if (separator == null) {
			return;
		}
		String trimmed = separator.trim();
		if (trimmed.length() == separator.length()) {
			return;
		}
		// separator ends in whitespace, so we might want to see if the script is trying
		// to end the same way
		if (scriptBuilder.lastIndexOf(trimmed) == scriptBuilder.length() - trimmed.length()) {
			scriptBuilder.append(separator.substring(trimmed.length()));
		}
	}

	private static boolean startsWithAny(String script, String[] prefixes, int offset) {
		for (String prefix : prefixes) {
			if (script.startsWith(prefix, offset)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Does the provided SQL script contain the specified delimiter?
	 * @param script the SQL script
	 * @param delim the string delimiting each statement - typically a ';' character
	 */
	public static boolean containsSqlScriptDelimiters(String script, String delim) {
		boolean inLiteral = false;
		boolean inEscape = false;

		for (int i = 0; i < script.length(); i++) {
			char c = script.charAt(i);
			if (inEscape) {
				inEscape = false;
				continue;
			}
			// MySQL style escapes
			if (c == '\\') {
				inEscape = true;
				continue;
			}
			if (c == '\'') {
				inLiteral = !inLiteral;
			}
			if (!inLiteral && script.startsWith(delim, i)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Execute the given SQL script using default settings for statement
	 * separators, comment delimiters, and exception handling flags.
	 * <p>Statement separators and comments will be removed before executing
	 * individual statements within the supplied script.
	 * <p><strong>Warning</strong>: this method does <em>not</em> release the
	 * provided {@link Connection}.
	 * @param connection the R2DBC connection to use to execute the script; already
	 * configured and ready to use
	 * @param resource the resource to load the SQL script from; encoded with the
	 * current platform's default encoding
	 * @throws ScriptException if an error occurred while executing the SQL script
	 * @see #executeSqlScript(Connection, EncodedResource, DataBufferFactory, boolean, boolean, String[], String, String, String)
	 * @see #DEFAULT_STATEMENT_SEPARATOR
	 * @see #DEFAULT_COMMENT_PREFIX
	 * @see #DEFAULT_BLOCK_COMMENT_START_DELIMITER
	 * @see #DEFAULT_BLOCK_COMMENT_END_DELIMITER
	 * @see org.springframework.r2dbc.connection.ConnectionFactoryUtils#getConnection
	 * @see org.springframework.r2dbc.connection.ConnectionFactoryUtils#releaseConnection
	 */
	public static Mono<Void> executeSqlScript(Connection connection, Resource resource) throws ScriptException {
		return executeSqlScript(connection, new EncodedResource(resource));
	}

	/**
	 * Execute the given SQL script using default settings for statement
	 * separators, comment delimiters, and exception handling flags.
	 * <p>Statement separators and comments will be removed before executing
	 * individual statements within the supplied script.
	 * <p><strong>Warning</strong>: this method does <em>not</em> release the
	 * provided {@link Connection}.
	 * @param connection the R2DBC connection to use to execute the script; already
	 * configured and ready to use
	 * @param resource the resource (potentially associated with a specific encoding)
	 * to load the SQL script from
	 * @throws ScriptException if an error occurred while executing the SQL script
	 * @see #executeSqlScript(Connection, EncodedResource, DataBufferFactory, boolean, boolean, String[], String, String, String)
	 * @see #DEFAULT_STATEMENT_SEPARATOR
	 * @see #DEFAULT_COMMENT_PREFIX
	 * @see #DEFAULT_BLOCK_COMMENT_START_DELIMITER
	 * @see #DEFAULT_BLOCK_COMMENT_END_DELIMITER
	 * @see org.springframework.r2dbc.connection.ConnectionFactoryUtils#getConnection
	 * @see org.springframework.r2dbc.connection.ConnectionFactoryUtils#releaseConnection
	 */
	public static Mono<Void> executeSqlScript(Connection connection, EncodedResource resource) throws ScriptException {
		return executeSqlScript(connection, resource, new DefaultDataBufferFactory(), false, false, DEFAULT_COMMENT_PREFIX,
				DEFAULT_STATEMENT_SEPARATOR, DEFAULT_BLOCK_COMMENT_START_DELIMITER, DEFAULT_BLOCK_COMMENT_END_DELIMITER);
	}

	/**
	 * Execute the given SQL script.
	 * <p>Statement separators and comments will be removed before executing
	 * individual statements within the supplied script.
	 * <p><strong>Warning</strong>: this method does <em>not</em> release the
	 * provided {@link Connection}.
	 * @param connection the R2DBC connection to use to execute the script; already
	 * configured and ready to use
	 * @param resource the resource (potentially associated with a specific encoding)
	 * to load the SQL script from
	 * @param continueOnError whether or not to continue without throwing an exception
	 * in the event of an error
	 * @param ignoreFailedDrops whether or not to continue in the event of specifically
	 * an error on a {@code DROP} statement
	 * @param commentPrefix the prefix that identifies single-line comments in the
	 * SQL script (typically "--")
	 * @param separator the script statement separator; defaults to
	 * {@value #DEFAULT_STATEMENT_SEPARATOR} if not specified and falls back to
	 * {@value #FALLBACK_STATEMENT_SEPARATOR} as a last resort; may be set to
	 * {@value #EOF_STATEMENT_SEPARATOR} to signal that the script contains a
	 * single statement without a separator
	 * @param blockCommentStartDelimiter the <em>start</em> block comment delimiter
	 * @param blockCommentEndDelimiter the <em>end</em> block comment delimiter
	 * @throws ScriptException if an error occurred while executing the SQL script
	 * @see #DEFAULT_STATEMENT_SEPARATOR
	 * @see #FALLBACK_STATEMENT_SEPARATOR
	 * @see #EOF_STATEMENT_SEPARATOR
	 * @see org.springframework.r2dbc.connection.ConnectionFactoryUtils#getConnection
	 * @see org.springframework.r2dbc.connection.ConnectionFactoryUtils#releaseConnection
	 */
	public static Mono<Void> executeSqlScript(Connection connection, EncodedResource resource,
			DataBufferFactory dataBufferFactory, boolean continueOnError, boolean ignoreFailedDrops, String commentPrefix,
			@Nullable String separator, String blockCommentStartDelimiter, String blockCommentEndDelimiter)
			throws ScriptException {

		return executeSqlScript(connection, resource, dataBufferFactory, continueOnError,
				ignoreFailedDrops, new String[] { commentPrefix }, separator,
				blockCommentStartDelimiter,	blockCommentEndDelimiter);
	}

	/**
	 * Execute the given SQL script.
	 * <p>Statement separators and comments will be removed before executing
	 * individual statements within the supplied script.
	 * <p><strong>Warning</strong>: this method does <em>not</em> release the
	 * provided {@link Connection}.
	 * @param connection the R2DBC connection to use to execute the script; already
	 * configured and ready to use
	 * @param resource the resource (potentially associated with a specific encoding)
	 * to load the SQL script from
	 * @param continueOnError whether or not to continue without throwing an exception
	 * in the event of an error
	 * @param ignoreFailedDrops whether or not to continue in the event of specifically
	 * an error on a {@code DROP} statement
	 * @param commentPrefixes the prefixes that identify single-line comments in the
	 * SQL script (typically "--")
	 * @param separator the script statement separator; defaults to
	 * {@value #DEFAULT_STATEMENT_SEPARATOR} if not specified and falls back to
	 * {@value #FALLBACK_STATEMENT_SEPARATOR} as a last resort; may be set to
	 * {@value #EOF_STATEMENT_SEPARATOR} to signal that the script contains a
	 * single statement without a separator
	 * @param blockCommentStartDelimiter the <em>start</em> block comment delimiter
	 * @param blockCommentEndDelimiter the <em>end</em> block comment delimiter
	 * @throws ScriptException if an error occurred while executing the SQL script
	 * @see #DEFAULT_STATEMENT_SEPARATOR
	 * @see #FALLBACK_STATEMENT_SEPARATOR
	 * @see #EOF_STATEMENT_SEPARATOR
	 * @see org.springframework.r2dbc.connection.ConnectionFactoryUtils#getConnection
	 * @see org.springframework.r2dbc.connection.ConnectionFactoryUtils#releaseConnection
	 */
	public static Mono<Void> executeSqlScript(Connection connection, EncodedResource resource, DataBufferFactory dataBufferFactory,
			boolean continueOnError,
			boolean ignoreFailedDrops, String[] commentPrefixes, @Nullable String separator,
			String blockCommentStartDelimiter, String blockCommentEndDelimiter) throws ScriptException {

		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL script from " + resource);
		}

		long startTime = System.currentTimeMillis();

		Mono<String> script = readScript(resource, dataBufferFactory, commentPrefixes, separator, blockCommentEndDelimiter)
				.onErrorMap(IOException.class, ex -> new CannotReadScriptException(resource, ex));

		AtomicInteger statementNumber = new AtomicInteger();

		Flux<Void> executeScript = script.flatMapIterable(statement -> {
			List<String> statements = new ArrayList<>();
			String separatorToUse = separator;
			if (separatorToUse == null) {
				separatorToUse = DEFAULT_STATEMENT_SEPARATOR;
			}
			if (!EOF_STATEMENT_SEPARATOR.equals(separatorToUse) && !containsSqlScriptDelimiters(statement, separatorToUse)) {
				separatorToUse = FALLBACK_STATEMENT_SEPARATOR;
			}
			splitSqlScript(resource, statement, separatorToUse, commentPrefixes, blockCommentStartDelimiter,
					blockCommentEndDelimiter, statements);
			return statements;
		}).concatMap(statement -> {

			statementNumber.incrementAndGet();
			return runStatement(statement, connection, resource, continueOnError, ignoreFailedDrops, statementNumber);
		});

		if (logger.isDebugEnabled()) {

			executeScript = executeScript.doOnComplete(() -> {

				long elapsedTime = System.currentTimeMillis() - startTime;
				logger.debug("Executed SQL script from " + resource + " in " + elapsedTime + " ms.");
			});
		}

		return executeScript.onErrorMap(ex -> !(ex instanceof ScriptException),
				ex -> new UncategorizedScriptException("Failed to execute database script from resource [" + resource + "]",
						ex))
				.then();
	}

	private static Publisher<? extends Void> runStatement(String statement, Connection connection,
			EncodedResource resource, boolean continueOnError, boolean ignoreFailedDrops, AtomicInteger statementNumber) {

		Mono<Long> execution = Flux.from(connection.createStatement(statement).execute())
				.flatMap(Result::getRowsUpdated)
				.collect(Collectors.summingLong(count -> count));

		if (logger.isDebugEnabled()) {
			execution = execution.doOnNext(rowsAffected -> logger.debug(rowsAffected + " returned as update count for SQL: " + statement));
		}

		return execution.onErrorResume(ex -> {

			boolean dropStatement = StringUtils.startsWithIgnoreCase(statement.trim(), "drop");
			if (continueOnError || (dropStatement && ignoreFailedDrops)) {
				if (logger.isDebugEnabled()) {
					logger.debug(ScriptStatementFailedException.buildErrorMessage(statement, statementNumber.get(), resource),
							ex);
				}
			}
			else {
				return Mono.error(new ScriptStatementFailedException(statement, statementNumber.get(), resource, ex));
			}

			return Mono.empty();
		}).then();
	}

}
