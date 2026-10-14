package com.blackwatermacros.app.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.Serializable
import org.junit.Test
import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

/**
 * The release build shrinks the code with R8. Retrofit reads a suspend call's response type
 * from the method's generic signature, so a response class R8 removes (because the code
 * never reads it) breaks the call: «Unable to create converter for class java.lang.Object».
 * That is how `updateSettings` silently failed and blocked every sync with a profile change.
 * `proguard-rules.pro` keeps every @Serializable class of this package; this checks that
 * every response type is one of them and that the rule is still there.
 */
class ReleaseRulesTest {

    @Test
    fun `every API response type is a serializable wire model the release rules keep`() {
        val calls = ApiService::class.java.declaredMethods.filter {
            !it.isSynthetic && it.parameterTypes.lastOrNull() == kotlin.coroutines.Continuation::class.java
        }
        val responseTypes = calls.map { method ->
            // suspend fun f(...): T compiles to f(..., Continuation<? super T>): Object.
            val continuation = method.genericParameterTypes.last() as ParameterizedType
            val type = continuation.actualTypeArguments.single().let { if (it is WildcardType) it.lowerBounds.single() else it }
            method.name to (type as Class<*>)
        }
        assertThat(responseTypes.map { it.first }).containsAtLeast("updateSettings", "session", "putMeal", "logout")
        for ((name, type) in responseTypes) {
            assertWithMessage("response of $name").that(type.packageName).isEqualTo(ApiService::class.java.packageName)
            assertWithMessage("response of $name is @Serializable").that(type.isAnnotationPresent(Serializable::class.java)).isTrue()
        }

        val rules = File("proguard-rules.pro").readText()
        assertThat(rules).contains("-keep,allowobfuscation @kotlinx.serialization.Serializable class com.blackwatermacros.app.data.** { *; }")
    }
}
