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
    /** The models each cloud key can use, cached for the dropdown in Ajustes → IA. */
    lateinit var modelLists: ModelListStore
        private set
    lateinit var mealEstimator: MealEstimator
        private set
    lateinit var localModels: LocalModelManager
        private set
    /** The phone models on offer, from the Blackwater site (cached; refreshed in Ajustes → IA). */
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
     * phone-model catalog is not: our server is asked only when the user opens
     * Ajustes → IA, so just opening the app contacts no Blackwater server without an account.
     */
    fun refreshOnOpen() {
        backgroundScope.launch { runCatching { modelLists.refresh(aiSettings.current.config) } }
    }

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
        modelLists = ModelListStore(context.getSharedPreferences(ModelListStore.PREFS_NAME, Context.MODE_PRIVATE), fetch = ai::listModels)
        localCatalog = LocalModelCatalog(context, baseUrl + "models/local-models.json", BuildConfig.VERSION_CODE)
            .also { it.loadCached() }
        localModels = LocalModelManager(context)
        modelRunGuard = ModelRunGuard(context)
        localEngine = LocalEngine(context, modelRunGuard)
        mealEstimator = MealEstimator(ai, aiSettings, localEngine)
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
        // Meal photos are never kept: remove any camera file left by a crash.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { java.io.File(cacheDir, PHOTO_CACHE_DIR).deleteRecursively() } }
    }
}

/** Temporary folder for a camera photo (see `res/xml/photo_paths.xml`). */
const val PHOTO_CACHE_DIR = "ai-photos"
