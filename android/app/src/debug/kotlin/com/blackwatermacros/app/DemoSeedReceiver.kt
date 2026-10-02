package com.blackwatermacros.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.data.CsvBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug builds only (`src/debug`, never in a release): loads the demo data for
 * the store screenshots. `scripts/screenshots.sh` copies `meals.csv` and
 * `weights.csv` (from `scripts/demo-csv.ts`) into `files/seed/` and sends
 * `adb shell am broadcast -n com.blackwatermacros.app/.DemoSeedReceiver`.
 * It also sets the demo person's profile, skips the first steps and stores a
 * placeholder AI key so the AI screens show their normal state (no request is
 * made unless something is sent).
 */
class DemoSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = File(context.filesDir, "seed")
                for (file in listOf("meals.csv", "weights.csv")) {
                    when (val parsed = CsvBackup.parse(File(dir, file).readText())) {
                        is CsvBackup.Parsed.Meals -> AppGraph.repository.importMeals(parsed.meals)
                        is CsvBackup.Parsed.Weights -> AppGraph.repository.importWeights(parsed.weights)
                        CsvBackup.Parsed.Unknown -> error("$file is not a Blackwater CSV")
                    }
                }
                AppGraph.repository.saveProfile(CalorieProfile(Gender.MALE, 1990, 178.0, 3, 60, 30, Goal.CUT))
                AppGraph.preferences.onboardingDone = true
                AppGraph.aiSettings.update {
                    it.copy(provider = AiProvider.GEMINI, apiKeys = it.apiKeys + (AiProvider.GEMINI to "screenshot-demo"))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
