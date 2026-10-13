package com.blackwatermacros.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncOutcome
import com.blackwatermacros.app.data.sync.SyncScheduler
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Several accounts on one phone, switched with a tap (Android only; the web keeps one
 * account per browser on purpose). Real Room databases (in memory, one per account),
 * real Retrofit clients and a fake backend with two users, Ana and Luis.
 *
 * - An install with one account keeps working as before, with nothing to migrate.
 * - «Añadir cuenta» gives the new account its own, empty data; the first one is untouched.
 * - Switching shows each account's own meals; nothing leaks from one to the other.
 * - Background sync uploads every account's pending changes, to the right account.
 * - Logging out of one of several accounts removes only its data and keeps the others.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MultiAccountTest {

    private lateinit var server: MockWebServer
    private val ana = FakeServer(validToken = "token-ana", username = "ana")
    private val luis = FakeServer(validToken = "token-luis", username = "luis")
    private lateinit var context: Context
    private lateinit var baseUrl: String
    /** Fresh preference files for every test. */
    private val run = UUID.randomUUID().toString()
    private val scheduler = CountingScheduler()
    private val databases = mutableMapOf<String, LocalDatabase>()
    private val destroyed = mutableListOf<String>()
    private lateinit var profiles: AccountProfiles

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = TwoUsers(listOf(ana, luis))
            start()
        }
        context = ApplicationProvider.getApplicationContext()
        baseUrl = server.url("/").toString()
        profiles = newProfiles()
    }

    @After
    fun tearDown() {
        databases.values.forEach { it.close() }
        server.shutdown()
    }

    /** Built like `AppGraph`, with in-memory databases and test preference files. */
    private fun newProfiles(): AccountProfiles {
        val apiWithToken = { token: String -> ApiClient.create(baseUrl = baseUrl, tokenProvider = { token }) }
        return AccountProfiles(
            prefs = prefs("profiles"),
            createScope = { id ->
                val db = databases.getOrPut(id) {
                    Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).allowMainThreadQueries().build()
                }
                val account = AccountStore(prefs("account-$id"))
                val api = ApiClient.create(baseUrl = baseUrl, tokenProvider = account::token)
                val sync = SyncEngine(db, account, api)
                val repository = AppRepository(db, account, onLocalChange = { scheduler.requestSync(2) })
                ProfileScope(id, db, account, api, sync, repository, AccountController(account, repository, sync, scheduler, api, apiWithToken))
            },
            deleteFiles = { id ->
                destroyed += id
                databases.remove(id)?.close()
                prefs("account-$id").edit().clear().commit()
            },
            loginApi = ApiClient.create(baseUrl = baseUrl, tokenProvider = { null }),
            apiWithToken = apiWithToken,
            requestSync = { scheduler.requestSync(0) },
        )
    }

    private fun prefs(name: String) = context.getSharedPreferences("$run-$name", Context.MODE_PRIVATE)

    /** The single-account flow that exists today: Ajustes → Cuenta → «Iniciar sesión». */
    private suspend fun logInFirstAccount() {
        val controller = profiles.active.accounts
        controller.connect(controller.verify("ana", "secreta"), uploadLocalData = true)
        profiles.active.sync.sync()
    }

    private suspend fun titlesOfActive(day: String = DAY) =
        profiles.active.repository.mealsForDay(day).first().map { it.title }

    // --- Existing installs ---

    @Test
    fun `an install with one account keeps its data and session as the only profile`() = runBlocking<Unit> {
        logInFirstAccount()
        profiles.active.repository.saveMeal(null, meal("Desayuno de Ana"))

        assertThat(profiles.ids.value).containsExactly(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.activeId.value).isEqualTo(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.summaries().map { it.username }).containsExactly("ana")
        assertThat(titlesOfActive()).containsExactly("Desayuno de Ana")
        // The files a single-account install already had: nothing to migrate.
        assertThat(AccountProfiles.databaseName(AccountProfiles.DEFAULT_ID)).isEqualTo("blackwater.db")
        assertThat(AccountProfiles.accountPrefsName(AccountProfiles.DEFAULT_ID)).isEqualTo("account")
    }

    @Test
    fun `without an account, adding a second one is not offered`() = runBlocking<Unit> {
        assertThat(profiles.canAddAccount).isFalse()
        assertThat(profiles.summaries()).isEmpty()
        assertThat(runCatching { profiles.addAccount("luis", "secreta") }.isFailure).isTrue()
        assertThat(profiles.ids.value).containsExactly(AccountProfiles.DEFAULT_ID)
        assertThat(luis.requestCount).isEqualTo(0)
    }

    // --- Adding and switching ---

    @Test
    fun `adding an account gives it its own empty data and makes it active`() = runBlocking<Unit> {
        logInFirstAccount()
        profiles.active.repository.saveMeal(null, meal("Desayuno de Ana"))
        assertThat(profiles.canAddAccount).isTrue()

        val luisId = profiles.addAccount("luis", "secreta")

        assertThat(profiles.activeId.value).isEqualTo(luisId)
        assertThat(profiles.ids.value).containsExactly(AccountProfiles.DEFAULT_ID, luisId).inOrder()
        assertThat(profiles.summaries().map { it.username }).containsExactly("ana", "luis").inOrder()
        assertThat(profiles.active.account.current?.username).isEqualTo("luis")
        assertThat(titlesOfActive()).isEmpty()
        assertThat(profiles.scope(AccountProfiles.DEFAULT_ID).repository.mealsForDay(DAY).first().map { it.title })
            .containsExactly("Desayuno de Ana")
    }

    @Test
    fun `the new account downloads its own meals, not the other account's`() = runBlocking<Unit> {
        logInFirstAccount()
        profiles.active.repository.saveMeal(null, meal("Desayuno de Ana"))
        profiles.active.sync.sync()
        profiles.addAccount("luis", "secreta")
        luisMealOnServer("Cena de Luis")

        assertThat(profiles.active.sync.sync()).isEqualTo(SyncOutcome.Success)

        assertThat(titlesOfActive()).containsExactly("Cena de Luis")
        assertThat(luis.meals.values.map { it.title }).containsExactly("Cena de Luis")
        assertThat(ana.meals.values.map { it.title }).containsExactly("Desayuno de Ana")
    }

    @Test
    fun `switching shows each account's own meals`() = runBlocking<Unit> {
        logInFirstAccount()
        profiles.active.repository.saveMeal(null, meal("Desayuno de Ana"))
        val luisId = profiles.addAccount("luis", "secreta")
        profiles.active.repository.saveMeal(null, meal("Desayuno de Luis"))

        profiles.switchTo(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.active.account.current?.username).isEqualTo("ana")
        assertThat(titlesOfActive()).containsExactly("Desayuno de Ana")

        profiles.switchTo(luisId)
        assertThat(titlesOfActive()).containsExactly("Desayuno de Luis")
    }

    @Test
    fun `adding an account that is already on the phone switches to it instead of adding it twice`() = runBlocking<Unit> {
        logInFirstAccount()
        val luisId = profiles.addAccount("luis", "secreta")

        assertThat(profiles.addAccount("ana", "secreta")).isEqualTo(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.activeId.value).isEqualTo(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.addAccount("luis", "secreta")).isEqualTo(luisId)
        assertThat(profiles.ids.value).hasSize(2)
    }

    @Test
    fun `a wrong password or no network adds nothing and keeps the active account`() = runBlocking<Unit> {
        logInFirstAccount()

        assertThat(runCatching { profiles.addAccount("luis", "mala") }.isFailure).isTrue()
        ana.offline = true
        luis.offline = true
        assertThat(runCatching { profiles.addAccount("luis", "secreta") }.isFailure).isTrue()

        assertThat(profiles.ids.value).containsExactly(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.activeId.value).isEqualTo(AccountProfiles.DEFAULT_ID)
    }

    @Test
    fun `the accounts and the active one are remembered after a restart`() = runBlocking<Unit> {
        logInFirstAccount()
        val luisId = profiles.addAccount("luis", "secreta")

        val restarted = newProfiles()

        assertThat(restarted.ids.value).containsExactly(AccountProfiles.DEFAULT_ID, luisId).inOrder()
        assertThat(restarted.activeId.value).isEqualTo(luisId)
        assertThat(restarted.summaries().map { it.username }).containsExactly("ana", "luis").inOrder()
    }

    @Test
    fun `the switcher list follows a session that expires`() = runBlocking<Unit> {
        logInFirstAccount()
        profiles.addAccount("luis", "secreta")
        luis.tokenRevoked = true

        assertThat(profiles.active.sync.sync()).isEqualTo(SyncOutcome.SessionExpired)

        assertThat(profiles.summariesFlow().first().single { it.username == "luis" }.sessionExpired).isTrue()
        assertThat(profiles.summariesFlow().first().single { it.username == "ana" }.sessionExpired).isFalse()
    }

    // --- Background sync ---

    @Test
    fun `background sync uploads the changes of every account to its own account`() = runBlocking<Unit> {
        logInFirstAccount()
        val luisId = profiles.addAccount("luis", "secreta")
        ana.offline = true
        luis.offline = true
        // At the gym: a meal for Luis, then switch and one for Ana.
        profiles.active.repository.saveMeal(null, meal("Desayuno de Luis"))
        profiles.switchTo(AccountProfiles.DEFAULT_ID)
        profiles.active.repository.saveMeal(null, meal("Desayuno de Ana"))
        assertThat(profiles.syncAll()).isEqualTo(SyncOutcome.Offline)

        ana.offline = false
        luis.offline = false
        assertThat(profiles.syncAll()).isEqualTo(SyncOutcome.Success)

        assertThat(ana.meals.values.map { it.title }).containsExactly("Desayuno de Ana")
        assertThat(luis.meals.values.map { it.title }).containsExactly("Desayuno de Luis")
        assertThat(profiles.scope(luisId).repository.pendingChanges().first()).isEqualTo(0)
        assertThat(profiles.active.repository.pendingChanges().first()).isEqualTo(0)
    }

    // --- Logging out ---

    @Test
    fun `logging out of one of several accounts removes only its data`() = runBlocking<Unit> {
        logInFirstAccount()
        profiles.active.repository.saveMeal(null, meal("Desayuno de Ana"))
        profiles.active.sync.sync()
        val luisId = profiles.addAccount("luis", "secreta")
        profiles.active.repository.saveMeal(null, meal("Desayuno de Luis"))
        profiles.active.sync.sync()

        profiles.logoutActive()

        // Its data is gone now; the empty files go at the next start, when no screen reads them.
        assertThat(profiles.scope(AccountProfiles.DEFAULT_ID).account.current?.username).isEqualTo("ana")
        assertThat(destroyed).isEmpty()
        assertThat(profiles.ids.value).containsExactly(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.activeId.value).isEqualTo(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.active.account.current?.username).isEqualTo("ana")
        assertThat(titlesOfActive()).containsExactly("Desayuno de Ana")
        // Safe on the server, as with a single account.
        assertThat(luis.meals.values.map { it.title }).containsExactly("Desayuno de Luis")

        val restarted = newProfiles()
        assertThat(destroyed).containsExactly(luisId)
        assertThat(restarted.ids.value).containsExactly(AccountProfiles.DEFAULT_ID)
        newProfiles()
        assertThat(destroyed).containsExactly(luisId) // only once
    }

    @Test
    fun `logging out of the last account goes back to local mode as before`() = runBlocking<Unit> {
        logInFirstAccount()
        profiles.active.repository.saveMeal(null, meal("Desayuno de Ana"))
        profiles.active.sync.sync()

        profiles.logoutActive()

        assertThat(destroyed).isEmpty()
        assertThat(profiles.ids.value).containsExactly(AccountProfiles.DEFAULT_ID)
        assertThat(profiles.active.account.current).isNull()
        assertThat(profiles.summaries()).isEmpty()
        assertThat(titlesOfActive()).isEmpty()
    }

    private fun luisMealOnServer(title: String) = runBlocking {
        ApiClient.create(baseUrl = baseUrl, tokenProvider = { luis.validToken }).putMeal(UUID.randomUUID().toString(), meal(title))
    }

    private fun meal(title: String) = MealRequest(
        logDate = DAY,
        title = title,
        entryMode = WireEntryMode.TOTAL_ONLY,
        totalCalories = 500.0,
        totalProtein = 30.0,
        totalCarbs = 50.0,
        totalFat = 15.0,
    )

    /** One backend, two users: the login goes by username, every other call by token. */
    private class TwoUsers(private val users: List<FakeServer>) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val user = if (request.path?.startsWith(AccountInterceptor.LOGIN_PATH) == true) {
                val body = request.body.clone().readUtf8()
                users.firstOrNull { "\"username\":\"${it.username}\"" in body } ?: users.first()
            } else {
                users.firstOrNull { request.getHeader("Authorization") == "Bearer ${it.validToken}" } ?: users.first()
            }
            return user.dispatch(request)
        }
    }

    private class CountingScheduler : SyncScheduler {
        var requests = 0
        override fun requestSync(delaySeconds: Long) {
            requests++
        }
        override fun cancel() = Unit
    }

    private companion object {
        const val DAY = "2026-06-15"
    }
}
