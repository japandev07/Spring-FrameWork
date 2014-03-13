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

import org.junit.After;
import org.junit.Test;
import org.springframework.core.io.ClassRelativeResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Dave Syer
 * @author Sam Brannen
 * @author Oliver Gierke
 */
public class DatabasePopulatorTests {

	private final EmbeddedDatabase db = new EmbeddedDatabaseBuilder().build();

	private final ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator();

	private final ClassRelativeResourceLoader resourceLoader = new ClassRelativeResourceLoader(getClass());

	private final JdbcTemplate jdbcTemplate = new JdbcTemplate(db);


	private void assertTestDatabaseCreated() {
		assertTestDatabaseCreated("Keith");
	}

	private void assertTestDatabaseCreated(String name) {
		assertEquals(name, jdbcTemplate.queryForObject("select NAME from T_TEST", String.class));
	}

	private void assertUsersDatabaseCreated(String... lastNames) {
		for (String lastName : lastNames) {
			assertThat("Did not find user with last name [" + lastName + "].",
				jdbcTemplate.queryForObject("select count(0) from users where last_name = ?", Integer.class, lastName),
				equalTo(1));
		}
	}

	private Resource resource(String path) {
		return resourceLoader.getResource(path);
	}

	private Resource defaultSchema() {
		return resource("db-schema.sql");
	}

	private Resource usersSchema() {
		return resource("users-schema.sql");
	}

	@After
	public void shutDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clear();
			TransactionSynchronizationManager.unbindResource(db);
		}
		db.shutdown();
	}

	@Test
	public void buildWithCommentsAndFailedDrop() throws Exception {
		databasePopulator.addScript(resource("db-schema-failed-drop-comments.sql"));
		databasePopulator.addScript(resource("db-test-data.sql"));
		databasePopulator.setIgnoreFailedDrops(true);
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertTestDatabaseCreated();
	}

	@Test
	public void buildWithNormalEscapedLiteral() throws Exception {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-escaped-literal.sql"));
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertTestDatabaseCreated("'Keith'");
	}

	@Test
	public void buildWithMySQLEscapedLiteral() throws Exception {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-mysql-escaped-literal.sql"));
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertTestDatabaseCreated("\\$Keith\\$");
	}

	@Test
	public void buildWithMultipleStatements() throws Exception {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-multiple.sql"));
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Keith'", Integer.class),
			equalTo(1));
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Dave'", Integer.class),
			equalTo(1));
	}

	@Test
	public void buildWithMultipleStatementsLongSeparator() throws Exception {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-endings.sql"));
		databasePopulator.setSeparator("@@");
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Keith'", Integer.class),
			equalTo(1));
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Dave'", Integer.class),
			equalTo(1));
	}

	@Test
	public void buildWithMultipleStatementsWhitespaceSeparator() throws Exception {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-whitespace.sql"));
		databasePopulator.setSeparator("/\n");
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Keith'", Integer.class),
			equalTo(1));
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Dave'", Integer.class),
			equalTo(1));
	}

	@Test
	public void buildWithMultipleStatementsNewlineSeparator() throws Exception {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-newline.sql"));
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Keith'", Integer.class),
			equalTo(1));
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Dave'", Integer.class),
			equalTo(1));
	}

	@Test
	public void buildWithMultipleStatementsMultipleNewlineSeparator() throws Exception {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-multi-newline.sql"));
		databasePopulator.setSeparator("\n\n");
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Keith'", Integer.class),
			equalTo(1));
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Dave'", Integer.class),
			equalTo(1));
	}

	@Test
	public void scriptWithEolBetweenTokens() throws Exception {
		databasePopulator.addScript(usersSchema());
		databasePopulator.addScript(resource("users-data.sql"));
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertUsersDatabaseCreated("Brannen");
	}

	@Test
	public void scriptWithCommentsWithinStatements() throws Exception {
		databasePopulator.addScript(usersSchema());
		databasePopulator.addScript(resource("users-data-with-comments.sql"));
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertUsersDatabaseCreated("Brannen", "Hoeller");
	}

	@Test
	public void constructorWithMultipleScriptResources() throws Exception {
		final ResourceDatabasePopulator populator = new ResourceDatabasePopulator(false, false, null, usersSchema(),
			resource("users-data-with-comments.sql"));
		DatabasePopulatorUtils.execute(populator, db);
		assertUsersDatabaseCreated("Brannen", "Hoeller");
	}

	@Test
	public void buildWithSelectStatements() throws Exception {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-select.sql"));
		DatabasePopulatorUtils.execute(databasePopulator, db);
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Keith'", Integer.class),
			equalTo(1));
		assertThat(jdbcTemplate.queryForObject("select COUNT(NAME) from T_TEST where NAME='Dave'", Integer.class),
			equalTo(1));
	}

	/**
	 * See SPR-9457
	 */
	@Test
	public void usesBoundConnectionIfAvailable() throws SQLException {
		TransactionSynchronizationManager.initSynchronization();
		Connection connection = DataSourceUtils.getConnection(db);
		DatabasePopulator populator = mock(DatabasePopulator.class);
		DatabasePopulatorUtils.execute(populator, db);
		verify(populator).populate(connection);
	}

	/**
	 * See SPR-9781
	 */
	@Test(timeout = 1000)
	public void executesHugeScriptInReasonableTime() throws SQLException {
		databasePopulator.addScript(defaultSchema());
		databasePopulator.addScript(resource("db-test-data-huge.sql"));
		DatabasePopulatorUtils.execute(databasePopulator, db);
	}

}
