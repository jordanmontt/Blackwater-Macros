package com.blackwatermacros.app.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «A new version is available» (website APK only): GitHub is asked at most once
 * a day, never by an F-Droid build (another key) or with the switch off, and
 * «Not now» hides that version but not the next one. «Check now» (Ajustes) asks
 * right away, also with the switch off, and says what it found.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UpdateCheckerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("updates-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private var clock = 1_000_000_000L
    private var calls = 0
    private var latest: String? = "1.1.0"

    private fun checker(ownBuild: Boolean = true, current: String = "1.0.1") =
        UpdateChecker(prefs, current, ownBuild, fetchLatestVersion = { calls++; latest }, now = { clock })

    @Test
    fun `versions compare by number, and malformed ones are never newer`() {
        assertThat(isNewerVersion("1.0.10", "1.0.9")).isTrue()
        assertThat(isNewerVersion("v2.0.0", "1.9.9")).isTrue()
        assertThat(isNewerVersion("1.0.1", "1.0.1")).isFalse()
        assertThat(isNewerVersion("1.0.0", "1.0.1")).isFalse()
        assertThat(isNewerVersion("1.1", "1.0.0")).isFalse()
        assertThat(isNewerVersion("nightly", "1.0.0")).isFalse()
    }

    @Test
    fun `the release's tag is read as a version`() {
        assertThat(parseLatestRelease("""{"tag_name":"v1.2.3","name":"x"}""")).isEqualTo("1.2.3")
        assertThat(parseLatestRelease("""{"tag_name":"test-build"}""")).isNull()
        assertThat(parseLatestRelease("not json")).isNull()
    }

    @Test
    fun `a newer release is offered, and GitHub is asked again only after a day`() = runBlocking {
        val updates = checker()
        updates.checkIfDue()
        assertThat(updates.available.value).isEqualTo("1.1.0")
        updates.checkIfDue()
        assertThat(calls).isEqualTo(1)
        clock += UpdateChecker.MIN_INTERVAL_MS
        updates.checkIfDue()
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `the same or an older release offers nothing`() = runBlocking {
        latest = "1.0.1"
        val updates = checker()
        updates.checkIfDue()
        assertThat(updates.available.value).isNull()
    }

    @Test
    fun `an F-Droid build or the switch off never asks GitHub`() = runBlocking {
        checker(ownBuild = false).checkIfDue()
        val updates = checker()
        updates.setEnabled(false)
        updates.checkIfDue()
        assertThat(calls).isEqualTo(0)
        assertThat(updates.available.value).isNull()
    }

    @Test
    fun `not now hides this version only, also after a restart`() = runBlocking {
        val updates = checker()
        updates.checkIfDue()
        updates.dismiss()
        assertThat(updates.available.value).isNull()
        assertThat(checker().available.value).isNull()
        latest = "1.2.0"
        clock += UpdateChecker.MIN_INTERVAL_MS
        updates.checkIfDue()
        assertThat(updates.available.value).isEqualTo("1.2.0")
    }

    @Test
    fun `after updating, the release just installed is no longer offered`() = runBlocking {
        checker().checkIfDue()
        assertThat(checker(current = "1.1.0").available.value).isNull()
    }

    @Test
    fun `a failed check stays silent`() = runBlocking {
        val updates = UpdateChecker(prefs, "1.0.1", true, fetchLatestVersion = { error("offline") }, now = { clock })
        updates.checkIfDue()
        assertThat(updates.available.value).isNull()
    }

    @Test
    fun `check now asks right away, even with the daily check off`() = runBlocking {
        val updates = checker()
        updates.setEnabled(false)
        updates.checkNow()
        assertThat(calls).isEqualTo(1)
        assertThat(updates.newer.value).isEqualTo("1.1.0")
        assertThat(updates.check.value).isEqualTo(CheckState.IDLE)
        assertThat(updates.lastChecked.value).isEqualTo(clock)
        // The switch stays off, so the Today notice stays quiet.
        assertThat(updates.available.value).isNull()
        updates.checkNow()
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `check now says when this is the latest version`() = runBlocking {
        latest = "1.0.1"
        val updates = checker()
        updates.checkNow()
        assertThat(updates.check.value).isEqualTo(CheckState.UP_TO_DATE)
        assertThat(updates.newer.value).isNull()
    }

    @Test
    fun `check now says when it couldn't check, keeping what it knew`() = runBlocking {
        checker().checkNow()
        val offline = UpdateChecker(prefs, "1.0.1", true, fetchLatestVersion = { error("offline") }, now = { clock })
        offline.checkNow()
        assertThat(offline.check.value).isEqualTo(CheckState.FAILED)
        assertThat(offline.newer.value).isEqualTo("1.1.0")
        latest = null
        val badAnswer = checker()
        badAnswer.checkNow()
        assertThat(badAnswer.check.value).isEqualTo(CheckState.FAILED)
    }

    @Test
    fun `check now brings back a version hidden with not now, and shows the newest`() = runBlocking {
        val updates = checker()
        updates.checkIfDue()
        updates.dismiss()
        assertThat(updates.newer.value).isEqualTo("1.1.0")
        latest = "1.2.0"
        updates.checkNow()
        assertThat(updates.newer.value).isEqualTo("1.2.0")
        assertThat(updates.available.value).isEqualTo("1.2.0")
    }

    @Test
    fun `an F-Droid build never checks, not even when asked`() = runBlocking {
        val updates = checker(ownBuild = false)
        updates.checkNow()
        assertThat(calls).isEqualTo(0)
        assertThat(updates.newer.value).isNull()
    }

    @Test
    fun `only the expected certificate counts as our build`() {
        // Robolectric's package has no real signature: neither an empty nor a random hash matches.
        assertThat(signedWith(context, "")).isFalse()
        assertThat(signedWith(context, "ab".repeat(32))).isFalse()
    }
}
