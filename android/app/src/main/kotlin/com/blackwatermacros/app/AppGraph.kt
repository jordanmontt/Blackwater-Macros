package com.blackwatermacros.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.blackwatermacros.app.data.AccountController
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppPreferences
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.ApiService
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.ai.AiClient
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.foods.GenericFoodsStore
import com.blackwatermacros.app.data.foods.OpenFoodFactsClient
import com.blackwatermacros.app.data.foods.RecentFoods
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncScheduler
import com.blackwatermacros.app.data.sync.WorkManagerSyncScheduler

/**
 * App-wide singletons, built once in [BlackwaterApp.onCreate]. A plain service
 * locator: ViewModels read from here instead of building their own clients.
 */
object AppGraph {
    lateinit var account: AccountStore
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var api: ApiService
        private set
    lateinit var repository: AppRepository
        private set
    lateinit var sync: SyncEngine
        private set
    lateinit var scheduler: SyncScheduler
        private set
    lateinit var accounts: AccountController
        private set
    lateinit var genericFoods: GenericFoodsStore
        private set
    lateinit var openFoodFacts: OpenFoodFactsClient
        private set
    lateinit var recentFoods: RecentFoods
        private set
    lateinit var aiSettings: AiSettingsStore
        private set
    lateinit var ai: AiClient
        private set

    fun init(context: Context) {
        val baseUrl = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        val db = LocalDatabase.open(context)
        account = AccountStore(context)
        preferences = AppPreferences(context)
        api = ApiClient.create(baseUrl = baseUrl, tokenProvider = account::token)
        scheduler = WorkManagerSyncScheduler(context)
        sync = SyncEngine(db, account, api)
        repository = AppRepository(db, account, onLocalChange = { scheduler.requestSync(delaySeconds = 2) })
        genericFoods = GenericFoodsStore.fromAssets(context)
        openFoodFacts = OpenFoodFactsClient.create()
        recentFoods = RecentFoods(context)
        aiSettings = AiSettingsStore(context)
        ai = AiClient.create()
        accounts = AccountController(
            account = account,
            repository = repository,
            sync = sync,
            scheduler = scheduler,
            api = api,
            apiWithToken = { token -> ApiClient.create(baseUrl = baseUrl, tokenProvider = { token }) },
        )
    }
}

class BlackwaterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // Read the bundled foods in the background so the first search is instant.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { AppGraph.genericFoods.all() } }
    }
}
