import { describe, expect, it } from "vitest";
import * as schema from "@/server/db/schema";
import {
  expectedSchema,
  schemaProblems,
  type DatabaseColumn,
  type DatabaseEnumValue,
} from "@/server/db/schema-check";

// Before every build the database is compared with src/server/db/schema.ts
// (scripts/check-db-schema.ts). On 24 Nov 2026 production lacked weights.fasted and
// weights.time_zone: /api/weights answered 500 and the phone stopped downloading.

const expected = expectedSchema(schema);

/** A database built exactly from the code. */
function matchingDatabase(): { columns: DatabaseColumn[]; enumValues: DatabaseEnumValue[] } {
  const columns = [...expected.tables].flatMap(([table, names]) =>
    names.map((column) => ({ table, column, required: false })),
  );
  const enumValues = [...expected.enums].flatMap(([enumName, values]) =>
    values.map((value) => ({ enumName, value })),
  );
  return { columns, enumValues };
}

describe("database check before a build", () => {
  it("reads every table, column and enum of the schema", () => {
    expect([...expected.tables.keys()].sort()).toEqual(["meal_templates", "meals", "sessions", "users", "weights"]);
    expect(expected.tables.get("weights")).toEqual(expect.arrayContaining(["fasted", "time_zone", "weight_kg"]));
    expect(expected.enums.get("calorie_goal")).toEqual(["cut", "maintain", "surplus"]);
  });

  it("a database that matches the code passes", () => {
    const { columns, enumValues } = matchingDatabase();
    expect(schemaProblems(expected, columns, enumValues)).toEqual([]);
  });

  it("names the columns the code reads but the database lacks (the 24 Nov outage)", () => {
    const { columns, enumValues } = matchingDatabase();
    const before = columns.filter((c) => !(c.table === "weights" && (c.column === "fasted" || c.column === "time_zone")));
    expect(schemaProblems(expected, before, enumValues)).toEqual([
      'column "weights.fasted" is missing',
      'column "weights.time_zone" is missing',
    ]);
  });

  it("a missing table is one problem, not one per column", () => {
    const { columns, enumValues } = matchingDatabase();
    const problems = schemaProblems(expected, columns.filter((c) => c.table !== "weights"), enumValues);
    expect(problems).toEqual(['table "weights" is missing']);
  });

  it("a column the database requires but the code no longer writes fails; one that may stay empty doesn't", () => {
    const { columns, enumValues } = matchingDatabase();
    const optional = [...columns, { table: "meals", column: "old_note", required: false }];
    expect(schemaProblems(expected, optional, enumValues)).toEqual([]);
    const required = [...columns, { table: "meals", column: "old_kind", required: true }];
    expect(schemaProblems(expected, required, enumValues)).toEqual([
      'column "meals.old_kind" is required by the database but unknown to the code',
    ]);
  });

  it("a missing enum, or a value the code may write, fails; tables only the database has are ignored", () => {
    const { columns, enumValues } = matchingDatabase();
    const extraTable = [...columns, { table: "drizzle_log", column: "id", required: true }];
    const noSurplus = enumValues.filter((e) => !(e.enumName === "calorie_goal" && e.value === "surplus"));
    expect(schemaProblems(expected, extraTable, noSurplus)).toEqual(['enum "calorie_goal" lacks the value "surplus"']);
    const noGender = enumValues.filter((e) => e.enumName !== "gender");
    expect(schemaProblems(expected, columns, noGender)).toEqual(['enum "gender" is missing']);
  });
});
