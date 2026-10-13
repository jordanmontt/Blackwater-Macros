package com.blackwatermacros.app

import android.app.Application
import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.blackwatermacros.app.data.AccountController
import com.blackwatermacros.app.data.AccountProfiles
import com.blackwatermacros.app.data.ProfileScope
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppPreferences
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.ApiService
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.ai.AiClient
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.ai.MealEstimator
import com.blackwatermacros.app.data.ai.ModelListStore
import com.blackwatermacros.app.data.ai.local.LocalEngine
import com.blackwatermacros.app.data.ai.local.LocalModelCatalog
import com.blackwatermacros.app.data.ai.local.ModelRunGuard
import com.blackwatermacros.app.data.ai.local.LocalModelManager
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
 * Everything here gets the Application context (never an Activity), so holding
 * it for the life of the process is not a leak.
 */
@SuppressLint("StaticFieldLeak")
object AppGraph {
    /** Every account on the phone; the getters below are the active one's. */
    lateinit var profiles: AccountProfiles
        private set
    val account: AccountStore get() = profiles.active.account
    val api: ApiService get() = profiles.active.api
    val repository: AppRepository get() = profiles.active.repository
    val sync: SyncEngine get() = profiles.active.sync
    val accounts: AccountController get() = profiles.active.accounts
    lateinit var preferences: AppPreferences
        private set
    lateinit var scheduler: SyncScheduler
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
    /** The models each cloud key can use, cached for the dropdown in Ajustes → IA. */
    lateinit var modelLists: ModelListStore
        private set
    lateinit var mealEstimator: MealEstimator
        private set
    lateinit var localModels: LocalModelManager
        private set
    /** The phone models on offer, from the public repository (cached; refreshed in Ajustes → IA). */
    lateinit var localCatalog: LocalModelCatalog
        private set
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var localEngine: LocalEngine
    /** Notices when Android killed the app for memory while the phone's model ran. */
    lateinit var modelRunGuard: ModelRunGuard
        private set

    /**
     * Each time the app opens, the cloud model list is refreshed in the background (at
     * most once a day, only with a key: it goes to the provider the user chose). The
     * phone-model catalog is not: it is read from the public repository only when the user
     * opens Ajustes → IA. Without an account the app never contacts the Blackwater server.
     */
    fun refreshOnOpen() {
        backgroundScope.launch { runCatching { modelLists.refresh(aiSettings.current.config) } }
    }

    fun init(context: Context) {
        val baseUrl = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        preferences = AppPreferences(context)
        scheduler = WorkManagerSyncScheduler(context)
        val apiWithToken = { token: String -> ApiClient.create(baseUrl = baseUrl, tokenProvider = { token }) }
        profiles = AccountProfiles(
            prefs = context.getSharedPreferences(AccountProfiles.PREFS_NAME, Context.MODE_PRIVATE),
            createScope = { id -> profileScope(context, id, baseUrl, apiWithToken) },
            deleteFiles = { id ->
                context.deleteDatabase(AccountProfiles.databaseName(id))
                context.deleteSharedPreferences(AccountProfiles.accountPrefsName(id))
            },
            loginApi = ApiClient.create(baseUrl = baseUrl, tokenProvider = { null }),
            apiWithToken = apiWithToken,
            requestSync = { scheduler.requestSync() },
        )
        genericFoods = GenericFoodsStore.fromAssets(context)
        openFoodFacts = OpenFoodFactsClient.create()
        recentFoods = RecentFoods(context)
        aiSettings = AiSettingsStore(context)
        ai = AiClient.create()
        modelLists = ModelListStore(context.getSharedPreferences(ModelListStore.PREFS_NAME, Context.MODE_PRIVATE), fetch = ai::listModels)
        localCatalog = LocalModelCatalog(context, BuildConfig.MODEL_CATALOG_URL, BuildConfig.VERSION_CODE)
            .also { it.loadCached() }
        localModels = LocalModelManager(context)
        modelRunGuard = ModelRunGuard(context)
        localEngine = LocalEngine(context, modelRunGuard)
        mealEstimator = MealEstimator(ai, aiSettings, localEngine)
    }

    private fun profileScope(context: Context, id: String, baseUrl: String, apiWithToken: (String) -> ApiService): ProfileScope {
        val db = LocalDatabase.open(context, AccountProfiles.databaseName(id))
        val account = AccountStore(context.getSharedPreferences(AccountProfiles.accountPrefsName(id), Context.MODE_PRIVATE))
        val api = ApiClient.create(baseUrl = baseUrl, tokenProvider = account::token)
        val sync = SyncEngine(db, account, api)
        val repository = AppRepository(db, account, onLocalChange = { scheduler.requestSync(delaySeconds = 2) })
        val accounts = AccountController(account, repository, sync, scheduler, api, apiWithToken)
        return ProfileScope(id, db, account, api, sync, repository, accounts)
    }
}

class BlackwaterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // Read the bundled foods in the background so the first search is instant.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { AppGraph.genericFoods.all() } }
        // Meal photos are never kept: remove any camera file left by a crash.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { java.io.File(cacheDir, PHOTO_CACHE_DIR).deleteRecursively() } }
    }
}

/** Temporary folder for a camera photo (see `res/xml/photo_paths.xml`). */
const val PHOTO_CACHE_DIR = "ai-photos"
