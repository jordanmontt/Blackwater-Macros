package com.blackwatermacros.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.ExpenditureNoticeDismissal
import com.blackwatermacros.app.core.ExpenditureVerdict
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Not now» on the expenditure notices survives closing the app (see core `showsGapNotice`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AppPreferencesTest {

    private fun prefs() = AppPreferences(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("prefs-test", Context.MODE_PRIVATE),
    )

    @Test
    fun `dismissing the expenditure notices is remembered after a restart`() {
        val first = prefs()
        assertThat(first.expenditureNoticeDismissal.value).isNull()
        assertThat(first.weighInHintDismissed.value).isFalse()

        first.dismissExpenditureNotice(ExpenditureNoticeDismissal(ExpenditureVerdict.LOWER, "2026-03-01"))
        first.dismissWeighInHint()

        val restarted = prefs()
        assertThat(restarted.expenditureNoticeDismissal.value)
            .isEqualTo(ExpenditureNoticeDismissal(ExpenditureVerdict.LOWER, "2026-03-01"))
        assertThat(restarted.weighInHintDismissed.value).isTrue()
    }
}
