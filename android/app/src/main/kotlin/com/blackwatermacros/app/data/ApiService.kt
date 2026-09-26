package com.blackwatermacros.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface mirroring `docs/api.md`. All paths are **absolute from the
 * origin root** and include the `/api` prefix, so the base URL is simply the
 * deployment origin (e.g. `https://blackwater-macros.jordanmontt.fr/`). Auth is a
 * Bearer token attached by the `BearerAuthInterceptor`.
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

    @POST("/api/meals")
    suspend fun createMeal(@Body body: MealRequest): MealResponse

    @PATCH("/api/meals/{id}")
    suspend fun updateMeal(@Path("id") id: String, @Body body: MealRequest): MealResponse

    @DELETE("/api/meals/{id}")
    suspend fun deleteMeal(@Path("id") id: String): OkResponse

    @PATCH("/api/meals/reorder")
    suspend fun reorderMeals(@Body body: ReorderRequest): OkResponse

    // --- Templates ---
    @GET("/api/templates")
    suspend fun listTemplates(): TemplatesResponse

    @POST("/api/templates")
    suspend fun createTemplate(@Body body: TemplateRequest): TemplateResponse

    @PATCH("/api/templates/{id}")
    suspend fun updateTemplate(@Path("id") id: String, @Body body: TemplateRequest): TemplateResponse

    @DELETE("/api/templates/{id}")
    suspend fun deleteTemplate(@Path("id") id: String): OkResponse

    // --- Weights ---
    @GET("/api/weights")
    suspend fun listWeights(): WeightsResponse

    @POST("/api/weights")
    suspend fun createWeight(@Body body: WeightRequest): WeightResponse

    @PATCH("/api/weights/{id}")
    suspend fun updateWeight(@Path("id") id: String, @Body body: WeightRequest): WeightResponse

    @DELETE("/api/weights/{id}")
    suspend fun deleteWeight(@Path("id") id: String): OkResponse

    // --- Stats ---
    @GET("/api/stats")
    suspend fun stats(@Query("range") range: String? = null, @Query("today") today: String? = null): StatsSummary

    // --- Settings ---
    @PUT("/api/settings")
    suspend fun updateSettings(@Body body: WireCalorieProfile): SettingsResponse

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