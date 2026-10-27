package com.blackwatermacros.app.data

import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The import reads the export file back (web `core/backup.ts`, Kotlin `core/Backup.kt`):
 * the same file from either platform, all or nothing.
 */
class BackupImportTest {

    /** The file `backup.test.ts` / `BackupTest.kt` expect both platforms to write. */
    private val webFile = """
        {
          "app": "Blackwater Macros",
          "format": 1,
          "exportedAt": "2026-10-26T21:00:00.000Z",
          "profile": {
            "sex": "male",
            "birthYear": 1990,
            "heightCm": 178.5,
            "gymDaysPerWeek": 3,
            "gymMinutesPerSession": 60,
            "walkingMinutesPerDay": 30,
            "goal": "bulk"
          },
          "pastProfiles": [
            {
              "until": "2026-10-14",
              "sex": "male",
              "birthYear": 1990,
              "heightCm": 178.5,
              "gymDaysPerWeek": 0,
              "gymMinutesPerSession": 60,
              "walkingMinutesPerDay": 30,
              "goal": null
            }
          ],
          "weighIns": [
            {
              "time": "2026-10-20T07:30:00.000Z",
              "kg": 80.25,
              "bodyFatPct": 18,
              "note": null
            },
            {
              "time": "2026-10-26T07:15:00.000Z",
              "kg": 80,
              "bodyFatPct": null,
              "note": "After \"cheat\" day"
            }
          ],
          "meals": [
            {
              "date": "2026-10-26",
              "title": "Breakfast",
              "notes": null,
              "mode": "foods",
              "foods": [
                {
                  "name": "Oats",
                  "amount": "80 g",
                  "kcal": 300,
                  "protein": 10.5,
                  "carbs": 54,
                  "fat": 6
                },
                {
                  "name": "Coffee"
                }
              ],
              "total": {
                "kcal": 300,
                "protein": 10.5,
                "carbs": 54,
                "fat": 6
              }
            },
            {
              "date": "2026-10-26",
              "title": "Restaurant",
              "notes": "Pizza\nand salad",
              "mode": "total",
              "foods": [],
              "total": {
                "kcal": 1200,
                "protein": 45,
                "carbs": null,
                "fat": null
              }
            }
          ],
          "templates": [
            {
              "name": "Shake",
              "title": "Protein shake",
              "notes": null,
              "mode": "foods",
              "foods": [
                {
                  "name": "Whey",
                  "amount": "30 g",
                  "kcal": 120,
                  "protein": 24
                }
              ],
              "total": {
                "kcal": 120,
                "protein": 24,
                "carbs": 0,
                "fat": 0
              }
            }
          ]
        }
    """.trimIndent() + "\n"

    private fun contents(text: String = webFile) = (BackupImport.parse(text) as BackupImport.Result.Ok).contents

    private fun invalid(text: String) = (BackupImport.parse(text) as BackupImport.Result.Invalid).where

    @Test
    fun `reads everything the web writes`() {
        val file = contents()
        assertThat(file.profile.gender).isEqualTo(Gender.MALE)
        assertThat(file.profile.heightCm).isEqualTo(178.5)
        assertThat(file.profile.calorieGoal).isEqualTo(Goal.SURPLUS)
        assertThat(file.profileHistory.single().until).isEqualTo("2026-10-14")
        assertThat(file.profileHistory.single().profile.calorieGoal).isNull()
        assertThat(file.weights.map { it.weightKg }).containsExactly(80.25, 80.0).inOrder()
        assertThat(file.weights[1].note).isEqualTo("After \"cheat\" day")

        val (breakfast, restaurant) = file.meals
        assertThat(breakfast.entryMode).isEqualTo(WireEntryMode.PER_INGREDIENT)
        assertThat(breakfast.ingredients).containsExactly(
            WireIngredient("Oats", "80 g", 300.0, 10.5, 54.0, 6.0),
            WireIngredient("Coffee"),
        ).inOrder()
        // «foods»: the total is recomputed from the foods when saved.
        assertThat(breakfast.totalCalories).isNull()
        assertThat(restaurant.entryMode).isEqualTo(WireEntryMode.TOTAL_ONLY)
        assertThat(restaurant.notes).isEqualTo("Pizza\nand salad")
        assertThat(listOf(restaurant.totalCalories, restaurant.totalProtein, restaurant.totalCarbs, restaurant.totalFat))
            .containsExactly(1200.0, 45.0, null, null).inOrder()
        assertThat(file.templates.single().name).isEqualTo("Shake")
        assertThat(file.firstDay).isEqualTo("2026-10-20")
        assertThat(file.lastDay).isEqualTo("2026-10-26")
    }

    @Test
    fun `anything that is not an export is refused`() {
        for (text in listOf("hello", "[]", "{}", "{\"app\": \"Other\", \"format\": 1}", "{\"app\": \"Blackwater Macros\"}")) {
            assertThat(BackupImport.parse(text)).isEqualTo(BackupImport.Result.NotBackup)
        }
    }

    @Test
    fun `a file from a newer version asks to update the app`() {
        assertThat(BackupImport.parse(webFile.replace("\"format\": 1", "\"format\": 2")))
            .isEqualTo(BackupImport.Result.NewerFormat)
    }

    @Test
    fun `one wrong value refuses the whole file and says where it is`() {
        assertThat(invalid(webFile.replace("\"kcal\": 300,", "\"kcal\": -5,"))).isEqualTo("meals › 1 › foods › 1 › kcal")
        assertThat(invalid(webFile.replace("\"kg\": 80.25", "\"kg\": 900"))).isEqualTo("weighIns › 1 › kg")
        assertThat(invalid(webFile.replace("\"mode\": \"total\"", "\"mode\": \"menu\""))).isEqualTo("meals › 2 › mode")
        assertThat(invalid(webFile.replaceFirst("\"date\": \"2026-10-26\"", "\"date\": \"2026-02-30\""))).isEqualTo("meals › 1 › date")
        assertThat(invalid(webFile.replace("\"birthYear\": 1990,\n    \"heightCm\"", "\"birthYear\": 1990.5,\n    \"heightCm\""))).isEqualTo("profile › birthYear")
        assertThat(invalid(webFile.replace("\"name\": \"Whey\"", "\"name\": \"  \""))).isEqualTo("templates › 1 › foods › 1 › name")
        assertThat(invalid(webFile.replace("\"weighIns\": [", "\"weighInz\": ["))).isEqualTo("weighIns")
    }

    @Test
    fun `text is stored as the server would store it`() {
        val file = contents(webFile.replace("\"title\": \"Breakfast\"", "\"title\": \"  Breakfast \"").replace("\"amount\": \"80 g\"", "\"amount\": \" \""))
        assertThat(file.meals[0].title).isEqualTo("Breakfast")
        assertThat(file.meals[0].ingredients[0].quantity).isNull()
    }
}
