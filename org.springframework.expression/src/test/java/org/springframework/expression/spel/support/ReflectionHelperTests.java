/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.expression.spel.support;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import org.junit.Test;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ParseException;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionTestCase;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.SpelUtilities;
import org.springframework.expression.spel.ast.FormatHelper;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.support.ReflectionHelper.ArgsMatchKind;

/**
 * Tests for any helper code.
 * 
 * @author Andy Clement
 */
public class ReflectionHelperTests extends ExpressionTestCase {

	@Test
	public void testFormatHelperForClassName() {
		Assert.assertEquals("java.lang.String",FormatHelper.formatClassNameForMessage(String.class));
		Assert.assertEquals("java.lang.String[]",FormatHelper.formatClassNameForMessage(new String[1].getClass()));
		Assert.assertEquals("int[]",FormatHelper.formatClassNameForMessage(new int[1].getClass()));
		Assert.assertEquals("int[][]",FormatHelper.formatClassNameForMessage(new int[1][2].getClass()));
		Assert.assertEquals("null",FormatHelper.formatClassNameForMessage(null));
	}
	
	/*
	@Test
	public void testFormatHelperForMethod() {
		Assert.assertEquals("foo(java.lang.String)",FormatHelper.formatMethodForMessage("foo", String.class));
		Assert.assertEquals("goo(java.lang.String,int[])",FormatHelper.formatMethodForMessage("goo", String.class,new int[1].getClass()));
		Assert.assertEquals("boo()",FormatHelper.formatMethodForMessage("boo"));
	}
	*/
	
	@Test
	public void testUtilities() throws ParseException {
		SpelExpression expr = (SpelExpression)parser.parseExpression("3+4+5+6+7-2");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		SpelUtilities.printAbstractSyntaxTree(ps, expr);
		ps.flush();
		String s = baos.toString();
//		===> Expression '3+4+5+6+7-2' - AST start
//		OperatorMinus  value:(((((3 + 4) + 5) + 6) + 7) - 2)  #children:2
//		  OperatorPlus  value:((((3 + 4) + 5) + 6) + 7)  #children:2
//		    OperatorPlus  value:(((3 + 4) + 5) + 6)  #children:2
//		      OperatorPlus  value:((3 + 4) + 5)  #children:2
//		        OperatorPlus  value:(3 + 4)  #children:2
//		          CompoundExpression  value:3
//		            IntLiteral  value:3
//		          CompoundExpression  value:4
//		            IntLiteral  value:4
//		        CompoundExpression  value:5
//		          IntLiteral  value:5
//		      CompoundExpression  value:6
//		        IntLiteral  value:6
//		    CompoundExpression  value:7
//		      IntLiteral  value:7
//		  CompoundExpression  value:2
//		    IntLiteral  value:2
//		===> Expression '3+4+5+6+7-2' - AST end
		Assert.assertTrue(s.indexOf("===> Expression '3+4+5+6+7-2' - AST start")!=-1);
		Assert.assertTrue(s.indexOf(" OpPlus  value:((((3 + 4) + 5) + 6) + 7)  #children:2")!=-1);
	}
	
	@Test
	public void testTypedValue() {
		TypedValue tValue = new TypedValue("hello");
		Assert.assertEquals(String.class,tValue.getTypeDescriptor().getType());
		Assert.assertEquals("TypedValue: 'hello' of [java.lang.String]",tValue.toString());
	}
	
	@Test
	public void testReflectionHelperCompareArguments_ExactMatching() {
		StandardTypeConverter typeConverter = new StandardTypeConverter();
		
		// Calling foo(String) with (String) is exact match
		checkMatch(new Class[]{String.class},new Class[]{String.class},typeConverter,ArgsMatchKind.EXACT);
		
		// Calling foo(String,Integer) with (String,Integer) is exact match
		checkMatch(new Class[]{String.class,Integer.class},new Class[]{String.class,Integer.class},typeConverter,ArgsMatchKind.EXACT);
	}
	
	@Test
	public void testReflectionHelperCompareArguments_CloseMatching() {
		StandardTypeConverter typeConverter = new StandardTypeConverter();
		
		// Calling foo(List) with (ArrayList) is close match (no conversion required)
		checkMatch(new Class[]{ArrayList.class},new Class[]{List.class},typeConverter,ArgsMatchKind.CLOSE);
		
		// Passing (Sub,String) on call to foo(Super,String) is close match
		checkMatch(new Class[]{Sub.class,String.class},new Class[]{Super.class,String.class},typeConverter,ArgsMatchKind.CLOSE);
		
		// Passing (String,Sub) on call to foo(String,Super) is close match
		checkMatch(new Class[]{String.class,Sub.class},new Class[]{String.class,Super.class},typeConverter,ArgsMatchKind.CLOSE);
	}
	
	@Test
	public void testReflectionHelperCompareArguments_RequiresConversionMatching() {
		StandardTypeConverter typeConverter = new StandardTypeConverter();
		
		// Calling foo(String,int) with (String,Integer) requires boxing conversion of argument one
		checkMatch(new Class[]{String.class,Integer.TYPE},new Class[]{String.class,Integer.class},typeConverter,ArgsMatchKind.CLOSE,1);

		// Passing (int,String) on call to foo(Integer,String) requires boxing conversion of argument zero
		checkMatch(new Class[]{Integer.TYPE,String.class},new Class[]{Integer.class, String.class},typeConverter,ArgsMatchKind.CLOSE,0);
		
		// Passing (int,Sub) on call to foo(Integer,Super) requires boxing conversion of argument zero
		checkMatch(new Class[]{Integer.TYPE,Sub.class},new Class[]{Integer.class, Super.class},typeConverter,ArgsMatchKind.CLOSE,0);
		
		// Passing (int,Sub,boolean) on call to foo(Integer,Super,Boolean) requires boxing conversion of arguments zero and two
		// TODO checkMatch(new Class[]{Integer.TYPE,Sub.class,Boolean.TYPE},new Class[]{Integer.class, Super.class,Boolean.class},typeConverter,ArgsMatchKind.REQUIRES_CONVERSION,0,2);
	}

	@Test
	public void testReflectionHelperCompareArguments_NotAMatch() {
		StandardTypeConverter typeConverter = new StandardTypeConverter();
		
		// Passing (Super,String) on call to foo(Sub,String) is not a match
		checkMatch(new Class[]{Super.class,String.class},new Class[]{Sub.class,String.class},typeConverter,null);
	}

	@Test
	public void testReflectionHelperCompareArguments_Varargs_ExactMatching() {
		StandardTypeConverter tc = new StandardTypeConverter();
		Class<?> stringArrayClass = new String[0].getClass();
		Class<?> integerArrayClass = new Integer[0].getClass();
				
		// Passing (String[]) on call to (String[]) is exact match
		checkMatch2(new Class[]{stringArrayClass},new Class[]{stringArrayClass},tc,ArgsMatchKind.EXACT);
		
		// Passing (Integer, String[]) on call to (Integer, String[]) is exact match
		checkMatch2(new Class[]{Integer.class,stringArrayClass},new Class[]{Integer.class,stringArrayClass},tc,ArgsMatchKind.EXACT);

		// Passing (String, Integer, String[]) on call to (String, String, String[]) is exact match
		checkMatch2(new Class[]{String.class,Integer.class,stringArrayClass},new Class[]{String.class,Integer.class,stringArrayClass},tc,ArgsMatchKind.EXACT);
		
		// Passing (Sub, String[]) on call to (Super, String[]) is exact match
		checkMatch2(new Class[]{Sub.class,stringArrayClass},new Class[]{Super.class,stringArrayClass},tc,ArgsMatchKind.CLOSE);

		// Passing (Integer, String[]) on call to (String, String[]) is exact match
		checkMatch2(new Class[]{Integer.class,stringArrayClass},new Class[]{String.class,stringArrayClass},tc,ArgsMatchKind.REQUIRES_CONVERSION,0);

		// Passing (Integer, Sub, String[]) on call to (String, Super, String[]) is exact match
		checkMatch2(new Class[]{Integer.class,Sub.class,String[].class},new Class[]{String.class,Super.class,String[].class},tc,ArgsMatchKind.REQUIRES_CONVERSION,0);
		
		// Passing (String) on call to (String[]) is exact match
		checkMatch2(new Class[]{String.class},new Class[]{stringArrayClass},tc,ArgsMatchKind.EXACT);
		
		// Passing (Integer,String) on call to (Integer,String[]) is exact match
		checkMatch2(new Class[]{Integer.class,String.class},new Class[]{Integer.class,stringArrayClass},tc,ArgsMatchKind.EXACT);

		// Passing (String) on call to (Integer[]) is conversion match (String to Integer)
		checkMatch2(new Class[]{String.class},new Class[]{integerArrayClass},tc,ArgsMatchKind.REQUIRES_CONVERSION,0);

		// Passing (Sub) on call to (Super[]) is close match
		checkMatch2(new Class[]{Sub.class},new Class[]{new Super[0].getClass()},tc,ArgsMatchKind.CLOSE);
		
		// Passing (Super) on call to (Sub[]) is not a match
		checkMatch2(new Class[]{Super.class},new Class[]{new Sub[0].getClass()},tc,null);

		checkMatch2(new Class[]{Unconvertable.class,String.class},new Class[]{Sub.class,Super[].class},tc,null);

		checkMatch2(new Class[]{Integer.class,Integer.class,String.class},new Class[]{String.class,String.class,Super[].class},tc,null);

		checkMatch2(new Class[]{Unconvertable.class,String.class},new Class[]{Sub.class,Super[].class},tc,null);

		checkMatch2(new Class[]{Integer.class,Integer.class,String.class},new Class[]{String.class,String.class,Super[].class},tc,null);

		checkMatch2(new Class[]{Integer.class,Integer.class,Sub.class},new Class[]{String.class,String.class,Super[].class},tc,ArgsMatchKind.REQUIRES_CONVERSION,0,1);

		checkMatch2(new Class[]{Integer.class,Integer.class,Integer.class},new Class[]{Integer.class,String[].class},tc,ArgsMatchKind.REQUIRES_CONVERSION,1,2);
		// what happens on (Integer,String) passed to (Integer[]) ?
	}

	@Test
	public void testConvertArguments() throws Exception {
		StandardTypeConverter tc = new StandardTypeConverter();
		Method oneArg = TestInterface.class.getMethod("oneArg", String.class);
		Method twoArg = TestInterface.class.getMethod("twoArg", String.class, String[].class);

		// basic conversion int>String
		Object[] args = new Object[]{3};
		ReflectionHelper.convertArguments(tc, args, oneArg, new int[]{0}, null);
		checkArguments(args, "3");

		// varargs but nothing to convert
		args = new Object[]{3};
		ReflectionHelper.convertArguments(tc, args, twoArg, new int[]{0}, 1);
		checkArguments(args, "3");

		// varargs with nothing needing conversion
		args = new Object[]{3,"abc","abc"};
		ReflectionHelper.convertArguments(tc, args, twoArg, new int[]{0,1,2}, 1);
		checkArguments(args, "3","abc","abc");

		// varargs with conversion required
		args = new Object[]{3,false,3.0d};
		ReflectionHelper.convertArguments(tc, args, twoArg, new int[]{0,1,2}, 1);
		checkArguments(args, "3","false","3.0");
	}

	@Test
	public void testConvertArguments2() throws Exception {
		StandardTypeConverter tc = new StandardTypeConverter();
		Method oneArg = TestInterface.class.getMethod("oneArg", String.class);
		Method twoArg = TestInterface.class.getMethod("twoArg", String.class, String[].class);

		// Simple conversion: int to string
		Object[] args = new Object[]{3};
		ReflectionHelper.convertAllArguments(tc, args, oneArg);
		checkArguments(args,"3");

		// varargs conversion
		args = new Object[]{3,false,3.0f};
		ReflectionHelper.convertAllArguments(tc, args, twoArg);
		checkArguments(args,"3","false","3.0");

		// varargs conversion but no varargs
		args = new Object[]{3};
		ReflectionHelper.convertAllArguments(tc, args, twoArg);
		checkArguments(args,"3");

		// missing converter
		args = new Object[]{3,false,3.0f};
		try {
			ReflectionHelper.convertAllArguments(null, args, twoArg);
			Assert.fail("Should have failed because no converter supplied");
		}
		catch (SpelEvaluationException se) {
			Assert.assertEquals(SpelMessage.TYPE_CONVERSION_ERROR,se.getMessageCode());
		}
		
		// null value
		args = new Object[]{3,null,3.0f};
		ReflectionHelper.convertAllArguments(tc, args, twoArg);
		checkArguments(args,"3",null,"3.0");
	}
	
	@Test
	public void testSetupArguments() {
		Object[] newArray = ReflectionHelper.setupArgumentsForVarargsInvocation(new Class[]{new String[0].getClass()},"a","b","c");
		
		Assert.assertEquals(1,newArray.length);
		Object firstParam = newArray[0];
		Assert.assertEquals(String.class,firstParam.getClass().getComponentType());
		Object[] firstParamArray = (Object[])firstParam;
		Assert.assertEquals(3,firstParamArray.length);
		Assert.assertEquals("a",firstParamArray[0]);
		Assert.assertEquals("b",firstParamArray[1]);
		Assert.assertEquals("c",firstParamArray[2]);
	}
	
	@Test
	public void testReflectivePropertyResolver() throws Exception {
		ReflectivePropertyAccessor rpr = new ReflectivePropertyAccessor();
		Tester t = new Tester();
		t.setProperty("hello");
		EvaluationContext ctx = new StandardEvaluationContext(t);
		Assert.assertTrue(rpr.canRead(ctx, t, "property"));
		Assert.assertEquals("hello",rpr.read(ctx, t, "property").getValue());
		Assert.assertEquals("hello",rpr.read(ctx, t, "property").getValue()); // cached accessor used

		Assert.assertTrue(rpr.canRead(ctx, t, "field"));
		Assert.assertEquals(3,rpr.read(ctx, t, "field").getValue());
		Assert.assertEquals(3,rpr.read(ctx, t, "field").getValue()); // cached accessor used
		
		Assert.assertTrue(rpr.canWrite(ctx, t, "property"));
		rpr.write(ctx, t, "property","goodbye");
		rpr.write(ctx, t, "property","goodbye"); // cached accessor used
				
		Assert.assertTrue(rpr.canWrite(ctx, t, "field"));
		rpr.write(ctx, t, "field",12);
		rpr.write(ctx, t, "field",12);

		// Attempted write as first activity on this field and property to drive testing 
		// of populating type descriptor cache
		rpr.write(ctx,t,"field2",3);
		rpr.write(ctx, t, "property2","doodoo");
		Assert.assertEquals(3,rpr.read(ctx,t,"field2").getValue());

		// Attempted read as first activity on this field and property (no canRead before them)
		Assert.assertEquals(0,rpr.read(ctx,t,"field3").getValue());
		Assert.assertEquals("doodoo",rpr.read(ctx,t,"property3").getValue());

		// Access through is method
//		Assert.assertEquals(0,rpr.read(ctx,t,"field3").getValue());
		Assert.assertEquals(false,rpr.read(ctx,t,"property4").getValue());
		Assert.assertTrue(rpr.canRead(ctx,t,"property4"));
		
	}
	
	@Test
	public void testOptimalReflectivePropertyResolver() throws Exception {
		ReflectivePropertyAccessor rpr = new ReflectivePropertyAccessor();
		Tester t = new Tester();
		t.setProperty("hello");
		EvaluationContext ctx = new StandardEvaluationContext(t);
//		Assert.assertTrue(rpr.canRead(ctx, t, "property"));
//		Assert.assertEquals("hello",rpr.read(ctx, t, "property").getValue());
//		Assert.assertEquals("hello",rpr.read(ctx, t, "property").getValue()); // cached accessor used
		
		PropertyAccessor optA = rpr.createOptimalAccessor(ctx, t, "property");
		Assert.assertTrue(optA.canRead(ctx, t, "property"));
		Assert.assertFalse(optA.canRead(ctx, t, "property2"));
		try {
			optA.canWrite(ctx, t, "property");
			Assert.fail();
		} catch (UnsupportedOperationException uoe) {
			// success
		}
		try {
			optA.canWrite(ctx, t, "property2");
			Assert.fail();
		} catch (UnsupportedOperationException uoe) {
			// success
		}
		Assert.assertEquals("hello",optA.read(ctx, t, "property").getValue());
		Assert.assertEquals("hello",optA.read(ctx, t, "property").getValue()); // cached accessor used

		try {
			optA.getSpecificTargetClasses();
			Assert.fail();
		} catch (UnsupportedOperationException uoe) {
			// success
		}
		try {
			optA.write(ctx,t,"property",null);
			Assert.fail();
		} catch (UnsupportedOperationException uoe) {
			// success
		}

		optA = rpr.createOptimalAccessor(ctx, t, "field");
		Assert.assertTrue(optA.canRead(ctx, t, "field"));
		Assert.assertFalse(optA.canRead(ctx, t, "field2"));
		try {
			optA.canWrite(ctx, t, "field");
			Assert.fail();
		} catch (UnsupportedOperationException uoe) {
			// success
		}
		try {
			optA.canWrite(ctx, t, "field2");
			Assert.fail();
		} catch (UnsupportedOperationException uoe) {
			// success
		}
		Assert.assertEquals(3,optA.read(ctx, t, "field").getValue());
		Assert.assertEquals(3,optA.read(ctx, t, "field").getValue()); // cached accessor used

		try {
			optA.getSpecificTargetClasses();
			Assert.fail();
		} catch (UnsupportedOperationException uoe) {
			// success
		}
		try {
			optA.write(ctx,t,"field",null);
			Assert.fail();
		} catch (UnsupportedOperationException uoe) {
			// success
		}


	}
	

	// test classes
	static class Tester {
		String property;
		public int field = 3;
		public int field2;
		public int field3 = 0;
		String property2;
		String property3 = "doodoo";
		boolean property4 = false;

		public String getProperty() { return property; }
		public void setProperty(String value) { property = value; }

		public void setProperty2(String value) { property2 = value; }

		public String getProperty3() { return property3; }
		
		public boolean isProperty4() { return property4; }
	}
	
	static class Super {
	}
	
	static class Sub extends Super {
	}
	
	static class Unconvertable {}
	
	// ---
	
	/**
	 * Used to validate the match returned from a compareArguments call.
	 */
	private void checkMatch(Class[] inputTypes, Class[] expectedTypes, StandardTypeConverter typeConverter,ArgsMatchKind expectedMatchKind,int... argsForConversion) {
		ReflectionHelper.ArgumentsMatchInfo matchInfo = ReflectionHelper.compareArguments(getTypeDescriptors(expectedTypes), getTypeDescriptors(inputTypes), typeConverter);
		if (expectedMatchKind==null) {
			Assert.assertNull("Did not expect them to match in any way", matchInfo);
		} else {
			Assert.assertNotNull("Should not be a null match", matchInfo);
		}

		if (expectedMatchKind==ArgsMatchKind.EXACT) {
			Assert.assertTrue(matchInfo.isExactMatch());
			Assert.assertNull(matchInfo.argsRequiringConversion);		
		} else if (expectedMatchKind==ArgsMatchKind.CLOSE) {
			Assert.assertTrue(matchInfo.isCloseMatch());
			Assert.assertNull(matchInfo.argsRequiringConversion);		
		} else if (expectedMatchKind==ArgsMatchKind.REQUIRES_CONVERSION) {
			Assert.assertTrue("expected to be a match requiring conversion, but was "+matchInfo,matchInfo.isMatchRequiringConversion());
			if (argsForConversion==null) {
				Assert.fail("there are arguments that need conversion");
			}
			Assert.assertEquals("The array of args that need conversion is different length to that expected",argsForConversion.length, matchInfo.argsRequiringConversion.length);
			for (int a=0;a<argsForConversion.length;a++) {
				Assert.assertEquals(argsForConversion[a],matchInfo.argsRequiringConversion[a]);
			}
		}
	}

	/**
	 * Used to validate the match returned from a compareArguments call.
	 */
	private void checkMatch2(Class[] inputTypes, Class[] expectedTypes, StandardTypeConverter typeConverter,ArgsMatchKind expectedMatchKind,int... argsForConversion) {
		ReflectionHelper.ArgumentsMatchInfo matchInfo = ReflectionHelper.compareArgumentsVarargs(getTypeDescriptors(expectedTypes), getTypeDescriptors(inputTypes), typeConverter);
		if (expectedMatchKind==null) {
			Assert.assertNull("Did not expect them to match in any way: "+matchInfo, matchInfo);
		} else {
			Assert.assertNotNull("Should not be a null match", matchInfo);
		}

		if (expectedMatchKind==ArgsMatchKind.EXACT) {
			Assert.assertTrue(matchInfo.isExactMatch());
			Assert.assertNull(matchInfo.argsRequiringConversion);		
		} else if (expectedMatchKind==ArgsMatchKind.CLOSE) {
			Assert.assertTrue(matchInfo.isCloseMatch());
			Assert.assertNull(matchInfo.argsRequiringConversion);		
		} else if (expectedMatchKind==ArgsMatchKind.REQUIRES_CONVERSION) {
			Assert.assertTrue("expected to be a match requiring conversion, but was "+matchInfo,matchInfo.isMatchRequiringConversion());
			if (argsForConversion==null) {
				Assert.fail("there are arguments that need conversion");
			}
			Assert.assertEquals("The array of args that need conversion is different length to that expected",argsForConversion.length, matchInfo.argsRequiringConversion.length);
			for (int a=0;a<argsForConversion.length;a++) {
				Assert.assertEquals(argsForConversion[a],matchInfo.argsRequiringConversion[a]);
			}
		}
	}

	private void checkArguments(Object[] args, Object... expected) {
		Assert.assertEquals(expected.length,args.length);
		for (int i=0;i<expected.length;i++) {
			checkArgument(expected[i],args[i]);
		}
	}
	
	private void checkArgument(Object expected, Object actual) {
		Assert.assertEquals(expected,actual);
	}

	private List<TypeDescriptor> getTypeDescriptors(Class... types) {
		List<TypeDescriptor> typeDescriptors = new ArrayList<TypeDescriptor>(types.length);
		for (Class type : types) {
			typeDescriptors.add(TypeDescriptor.valueOf(type));
		}
		return typeDescriptors;
	}


	public interface TestInterface {

		void oneArg(String arg1);

		void twoArg(String arg1, String... arg2);
	}

}
