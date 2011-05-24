/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.core.convert;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.springframework.core.GenericCollectionTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

/**
 * Context about a type to convert to.
 *
 * @author Keith Donald
 * @author Andy Clement
 * @author Juergen Hoeller
 * @since 3.0
 */
public class TypeDescriptor {

	/** Constant defining a TypeDescriptor for a <code>null</code> value */
	public static final TypeDescriptor NULL = new TypeDescriptor();

	private static final Map<Class<?>, TypeDescriptor> typeDescriptorCache = new HashMap<Class<?>, TypeDescriptor>();

	private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

	static {
		typeDescriptorCache.put(boolean.class, new TypeDescriptor(boolean.class));
		typeDescriptorCache.put(Boolean.class, new TypeDescriptor(Boolean.class));
		typeDescriptorCache.put(byte.class, new TypeDescriptor(byte.class));
		typeDescriptorCache.put(Byte.class, new TypeDescriptor(Byte.class));
		typeDescriptorCache.put(char.class, new TypeDescriptor(char.class));
		typeDescriptorCache.put(Character.class, new TypeDescriptor(Character.class));
		typeDescriptorCache.put(short.class, new TypeDescriptor(short.class));
		typeDescriptorCache.put(Short.class, new TypeDescriptor(Short.class));
		typeDescriptorCache.put(int.class, new TypeDescriptor(int.class));
		typeDescriptorCache.put(Integer.class, new TypeDescriptor(Integer.class));
		typeDescriptorCache.put(long.class, new TypeDescriptor(long.class));
		typeDescriptorCache.put(Long.class, new TypeDescriptor(Long.class));
		typeDescriptorCache.put(float.class, new TypeDescriptor(float.class));
		typeDescriptorCache.put(Float.class, new TypeDescriptor(Float.class));
		typeDescriptorCache.put(double.class, new TypeDescriptor(double.class));
		typeDescriptorCache.put(Double.class, new TypeDescriptor(Double.class));
		typeDescriptorCache.put(String.class, new TypeDescriptor(String.class));
	}


	private Class<?> type;

	private MethodParameter methodParameter;

	private Field field;

	private int fieldNestingLevel = 1;

	private TypeDescriptor elementType;

	private TypeDescriptor mapKeyType;

	private TypeDescriptor mapValueType;

	private Annotation[] annotations;


	/**
	 * Create a new type descriptor from a method or constructor parameter.
	 * Use this constructor when a target conversion point originates from a method parameter, such as a setter method argument.
	 * @param methodParameter the MethodParameter to wrap
	 */
	public TypeDescriptor(MethodParameter methodParameter) {
		Assert.notNull(methodParameter, "MethodParameter must not be null");
		this.type = methodParameter.getParameterType();
		this.methodParameter = methodParameter;
	}

	/**
	 * Create a new type descriptor for a field.
	 * Use this constructor when a target conversion point originates from a field.
	 * @param field the field to wrap
	 */
	public TypeDescriptor(Field field) {
		Assert.notNull(field, "Field must not be null");
		this.type = field.getType();
		this.field = field;
	}

	/**
	 * Create a new type descriptor for the given class.
	 * Use this to instruct the conversion system to convert to an object to a specific target type, when no type location such as a method parameter or field is available to provide additional conversion context.
	 * Generally prefer use of {@link #forObject(Object)} for constructing source type descriptors for source objects.
	 * @param type the class
	 * @return the type descriptor
	 */
	public static TypeDescriptor valueOf(Class<?> type) {
		if (type == null) {
			return NULL;
		}
		TypeDescriptor desc = typeDescriptorCache.get(type);
		return (desc != null ? desc : new TypeDescriptor(type));
	}
	
	/**
	 * Create a new type descriptor for an object.
	 * Use this factory method to introspect a source object's type before asking the conversion system to convert it to some another type.
	 * Builds in population of nested type descriptors for collection and map objects through object introspection.
	 * If the object is null, returns {@link TypeDescriptor#NULL}.
	 * If the object is not a collection or map, simply calls {@link #valueOf(Class)}.
	 * If the object is a collection or map, this factory method will derive the element type(s) by introspecting the collection or map.
	 * @param object the source object
	 * @return the type descriptor
	 * @see ConversionService#convert(Object, Class)
	 * @see CollectionUtils#findCommonElementType(Collection)
	 */
	public static TypeDescriptor forObject(Object object) {
		if (object == null) {
			return NULL;
		}
		if (object instanceof Collection<?>) {
			return new TypeDescriptor(object.getClass(), findCommonElement((Collection<?>) object));
		}
		else if (object instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) object;
			return new TypeDescriptor(map.getClass(), findCommonElement(map.keySet()), findCommonElement(map.values()));
		}
		else {
			return valueOf(object.getClass());
		}
	}

	/**
	 * Create a new type descriptor for a nested type declared on an array, collection, or map-based method parameter.
	 * Use this factory method when you've resolved a nested source object such as a collection element or map value and wish to have it converted.
	 * @param methodParameter the method parameter declaring the collection or map
	 * @return the nested type descriptor
	 */
	public static TypeDescriptor forNestedType(MethodParameter methodParameter) {
		return new TypeDescriptor(resolveNestedType(methodParameter), methodParameter);
	}

	/**
	 * Create a new type descriptor for a nested type declared on an array, collection, or map-based method parameter.
	 * Use this factory method when you've resolved a nested source object such as a collection element or map value and wish to have it converted.
	 * @param nestedType the nested type
	 * @param methodParameter the method parameter declaring the collection or map
	 * @return the nested type descriptor
	 */
	public static TypeDescriptor forNestedType(Class<?> nestedType, MethodParameter methodParameter) {
		return new TypeDescriptor(nestedType, methodParameter);
	}

	/**
	 * Determine the declared (non-generic) type of the wrapped parameter/field.
	 * @return the declared type, or <code>null</code> if this is {@link TypeDescriptor#NULL}
	 */
	public Class<?> getType() {
		return type;
	}

	/**
	 * Determine the declared type of the wrapped parameter/field.
	 * Returns the Object wrapper type if the underlying type is a primitive.
	 */
	public Class<?> getObjectType() {
		return ClassUtils.resolvePrimitiveIfNecessary(getType());
	}

	/**
	 * Returns the name of this type: the fully qualified class name.
	 */
	public String getName() {
		return ClassUtils.getQualifiedName(getType());
	}

	/**
	 * Is this type a primitive type?
	 */
	public boolean isPrimitive() {
		return getType().isPrimitive();
	}

	/**
	 * Obtain the annotations associated with the wrapped parameter/field, if any.
	 */
	public synchronized Annotation[] getAnnotations() {
		if (this.annotations == null) {
			this.annotations = resolveAnnotations();
		}
		return this.annotations;
	}

	/**
	 * Obtain the annotation associated with the wrapped parameter/field, if any.
	 */
	public Annotation getAnnotation(Class<? extends Annotation> annotationType) {
		for (Annotation annotation : getAnnotations()) {
			if (annotation.annotationType().equals(annotationType)) {
				return annotation;
			}
		}
		return null;
	}

	/**
	 * Returns true if an object of this type can be assigned to a reference of given targetType.
	 * @param targetType the target type
	 * @return true if this type is assignable to the target
	 */
	public boolean isAssignableTo(TypeDescriptor targetType) {
		if (this == TypeDescriptor.NULL || targetType == TypeDescriptor.NULL) {
			return true;
		}
		if (isCollection() && targetType.isCollection() || isArray() && targetType.isArray()) {
			return targetType.getType().isAssignableFrom(getType()) &&
					getElementTypeDescriptor().isAssignableTo(targetType.getElementTypeDescriptor());
		}
		else if (isMap() && targetType.isMap()) {
			return targetType.getType().isAssignableFrom(getType()) &&
					getMapKeyTypeDescriptor().isAssignableTo(targetType.getMapKeyTypeDescriptor()) &&
					getMapValueTypeDescriptor().isAssignableTo(targetType.getMapValueTypeDescriptor());
		}
		else {
			return targetType.getObjectType().isAssignableFrom(getObjectType());
		}
	}

	/**
	 * A textual representation of the type descriptor (eg. Map<String,Foo>) for use in messages.
	 */
	public String asString() {
		return toString();
	}
	
	// indexable type descriptor operations
	
	/**
	 * Is this type an array type?
	 */
	public boolean isArray() {
		return getType().isArray();
	}

	/**
	 * Is this type a {@link Collection} type?
	 */
	public boolean isCollection() {
		return Collection.class.isAssignableFrom(getType());
	}

	/**
	 * If this type is an array type or {@link Collection} type, returns the underlying element type.
	 * Returns <code>null</code> if the type is neither an array or collection.
	 */
	public Class<?> getElementType() {
		return getElementTypeDescriptor().getType();
	}

	/**
	 * Return the element type as a type descriptor.
	 */
	public synchronized TypeDescriptor getElementTypeDescriptor() {
		if (this.elementType == null) {
			this.elementType = resolveElementTypeDescriptor();
		}
		return this.elementType;
	}

	// map type descriptor operations
	
	/**
	 * Is this type a {@link Map} type?
	 */
	public boolean isMap() {
		return Map.class.isAssignableFrom(getType());
	}

	/**
	 * Determine the generic key type of the wrapped Map parameter/field, if any.
	 * @return the generic type, or <code>null</code> if none
	 */
	public Class<?> getMapKeyType() {
		return getMapKeyTypeDescriptor().getType();
	}

	/**
	 * Returns map key type as a type descriptor.
	 */
	public synchronized TypeDescriptor getMapKeyTypeDescriptor() {
		if (this.mapKeyType == null) {
			this.mapKeyType = resolveMapKeyTypeDescriptor();
		}
		return this.mapKeyType;
	}

	/**
	 * Determine the generic value type of the wrapped Map parameter/field, if any.
	 * @return the generic type, or <code>null</code> if none
	 */
	public Class<?> getMapValueType() {
		return getMapValueTypeDescriptor().getType();
	}

	/**
	 * Returns map value type as a type descriptor.
	 */
	public synchronized TypeDescriptor getMapValueTypeDescriptor() {
		if (this.mapValueType == null) {
			this.mapValueType = resolveMapValueTypeDescriptor();
		}
		return this.mapValueType;
	}
	
	// special case public operations

	/**
	 * Exposes the underlying MethodParameter providing context for this TypeDescriptor.
	 * Used to support legacy code scenarios where callers are already using the MethodParameter API (BeanWrapper).
	 * In general, favor use of the TypeDescriptor API over the MethodParameter API as it is independent of type context location.
	 * May be null if no MethodParameter was provided when this TypeDescriptor was constructed.
	 */
	public MethodParameter getMethodParameter() {
		return methodParameter;
	}
	
	// extending Object
	
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof TypeDescriptor) || obj == TypeDescriptor.NULL) {
			return false;
		}
		TypeDescriptor other = (TypeDescriptor) obj;
		boolean annotatedTypeEquals = getType().equals(other.getType()) && ObjectUtils.nullSafeEquals(getAnnotations(), other.getAnnotations());
		if (isCollection()) {
			return annotatedTypeEquals && ObjectUtils.nullSafeEquals(getElementType(), other.getElementType());
		}
		else if (isMap()) {
			return annotatedTypeEquals && ObjectUtils.nullSafeEquals(getMapKeyType(), other.getMapKeyType()) &&
					ObjectUtils.nullSafeEquals(getMapValueType(), other.getMapValueType());
		}
		else {
			return annotatedTypeEquals;
		}
	}

	public int hashCode() {
		return (this == TypeDescriptor.NULL ? 0 : getType().hashCode());
	}

	public String toString() {
		if (this == TypeDescriptor.NULL) {
			return "null";
		}
		else {
			StringBuilder builder = new StringBuilder();
			Annotation[] anns = getAnnotations();
			for (Annotation ann : anns) {
				builder.append("@").append(ann.annotationType().getName()).append(' ');
			}
			builder.append(ClassUtils.getQualifiedName(getType()));
			if (isMap()) {
				builder.append("<").append(getMapKeyTypeDescriptor());
				builder.append(", ").append(getMapValueTypeDescriptor()).append(">");
			}
			else if (isCollection()) {
				builder.append("<").append(getElementTypeDescriptor()).append(">");
			}
			return builder.toString();
		}
	}

	// subclassing hooks

	protected TypeDescriptor(Class<?> nestedType, MethodParameter methodParameter) {
		this.type = handleUnknownNestedType(nestedType);
		this.methodParameter = createNestedMethodParameter(methodParameter);
	}
	
	protected Annotation[] resolveAnnotations() {
		if (this.field != null) {
			return this.field.getAnnotations();
		}
		else if (this.methodParameter != null) {
			if (this.methodParameter.getParameterIndex() < 0) {
				return this.methodParameter.getMethodAnnotations();
			}
			else {
				return this.methodParameter.getParameterAnnotations();
			}
		}
		else {
			return EMPTY_ANNOTATION_ARRAY;
		}
	}

	protected TypeDescriptor newNestedTypeDescriptor(Class<?> nestedType, MethodParameter nested) {
		return new TypeDescriptor(nestedType, nested);
	}
	
	protected static Class<?> resolveNestedType(MethodParameter methodParameter) {
		if (Collection.class.isAssignableFrom(methodParameter.getParameterType())) {
			return GenericCollectionTypeResolver.getCollectionParameterType(methodParameter);
		} else if (Map.class.isAssignableFrom(methodParameter.getParameterType())) {
			return GenericCollectionTypeResolver.getMapValueParameterType(methodParameter);			
		} else if (methodParameter.getParameterType().isArray()) {
			return methodParameter.getParameterType().getComponentType();
		} else {			
			throw new IllegalStateException("Not a collection, map, or array method parameter type " + methodParameter.getParameterType());
		}
	}
	
	// internal helpers

	private TypeDescriptor resolveElementTypeDescriptor() {
		if (isCollection()) {
			return createNestedTypeDescriptor(resolveCollectionElementType());
		}
		else {
			// TODO: GenericCollectionTypeResolver is not capable of applying nesting levels to array fields;
			// this means generic info of nested lists or maps stored inside array method parameters or fields is not obtainable
			return createNestedTypeDescriptor(getType().getComponentType());
		}
	}

	private TypeDescriptor resolveMapKeyTypeDescriptor() {
		return createNestedTypeDescriptor(resolveMapKeyType());
	}

	private TypeDescriptor resolveMapValueTypeDescriptor() {
		return createNestedTypeDescriptor(resolveMapValueType());
	}

	private TypeDescriptor createNestedTypeDescriptor(Class<?> nestedType) {
		nestedType = handleUnknownNestedType(nestedType);
		if (this.methodParameter != null) {
			return newNestedTypeDescriptor(nestedType, createNestedMethodParameter(this.methodParameter));				
		}
		else if (this.field != null) {
			return new TypeDescriptor(nestedType, this.field, this.fieldNestingLevel + 1);
		}
		else {
			return TypeDescriptor.valueOf(nestedType);
		}
	}
	
	@SuppressWarnings("unchecked")
	private Class<?> resolveCollectionElementType() {
		if (this.methodParameter != null) {
			return GenericCollectionTypeResolver.getCollectionParameterType(this.methodParameter);
		}
		else if (this.field != null) {
			return GenericCollectionTypeResolver.getCollectionFieldType(this.field, this.fieldNestingLevel);
		}
        else {
			return GenericCollectionTypeResolver.getCollectionType((Class<? extends Collection<?>>) this.type);
		}		
	}
	
	@SuppressWarnings("unchecked")
	private Class<?> resolveMapKeyType() {
		if (this.methodParameter != null) {
			return GenericCollectionTypeResolver.getMapKeyParameterType(this.methodParameter);
		}
		else if (this.field != null) {
			return GenericCollectionTypeResolver.getMapKeyFieldType(this.field, this.fieldNestingLevel);
		}
		else {
			return GenericCollectionTypeResolver.getMapKeyType((Class<? extends Map<?, ?>>) this.type);
		}
	}

	@SuppressWarnings("unchecked")
	private Class<?> resolveMapValueType() {
		if (this.methodParameter != null) {
			return GenericCollectionTypeResolver.getMapValueParameterType(this.methodParameter);
		}
		else if (this.field != null) {
			return GenericCollectionTypeResolver.getMapValueFieldType(this.field, this.fieldNestingLevel);
		}
		else {
			return GenericCollectionTypeResolver.getMapValueType((Class<? extends Map<?, ?>>) this.type);
		}
	}

	private Class<?> handleUnknownNestedType(Class<?> nestedType) {
		return nestedType != null ? nestedType : Object.class;
	}

	private MethodParameter createNestedMethodParameter(MethodParameter parentMethodParameter) {
		MethodParameter methodParameter = new MethodParameter(parentMethodParameter);
		methodParameter.increaseNestingLevel();
		return methodParameter;
	}

	private static CommonElement findCommonElement(Collection<?> values) {
		Class<?> commonType = null;
		Object candidate = null;
		for (Object value : values) {
			if (value != null) {
				if (candidate == null) {
					commonType = value.getClass();
					candidate = value;
				} else {
					commonType = commonType(commonType, value.getClass());
					if (commonType == Object.class) {
						return null;
					}
				}
			}
		}
		return new CommonElement(commonType, candidate);
	}

	private static Class<?> commonType(Class<?> commonType, Class<?> valueClass) {
		Set<Class<?>> interfaces = new LinkedHashSet<Class<?>>();
		LinkedList<Class<?>> classQueue = new LinkedList<Class<?>>();
		classQueue.addFirst(commonType);
		while (!classQueue.isEmpty()) {
			Class<?> currentClass = classQueue.removeLast();
			if (currentClass.isAssignableFrom(valueClass)) {
				return currentClass;
			}
			Class<?> superClass = currentClass.getSuperclass();
			if (superClass != null && superClass != Object.class) {
				classQueue.addFirst(currentClass.getSuperclass());
			}
			for (Class<?> interfaceType : currentClass.getInterfaces()) {
				addInterfaceHierarchy(interfaceType, interfaces);
			}
		}
		for (Class<?> interfaceType : interfaces) {
			if (interfaceType.isAssignableFrom(valueClass)) {
				return interfaceType;
			}			
		}
		return Object.class;
	}

	private static void addInterfaceHierarchy(Class<?> interfaceType, Set<Class<?>> interfaces) {
		interfaces.add(interfaceType);
		for (Class<?> inheritedInterface : interfaceType.getInterfaces()) {
			addInterfaceHierarchy(inheritedInterface, interfaces);
		}
	}

	// internal constructors

	private TypeDescriptor() {
	}

	private TypeDescriptor(Class<?> type) {
		Assert.notNull(type, "Type must not be null");
		this.type = type;
	}

	private TypeDescriptor(Class<?> nestedType, Field field, int nestingLevel) {
		this.type = nestedType;
		this.field = field;
		this.fieldNestingLevel = nestingLevel;
	}

	private TypeDescriptor(Class<?> mapType, CommonElement commonKey, CommonElement commonValue) {
		this.type = mapType;
		this.mapKeyType = applyCommonElement(commonKey);
		this.mapValueType = applyCommonElement(commonValue);
	}
	
	private TypeDescriptor(Class<?> collectionType, CommonElement commonElement) {
		this.type = collectionType;
		this.elementType = applyCommonElement(commonElement);
	}

	private TypeDescriptor applyCommonElement(CommonElement commonElement) {
		if (commonElement == null) {
			return TypeDescriptor.valueOf(Object.class);
		}
		if (commonElement.getValue() instanceof Collection<?>) {
			Collection<?> collection = (Collection<?>) commonElement.getValue();
			if (collection.size() == 0) {
				return TypeDescriptor.valueOf(Object.class);
			}
			return new TypeDescriptor(commonElement.getType(), findCommonElement(collection));			
		}
		else if (commonElement.getValue() instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) commonElement.getValue();
			if (map.size() == 0) {
				return TypeDescriptor.valueOf(Object.class);				
			}
			return new TypeDescriptor(commonElement.getType(), findCommonElement(map.keySet()), findCommonElement(map.values()));
		}
		else {
			return TypeDescriptor.valueOf(commonElement.getType());
		}
	}

	// inner classes
	
	private static class CommonElement {
		
		private Class<?> type;
		
		private Object value;

		public CommonElement(Class<?> type, Object value) {
			this.type = type;
			this.value = value;
		}

		public Class<?> getType() {
			return type;
		}

		public Object getValue() {
			return value;
		}
		
	}

}