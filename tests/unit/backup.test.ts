import { describe, expect, it } from "vitest";
import { backupFileName, buildBackup, type BackupData } from "../../src/lib/core/backup";

/** One of everything: a meal by foods, one typed as a total, a template, a past profile. */
const DATA: BackupData = {
  profile: {
    gender: "male",
    birthYear: 1990,
    heightCm: 178.5,
    gymDaysPerWeek: 3,
    gymSessionMinutes: 60,
    walkingMinutesPerDay: 30,
    calorieGoal: "surplus",
  },
  profileHistory: [
    {
      until: "2026-10-14",
      gender: "male",
      birthYear: 1990,
      heightCm: 178.5,
      gymDaysPerWeek: 0,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 30,
      calorieGoal: null,
    },
  ],
  weights: [
    { measuredAt: "2026-10-20T07:30:00.000Z", weightKg: 80.25, bodyFatPct: 18, note: null },
    { measuredAt: "2026-10-26T07:15:00.000Z", weightKg: 80, bodyFatPct: null, note: "After \"cheat\" day" },
  ],
  meals: [
    {
      logDate: "2026-10-26",
      title: "Breakfast",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: [
        { name: "Oats", quantity: "80 g", calories: 300, protein: 10.5, carbs: 54, fat: 6 },
        { name: "Coffee" },
      ],
      totalCalories: null,
      totalProtein: null,
      totalCarbs: null,
      totalFat: null,
      resolvedCalories: 300,
      resolvedProtein: 10.5,
      resolvedCarbs: 54,
      resolvedFat: 6,
    },
    {
      logDate: "2026-10-26",
      title: "Restaurant",
      notes: "Pizza\nand salad",
      entryMode: "total_only",
      ingredients: [],
      totalCalories: 1200,
      totalProtein: 45,
      totalCarbs: null,
      totalFat: null,
      resolvedCalories: 1200,
      resolvedProtein: 45,
      resolvedCarbs: 0,
      resolvedFat: 0,
    },
  ],
  templates: [
    {
      name: "Shake",
      title: "Protein shake",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: [{ name: "Whey", quantity: "30 g", calories: 120, protein: 24 }],
      totalCalories: null,
      totalProtein: null,
      totalCarbs: null,
      totalFat: null,
      resolvedCalories: 120,
      resolvedProtein: 24,
      resolvedCarbs: 0,
      resolvedFat: 0,
    },
  ],
};

/** Kept identical in BackupTest.kt: both platforms write this exact text. */
const EXPECTED = `{
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
      "note": "After \\"cheat\\" day"
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
      "notes": "Pizza\\nand salad",
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
`;

describe("buildBackup", () => {
  it("writes everything as indented JSON with plain English names", () => {
    expect(buildBackup(DATA, "2026-10-26T21:00:00.000Z")).toBe(EXPECTED);
  });

  it("an empty account is still a valid file", () => {
    const empty: BackupData = {
      profile: {
        gender: null,
        birthYear: null,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: null,
      },
      profileHistory: [],
      weights: [],
      meals: [],
      templates: [],
    };
    const file = JSON.parse(buildBackup(empty, "2026-10-26T21:00:00.000Z"));
    expect(file).toMatchObject({ app: "Blackwater Macros", format: 1, pastProfiles: [], weighIns: [], meals: [], templates: [] });
    expect(file.profile.goal).toBeNull();
  });

  it("names the file after the day", () => {
    expect(backupFileName("2026-10-26")).toBe("blackwater-macros-2026-10-26.json");
  });
});
