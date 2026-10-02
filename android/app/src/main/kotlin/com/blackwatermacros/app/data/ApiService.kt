package com.blackwatermacros.app.data

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the endpoints the Android app uses:
 * sync (full lists + idempotent `PUT /:id` + `DELETE`), auth, settings and admin.
 * The app never creates/edits through POST/PATCH — writes are local first and
 * pushed later by `SyncEngine`. All paths are **absolute from the
 * origin root** and include the `/api` prefix, so the base URL is simply the
 * deployment origin (e.g. `https://blackwater-macros.jordanmontt.fr/`). Auth is a
 * Bearer token attached by the `AccountInterceptor` (which refuses every request but the login without an account).
 */
interface ApiService {

    // --- Auth ---
    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("/api/auth/logout")
    suspend fun logout(): OkResponse

    @GET("/api/auth/session")
    suspend fun session(): SessionResponse

    // --- Meals ---
    @GET("/api/meals")
    suspend fun listMeals(@Query("from") from: String? = null, @Query("to") to: String? = null): MealsResponse

    /** Idempotent create-or-replace with a phone-generated id (offline sync). */
    @PUT("/api/meals/{id}")
    suspend fun putMeal(@Path("id") id: String, @Body body: MealRequest): MealResponse

    @DELETE("/api/meals/{id}")
    suspend fun deleteMeal(@Path("id") id: String): OkResponse


    // --- Templates ---
    @GET("/api/templates")
    suspend fun listTemplates(): TemplatesResponse

    @PUT("/api/templates/{id}")
    suspend fun putTemplate(@Path("id") id: String, @Body body: TemplateRequest): TemplateResponse

    @DELETE("/api/templates/{id}")
    suspend fun deleteTemplate(@Path("id") id: String): OkResponse

    // --- Weights ---
    @GET("/api/weights")
    suspend fun listWeights(): WeightsResponse

    @PUT("/api/weights/{id}")
    suspend fun putWeight(@Path("id") id: String, @Body body: WeightRequest): WeightResponse

    @DELETE("/api/weights/{id}")
    suspend fun deleteWeight(@Path("id") id: String): OkResponse

    // --- Settings ---
    /**
     * Body must carry every profile key with explicit `null`s (the server schema
     * is `.nullable()`, not optional) — build it with [profileBody].
     */
    @PUT("/api/settings")
    suspend fun updateSettings(@Body body: JsonObject): SettingsResponse

    // --- Admin ---
    @GET("/api/admin/users")
    suspend fun listUsers(): UsersResponse

    @POST("/api/admin/users")
    suspend fun createUser(@Body body: AdminCreateUserRequest): OkResponse

    @PATCH("/api/admin/users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body body: AdminUpdateUserRequest): UserResponse

    @DELETE("/api/admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: String): OkResponse
}