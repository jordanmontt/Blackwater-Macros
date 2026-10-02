import { describe, expect, it } from "vitest";
import { formatDecimal, normalizeDecimal } from "../../src/lib/core/numbers";

const NNBSP = " ";

describe("formatDecimal", () => {
  it("writes decimals with a comma and drops trailing zeros", () => {
    expect(formatDecimal(1.6, 1)).toBe("1,6");
    expect(formatDecimal(1.25, 1)).toBe("1,3");
    expect(formatDecimal(8.5, 6)).toBe("8,5");
    expect(formatDecimal(2.0, 1)).toBe("2");
    expect(formatDecimal(0.05, 1)).toBe("0,1");
    expect(formatDecimal(0.05)).toBe("0");
    expect(formatDecimal(1999.96, 1)).toBe("2000");
  });

  it("never prints minus zero", () => {
    expect(formatDecimal(-0.04, 1)).toBe("0");
    expect(formatDecimal(-0.36, 1)).toBe("-0,4");
    expect(formatDecimal(-12345.6)).toBe(`-12${NNBSP}346`);
  });

  it("groups thousands with a narrow space from five digits up", () => {
    expect(formatDecimal(2000)).toBe("2000");
    expect(formatDecimal(12345.5, 1)).toBe(`12${NNBSP}345,5`);
    expect(formatDecimal(1234567)).toBe(`1${NNBSP}234${NNBSP}567`);
  });

  it("does not group when asked not to (inputs)", () => {
    expect(formatDecimal(12345.5, 6, false)).toBe("12345,5");
  });
});

describe("normalizeDecimal", () => {
  it("turns every typed dot into a comma", () => {
    expect(normalizeDecimal("1.6")).toBe("1,6");
    expect(normalizeDecimal("1,6")).toBe("1,6");
    expect(normalizeDecimal("")).toBe("");
  });
});
