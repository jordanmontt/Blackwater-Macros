package com.blackwatermacros.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Metodología's index («En esta página»): one entry per section, in page order, in the same
 * four groups and with the same ids as the web's `sectionGroups()` (the ids are the web anchors
 * other screens open, so a link means the same on both).
 */
class MethodologyIndexTest {

    private val uiDir = File("src/main/kotlin/com/blackwatermacros/app/ui")
    private val webPage = File("../../src/app/metodologia/page.tsx").readText()

    /** The index as ids, one list per group. */
    private val androidGroups = SECTION_GROUPS.map { group -> group.sections.map { it.id } }

    @Test
    fun `the index has the web's groups and ids, in the same order`() {
        val block = webPage.substringAfter("const sectionGroups = ").substringBefore("\n];")
        val webGroups = block.split("title: t.metodologia.indexGroups.").drop(1).map { group ->
            Regex("""id: "([^"]+)"""").findAll(group).map { it.groupValues[1] }.toList()
        }
        assertThat(androidGroups).isEqualTo(webGroups)
    }

    @Test
    fun `the index lists every section of the page, in page order`() {
        // `const val FASTED_SECTION = "fasted"` and the like, used by `at(...)` on the screen.
        val constants = uiDir.listFiles()!!.filter { it.isFile }.flatMap { file ->
            Regex("""const val (\w+_SECTION) = "([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] to it.groupValues[2] }
        }.toMap()
        val screen = File(uiDir, "MethodologyScreen.kt").readText()
        val pageOrder = Regex("""at\((?:"([^"]+)"|(\w+_SECTION))\)""").findAll(screen).map { match ->
            match.groupValues[1].ifEmpty { constants.getValue(match.groupValues[2]) }
        }.toList()
        assertThat(androidGroups.flatten()).containsExactlyElementsIn(pageOrder).inOrder()
    }

    @Test
    fun `every section other screens open is in the index`() {
        val ids = androidGroups.flatten()
        assertThat(ids).containsAtLeast(FASTED_SECTION, CALORIES_SECTION, PAST_DAYS_SECTION, ADAPTIVE_SECTION, CHECK_SECTION, FAT_SECTION, FOOD_QUESTIONS_SECTION)
        assertThat(ids).containsNoDuplicates()
    }
}
