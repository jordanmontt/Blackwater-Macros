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
    fun putMeal_perIngredientParsesResolvedTotals() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"meal":{"id":"m-1","logDate":"2026-06-15","title":"Desayuno","notes":null,"entryMode":"per_ingredient","ingredients":[],"totalCalories":null,"totalProtein":null,"totalCarbs":null,"totalFat":null,"resolvedCalories":250.0,"resolvedProtein":8.5,"resolvedCarbs":32.0,"resolvedFat":10.0,"updatedAt":"2026-06-15T08:00:00.000Z"}}""",
            ),
        )
        val meal = api.putMeal(
            "m-1",
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
    fun putMeal_validationErrorThrows400() = runTest {
        server.enqueue(jsonResponse(400, """{"error":"El titulo es obligatorio"}"""))
        val e = runCatching {
            api.putMeal(
                "m-1",
                MealRequest(logDate = "2026-06-15", title = "", entryMode = WireEntryMode.PER_INGREDIENT),
            )
        }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(400)
    }

    @Test
    fun putMeal_replacesExistingMeal() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"meal":{"id":"m-1","logDate":"2026-06-15","title":"Despues","notes":null,"entryMode":"total_only","ingredients":[],"totalCalories":600.0,"totalProtein":null,"totalCarbs":null,"totalFat":null,"resolvedCalories":600.0,"resolvedProtein":0.0,"resolvedCarbs":0.0,"resolvedFat":0.0,"updatedAt":"2026-06-15T09:00:00.000Z"}}""",
            ),
        )
        val meal = api.putMeal(
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

    // --- Templates ---

    @Test
    fun putTemplate_parsesTemplate() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"template":{"id":"t-1","name":"Desayuno base","title":"Avena con leche","notes":null,"entryMode":"per_ingredient","ingredients":[{"name":"Avena","calories":150,"protein":5}],"totalCalories":null,"totalProtein":null,"totalCarbs":null,"totalFat":null,"resolvedCalories":250.0,"resolvedProtein":8.5,"resolvedCarbs":0.0,"resolvedFat":0.0,"updatedAt":"2026-06-15T08:00:00.000Z"}}""",
            ),
        )
        val template = api.putTemplate(
            "t-1",
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
    fun putTemplate_otherUsersThrows404() = runTest {
        server.enqueue(jsonResponse(404, """{"error":"Plantilla no encontrada"}"""))
        val e = runCatching {
            api.putTemplate(
                "t-otra",
                TemplateRequest(name = "X", title = "X", entryMode = WireEntryMode.PER_INGREDIENT),
            )
        }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(404)
    }

    // --- Weights ---

    @Test
    fun putWeight_withBodyFat() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"weight":{"id":"w-1","measuredAt":"2026-06-15T08:00:00.000Z","weightKg":77.4,"bodyFatPct":18.5,"note":null,"updatedAt":"2026-06-15T08:00:00.000Z"}}""",
            ),
        )
        val weight = api.putWeight(
            "w-1",
            WeightRequest(measuredAt = "2026-06-15T08:00:00.000Z", weightKg = 77.4, bodyFatPct = 18.5),
        ).weight
        assertThat(weight.weightKg).isEqualTo(77.4)
        assertThat(weight.bodyFatPct).isEqualTo(18.5)
    }

    @Test
    fun putWeight_outOfRangeThrows400() = runTest {
        server.enqueue(jsonResponse(400, """{"error":"Peso fuera de rango"}"""))
        val e = runCatching {
            api.putWeight("w-1", WeightRequest(measuredAt = "2026-06-15T08:00:00.000Z", weightKg = 5.0))
        }.exceptionOrNull()
        assertThat(e).isInstanceOf(HttpException::class.java)
        assertThat((e as HttpException).code()).isEqualTo(400)
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
            profileBody(
                WireCalorieProfile(
                    gender = WireGender.MALE,
                    birthYear = 1990,
                    heightCm = 178.0,
                    gymDaysPerWeek = 3,
                    gymSessionMinutes = 60,
                    walkingMinutesPerDay = 30,
                    calorieGoal = WireGoal.CUT,
                ),
            ),
        )
        assertThat(settings.calorieProfile.gender).isEqualTo(WireGender.MALE)
        assertThat(settings.calorieProfile.heightCm).isEqualTo(178.0)
        assertThat(settings.calorieProfile.calorieGoal).isEqualTo(WireGoal.CUT)
    }

    @Test
    fun updateSettings_sendsUnsetFieldsAsExplicitNulls() = runTest {
        // The server schema is `.nullable()` (key required), so omitting a key is a 400.
        server.enqueue(jsonResponse(200, """{"calorieProfile":{}}"""))
        api.updateSettings(profileBody(WireCalorieProfile(calorieGoal = WireGoal.MAINTAIN)))
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"gender\":null")
        assertThat(body).contains("\"walkingMinutesPerDay\":null")
        assertThat(body).contains("\"calorieGoal\":\"maintain\"")
    }

    // --- Offline sync upserts ---

    @Test
    fun putMeal_sendsPhoneIdAndSortOrder() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"meal":{"id":"5b0f7a52-9d0a-4f5e-8a57-3f1c2b6f0a11","logDate":"2026-06-15","title":"Desayuno","notes":null,"entryMode":"total_only","ingredients":[],"totalCalories":400,"totalProtein":20,"totalCarbs":null,"totalFat":null,"resolvedCalories":400,"resolvedProtein":20,"resolvedCarbs":0,"resolvedFat":0,"sortOrder":2,"updatedAt":"2026-06-15T08:00:00.000Z"}}""",
            ),
        )
        val meal = api.putMeal(
            "5b0f7a52-9d0a-4f5e-8a57-3f1c2b6f0a11",
            MealRequest(
                logDate = "2026-06-15",
                title = "Desayuno",
                entryMode = WireEntryMode.TOTAL_ONLY,
                totalCalories = 400.0,
                totalProtein = 20.0,
                sortOrder = 2,
            ),
        ).meal
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/meals/5b0f7a52-9d0a-4f5e-8a57-3f1c2b6f0a11")
        assertThat(request.body.readUtf8()).contains("\"sortOrder\":2")
        assertThat(meal.sortOrder).isEqualTo(2)
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