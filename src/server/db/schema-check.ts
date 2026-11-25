import { is } from "drizzle-orm";
import { PgTable, getTableConfig, isPgEnum } from "drizzle-orm/pg-core";

/**
 * Does the database match the code? Run before every build (`scripts/check-db-schema.ts`):
 * Vercel deploys `dev` to production, but a new column only reaches the database with
 * `npm run db:push`. Without it every query on that table fails (on 24 Nov 2026 the
 * weigh-ins answered 500 and the phone's whole download stopped). A build that fails here
 * never goes live: the version already online keeps running.
 */

/** What the code reads and writes: each table with its columns, each enum with its values. */
export interface ExpectedSchema {
  tables: Map<string, string[]>;
  enums: Map<string, string[]>;
}

/** One column as the database describes it (`information_schema.columns`). */
export interface DatabaseColumn {
  table: string;
  column: string;
  /** NOT NULL with no default: an insert that leaves it out fails. */
  required: boolean;
}

/** One value of a database enum (`pg_enum`). */
export interface DatabaseEnumValue {
  enumName: string;
  value: string;
}

/** The tables and enums declared in a Drizzle schema module (`src/server/db/schema.ts`). */
export function expectedSchema(schemaModule: Record<string, unknown>): ExpectedSchema {
  const tables = new Map<string, string[]>();
  const enums = new Map<string, string[]>();
  for (const value of Object.values(schemaModule)) {
    if (is(value, PgTable)) {
      const config = getTableConfig(value);
      tables.set(config.name, config.columns.map((column) => column.name));
    } else if (isPgEnum(value)) {
      enums.set(value.enumName, [...value.enumValues]);
    }
  }
  return { tables, enums };
}

/**
 * Everything the code needs that the database lacks: a table, a column, an enum or one of
 * its values. Also a column the database requires but the code no longer writes (its inserts
 * would fail). Extra tables, and extra columns that may stay empty, are harmless.
 */
export function schemaProblems(
  expected: ExpectedSchema,
  columns: DatabaseColumn[],
  enumValues: DatabaseEnumValue[],
): string[] {
  const problems: string[] = [];

  const columnsByTable = new Map<string, DatabaseColumn[]>();
  for (const column of columns) {
    columnsByTable.set(column.table, [...(columnsByTable.get(column.table) ?? []), column]);
  }
  for (const [table, expectedColumns] of expected.tables) {
    const actual = columnsByTable.get(table);
    if (!actual) {
      problems.push(`table "${table}" is missing`);
      continue;
    }
    const actualNames = new Set(actual.map((column) => column.column));
    for (const column of expectedColumns) {
      if (!actualNames.has(column)) problems.push(`column "${table}.${column}" is missing`);
    }
    for (const column of actual) {
      if (column.required && !expectedColumns.includes(column.column)) {
        problems.push(`column "${table}.${column.column}" is required by the database but unknown to the code`);
      }
    }
  }

  const valuesByEnum = new Map<string, Set<string>>();
  for (const { enumName, value } of enumValues) {
    valuesByEnum.set(enumName, (valuesByEnum.get(enumName) ?? new Set()).add(value));
  }
  for (const [enumName, values] of expected.enums) {
    const actual = valuesByEnum.get(enumName);
    if (!actual) {
      problems.push(`enum "${enumName}" is missing`);
      continue;
    }
    for (const value of values) {
      if (!actual.has(value)) problems.push(`enum "${enumName}" lacks the value "${value}"`);
    }
  }

  return problems;
}
