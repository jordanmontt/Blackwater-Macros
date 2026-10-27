package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/backup.test.ts`: both platforms write the same file. */
class BackupTest {

    private val profile = CalorieProfile(Gender.MALE, 1990, 178.5, 3, 60, 30, Goal.SURPLUS)

    private fun content(
        title: String,
        mode: EntryMode,
        ingredients: List<IngredientInput>,
        typed: List<Double?> = listOf(null, null, null, null),
        resolved: List<Double>,
        notes: String? = null,
    ) = BackupContent(
        title, notes, mode, ingredients,
        typed[0], typed[1], typed[2], typed[3],
        resolved[0], resolved[1], resolved[2], resolved[3],
    )

    private val data = BackupData(
        profile = profile,
        profileHistory = listOf(ProfilePeriod("2026-10-14", profile.copy(gymDaysPerWeek = 0, calorieGoal = null))),
        weights = listOf(
            BackupWeight("2026-10-20T07:30:00.000Z", 80.25, 18.0, null),
            BackupWeight("2026-10-26T07:15:00.000Z", 80.0, null, "After \"cheat\" day"),
        ),
        meals = listOf(
            BackupMeal(
                "2026-10-26",
                content(
                    "Breakfast",
                    EntryMode.PER_INGREDIENT,
                    listOf(IngredientInput("Oats", "80 g", 300.0, 10.5, 54.0, 6.0), IngredientInput("Coffee")),
                    resolved = listOf(300.0, 10.5, 54.0, 6.0),
                ),
            ),
            BackupMeal(
                "2026-10-26",
                content(
                    "Restaurant",
                    EntryMode.TOTAL_ONLY,
                    emptyList(),
                    typed = listOf(1200.0, 45.0, null, null),
                    resolved = listOf(1200.0, 45.0, 0.0, 0.0),
                    notes = "Pizza\nand salad",
                ),
            ),
        ),
        templates = listOf(
            BackupTemplate(
                "Shake",
                content(
                    "Protein shake",
                    EntryMode.PER_INGREDIENT,
                    listOf(IngredientInput("Whey", "30 g", 120.0, 24.0)),
                    resolved = listOf(120.0, 24.0, 0.0, 0.0),
                ),
            ),
        ),
    )

    /** Kept identical in backup.test.ts. */
    private val expected = """
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

    @Test
    fun writesEverythingAsIndentedJsonWithPlainEnglishNames() {
        assertThat(buildBackup(data, "2026-10-26T21:00:00.000Z")).isEqualTo(expected)
    }

    @Test
    fun anEmptyAccountIsStillAValidFile() {
        val empty = BackupData(CalorieProfile(null, null, null, null, null, null, null), emptyList(), emptyList(), emptyList(), emptyList())
        val text = buildBackup(empty, "2026-10-26T21:00:00.000Z")
        assertThat(text).contains("\"pastProfiles\": [],")
        assertThat(text).contains("\"templates\": []\n}")
        assertThat(text).contains("\"goal\": null")
    }

    @Test
    fun namesTheFileAfterTheDay() {
        assertThat(backupFileName("2026-10-26")).isEqualTo("blackwater-macros-2026-10-26.json")
    }
}
