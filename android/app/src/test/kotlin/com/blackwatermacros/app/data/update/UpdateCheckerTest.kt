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
 * «Not now» hides that version but not the next one.
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
    fun `only the expected certificate counts as our build`() {
        // Robolectric's package has no real signature: neither an empty nor a random hash matches.
        assertThat(signedWith(context, "")).isFalse()
        assertThat(signedWith(context, "ab".repeat(32))).isFalse()
    }
}
