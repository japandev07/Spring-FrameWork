package org.springframework.web.servlet.mvc.annotation;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.ConversionServiceFactory;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;

public class Spr7839Tests {

	AnnotationMethodHandlerAdapter adapter = new AnnotationMethodHandlerAdapter();

	MockHttpServletRequest request = new MockHttpServletRequest();

	MockHttpServletResponse response = new MockHttpServletResponse();

	Spr7839Controller controller = new Spr7839Controller();

	@Before
	public void setUp() {
		ConfigurableWebBindingInitializer binder = new ConfigurableWebBindingInitializer();
		GenericConversionService service = ConversionServiceFactory.createDefaultConversionService();
		service.addConverter(new Converter<String, NestedBean>() {
			public NestedBean convert(String source) {
				return new NestedBean(source);
			}
		});
		binder.setConversionService(service);
		adapter.setWebBindingInitializer(binder);
	}

	@Test
	public void object() throws Exception {
		request.setRequestURI("/nested");
		request.addParameter("nested", "Nested");
		adapter.handle(request, response, controller);
	}

	@Test
	public void list() throws Exception {
		request.setRequestURI("/nested/list");
		request.addParameter("nested.list", "Nested1,Nested2");
		adapter.handle(request, response, controller);
	}
	
	@Test
	public void listElement() throws Exception {
		request.setRequestURI("/nested/listElement");
		request.addParameter("nested.list[0]", "Nested");
		adapter.handle(request, response, controller);
	}

	@Test
	public void map() throws Exception {
		request.setRequestURI("/nested/map");		
		request.addParameter("nested.map['apple'].foo", "bar");
		adapter.handle(request, response, controller);
	}

	@Controller
	public static class Spr7839Controller {

		@RequestMapping("/nested")
		public void handler(JavaBean bean) {
			assertEquals("Nested", bean.nested.foo);
		}

		@RequestMapping("/nested/list")
		public void handlerList(JavaBean bean) {
			assertEquals("Nested2", bean.nested.list.get(1).foo);
		}

		@RequestMapping("/nested/map")
		public void handlerMap(JavaBean bean) {
			assertEquals("bar", bean.nested.map.get("apple").foo);
		}

		@RequestMapping("/nested/listElement")
		public void handlerListElement(JavaBean bean) {
			assertEquals("Nested", bean.nested.list.get(0).foo);
		}

	}
	
	public static class JavaBean {

	    private NestedBean nested;

		public NestedBean getNested() {
			return nested;
		}

		public void setNested(NestedBean nested) {
			this.nested = nested;
		}

	    
	}

	public static class NestedBean {

	    private String foo;

	    private List<NestedBean> list;
		
	    private Map<String, NestedBean> map = new HashMap<String, NestedBean>();

	    public NestedBean() {
	    	
	    }
	    
	    public NestedBean(String foo) {
	    	this.foo = foo;
	    }
	    
		public String getFoo() {
			return foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}

		public List<NestedBean> getList() {
			return list;
		}

		public void setList(List<NestedBean> list) {
			this.list = list;
		}

		public Map<String, NestedBean> getMap() {
			return map;
		}

		public void setMap(Map<String, NestedBean> map) {
			this.map = map;
		}

	    
	    
	}
	
}
