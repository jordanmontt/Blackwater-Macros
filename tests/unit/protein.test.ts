import { describe, expect, it } from "vitest";
import { calculateProteinRecommendation } from "../../src/lib/protein";

describe("calculateProteinRecommendation", () => {
  const WEIGHT = 80;

  it("maintain: 1.2–1.6 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, "maintain");
    expect(rec.bwRange).toEqual({ min: 96, max: 128 });
    expect(rec.bwPerKg).toEqual({ min: 1.2, max: 1.6 });
  });

  it("surplus: 1.6–2.0 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, "surplus");
    expect(rec.bwRange).toEqual({ min: 128, max: 160 });
    expect(rec.bwPerKg).toEqual({ min: 1.6, max: 2.0 });
  });

  it("cut: 1.6–2.2 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, "cut");
    expect(rec.bwRange).toEqual({ min: 128, max: 176 });
    expect(rec.bwPerKg).toEqual({ min: 1.6, max: 2.2 });
  });

  it("rounds to nearest integer", () => {
    const rec = calculateProteinRecommendation(75, "maintain");
    expect(rec.bwRange).toEqual({ min: 90, max: 120 });
  });

  it("returns metadata correctly", () => {
    const rec = calculateProteinRecommendation(82.5, "surplus");
    expect(rec.goal).toBe("surplus");
    expect(rec.bodyWeightKg).toBe(82.5);
  });

  it("target is the midpoint of the range", () => {
    expect(calculateProteinRecommendation(80, "maintain").target).toBe(112);
    expect(calculateProteinRecommendation(80, "surplus").target).toBe(144);
    expect(calculateProteinRecommendation(80, "cut").target).toBe(152);
  });
});
