package org.springframework.web.client

import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.RequestEntity
import java.net.URI


/**
 * Extension for [RestOperations.getForObject] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.getForObject(url: String, vararg uriVariables: Any) =
		getForObject(url, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.getForObject] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.getForObject(url: String, uriVariables: Map<String, Any?>) =
		getForObject(url, T::class.java, uriVariables)

/**
 * Extension for [RestOperations.getForObject] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.getForObject(url: URI) =
		getForObject(url, T::class.java)

/**
 * Extension for [RestOperations.getForEntity] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.getForEntity(url: String, vararg uriVariables: Any) =
		getForEntity(url, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.postForObject] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForObject(url: String, request: Any, vararg uriVariables: Any) =
		postForObject(url, request, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.postForObject] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForObject(url: String, request: Any, uriVariables: Map<String, *>) =
		postForObject(url, request, T::class.java, uriVariables)

/**
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForObject(url: URI, request: Any) =
		postForObject(url, request, T::class.java)

/**
 * Extension for [RestOperations.postForEntity] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForEntity(url: String, request: Any, vararg uriVariables: Any) =
		postForEntity(url, request, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.postForEntity] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForEntity(url: String, request: Any, uriVariables: Map<String, *>) =
		postForEntity(url, request, T::class.java, uriVariables)

/**
 * Extension for [RestOperations.postForEntity] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.postForEntity(url: URI, request: Any) =
		postForEntity(url, request, T::class.java)

/**
 * Extension for [RestOperations.exchange] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.exchange(url: String, method: HttpMethod, requestEntity: HttpEntity<*>, vararg uriVariables: Any) =
		exchange(url, method, requestEntity, T::class.java, *uriVariables)

/**
 * Extension for [RestOperations.exchange] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.exchange(url: String, method: HttpMethod, requestEntity: HttpEntity<*>, uriVariables: Map<String, *>) =
		exchange(url, method, requestEntity, T::class.java, uriVariables)

/**
 * Extension for [RestOperations.exchange] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.exchange(url: URI, method: HttpMethod, requestEntity: HttpEntity<*>) =
		exchange(url, method, requestEntity, T::class.java)

/**
 * Extension for [RestOperations.exchange] avoiding specifying thanks to Kotlin reified type parameters.
 *
 * @author Jon Schneider
 * @author Sebastien Deleuze
 * @since 5.0
 */
@Throws(RestClientException::class)
inline fun <reified T: Any> RestOperations.exchange(requestEntity: RequestEntity<*>) =
		exchange(requestEntity, T::class.java)
