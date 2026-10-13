package com.blackwatermacros.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.data.AccountController
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncScheduler
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * The login screen in «Añadir cuenta» mode (Android only): the account goes to its own
 * profile (`AccountProfiles.addAccount`), never through the «upload or discard your
 * local data» question of the first login, and errors read like a normal login's.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AddAccountLoginTest {

    private val main = newSingleThreadContext("main")
    private lateinit var db: LocalDatabase
    private lateinit var controller: AccountController

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).allowMainThreadQueries().build()
        val account = AccountStore(context.getSharedPreferences("add-account", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
        // Never reached in «Añadir cuenta» mode: any call would fail the test.
        val api = ApiClient.create(baseUrl = "http://unused.invalid/", tokenProvider = account::token)
        val scheduler = object : SyncScheduler {
            override fun requestSync(delaySeconds: Long) = Unit
            override fun cancel() = Unit
        }
        val repository = AppRepository(db, account, onLocalChange = {})
        controller = AccountController(account, repository, SyncEngine(db, account, api), scheduler, api, apiWithToken = { api })
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    @Test
    fun `adding an account connects it without asking about local data`() = runBlocking<Unit> {
        val added = mutableListOf<Pair<String, String>>()
        val viewModel = LoginViewModel(controller, addAccount = { u, p -> added += u to p })

        viewModel.login("luis", "secreta", asNewAccount = true)

        assertThat(withTimeout(5_000) { viewModel.state.first { it != LoginUiState.Loading && it != LoginUiState.Idle } })
            .isEqualTo(LoginUiState.Connected)
        assertThat(added).containsExactly("luis" to "secreta")
    }

    @Test
    fun `a wrong password or no network says so, like a normal login`() = runBlocking<Unit> {
        val unauthorized = HttpException(Response.error<Any>(401, "{}".toResponseBody("application/json".toMediaType())))
        val wrongPassword = LoginViewModel(controller, addAccount = { _, _ -> throw unauthorized })
        wrongPassword.login("luis", "mala", asNewAccount = true)
        assertThat(withTimeout(5_000) { wrongPassword.state.first { it is LoginUiState.Error } })
            .isEqualTo(LoginUiState.Error(LoginError.BadCredentials))

        val offline = LoginViewModel(controller, addAccount = { _, _ -> throw IOException("offline") })
        offline.login("luis", "secreta", asNewAccount = true)
        assertThat(withTimeout(5_000) { offline.state.first { it is LoginUiState.Error } })
            .isEqualTo(LoginUiState.Error(LoginError.Network))
    }

    @Test
    fun `empty fields are caught before anything is sent`() {
        var called = false
        val viewModel = LoginViewModel(controller, addAccount = { _, _ -> called = true })

        viewModel.login("", "secreta", asNewAccount = true)

        assertThat(viewModel.state.value).isEqualTo(LoginUiState.Error(LoginError.MissingFields))
        assertThat(called).isFalse()
    }
}
