package com.blackwatermacros.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.data.BackupImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug builds only (`src/debug`, never in a release): loads the demo data for
 * the store screenshots. `scripts/screenshots.sh` copies `backup.json` (the export
 * file, from `scripts/demo-backup.ts`: meals, weigh-ins and the demo person's profile)
 * into `files/seed/` and sends
 * `adb shell am broadcast -n com.blackwatermacros.app.debug/com.blackwatermacros.app.DemoSeedReceiver`.
 * It imports it as the app's import does, skips the first steps and stores a
 * placeholder AI key so the AI screens show their normal state (no request is
 * made unless something is sent).
 */
class DemoSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = File(File(context.filesDir, "seed"), "backup.json").readText()
                when (val parsed = BackupImport.parse(text)) {
                    is BackupImport.Result.Ok -> AppGraph.repository.replaceAll(parsed.contents)
                    else -> error("backup.json can't be imported: $parsed")
                }
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
