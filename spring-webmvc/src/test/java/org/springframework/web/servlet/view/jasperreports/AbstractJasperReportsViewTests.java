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

package org.springframework.web.servlet.view.jasperreports;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRAbstractBeanDataSourceProvider;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import org.junit.Ignore;
import org.junit.Test;

import org.springframework.context.ApplicationContextException;
import org.springframework.mock.web.test.MockServletContext;
import org.springframework.tests.Assume;
import org.springframework.tests.TestGroup;
import org.springframework.ui.jasperreports.PersonBean;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;

/**
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Sam Brannen
 */
public abstract class AbstractJasperReportsViewTests extends AbstractJasperReportsTests {

	protected AbstractJasperReportsView getView(String url) throws Exception {
		AbstractJasperReportsView view = getViewImplementation();
		view.setUrl(url);
		StaticWebApplicationContext ac = new StaticWebApplicationContext();
		ac.setServletContext(new MockServletContext());
		ac.addMessage("page", Locale.GERMAN, "MeineSeite");
		ac.refresh();
		request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, ac);
		view.setApplicationContext(ac);
		return view;
	}

	/**
	 * Simple test to see if compiled report succeeds.
	 */
	@Test
	public void testCompiledReport() throws Exception {
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(getModel(), request, response);
		assertTrue(response.getContentAsByteArray().length > 0);
		if (view instanceof AbstractJasperReportsSingleFormatView &&
				((AbstractJasperReportsSingleFormatView) view).useWriter()) {
			String output = response.getContentAsString();
			assertTrue("Output should contain 'MeineSeite'", output.contains("MeineSeite"));
		}
	}

	@Test
	public void testUncompiledReport() throws Exception {
		Assume.group(TestGroup.CUSTOM_COMPILATION);

		if (!canCompileReport) {
			return;
		}

		AbstractJasperReportsView view = getView(UNCOMPILED_REPORT);
		view.render(getModel(), request, response);
		assertTrue(response.getContentAsByteArray().length > 0);
	}

	@Test(expected = ApplicationContextException.class)
	public void testWithInvalidPath() throws Exception {
		getView("foo.jasper");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidExtension() throws Exception {
		getView("foo.bar");
	}

	@Test
	public void testContentType() throws Exception {
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(getModel(), request, response);
		assertEquals("Response content type is incorrect", getDesiredContentType(), response.getContentType());
	}

	@Test
	public void testWithoutDatasource() throws Exception {
		Map<String, Object> model = getModel();
		model.remove("dataSource");
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(model, request, response);
		assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
	}

	@Test
	public void testWithCollection() throws Exception {
		Map<String, Object> model = getModel();
		model.remove("dataSource");
		model.put("reportData", getData());
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(model, request, response);
		assertTrue(response.getContentAsByteArray().length > 0);
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void testWithMultipleCollections() throws Exception {
		Map<String, Object> model = getModel();
		model.remove("dataSource");
		model.put("reportData", getData());
		model.put("otherData", new LinkedList());
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(model, request, response);
		// no clear data source found
	}

	@Test
	public void testWithJRDataSourceProvider() throws Exception {
		Map<String, Object> model = getModel();
		model.remove("dataSource");
		model.put("dataSource", new MockDataSourceProvider(PersonBean.class));
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(model, request, response);
		assertTrue(response.getContentAsByteArray().length > 0);
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void testWithSpecificCollection() throws Exception {
		Map<String, Object> model = getModel();
		model.remove("dataSource");
		model.put("reportData", getData());
		model.put("otherData", new LinkedList());
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.setReportDataKey("reportData");
		view.render(model, request, response);
		assertTrue(response.getContentAsByteArray().length > 0);
	}

	@Test
	public void testWithArray() throws Exception {
		Map<String, Object> model = getModel();
		model.remove("dataSource");
		model.put("reportData", getData().toArray());
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(model, request, response);
		assertTrue(response.getContentAsByteArray().length > 0);
	}

	@Test
	public void testWithMultipleArrays() throws Exception {
		Map<String, Object> model = getModel();
		model.remove("dataSource");
		model.put("reportData", getData().toArray());
		model.put("otherData", new String[0]);
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(model, request, response);
		// no clear data source found
	}

	@Test
	public void testWithSpecificArray() throws Exception {
		Map<String, Object> model = getModel();
		model.remove("dataSource");
		model.put("reportData", getData().toArray());
		model.put("otherData", new String[0]);
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.setReportDataKey("reportData");
		view.render(model, request, response);
		assertTrue(response.getContentAsByteArray().length > 0);
	}

	@Test
	public void testWithSubReport() throws Exception {
		if (!canCompileReport) {
			return;
		}

		Map<String, Object> model = getModel();
		model.put("SubReportData", getProductData());

		Properties subReports = new Properties();
		subReports.put("ProductsSubReport", "/org/springframework/ui/jasperreports/subReportChild.jrxml");

		AbstractJasperReportsView view = getView(SUB_REPORT_PARENT);
		view.setReportDataKey("dataSource");
		view.setSubReportUrls(subReports);
		view.setSubReportDataKeys(new String[]{"SubReportData"});
		view.initApplicationContext();
		view.render(model, request, response);

		assertTrue(response.getContentAsByteArray().length > 0);
	}

	@Test
	public void testWithNonExistentSubReport() throws Exception {
		if (!canCompileReport) {
			return;
		}

		Map<String, Object> model = getModel();
		model.put("SubReportData", getProductData());

		Properties subReports = new Properties();
		subReports.put("ProductsSubReport", "org/springframework/ui/jasperreports/subReportChildFalse.jrxml");

		AbstractJasperReportsView view = getView(SUB_REPORT_PARENT);
		view.setReportDataKey("dataSource");
		view.setSubReportUrls(subReports);
		view.setSubReportDataKeys(new String[]{"SubReportData"});

		try {
			view.initApplicationContext();
			fail("Invalid report URL should throw ApplicationContextException");
		}
		catch (ApplicationContextException ex) {
			// success
		}
	}

	@Ignore
	public void ignoreTestOverrideExporterParameters() throws Exception {
		AbstractJasperReportsView view = getView(COMPILED_REPORT);

		if (!(view instanceof AbstractJasperReportsSingleFormatView) || !((AbstractJasperReportsSingleFormatView) view).useWriter()) {
			return;
		}

		String characterEncoding = "UTF-8";
		String overiddenCharacterEncoding = "ASCII";

		Map<Object, Object> parameters = new HashMap<Object, Object>();
		parameters.put(JRExporterParameter.CHARACTER_ENCODING, characterEncoding);

		view.setExporterParameters(parameters);
		view.convertExporterParameters();

		Map<String, Object> model = getModel();
		model.put(JRExporterParameter.CHARACTER_ENCODING.toString(), overiddenCharacterEncoding);

		view.render(model, this.request, this.response);

		assertEquals(overiddenCharacterEncoding, this.response.getCharacterEncoding());
	}

	@Test
	public void testSubReportWithUnspecifiedParentDataSource() throws Exception {
		if (!canCompileReport) {
			return;
		}

		Map<String, Object> model = getModel();
		model.put("SubReportData", getProductData());

		Properties subReports = new Properties();
		subReports.put("ProductsSubReport", "org/springframework/ui/jasperreports/subReportChildFalse.jrxml");

		AbstractJasperReportsView view = getView(SUB_REPORT_PARENT);
		view.setSubReportUrls(subReports);
		view.setSubReportDataKeys(new String[]{"SubReportData"});

		try {
			view.initApplicationContext();
			fail("Unspecified reportDataKey should throw exception when subReportDataSources is specified");
		}
		catch (ApplicationContextException ex) {
			// success
		}
	}

	@Test
	public void testContentDisposition() throws Exception {
		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.render(getModel(), request, response);
		assertEquals("Invalid content type", "inline", response.getHeader("Content-Disposition"));

	}

	@Test
	public void testOverrideContentDisposition() throws Exception {
		Properties headers = new Properties();
		String cd = "attachment";
		headers.setProperty("Content-Disposition", cd);

		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.setHeaders(headers);
		view.render(getModel(), request, response);
		assertEquals("Invalid content type", cd, response.getHeader("Content-Disposition"));
	}

	@Test
	public void testSetCustomHeaders() throws Exception {
		Properties headers = new Properties();

		String key = "foo";
		String value = "bar";

		headers.setProperty(key, value);

		AbstractJasperReportsView view = getView(COMPILED_REPORT);
		view.setHeaders(headers);
		view.render(getModel(), request, response);

		assertNotNull("Header not present", response.getHeader(key));
		assertEquals("Invalid header value", value, response.getHeader(key));
	}

	@Test
	public void testWithJdbcDataSource() throws Exception {
		Assume.group(TestGroup.CUSTOM_COMPILATION);

		if (!canCompileReport) {
			return;
		}

		AbstractJasperReportsView view = getView(UNCOMPILED_REPORT);
		view.setJdbcDataSource(getMockJdbcDataSource());

		Map<String, Object> model = getModel();
		model.remove("dataSource");

		try {
			view.render(model, request, response);
			fail("DataSource was not used as report DataSource");
		}
		catch (SQLException ex) {
			// expected
		}
	}

	@Test
	public void testWithJdbcDataSourceInModel() throws Exception {
		Assume.group(TestGroup.CUSTOM_COMPILATION);

		if (!canCompileReport) {
			return;
		}

		AbstractJasperReportsView view = getView(UNCOMPILED_REPORT);

		Map<String, Object> model = getModel();
		model.remove("dataSource");
		model.put("someKey", getMockJdbcDataSource());

		try {
			view.render(model, request, response);
			fail("DataSource was not used as report DataSource");
		}
		catch (SQLException ex) {
			// expected
		}
	}

	@Test
	public void testJRDataSourceOverridesJdbcDataSource() throws Exception {
		Assume.group(TestGroup.CUSTOM_COMPILATION);

		if (!canCompileReport) {
			return;
		}

		AbstractJasperReportsView view = getView(UNCOMPILED_REPORT);
		view.setJdbcDataSource(getMockJdbcDataSource());

		try {
			view.render(getModel(), request, response);
		}
		catch (SQLException ex) {
			fail("javax.sql.DataSource was used when JRDataSource should have overridden it");
		}
	}

	private DataSource getMockJdbcDataSource() throws SQLException {
		DataSource ds = mock(DataSource.class);
		given(ds.getConnection()).willThrow(new SQLException());
		return ds;
	}

	@Test
	public void testWithCharacterEncoding() throws Exception {
		AbstractJasperReportsView view = getView(COMPILED_REPORT);

		if (!(view instanceof AbstractJasperReportsSingleFormatView) || !((AbstractJasperReportsSingleFormatView) view).useWriter()) {
			return;
		}

		String characterEncoding = "UTF-8";

		Map<Object, Object> parameters = new HashMap<Object, Object>();
		parameters.put(JRExporterParameter.CHARACTER_ENCODING, characterEncoding);

		view.setExporterParameters(parameters);
		view.convertExporterParameters();

		view.render(getModel(), this.request, this.response);
		assertEquals(characterEncoding, this.response.getCharacterEncoding());
	}


	protected abstract AbstractJasperReportsView getViewImplementation();

	protected abstract String getDesiredContentType();


	private class MockDataSourceProvider extends JRAbstractBeanDataSourceProvider {

		public MockDataSourceProvider(Class<?> clazz) {
			super(clazz);
		}

		@Override
		public JRDataSource create(JasperReport jasperReport) throws JRException {
			return new JRBeanCollectionDataSource(getData());
		}

		@Override
		public void dispose(JRDataSource jrDataSource) throws JRException {

		}
	}

}
