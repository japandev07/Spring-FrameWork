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

package org.springframework.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Factory for collections that is aware of Java 5 and Java 6 collection types.
 * <p>Mainly for internal use within the framework.
 * <p>The goal of this class is to avoid runtime dependencies on a specific
 * Java version, while nevertheless using the best collection implementation
 * that is available at runtime.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @author Oliver Gierke
 * @author Sam Brannen
 * @since 1.1.1
 */
public abstract class CollectionFactory {

	private static final Set<Class<?>> approximableCollectionTypes = new HashSet<Class<?>>(11);

	private static final Set<Class<?>> approximableMapTypes = new HashSet<Class<?>>(7);


	static {
		// Standard collection interfaces
		approximableCollectionTypes.add(Collection.class);
		approximableCollectionTypes.add(List.class);
		approximableCollectionTypes.add(Set.class);
		approximableCollectionTypes.add(SortedSet.class);
		approximableCollectionTypes.add(NavigableSet.class);
		approximableMapTypes.add(Map.class);
		approximableMapTypes.add(SortedMap.class);
		approximableMapTypes.add(NavigableMap.class);

		// Common concrete collection classes
		approximableCollectionTypes.add(ArrayList.class);
		approximableCollectionTypes.add(LinkedList.class);
		approximableCollectionTypes.add(HashSet.class);
		approximableCollectionTypes.add(LinkedHashSet.class);
		approximableCollectionTypes.add(TreeSet.class);
		approximableCollectionTypes.add(EnumSet.class);
		approximableMapTypes.add(HashMap.class);
		approximableMapTypes.add(LinkedHashMap.class);
		approximableMapTypes.add(TreeMap.class);
		approximableMapTypes.add(EnumMap.class);
	}


	/**
	 * Determine whether the given collection type is an <em>approximable</em> type,
	 * i.e. a type that {@link #createApproximateCollection} can approximate.
	 * @param collectionType the collection type to check
	 * @return {@code true} if the type is <em>approximable</em>
	 */
	public static boolean isApproximableCollectionType(Class<?> collectionType) {
		return (collectionType != null && approximableCollectionTypes.contains(collectionType));
	}

	/**
	 * Create the most approximate collection for the given collection.
	 * @param collection the original collection object
	 * @param capacity the initial capacity
	 * @return the new, empty collection instance
	 * @see #isApproximableCollectionType
	 * @see java.util.LinkedList
	 * @see java.util.ArrayList
	 * @see java.util.EnumSet
	 * @see java.util.TreeSet
	 * @see java.util.LinkedHashSet
	 */
	@SuppressWarnings({ "unchecked", "cast", "rawtypes" })
	public static <E> Collection<E> createApproximateCollection(Object collection, int capacity) {
		if (collection instanceof LinkedList) {
			return new LinkedList<E>();
		}
		else if (collection instanceof List) {
			return new ArrayList<E>(capacity);
		}
		else if (collection instanceof EnumSet) {
			// Cast is necessary for compilation in Eclipse 4.4.1.
			Collection<E> enumSet = (Collection<E>) EnumSet.copyOf((EnumSet) collection);
			enumSet.clear();
			return enumSet;
		}
		else if (collection instanceof SortedSet) {
			return new TreeSet<E>(((SortedSet<E>) collection).comparator());
		}
		else {
			return new LinkedHashSet<E>(capacity);
		}
	}

	/**
	 * Create the most appropriate collection for the given collection type.
	 * <p>Delegates to {@link #createCollection(Class, Class, int)} with a
	 * {@code null} element type.
	 * @param collectionType the desired type of the target collection
	 * @param capacity the initial capacity
	 * @return the new collection instance
	 */
	public static <E> Collection<E> createCollection(Class<?> collectionType, int capacity) {
		return createCollection(collectionType, null, capacity);
	}

	/**
	 * Create the most appropriate collection for the given collection type.
	 * @param collectionType the desired type of the target collection; never {@code null}
	 * @param elementType the collection's element type, or {@code null} if not known
	 * (note: only relevant for {@link EnumSet} creation)
	 * @param capacity the initial capacity
	 * @return the new collection instance
	 * @since 4.1.3
	 * @see java.util.LinkedHashSet
	 * @see java.util.ArrayList
	 * @see java.util.TreeSet
	 * @see java.util.EnumSet
	 */
	@SuppressWarnings({ "unchecked", "cast" })
	public static <E> Collection<E> createCollection(Class<?> collectionType, Class<?> elementType, int capacity) {
		Assert.notNull(collectionType, "Collection type must not be null");
		if (collectionType.isInterface()) {
			if (Set.class.equals(collectionType) || Collection.class.equals(collectionType)) {
				return new LinkedHashSet<E>(capacity);
			}
			else if (List.class.equals(collectionType)) {
				return new ArrayList<E>(capacity);
			}
			else if (SortedSet.class.equals(collectionType) || NavigableSet.class.equals(collectionType)) {
				return new TreeSet<E>();
			}
			else {
				throw new IllegalArgumentException("Unsupported Collection interface: " + collectionType.getName());
			}
		}
		else if (EnumSet.class.equals(collectionType)) {
			Assert.notNull(elementType, "Cannot create EnumSet for unknown element type");
			// Cast is necessary for compilation in Eclipse 4.4.1.
			return (Collection<E>) EnumSet.noneOf(asEnumType(elementType));
		}
		else {
			if (!Collection.class.isAssignableFrom(collectionType)) {
				throw new IllegalArgumentException("Unsupported Collection type: " + collectionType.getName());
			}
			try {
				return (Collection<E>) collectionType.newInstance();
			}
			catch (Exception ex) {
				throw new IllegalArgumentException(
					"Could not instantiate Collection type: " + collectionType.getName(), ex);
			}
		}
	}

	/**
	 * Determine whether the given map type is an <em>approximable</em> type,
	 * i.e. a type that {@link #createApproximateMap} can approximate.
	 * @param mapType the map type to check
	 * @return {@code true} if the type is <em>approximable</em>
	 */
	public static boolean isApproximableMapType(Class<?> mapType) {
		return (mapType != null && approximableMapTypes.contains(mapType));
	}

	/**
	 * Create the most approximate map for the given map.
	 * @param map the original Map object
	 * @param capacity the initial capacity
	 * @return the new, empty Map instance
	 * @see #isApproximableMapType
	 * @see java.util.EnumMap
	 * @see java.util.TreeMap
	 * @see java.util.LinkedHashMap
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <K, V> Map<K, V> createApproximateMap(Object map, int capacity) {
		if (map instanceof EnumMap) {
			EnumMap enumMap = new EnumMap((EnumMap) map);
			enumMap.clear();
			return enumMap;
		}
		else if (map instanceof SortedMap) {
			return new TreeMap<K, V>(((SortedMap<K, V>) map).comparator());
		}
		else {
			return new LinkedHashMap<K, V>(capacity);
		}
	}

	/**
	 * Create the most appropriate map for the given map type.
	 * <p>Delegates to {@link #createMap(Class, Class, int)} with a
	 * {@code null} key type.
	 * @param mapType the desired type of the target Map
	 * @param capacity the initial capacity
	 * @return the new Map instance
	 */
	public static <K, V> Map<K, V> createMap(Class<?> mapType, int capacity) {
		return createMap(mapType, null, capacity);
	}

	/**
	 * Create the most appropriate map for the given map type.
	 * @param mapType the desired type of the target Map
	 * @param keyType the map's key type, or {@code null} if not known
	 * (note: only relevant for {@link EnumMap} creation)
	 * @param capacity the initial capacity
	 * @return the new Map instance
	 * @since 4.1.3
	 * @see java.util.LinkedHashMap
	 * @see java.util.TreeMap
	 * @see org.springframework.util.LinkedMultiValueMap
	 * @see java.util.EnumMap
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <K, V> Map<K, V> createMap(Class<?> mapType, Class<?> keyType, int capacity) {
		Assert.notNull(mapType, "Map type must not be null");
		if (mapType.isInterface()) {
			if (Map.class.equals(mapType)) {
				return new LinkedHashMap<K, V>(capacity);
			}
			else if (SortedMap.class.equals(mapType) || NavigableMap.class.equals(mapType)) {
				return new TreeMap<K, V>();
			}
			else if (MultiValueMap.class.equals(mapType)) {
				return new LinkedMultiValueMap();
			}
			else {
				throw new IllegalArgumentException("Unsupported Map interface: " + mapType.getName());
			}
		}
		else if (EnumMap.class.equals(mapType)) {
			Assert.notNull(keyType, "Cannot create EnumMap for unknown key type");
			return new EnumMap(asEnumType(keyType));
		}
		else {
			if (!Map.class.isAssignableFrom(mapType)) {
				throw new IllegalArgumentException("Unsupported Map type: " + mapType.getName());
			}
			try {
				return (Map<K, V>) mapType.newInstance();
			}
			catch (Exception ex) {
				throw new IllegalArgumentException("Could not instantiate Map type: " + mapType.getName(), ex);
			}
		}
	}

	/**
	 * Cast the given type to a subtype of {@link Enum}.
	 * @param enumType the enum type, never {@code null}
	 * @return the given type as subtype of {@link Enum}
	 * @throws IllegalArgumentException if the given type is not a subtype of {@link Enum}
	 */
	@SuppressWarnings("rawtypes")
	private static Class<? extends Enum> asEnumType(Class<?> enumType) {
		Assert.notNull(enumType, "Enum type must not be null");
		if (!Enum.class.isAssignableFrom(enumType)) {
			throw new IllegalArgumentException("Supplied type is not an enum: " + enumType.getName());
		}
		return enumType.asSubclass(Enum.class);
	}

}
