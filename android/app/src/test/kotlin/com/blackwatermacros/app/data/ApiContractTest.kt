package com.blackwatermacros.app.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * MockWebServer contract tests porting the wire contract from
 * `tests/behavior/routes-*.test.ts` (see `docs/api.md`). These are pure JVM tests
 * (Retrofit + MockWebServer) and do not require an Android SDK. Retrofit suspend
 * calls are driven with `runTest`.
 */
class ApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiService
    private val json = ApiJson
    private var token: String? = "test-token"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ApiClient.create(
            baseUrl = server.url("/api/").toString(),
            tokenProvider = { token },
            json = json,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun jsonResponse(code: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(body)

    // --- Auth ---

    @Test
    fun login_successParsesTokenAndExpiry() = runTest {
        server.enqueue(
            jsonResponse(200, """{"ok":true,"token":"abc123","expiresAt":"2026-12-01T15:14:08.141Z"}"""),
        )
        val response = api.login(LoginRequest("ana", "pass"))
        assertThat(response.ok).isTrue()
        assertThat(response.token).isEqualTo("abc123")
        assertThat(response.expiresAt).startsWith("2026-12-01")
    }

    @Test
    fun login_wrongCredentialsThrows401WithSpanishError() = runTest {
        server.enqueue(jsonResponse(401, """{"error":"Usuario o contrasena incorrectos"}"""))
        val e = runCatching { api.login(LoginRequest("ana", "incorrecta")) }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(401)
    }

    @Test
    fun logout_returnsOk() = runTest {
        server.enqueue(jsonResponse(200, """{"ok":true}"""))
        assertThat(api.logout().ok).isTrue()
    }

    @Test
    fun session_successReturnsUsernameIsAdminAndCalorieProfile() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"username":"ana","isAdmin":true,"calorieProfile":{"gender":null,"birthYear":null,"heightCm":null,"gymDaysPerWeek":null,"gymSessionMinutes":null,"walkingMinutesPerDay":null,"calorieGoal":null}}""",
            ),
        )
        val session = api.session()
        assertThat(session.username).isEqualTo("ana")
        assertThat(session.isAdmin).isTrue()
        assertThat(session.calorieProfile.gender).isNull()
    }

    @Test
    fun session_unauthThrows401() = runTest {
        server.enqueue(jsonResponse(401, """{"error":"No autenticado"}"""))
        val e = runCatching { api.session() }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(401)
    }

    @Test
    fun bearerInterceptorAttachesAuthorizationHeader() = runTest {
        server.enqueue(jsonResponse(200, """{"ok":true}"""))
        api.logout()
        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
    }

    // --- Meals ---

    @Test
    fun listMeals_returnsEmptyList() = runTest {
        server.enqueue(jsonResponse(200, """{"meals":[]}"""))
        assertThat(api.listMeals().meals).isEmpty()
    }

    @Test
    fun createMeal_perIngredientParsesResolvedTotals() = runTest {
        server.enqueue(
            jsonResponse(
                201,
                """{"meal":{"id":"m-1","logDate":"2026-06-15","title":"Desayuno","notes":null,"entryMode":"per_ingredient","ingredients":[],"totalCalories":null,"totalProtein":null,"totalCarbs":null,"totalFat":null,"resolvedCalories":250.0,"resolvedProtein":8.5,"resolvedCarbs":32.0,"resolvedFat":10.0,"updatedAt":"2026-06-15T08:00:00.000Z"}}""",
            ),
        )
        val meal = api.createMeal(
            MealRequest(
                logDate = "2026-06-15",
                title = "Desayuno",
                entryMode = WireEntryMode.PER_INGREDIENT,
            ),
        ).meal
        assertThat(meal.id).isEqualTo("m-1")
        assertThat(meal.resolvedCalories).isEqualTo(250.0)
        assertThat(meal.entryMode).isEqualTo(WireEntryMode.PER_INGREDIENT)
    }

    @Test
    fun createMeal_validationErrorThrows400() = runTest {
        server.enqueue(jsonResponse(400, """{"error":"El titulo es obligatorio"}"""))
        val e = runCatching {
            api.createMeal(
                MealRequest(logDate = "2026-06-15", title = "", entryMode = WireEntryMode.PER_INGREDIENT),
            )
        }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(400)
    }

    @Test
    fun updateMeal_returnsUpdatedMeal() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"meal":{"id":"m-1","logDate":"2026-06-15","title":"Despues","notes":null,"entryMode":"total_only","ingredients":[],"totalCalories":600.0,"totalProtein":null,"totalCarbs":null,"totalFat":null,"resolvedCalories":600.0,"resolvedProtein":0.0,"resolvedCarbs":0.0,"resolvedFat":0.0,"updatedAt":"2026-06-15T09:00:00.000Z"}}""",
            ),
        )
        val meal = api.updateMeal(
            "m-1",
            MealRequest(
                logDate = "2026-06-15",
                title = "Despues",
                entryMode = WireEntryMode.TOTAL_ONLY,
                totalCalories = 600.0,
            ),
        ).meal
        assertThat(meal.title).isEqualTo("Despues")
        assertThat(meal.resolvedCalories).isEqualTo(600.0)
    }

    @Test
    fun deleteMeal_otherUsersThrows404() = runTest {
        server.enqueue(jsonResponse(404, """{"error":"Comida no encontrada"}"""))
        val e = runCatching { api.deleteMeal("m-otro") }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(404)
    }

    @Test
    fun reorderMeals_returnsOkAndSendsOrderedIds() = runTest {
        server.enqueue(jsonResponse(200, """{"ok":true}"""))
        api.reorderMeals(ReorderRequest(listOf("m-2", "m-1")))
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/api/meals/reorder")
        val bodyObj = json.decodeFromString<ReorderRequest>(request.body.readUtf8())
        assertThat(bodyObj.orderedIds).containsExactly("m-2", "m-1").inOrder()
    }

    // --- Templates ---

    @Test
    fun createTemplate_parsesTemplate() = runTest {
        server.enqueue(
            jsonResponse(
                201,
                """{"template":{"id":"t-1","name":"Desayuno base","title":"Avena con leche","notes":null,"entryMode":"per_ingredient","ingredients":[{"name":"Avena","calories":150,"protein":5}],"totalCalories":null,"totalProtein":null,"totalCarbs":null,"totalFat":null,"resolvedCalories":250.0,"resolvedProtein":8.5,"resolvedCarbs":0.0,"resolvedFat":0.0,"updatedAt":"2026-06-15T08:00:00.000Z"}}""",
            ),
        )
        val template = api.createTemplate(
            TemplateRequest(
                name = "Desayuno base",
                title = "Avena con leche",
                entryMode = WireEntryMode.PER_INGREDIENT,
                ingredients = listOf(WireIngredient(name = "Avena", calories = 150.0, protein = 5.0)),
            ),
        ).template
        assertThat(template.name).isEqualTo("Desayuno base")
        assertThat(template.resolvedCalories).isEqualTo(250.0)
    }

    @Test
    fun updateTemplate_otherUsersThrows404() = runTest {
        server.enqueue(jsonResponse(404, """{"error":"Plantilla no encontrada"}"""))
        val e = runCatching {
            api.updateTemplate(
                "t-otra",
                TemplateRequest(name = "X", title = "X", entryMode = WireEntryMode.PER_INGREDIENT),
            )
        }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(404)
    }

    // --- Weights ---

    @Test
    fun createWeight_withBodyFat() = runTest {
        server.enqueue(
            jsonResponse(
                201,
                """{"weight":{"id":"w-1","measuredAt":"2026-06-15T08:00:00.000Z","weightKg":77.4,"bodyFatPct":18.5,"note":null,"updatedAt":"2026-06-15T08:00:00.000Z"}}""",
            ),
        )
        val weight = api.createWeight(
            WeightRequest(measuredAt = "2026-06-15T08:00:00.000Z", weightKg = 77.4, bodyFatPct = 18.5),
        ).weight
        assertThat(weight.weightKg).isEqualTo(77.4)
        assertThat(weight.bodyFatPct).isEqualTo(18.5)
    }

    @Test
    fun createWeight_outOfRangeThrows400() = runTest {
        server.enqueue(jsonResponse(400, """{"error":"Peso fuera de rango"}"""))
        val e = runCatching {
            api.createWeight(WeightRequest(measuredAt = "2026-06-15T08:00:00.000Z", weightKg = 5.0))
        }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(400)
    }

    // --- Stats (flat, not wrapped) ---

    @Test
    fun stats_parsesFlatSummaryWithWeight() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"calories":[{"date":"2026-06-15","calories":500.0,"protein":25.0,"carbs":50.0,"fat":20.0}],"protein":[],"carbs":[],"fat":[],"weights":[{"date":"2026-06-15","weight":80.0,"trend":null}],"bodyFat":[],"caloriesAvg":500.0,"caloriesMaxDay":{"date":"2026-06-15","calories":500.0,"protein":25.0,"carbs":50.0,"fat":20.0},"proteinAvg":null,"proteinMaxDay":null,"carbsAvg":null,"carbsMaxDay":null,"fatAvg":null,"fatMaxDay":null,"weight":{"currentWeightKg":80.0,"currentTrendKg":null,"changeSinceStartKg":null,"ratePerWeekKg":null,"minKg":80.0,"maxKg":80.0,"currentBodyFatPct":null,"changeBodyFatPct":null,"minBodyFatPct":null,"maxBodyFatPct":null},"weeklyWeightAvg":[]}""",
            ),
        )
        val stats = api.stats(range = "30d", today = "2026-06-15")
        assertThat(stats.caloriesAvg).isEqualTo(500.0)
        assertThat(stats.weight.currentWeightKg).isEqualTo(80.0)
        assertThat(stats.calories).hasSize(1)
        assertThat(stats.weight.currentBodyFatPct).isNull()
    }

    @Test
    fun stats_unauthThrows401() = runTest {
        server.enqueue(jsonResponse(401, """{"error":"No autenticado"}"""))
        val e = runCatching { api.stats(range = "30d", today = "2026-06-15") }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(401)
    }

    // --- Settings ---

    @Test
    fun updateSettings_parsesCalorieProfile() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"calorieProfile":{"gender":"male","birthYear":1990,"heightCm":178.0,"gymDaysPerWeek":3,"gymSessionMinutes":60,"walkingMinutesPerDay":30,"calorieGoal":"cut"}}""",
            ),
        )
        val settings = api.updateSettings(
            WireCalorieProfile(
                gender = WireGender.MALE,
                birthYear = 1990,
                heightCm = 178.0,
                gymDaysPerWeek = 3,
                gymSessionMinutes = 60,
                walkingMinutesPerDay = 30,
                calorieGoal = WireGoal.CUT,
            ),
        )
        assertThat(settings.calorieProfile.gender).isEqualTo(WireGender.MALE)
        assertThat(settings.calorieProfile.heightCm).isEqualTo(178.0)
        assertThat(settings.calorieProfile.calorieGoal).isEqualTo(WireGoal.CUT)
    }

    // --- Admin ---

    @Test
    fun admin_listUsers_parsesUsers() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"users":[{"id":"u-1","username":"jefe","isAdmin":true,"createdAt":"2026-06-15T08:00:00.000Z"},{"id":"u-2","username":"ana","isAdmin":false,"createdAt":"2026-06-15T08:00:00.000Z"}]}""",
            ),
        )
        val users = api.listUsers().users
        assertThat(users).hasSize(2)
        assertThat(users[0].username).isEqualTo("jefe")
        assertThat(users[0].isAdmin).isTrue()
    }

    @Test
    fun admin_normalUserThrows403() = runTest {
        server.enqueue(jsonResponse(403, """{"error":"No autorizado"}"""))
        val e = runCatching { api.listUsers() }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(403)
    }

    @Test
    fun admin_createUser_duplicateThrows409() = runTest {
        server.enqueue(jsonResponse(409, """{"error":"El usuario ya existe"}"""))
        val e = runCatching { api.createUser(AdminCreateUserRequest("luis", "mi-clave-123")) }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(409)
    }
}