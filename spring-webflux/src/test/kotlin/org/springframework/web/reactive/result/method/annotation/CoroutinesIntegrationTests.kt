/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.reactive.result.method.annotation

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.reactive.config.EnableWebFlux

@ExperimentalCoroutinesApi
class CoroutinesIntegrationTests : AbstractRequestMappingIntegrationTests() {

	override fun initApplicationContext(): ApplicationContext {
		val context = AnnotationConfigApplicationContext()
		context.register(WebConfig::class.java)
		context.refresh()
		return context
	}

	@Test
	fun `Suspending handler method`() {
		val entity = performGet<String>("/suspend", HttpHeaders.EMPTY, String::class.java)
		assertEquals(HttpStatus.OK, entity.statusCode)
		assertEquals("foo", entity.body)
	}

	@Test
	fun `Handler method returning Deferred`() {
		val entity = performGet<String>("/deferred", HttpHeaders.EMPTY, String::class.java)
		assertEquals(HttpStatus.OK, entity.statusCode)
		assertEquals("foo", entity.body)
	}

	@Test
	fun `Handler method returning Flow`() {
		val entity = performGet<String>("/flow", HttpHeaders.EMPTY, String::class.java)
		assertEquals(HttpStatus.OK, entity.statusCode)
		assertEquals("foobar", entity.body)
	}

	@Test
	fun `Suspending handler method returning Flow`() {
		val entity = performGet<String>("/suspending-flow", HttpHeaders.EMPTY, String::class.java)
		assertEquals(HttpStatus.OK, entity.statusCode)
		assertEquals("foobar", entity.body)
	}

	@Test(expected = HttpServerErrorException.InternalServerError::class)
	fun `Suspending handler method throwing exception`() {
		performGet<String>("/error", HttpHeaders.EMPTY, String::class.java)
	}

	@Test(expected = HttpServerErrorException.InternalServerError::class)
	fun `Handler method returning Flow throwing exception`() {
		performGet<String>("/flow-error", HttpHeaders.EMPTY, String::class.java)
	}

	@Configuration
	@EnableWebFlux
	@ComponentScan(resourcePattern = "**/CoroutinesIntegrationTests*")
	open class WebConfig

	@RestController
	class CoroutinesController {

		@GetMapping("/suspend")
		suspend fun suspendingEndpoint(): String {
			delay(1)
			return "foo"
		}

		@GetMapping("/deferred")
		fun deferredEndpoint(): Deferred<String> = GlobalScope.async {
			delay(1)
			"foo"
		}

		@GetMapping("/flow")
		fun flowEndpoint()= flow {
			emit("foo")
			delay(1)
			emit("bar")
			delay(1)
		}

		@GetMapping("/suspending-flow")
		suspend fun suspendingFlowEndpoint(): Flow<String> {
			delay(10)
			return flow {
				emit("foo")
				delay(1)
				emit("bar")
				delay(1)
			}
		}

		@GetMapping("/error")
		suspend fun error() {
			delay(1)
			throw IllegalStateException()
		}

		@GetMapping("/flow-error")
		suspend fun flowError() = flow<String> {
			delay(1)
			throw IllegalStateException()
		}

	}
}
