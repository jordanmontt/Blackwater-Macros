package com.blackwatermacros.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncOutcome
import com.blackwatermacros.app.data.sync.SyncScheduler
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * User-level requirements of the local-first app, exercised end to end:
 * real Room database (in memory) + real Retrofit client + a fake backend.
 *
 * - Without an account the app works fully and never talks to the network.
 * - With an account, data entered offline is kept and uploaded later, once.
 * - Changes made on the web show up on the phone (including deletions).
 * - Logging in asks about local data; logging out removes it from the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OfflineSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var backend: FakeServer
    private lateinit var db: LocalDatabase
    private lateinit var account: AccountStore
    private lateinit var sync: SyncEngine
    private lateinit var repository: AppRepository
    private lateinit var accounts: AccountController
    private val scheduler = RecordingScheduler()

    @Before
    fun setUp() {
        backend = FakeServer()
        server = MockWebServer().apply {
            dispatcher = backend
            start()
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).allowMainThreadQueries().build()
        account = AccountStore(context.getSharedPreferences("test-account", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        val baseUrl = server.url("/").toString()
        val api = ApiClient.create(baseUrl = baseUrl, tokenProvider = account::token)
        sync = SyncEngine(db, account, api)
        repository = AppRepository(db, account, onLocalChange = { scheduler.requestSync(2) })
        accounts = AccountController(
            account = account,
            repository = repository,
            sync = sync,
            scheduler = scheduler,
            api = api,
            apiWithToken = { token -> ApiClient.create(baseUrl = baseUrl, tokenProvider = { token }) },
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    // --- Local-only mode (F-Droid users without an account) ---

    @Test
    fun `without an account everything is stored on the phone and nothing is sent`() = runBlocking<Unit> {
        repository.saveMeal(null, breakfast())
        repository.saveWeight(null, WeightRequest(measuredAt = "2026-06-15T08:00", weightKg = 80.0))

        assertThat(repository.mealsForDay("2026-06-15").first().map { it.title }).containsExactly("Desayuno")
        assertThat(repository.weights().first()).hasSize(1)
        assertThat(sync.sync()).isEqualTo(SyncOutcome.NotLoggedIn)
        assertThat(backend.requestCount).isEqualTo(0)
        assertThat(scheduler.requests).isEqualTo(0)
    }

    @Test
    fun `without an account deleting removes the row for good`() = runBlocking<Unit> {
        repository.saveMeal(null, breakfast())
        val id = repository.mealsForDay("2026-06-15").first().single().id

        repository.deleteMeal(id)

        assertThat(db.meals().get(id)).isNull()
    }

    @Test
    fun `meal totals are computed on the phone for both entry modes`() = runBlocking<Unit> {
        repository.saveMeal(
            null,
            MealRequest(
                logDate = "2026-06-15",
                title = "Tostadas",
                entryMode = WireEntryMode.PER_INGREDIENT,
                ingredients = listOf(
                    WireIngredient("Pan", calories = 150.0, protein = 5.0),
                    WireIngredient("Huevo", calories = 90.0, protein = 6.5),
                ),
            ),
        )
        val meal = repository.mealsForDay("2026-06-15").first().single()
        assertThat(meal.resolvedCalories).isEqualTo(240.0)
        assertThat(meal.resolvedProtein).isEqualTo(11.5)
        assertThat(meal.totalCalories).isNull()
    }

    // --- Offline use while logged in ---

    @Test
    fun `a meal logged offline is kept and uploaded once the connection is back`() = runBlocking<Unit> {
        connect()
        backend.offline = true

        repository.saveMeal(null, breakfast())
        assertThat(sync.sync()).isEqualTo(SyncOutcome.Offline)
        assertThat(repository.mealsForDay("2026-06-15").first()).hasSize(1)
        assertThat(repository.pendingChanges().first()).isEqualTo(1)
        assertThat(scheduler.requests).isGreaterThan(0)

        backend.offline = false
        assertThat(sync.sync()).isEqualTo(SyncOutcome.Success)

        val local = repository.mealsForDay("2026-06-15").first().single()
        assertThat(backend.meals.keys).containsExactly(local.id)
        assertThat(repository.pendingChanges().first()).isEqualTo(0)
    }

    @Test
    fun `syncing again never duplicates data on the server`() = runBlocking<Unit> {
        connect()
        repository.saveMeal(null, breakfast())
        sync.sync()
        sync.sync()
        sync.sync()

        assertThat(backend.meals).hasSize(1)
        assertThat(repository.mealsForDay("2026-06-15").first()).hasSize(1)
    }

    @Test
    fun `edits and deletions made on the phone reach the server`() = runBlocking<Unit> {
        connect()
        repository.saveMeal(null, breakfast())
        sync.sync()
        val id = backend.meals.keys.single()

        repository.saveMeal(id, breakfast().copy(title = "Desayuno grande", totalCalories = 700.0))
        sync.sync()
        assertThat(backend.meals.getValue(id).title).isEqualTo("Desayuno grande")

        repository.deleteMeal(id)
        assertThat(repository.mealsForDay("2026-06-15").first()).isEmpty()
        sync.sync()
        assertThat(backend.meals).isEmpty()
        assertThat(db.meals().get(id)).isNull()
    }

    @Test
    fun `undo after deleting a meal brings it back, locally and on the server`() = runBlocking<Unit> {
        repository.saveMeal(null, breakfast())
        val meal = repository.mealsForDay("2026-06-15").first().single()
        repository.deleteMeal(meal.id)
        repository.restoreMeal(meal)
        assertThat(repository.mealsForDay("2026-06-15").first().map { it.id }).containsExactly(meal.id)

        connect()
        sync.sync()
        repository.deleteMeal(meal.id)
        sync.sync() // the delete already reached the server…
        assertThat(backend.meals).isEmpty()
        repository.restoreMeal(meal) // …and undo still works
        sync.sync()
        assertThat(backend.meals.keys).containsExactly(meal.id)
        assertThat(repository.mealsForDay("2026-06-15").first().map { it.title }).containsExactly("Desayuno")
    }

    @Test
    fun `reordering meals is uploaded`() = runBlocking<Unit> {
        connect()
        repository.saveMeal(null, breakfast().copy(title = "A"))
        repository.saveMeal(null, breakfast().copy(title = "B"))
        val (a, b) = repository.mealsForDay("2026-06-15").first().map { it.id }

        repository.reorderMeals(listOf(b, a))
        sync.sync()

        assertThat(repository.mealsForDay("2026-06-15").first().map { it.title }).containsExactly("B", "A").inOrder()
        assertThat(backend.meals.getValue(b).sortOrder).isEqualTo(0)
        assertThat(backend.meals.getValue(a).sortOrder).isEqualTo(1)
    }

    @Test
    fun `an edit made while its upload is in flight is not lost`() = runBlocking<Unit> {
        repository.saveMeal(null, breakfast())
        val sent = db.meals().pending().single()
        // The user edits again before the server answers the first upload…
        repository.saveMeal(sent.id, breakfast().copy(title = "Editada"))
        // …so the answer to the first upload must not clear the pending flag.
        db.meals().markSynced(sent.id, sent.updatedAt, "2026-06-15T10:00:00.000Z")

        assertThat(db.meals().get(sent.id)!!.pending).isTrue()
    }

    // --- Changes from the web ---

    @Test
    fun `meals created, edited or deleted on the web appear on the phone`() = runBlocking<Unit> {
        val web = backend.addWebMeal("Comida web")
        connect()
        sync.sync()
        assertThat(repository.mealsForDay("2026-06-15").first().map { it.title }).containsExactly("Comida web")

        backend.meals[web.id] = web.copy(title = "Editada en la web")
        sync.sync()
        assertThat(repository.mealsForDay("2026-06-15").first().map { it.title }).containsExactly("Editada en la web")

        backend.meals.clear()
        sync.sync()
        assertThat(repository.mealsForDay("2026-06-15").first()).isEmpty()
    }

    @Test
    fun `the profile is synced both ways and unset fields are sent as null`() = runBlocking<Unit> {
        backend.profile = WireCalorieProfile(calorieGoal = WireGoal.CUT, heightCm = 180.0)
        connect()
        sync.sync()
        assertThat(repository.profile().first().calorieGoal).isEqualTo(Goal.CUT)

        repository.saveProfile(CalorieProfile(null, null, 181.0, null, null, null, Goal.MAINTAIN))
        sync.sync()
        assertThat(backend.profile.calorieGoal).isEqualTo(WireGoal.MAINTAIN)
        assertThat(backend.settingsBodies.last()["gender"]).isEqualTo(JsonNull)
    }

    @Test
    fun `a broken response like a Wi-Fi login page fails the sync without losing data`() = runBlocking<Unit> {
        connect()
        repository.saveMeal(null, breakfast())
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody("<html>Login Wi-Fi</html>")
        }

        assertThat(sync.sync()).isInstanceOf(SyncOutcome.Failed::class.java)
        assertThat(repository.pendingChanges().first()).isEqualTo(1)
    }

    @Test
    fun `an expired session keeps local data and stops syncing until the next login`() = runBlocking<Unit> {
        connect()
        backend.tokenRevoked = true
        repository.saveMeal(null, breakfast())

        assertThat(sync.sync()).isEqualTo(SyncOutcome.SessionExpired)
        assertThat(account.current!!.sessionExpired).isTrue()
        assertThat(repository.mealsForDay("2026-06-15").first()).hasSize(1)

        backend.tokenRevoked = false
        val login = accounts.verify("ana", "secreta")
        assertThat(login.isReLogin).isTrue()
        accounts.connect(login, uploadLocalData = true)
        assertThat(sync.sync()).isEqualTo(SyncOutcome.Success)
        assertThat(backend.meals).hasSize(1)
    }

    // --- CSV import ---

    @Test
    fun `importing the same CSV twice does not duplicate anything`() = runBlocking<Unit> {
        repository.saveMeal(null, breakfast())
        val csv = CsvBackup.mealsCsv(repository.allMeals().first()) +
            "2026-06-16,Cena,total_only,,,,,,,,600,40,,\r\n"
        val meals = (CsvBackup.parse(csv) as CsvBackup.Parsed.Meals).meals

        assertThat(repository.importMeals(meals)).isEqualTo(ImportResult(added = 1, skipped = 1))
        assertThat(repository.importMeals(meals)).isEqualTo(ImportResult(added = 0, skipped = 2))
        assertThat(repository.allMeals().first().map { it.title }).containsExactly("Desayuno", "Cena")
    }

    @Test
    fun `data imported while logged in is uploaded`() = runBlocking<Unit> {
        connect()
        repository.importWeights(listOf(WeightRequest("2026-06-15T06:30:00.000Z", 80.4, 18.0, null)))
        sync.sync()
        assertThat(backend.weights.values.single().weightKg).isEqualTo(80.4)
    }

    // --- Login / logout ---

    @Test
    fun `logging in with local data and choosing upload merges it into the account`() = runBlocking<Unit> {
        backend.addWebMeal("Comida web")
        repository.saveMeal(null, breakfast())

        val login = accounts.verify("ana", "secreta")
        assertThat(login.localData.meals).isEqualTo(1)
        accounts.connect(login, uploadLocalData = true)
        sync.sync()

        assertThat(backend.meals.values.map { it.title }).containsExactly("Comida web", "Desayuno")
        assertThat(repository.mealsForDay("2026-06-15").first().map { it.title }).containsExactly("Comida web", "Desayuno")
    }

    @Test
    fun `logging in with local data and choosing discard keeps only the account data`() = runBlocking<Unit> {
        backend.addWebMeal("Comida web")
        repository.saveMeal(null, breakfast())

        accounts.connect(accounts.verify("ana", "secreta"), uploadLocalData = false)
        sync.sync()

        assertThat(backend.meals.values.map { it.title }).containsExactly("Comida web")
        assertThat(repository.mealsForDay("2026-06-15").first().map { it.title }).containsExactly("Comida web")
    }

    @Test
    fun `wrong credentials change nothing`() = runBlocking<Unit> {
        repository.saveMeal(null, breakfast())
        val error = runCatching { accounts.verify("ana", "mala") }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(account.current).isNull()
        assertThat(repository.mealsForDay("2026-06-15").first()).hasSize(1)
    }

    @Test
    fun `logging out deletes the data from the phone and returns to local mode`() = runBlocking<Unit> {
        connect()
        repository.saveMeal(null, breakfast())
        sync.sync()

        accounts.logout()

        assertThat(account.current).isNull()
        assertThat(repository.localDataSummary().isEmpty).isTrue()
        assertThat(backend.meals).hasSize(1) // still safe on the server
        assertThat(scheduler.cancelled).isTrue()
    }

    // --- Delete all data (this phone only) ---

    @Test
    fun `deleting all data without an account empties the phone`() = runBlocking<Unit> {
        repository.saveMeal(null, breakfast())
        repository.saveProfile(CalorieProfile(null, 1990, null, null, null, null, Goal.CUT))

        accounts.deleteLocalData()

        assertThat(repository.localDataSummary().isEmpty).isTrue()
        assertThat(repository.profile().first().calorieGoal).isNull()
        assertThat(account.current).isNull()
    }

    @Test
    fun `deleting all data while logged in never touches the server and comes back on sync`() = runBlocking<Unit> {
        connect()
        repository.saveMeal(null, breakfast())
        sync.sync()

        accounts.deleteLocalData()
        assertThat(repository.localDataSummary().isEmpty).isTrue()
        assertThat(backend.meals).hasSize(1)
        assertThat(account.current).isNotNull()

        sync.sync()
        assertThat(repository.mealsForDay("2026-06-15").first().map { it.title }).containsExactly("Desayuno")
    }

    private suspend fun connect() {
        accounts.connect(accounts.verify("ana", "secreta"), uploadLocalData = true)
    }

    private fun breakfast() = MealRequest(
        logDate = "2026-06-15",
        title = "Desayuno",
        entryMode = WireEntryMode.TOTAL_ONLY,
        totalCalories = 400.0,
        totalProtein = 20.0,
    )

    private class RecordingScheduler : SyncScheduler {
        var requests = 0
        var cancelled = false

        override fun requestSync(delaySeconds: Long) {
            requests++
        }

        override fun cancel() {
            cancelled = true
        }
    }
}
