package com.blackwatermacros.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.UUID

/**
 * In-memory stand-in for the Next.js backend, covering the endpoints the sync
 * engine uses. Behaves like the real routes: `PUT /:id` is create-or-replace,
 * `DELETE` of a missing id is 404, every call needs the Bearer token.
 */
class FakeServer(
    private val validToken: String = "token-ana",
    val username: String = "ana",
) : Dispatcher() {

    val meals = linkedMapOf<String, MealDTO>()
    val templates = linkedMapOf<String, TemplateDTO>()
    val weights = linkedMapOf<String, WeightDTO>()
    var profile: WireCalorieProfile = WireCalorieProfile()

    /** Raw `PUT /api/settings` bodies, to check explicit nulls. */
    val settingsBodies = mutableListOf<JsonObject>()

    /** Simulates the gym: every request fails at the socket level. */
    var offline = false

    var tokenRevoked = false
    var requestCount = 0
        private set

    private var clock = 0

    private fun stamp(): String = "2026-06-15T10:00:%02d.000Z".format(++clock % 60)

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (offline) {
            return MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        requestCount++
        val path = request.path!!.substringBefore('?')
        val method = request.method!!

        if (path == "/api/auth/login" && method == "POST") {
            val body = ApiJson.decodeFromString(LoginRequest.serializer(), request.body.readUtf8())
            return if (body.username == username && body.password == "secreta") {
                json(200, """{"ok":true,"token":"$validToken","expiresAt":"2026-12-01T00:00:00.000Z"}""")
            } else {
                json(401, """{"error":"Usuario o contraseña incorrectos"}""")
            }
        }

        if (tokenRevoked || request.getHeader("Authorization") != "Bearer $validToken") {
            return json(401, """{"error":"No autenticado"}""")
        }

        val segments = path.removePrefix("/api/").split('/')
        return when {
            path == "/api/auth/session" ->
                ok(SessionResponse.serializer(), SessionResponse(username, isAdmin = false, calorieProfile = profile))
            path == "/api/auth/logout" -> json(200, """{"ok":true}""")
            path == "/api/settings" && method == "PUT" -> {
                val raw = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
                settingsBodies += raw
                profile = ApiJson.decodeFromJsonElement(WireCalorieProfile.serializer(), raw)
                ok(SettingsResponse.serializer(), SettingsResponse(profile))
            }
            segments[0] == "meals" -> handleMeals(method, segments.getOrNull(1), request)
            segments[0] == "templates" -> handleTemplates(method, segments.getOrNull(1), request)
            segments[0] == "weights" -> handleWeights(method, segments.getOrNull(1), request)
            else -> json(404, """{"error":"No encontrado"}""")
        }
    }

    private fun handleMeals(method: String, id: String?, request: RecordedRequest): MockResponse = when {
        id == null && method == "GET" ->
            ok(MealsResponse.serializer(), MealsResponse(meals.values.sortedWith(compareBy({ it.logDate }, { it.sortOrder }))))
        id != null && method == "PUT" -> {
            val body = ApiJson.decodeFromString(MealRequest.serializer(), request.body.readUtf8())
            val meal = serverMeal(id, body, sortOrder = body.sortOrder ?: meals[id]?.sortOrder ?: 0)
            meals[id] = meal
            ok(MealResponse.serializer(), MealResponse(meal))
        }
        id != null && method == "DELETE" -> if (meals.remove(id) != null) json(200, """{"ok":true}""") else json(404, """{"error":"Comida no encontrada"}""")
        else -> json(405, """{"error":"No soportado"}""")
    }

    private fun handleTemplates(method: String, id: String?, request: RecordedRequest): MockResponse = when {
        id == null && method == "GET" -> ok(TemplatesResponse.serializer(), TemplatesResponse(templates.values.toList()))
        id != null && method == "PUT" -> {
            val body = ApiJson.decodeFromString(TemplateRequest.serializer(), request.body.readUtf8())
            val template = TemplateDTO(
                id, body.name, body.title, body.notes, body.entryMode, body.ingredients,
                body.totalCalories, body.totalProtein, body.totalCarbs, body.totalFat,
                body.totalCalories ?: 0.0, body.totalProtein ?: 0.0, body.totalCarbs ?: 0.0, body.totalFat ?: 0.0,
                stamp(),
            )
            templates[id] = template
            ok(TemplateResponse.serializer(), TemplateResponse(template))
        }
        id != null && method == "DELETE" -> if (templates.remove(id) != null) json(200, """{"ok":true}""") else json(404, """{"error":"Plantilla no encontrada"}""")
        else -> json(405, """{"error":"No soportado"}""")
    }

    private fun handleWeights(method: String, id: String?, request: RecordedRequest): MockResponse = when {
        id == null && method == "GET" -> ok(WeightsResponse.serializer(), WeightsResponse(weights.values.sortedBy { it.measuredAt }))
        id != null && method == "PUT" -> {
            val body = ApiJson.decodeFromString(WeightRequest.serializer(), request.body.readUtf8())
            val weight = WeightDTO(id, body.measuredAt, body.weightKg, body.bodyFatPct, body.note, stamp())
            weights[id] = weight
            ok(WeightResponse.serializer(), WeightResponse(weight))
        }
        id != null && method == "DELETE" -> if (weights.remove(id) != null) json(200, """{"ok":true}""") else json(404, """{"error":"Registro no encontrado"}""")
        else -> json(405, """{"error":"No soportado"}""")
    }

    /** Seeds a meal as if it had been created on the web. */
    fun addWebMeal(title: String, logDate: String = "2026-06-15", calories: Double = 500.0): MealDTO {
        val id = UUID.randomUUID().toString()
        val meal = serverMeal(
            id,
            MealRequest(logDate = logDate, title = title, entryMode = WireEntryMode.TOTAL_ONLY, totalCalories = calories),
            sortOrder = meals.values.count { it.logDate == logDate },
        )
        meals[id] = meal
        return meal
    }

    private fun serverMeal(id: String, body: MealRequest, sortOrder: Int) = MealDTO(
        id = id,
        logDate = body.logDate,
        title = body.title,
        notes = body.notes,
        entryMode = body.entryMode,
        ingredients = body.ingredients,
        totalCalories = body.totalCalories,
        totalProtein = body.totalProtein,
        totalCarbs = body.totalCarbs,
        totalFat = body.totalFat,
        resolvedCalories = body.totalCalories ?: body.ingredients.sumOf { it.calories ?: 0.0 },
        resolvedProtein = body.totalProtein ?: body.ingredients.sumOf { it.protein ?: 0.0 },
        resolvedCarbs = body.totalCarbs ?: 0.0,
        resolvedFat = body.totalFat ?: 0.0,
        updatedAt = stamp(),
        sortOrder = sortOrder,
    )

    private fun <T> ok(serializer: kotlinx.serialization.KSerializer<T>, value: T): MockResponse =
        json(200, ApiJson.encodeToString(serializer, value))

    private fun json(code: Int, body: String): MockResponse =
        MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body)
}
