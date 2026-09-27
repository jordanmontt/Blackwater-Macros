package com.blackwatermacros.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.Account
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppPreferences
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.MealRequest
import com.blackwatermacros.app.data.WireEntryMode
import com.blackwatermacros.app.data.local.LocalDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First launch (web `onboarding.test.tsx`): shown on a fresh install only, never
 * to someone who already uses the app; «Tus datos» saves the profile (with the
 * default activity) and the current weight; finishing or skipping marks it done.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingTest {

    private val main = newSingleThreadContext("main")
    private lateinit var db: LocalDatabase
    private lateinit var repository: AppRepository
    private lateinit var account: AccountStore
    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).allowMainThreadQueries().build()
        account = AccountStore(context.getSharedPreferences("ob-account", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        preferences = AppPreferences(context.getSharedPreferences("ob-settings", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        repository = AppRepository(db, account, onLocalChange = {})
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    @Test
    fun `only a fresh install without an account sees the first steps`() {
        assertThat(shouldShowOnboarding(done = false, loggedIn = false, hasLocalData = false)).isTrue()
        assertThat(shouldShowOnboarding(done = true, loggedIn = false, hasLocalData = false)).isFalse()
        assertThat(shouldShowOnboarding(done = false, loggedIn = true, hasLocalData = false)).isFalse()
        assertThat(shouldShowOnboarding(done = false, loggedIn = false, hasLocalData = true)).isFalse()
    }

    @Test
    fun `a fresh install opens on the first steps`() = runBlocking {
        assertThat(resolveOnboarding(preferences, account, repository)).isTrue()
        assertThat(preferences.onboardingDone).isFalse()
    }

    @Test
    fun `an update never shows them to someone with data, and remembers that`() = runBlocking {
        repository.saveMeal(null, MealRequest(todayKey(), "Comida", entryMode = WireEntryMode.TOTAL_ONLY, totalCalories = 500.0))
        assertThat(resolveOnboarding(preferences, account, repository)).isFalse()
        assertThat(preferences.onboardingDone).isTrue()
    }

    @Test
    fun `a logged-in user does not see them`() = runBlocking {
        account.save(Account("ana", "token", isAdmin = false))
        assertThat(resolveOnboarding(preferences, account, repository)).isFalse()
    }

    @Test
    fun `the form needs every field in range`() {
        val form = OnboardingForm(Gender.FEMALE, Goal.CUT, "1992", "165", "62,5")
        assertThat(form.parsed()).isEqualTo(CalorieProfile(Gender.FEMALE, 1992, 165.0, 0, 60, 30, Goal.CUT) to 62.5)
        assertThat(form.copy(birthYear = "1900").parsed()).isNull()
        assertThat(form.copy(weight = "10").parsed()).isNull()
        assertThat(form.copy(goal = null).parsed()).isNull()
        assertThat(form.copy(goal = null).filled).isFalse()
        assertThat(form.copy(height = "300").filled).isTrue()
    }

    @Test
    fun `«Tus datos» saves the profile and the weight, then goes to the AI step`() = runBlocking {
        val viewModel = OnboardingViewModel(repository, preferences, account)
        assertThat(viewModel.step.value).isEqualTo(OnboardingStep.WELCOME)
        viewModel.goTo(OnboardingStep.DATA)
        withTimeout(5_000) { viewModel.form.first { it.gymDays == "0" } }
        viewModel.update { it.copy(gender = Gender.MALE, goal = Goal.MAINTAIN, birthYear = "1990", height = "178", weight = "80") }
        viewModel.saveData()
        withTimeout(5_000) { viewModel.step.first { it == OnboardingStep.AI } }

        assertThat(repository.profile().first()).isEqualTo(CalorieProfile(Gender.MALE, 1990, 178.0, 0, 60, 30, Goal.MAINTAIN))
        assertThat(repository.weights().first().map { it.weightKg }).containsExactly(80.0)

        viewModel.finish()
        assertThat(preferences.onboardingDone).isTrue()
    }
}
