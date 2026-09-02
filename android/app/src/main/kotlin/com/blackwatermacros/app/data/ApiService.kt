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
 * Retrofit interface mirroring `docs/api.md`. Paths are relative to the base URL
 * (the deployed Next.js backend, `…/api`). Auth is a Bearer token attached by
 * the `BearerAuthInterceptor`.
 */
interface ApiService {

    // --- Auth ---
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout(): OkResponse

    @GET("auth/session")
    suspend fun session(): SessionResponse

    // --- Meals ---
    @GET("meals")
    suspend fun listMeals(@Query("from") from: String? = null, @Query("to") to: String? = null): MealsResponse

    @POST("meals")
    suspend fun createMeal(@Body body: MealRequest): MealResponse

    @PATCH("meals/{id}")
    suspend fun updateMeal(@Path("id") id: String, @Body body: MealRequest): MealResponse

    @DELETE("meals/{id}")
    suspend fun deleteMeal(@Path("id") id: String): OkResponse

    @PATCH("meals/reorder")
    suspend fun reorderMeals(@Body body: ReorderRequest): OkResponse

    // --- Templates ---
    @GET("templates")
    suspend fun listTemplates(): TemplatesResponse

    @POST("templates")
    suspend fun createTemplate(@Body body: TemplateRequest): TemplateResponse

    @PATCH("templates/{id}")
    suspend fun updateTemplate(@Path("id") id: String, @Body body: TemplateRequest): TemplateResponse

    @DELETE("templates/{id}")
    suspend fun deleteTemplate(@Path("id") id: String): OkResponse

    // --- Weights ---
    @GET("weights")
    suspend fun listWeights(): WeightsResponse

    @POST("weights")
    suspend fun createWeight(@Body body: WeightRequest): WeightResponse

    @PATCH("weights/{id}")
    suspend fun updateWeight(@Path("id") id: String, @Body body: WeightRequest): WeightResponse

    @DELETE("weights/{id}")
    suspend fun deleteWeight(@Path("id") id: String): OkResponse

    // --- Stats ---
    @GET("stats")
    suspend fun stats(@Query("range") range: String? = null, @Query("today") today: String? = null): StatsSummary

    // --- Settings ---
    @PUT("settings")
    suspend fun updateSettings(@Body body: WireCalorieProfile): SettingsResponse

    // --- Admin ---
    @GET("admin/users")
    suspend fun listUsers(): UsersResponse

    @POST("admin/users")
    suspend fun createUser(@Body body: AdminCreateUserRequest): OkResponse

    @PATCH("admin/users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body body: AdminUpdateUserRequest): UserResponse

    @DELETE("admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: String): OkResponse
}