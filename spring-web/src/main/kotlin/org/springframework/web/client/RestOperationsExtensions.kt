/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.client

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import java.net.URI


/**
 * Extension for [RestOperations.getForObject] avoiding specifying the type
 * parameter thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.getForObject(url: String, vararg uriVariables: Any): T? =
		getForObject(url, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.getForObject] avoiding specifying the type
 * parameter thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.getForObject(url: String, uriVariables: Map<String, Any?>): T? =
		getForObject(url, T::class.java, uriVariables)

/**
 * Extension for [RestOperations.getForObject] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.getForObject(url: URI): T? =
		getForObject(url, T::class.java)

/**
 * Extension for [RestOperations.getForEntity] avoiding requiring the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.getForEntity(url: String, vararg uriVariables: Any): ResponseEntity<T> =
		getForEntity(url, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.postForObject] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForObject(url: String, request: Any, vararg uriVariables: Any): T? =
		postForObject(url, request, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.postForObject] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForObject(url: String, request: Any, uriVariables: Map<String, *>): T? =
		postForObject(url, request, T::class.java, uriVariables)

/**
 * Extension for [RestOperations.postForObject] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForObject(url: URI, request: Any): T? =
		postForObject(url, request, T::class.java)

/**
 * Extension for [RestOperations.postForEntity] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForEntity(url: String, request: Any, vararg uriVariables: Any): ResponseEntity<T> =
		postForEntity(url, request, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.postForEntity] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForEntity(url: String, request: Any, uriVariables: Map<String, *>): ResponseEntity<T> =
		postForEntity(url, request, T::class.java, uriVariables)

/**
 * Extension for [RestOperations.postForEntity] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForEntity(url: URI, request: Any): ResponseEntity<T> =
		postForEntity(url, request, T::class.java)

/**
 * Extension for [RestOperations.exchange] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.exchange(url: String, method: HttpMethod, requestEntity: HttpEntity<*>, vararg uriVariables: Any): ResponseEntity<T> =
		exchange(url, method, requestEntity, object : ParameterizedTypeReference<T>() {}, *uriVariables)

/**
 * Extension for [RestOperations.exchange] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.exchange(url: String, method: HttpMethod, requestEntity: HttpEntity<*>, uriVariables: Map<String, *>): ResponseEntity<T> =
		exchange(url, method, requestEntity, object : ParameterizedTypeReference<T>() {}, uriVariables)

/**
 * Extension for [RestOperations.exchange] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.exchange(url: URI, method: HttpMethod, requestEntity: HttpEntity<*>): ResponseEntity<T> =
		exchange(url, method, requestEntity, object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [RestOperations.exchange] avoiding specifying the type parameter
 * thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.exchange(requestEntity: RequestEntity<*>): ResponseEntity<T> =
		exchange(requestEntity, object : ParameterizedTypeReference<T>() {})
