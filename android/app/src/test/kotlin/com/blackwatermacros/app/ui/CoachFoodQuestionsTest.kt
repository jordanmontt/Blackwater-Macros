package com.blackwatermacros.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.foodquestions.AnalysisId
import com.blackwatermacros.app.core.foodquestions.FoodAnswer
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.MealRequest
import com.blackwatermacros.app.data.WireEntryMode
import com.blackwatermacros.app.data.WireIngredient
import com.blackwatermacros.app.data.ai.AiClient
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.ai.SecretCipher
import com.blackwatermacros.app.data.ai.local.LocalModelState
import com.blackwatermacros.app.data.ai.local.LocalModels
import com.blackwatermacros.app.data.local.LocalDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Questions about the food log on the Coach (web `coach-food-questions.test.tsx`): the
 * model only turns the question into a request, the app computes from the phone's meals
 * and the model explains those numbers; with nothing to compute the app replies itself,
 * and that is never resent to the model. Never calls a real provider.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoachFoodQuestionsTest {

    private val server = MockWebServer()
    private val main = newSingleThreadContext("main")
    private lateinit var db: LocalDatabase
    private lateinit var repository: AppRepository
    private lateinit var settings: AiSettingsStore
    private lateinit var viewModel: CoachViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).allowMainThreadQueries().build()
        val account = AccountStore(context.getSharedPreferences("food-account", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        repository = AppRepository(db, account, onLocalChange = {})
        settings = AiSettingsStore(context.getSharedPreferences("food-ai", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, PlainCipher)
        settings.update { it.copy(provider = AiProvider.GEMINI, apiKeys = mapOf(AiProvider.GEMINI to "fake-key")) }
        val mock = server.url("/")
        val client = AiClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.newBuilder().scheme(mock.scheme).host(mock.host).port(mock.port).build()
                    chain.proceed(chain.request().newBuilder().url(url).build())
                }
                .build(),
        )
        viewModel = CoachViewModel(
            repository,
            settings,
            client,
            language = { "Spanish" },
            local = null,
            localModel = MutableStateFlow(LocalModelState.NotDownloaded),
            localModelSpec = MutableStateFlow(LocalModels.DEFAULT),
        )
        // 14 days of breakfasts with chocolate milk (200 ml) and oats.
        runBlocking {
            for (i in 0 until 14) {
                repository.saveMeal(
                    null,
                    MealRequest(
                        addDaysToKey(todayKey(), -i),
                        "Desayuno",
                        entryMode = WireEntryMode.PER_INGREDIENT,
                        ingredients = listOf(
                            WireIngredient("Leche chocolatada", "200 ml", 150.0, 6.0, 22.0, 4.0),
                            WireIngredient("Avena", "60 g", 230.0, 8.0, 40.0, 4.0),
                        ),
                    ),
                )
            }
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    /** Gemini's whole answer (step 1: the request). */
    private fun answer(text: String) =
        MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(text)}}]},"finishReason":"STOP"}]}""")

    /** Gemini's streamed answer (step 3: the coach explains). */
    private fun stream(text: String) =
        MockResponse().setBody("""data: {"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(text)}}]},"finishReason":"STOP"}]}""" + "\n\n")

    private fun sendAndWait(text: String): ChatState = runBlocking {
        val before = viewModel.state.value.messages.size
        viewModel.send(text)
        withTimeout(10_000) { viewModel.state.first { !it.streaming && it.messages.size == before + 2 } }
    }

    private class Sent(val path: String, val body: JsonObject)

    private fun sent(): Sent = server.takeRequest().let { Sent(it.path.orEmpty(), Json.parseToJsonElement(it.body.readUtf8()).jsonObject) }

    private fun JsonObject.lastUserText(): String =
        getValue("contents").jsonArray.last().jsonObject.getValue("parts").jsonArray.last().jsonObject.getValue("text").jsonPrimitive.content

    @Test
    fun `the app counts the meals and the model only explains its numbers`() {
        server.enqueue(answer("""{"analysis":"food_stats","foods":["leche chocolatada","chocolate milk"]}"""))
        server.enqueue(stream("Unos **200 ml** los días que la tomas."))
        val state = sendAndWait("¿Cuánta leche chocolatada tomo?")

        // Step 1: a short JSON request, not the coach's prompt with the data.
        val first = sent()
        assertThat(first.path).contains(":generateContent")
        assertThat(first.body["generationConfig"].toString()).contains("application/json")
        assertThat(first.body["systemInstruction"].toString()).contains("You turn a question about the user's own food log")
        assertThat(first.body.lastUserText()).isEqualTo("¿Cuánta leche chocolatada tomo?")

        // Step 3: the question with the app's numbers.
        val asked = sent().body.lastUserText()
        assertThat(asked).startsWith("¿Cuánta leche chocolatada tomo?\n\nFOOD LOG RESULT")
        assertThat(asked).contains("Eaten on 14 of the 14 logged days")
        assertThat(asked).contains("Amount in ml: 2800 ml in 14 entries; 200 ml per day it was eaten")

        // On screen the question reads as typed, and the answer carries «Cómo se calculó».
        assertThat(state.messages.first().text).isEqualTo("¿Cuánta leche chocolatada tomo?")
        val details = state.messages.last().foodDetails!!
        assertThat(details.analysis).isEqualTo(AnalysisId.FOOD_STATS)
        assertThat(details.counted).containsExactly("Leche chocolatada")
    }

    @Test
    fun `when nothing matches the app replies itself and that is not resent`() {
        server.enqueue(answer("""{"analysis":"food_stats","foods":["kombucha"]}"""))
        val state = sendAndWait("¿Cuánta kombucha tomo?")
        val reply = state.messages.last().foodReply as FoodReply.Answer
        assertThat((reply.answer as FoodAnswer.NoMatch).foods).containsExactly("kombucha")
        assertThat(server.requestCount).isEqualTo(1)
        sent()

        // Neither the reply nor its question go to the model later.
        assertThat(historyForModel(state.messages)).isEmpty()
        server.enqueue(answer("""{"analysis":"none"}"""))
        server.enqueue(stream("Vale."))
        sendAndWait("Gracias")
        sent()
        assertThat(sent().body.getValue("contents").jsonArray).hasSize(1)
    }

    @Test
    fun `a question it cannot read gets an example with the user's own food`() {
        server.enqueue(answer("No sé qué quieres decir."))
        val state = sendAndWait("¿Y lo otro?")
        assertThat(state.messages.last().foodReply).isEqualTo(FoodReply.Unclear("Avena"))
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a question not about the food log is answered as usual`() {
        server.enqueue(answer("""{"analysis":"none"}"""))
        server.enqueue(stream("Unos 120 g al día."))
        val state = sendAndWait("¿Cuánta proteína necesito?")
        sent()
        assertThat(sent().body.lastUserText()).isEqualTo("¿Cuánta proteína necesito?")
        assertThat(state.messages.last().text).isEqualTo("Unos 120 g al día.")
        assertThat(state.messages.last().foodDetails).isNull()
    }

    @Test
    fun `without permission to see the food there is no first step`() {
        settings.update { it.copy(coachSeesFood = false) }
        server.enqueue(stream("Hola."))
        sendAndWait("¿Cuánta leche chocolatada tomo?")
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(sent().path).contains("streamGenerateContent")
    }

    @Test
    fun `a follow-up carries the previous request`() {
        server.enqueue(answer("""{"analysis":"food_stats","foods":["leche chocolatada"]}"""))
        server.enqueue(stream("Unos 200 ml."))
        sendAndWait("¿Cuánta leche chocolatada tomo?")
        server.enqueue(answer("""{"analysis":"food_stats","foods":["leche chocolatada"],"from":"2026-01-01","to":"2026-01-31"}"""))
        server.enqueue(stream("En enero no la registraste."))
        sendAndWait("¿Y en enero?")
        sent()
        sent()
        assertThat(sent().body["systemInstruction"].toString())
            .contains("""The previous request in this conversation was: {\"analysis\":\"food_stats\",\"foods\":[\"leche chocolatada\"]}.""")
    }

    @Test
    fun `the example question uses one of the user's foods`() = runBlocking {
        val example = withTimeout(10_000) { viewModel.foodExample.first { it != null } }
        assertThat(example).isEqualTo(FoodExample("Avena"))
        assertThat(viewModel.state.value.messages.map { it.role }).doesNotContain(AiRole.USER)
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: String) = plain.reversed()
        override fun decrypt(encoded: String) = encoded.reversed()
    }
}
