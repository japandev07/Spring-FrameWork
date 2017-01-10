package org.springframework.context.support

import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.context.ApplicationContext
import java.util.function.Supplier
import kotlin.reflect.KClass

/**
 * Extension for [GenericApplicationContext] providing [KClass] based API and
 * avoiding specifying a class parameter for the [Supplier] based variant thanks to
 * Kotlin reified type parameters.
 *
 * @author Sebastien Deleuze
 * @since 5.0
 */
object GenericApplicationContextExtension {

	/**
	 * @see GenericApplicationContext.registerBean(Class<T>, BeanDefinitionCustomizer...)
	 */
	fun <T : Any> GenericApplicationContext.registerBean(beanClass: KClass<T>,
			vararg customizers: BeanDefinitionCustomizer) {
					registerBean(beanClass.java, *customizers)
	}

	/**
	 * @see GenericApplicationContext.registerBean(Class<T>, BeanDefinitionCustomizer...)
	 */
	inline fun <reified T : Any> GenericApplicationContext.registerBean(vararg customizers: BeanDefinitionCustomizer) {
					registerBean(T::class.java, *customizers)
	}

	/**
	 * @see GenericApplicationContext.registerBean(String, Class<T>, BeanDefinitionCustomizer...)
	 */
	fun <T : Any> GenericApplicationContext.registerBean(beanName: String, beanClass: KClass<T>,
			vararg customizers: BeanDefinitionCustomizer) {
					registerBean(beanName, beanClass.java, *customizers)
	}

	/**
	 * @see GenericApplicationContext.registerBean(String, Class<T>, BeanDefinitionCustomizer...)
	 */
	inline fun <reified T : Any> GenericApplicationContext.registerBean(beanName: String, vararg customizers: BeanDefinitionCustomizer) {
					registerBean(beanName, T::class.java, *customizers)
	}

	/**
	 * @see GenericApplicationContext.registerBean(Class<T>, Supplier<T>, BeanDefinitionCustomizer...)
	 */
	inline fun <reified T : Any> GenericApplicationContext.registerBean(
			vararg customizers: BeanDefinitionCustomizer, crossinline function: (ApplicationContext) -> T) {
					registerBean(T::class.java, Supplier { function.invoke(this) }, *customizers)
	}

	/**
	 * @see GenericApplicationContext.registerBean(String, Class<T>, Supplier<T>, BeanDefinitionCustomizer...)
	 */
	inline fun <reified T : Any> GenericApplicationContext.registerBean(name: String,
			vararg customizers: BeanDefinitionCustomizer, crossinline function: (ApplicationContext) -> T) {
					registerBean(name, T::class.java, Supplier { function.invoke(this) }, *customizers)
	}

	fun GenericApplicationContext(configure: GenericApplicationContext.()->Unit) = GenericApplicationContext().apply(configure)
}
