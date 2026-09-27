package com.blackwatermacros.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.MealRequest
import com.blackwatermacros.app.data.WeightRequest
import com.blackwatermacros.app.data.WireEntryMode
import com.blackwatermacros.app.data.ai.AiClient
import com.blackwatermacros.app.data.ai.AiFailure
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.ai.SecretCipher
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.ai.local.LocalModelState
import com.blackwatermacros.app.data.ai.local.LocalModels
import kotlinx.coroutines.flow.MutableStateFlow
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Coach tab (web `coach-page.test.tsx`): the answer is streamed, each question
 * carries a summary of the local data only when allowed, the conversation keeps
 * going until «Nueva conversación», failures are explained and not resent.
 * Never calls a real provider.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoachTest {

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
        val account = AccountStore(context.getSharedPreferences("coach-account", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        repository = AppRepository(db, account, onLocalChange = {})
        settings = AiSettingsStore(context.getSharedPreferences("coach-ai", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, PlainCipher)
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
        runBlocking {
            repository.saveProfile(CalorieProfile(Gender.FEMALE, 1992, 165.0, 3, 60, 30, Goal.CUT))
            repository.saveWeight(null, WeightRequest(Instant.now().toString(), 62.0))
            repository.saveMeal(null, MealRequest(todayKey(), "Desayuno", entryMode = WireEntryMode.TOTAL_ONLY, totalCalories = 380.0, totalProtein = 20.0))
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun stream(vararg pieces: String) = MockResponse().setBody(
        pieces.joinToString("") { """data: {"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(it)}}]}}]}""" + "\n\n" },
    )

    private fun sendAndWait(text: String): ChatState = runBlocking {
        viewModel.send(text)
        withTimeout(10_000) { viewModel.state.first { !it.streaming && it.messages.isNotEmpty() } }
    }

    private fun body() = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test
    fun `a question gets a streamed answer with a summary of the local data`() {
        server.enqueue(stream("Con tu tendencia, ", "**61,4 kg**."))
        val state = sendAndWait("¿Cuál será mi peso en 30 días?")

        assertThat(state.messages.map { it.role to it.text }).containsExactly(
            AiRole.USER to "¿Cuál será mi peso en 30 días?",
            AiRole.ASSISTANT to "Con tu tendencia, **61,4 kg**.",
        ).inOrder()
        val request = body()
        val system = request["systemInstruction"].toString()
        assertThat(system).contains("Always answer in Spanish.")
        assertThat(system).contains("USER DATA")
        assertThat(system).contains("Desayuno")
    }

    @Test
    fun `the conversation continues until a new one starts, failures are not resent`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.enqueue(stream("Una ensalada."))
        server.enqueue(stream("Unos 40 g."))

        assertThat(sendAndWait("Hola").messages.last().error).isEqualTo(AiFailure.QUOTA)
        body()
        sendAndWait("¿Qué ceno?")
        assertThat(body()["contents"]!!.jsonArray).hasSize(1)
        sendAndWait("¿Cuánta proteína?")
        assertThat(body()["contents"]!!.jsonArray.map { it.jsonObject["role"].toString() })
            .containsExactly("\"user\"", "\"model\"", "\"user\"").inOrder()

        viewModel.reset()
        assertThat(viewModel.state.value).isEqualTo(ChatState())
    }

    @Test
    fun `without permission the data is not sent`() {
        settings.update { it.copy(coachSeesData = false) }
        server.enqueue(stream("Hola."))
        sendAndWait("¿Como demasiado?")
        val system = body()["systemInstruction"].toString()
        assertThat(system).contains("The user chose not to share their data")
        assertThat(system).doesNotContain("Desayuno")
    }

    @Test
    fun `history leaves out failed or empty answers and starts with the user`() {
        val history = historyForModel(
            listOf(
                ChatMessage(1, AiRole.ASSISTANT, "Hola"),
                ChatMessage(2, AiRole.USER, "a"),
                ChatMessage(3, AiRole.ASSISTANT, "", AiFailure.QUOTA),
                ChatMessage(4, AiRole.USER, "b"),
                ChatMessage(5, AiRole.ASSISTANT, "B"),
                ChatMessage(6, AiRole.USER, "c"),
                ChatMessage(7, AiRole.ASSISTANT, ""),
            ),
        )
        assertThat(history).containsExactly(AiMessage(AiRole.USER, "b"), AiMessage(AiRole.ASSISTANT, "B")).inOrder()
    }

    @Test
    fun `answers show bullets and bold without the Markdown marks`() {
        val text = simpleMarkdown("## Plan\n- **Desayuno**: avena\n* Cena ligera")
        assertThat(text.text).isEqualTo("Plan\n• Desayuno: avena\n• Cena ligera")
        assertThat(text.spanStyles).hasSize(1)
        assertThat(text.text.substring(text.spanStyles[0].start, text.spanStyles[0].end)).isEqualTo("Desayuno")
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: String) = plain.reversed()
        override fun decrypt(encoded: String) = encoded.reversed()
    }
}
