package com.blackwatermacros.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.blackwatermacros.app.core.ExpenditureNoticeDismissal
import com.blackwatermacros.app.core.ExpenditureVerdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small UI preferences that must survive restarts. */
class AppPreferences(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences("settings", Context.MODE_PRIVATE))

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null)!!) }.getOrDefault(ThemeMode.SYSTEM),
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _theme.value = mode
    }

    /** The first-launch steps were finished or skipped. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_DONE, value) }

    private val _expenditureNoticeDismissal = MutableStateFlow(readDismissal())

    /** «Not now» on the Today card's expenditure gap (see core `showsGapNotice`). */
    val expenditureNoticeDismissal: StateFlow<ExpenditureNoticeDismissal?> = _expenditureNoticeDismissal.asStateFlow()

    fun dismissExpenditureNotice(dismissal: ExpenditureNoticeDismissal) {
        prefs.edit {
            putString(KEY_NOTICE_VERDICT, dismissal.verdict.name)
            putString(KEY_NOTICE_DATE, dismissal.date)
        }
        _expenditureNoticeDismissal.value = dismissal
    }

    private fun readDismissal(): ExpenditureNoticeDismissal? {
        val verdict = runCatching { ExpenditureVerdict.valueOf(prefs.getString(KEY_NOTICE_VERDICT, null)!!) }.getOrNull()
        val date = prefs.getString(KEY_NOTICE_DATE, null)
        return if (verdict != null && date != null) ExpenditureNoticeDismissal(verdict, date) else null
    }

    private val _weighInHintDismissed = MutableStateFlow(prefs.getBoolean(KEY_WEIGH_IN_HINT, false))

    /** «Not now» on the one-time «weigh yourself about 3 times a week» hint. */
    val weighInHintDismissed: StateFlow<Boolean> = _weighInHintDismissed.asStateFlow()

    fun dismissWeighInHint() {
        prefs.edit { putBoolean(KEY_WEIGH_IN_HINT, true) }
        _weighInHintDismissed.value = true
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_ONBOARDING_DONE = "onboardingDone"
        const val KEY_NOTICE_VERDICT = "expenditureNoticeVerdict"
        const val KEY_NOTICE_DATE = "expenditureNoticeDate"
        const val KEY_WEIGH_IN_HINT = "weighInHintDismissed"
    }
}
