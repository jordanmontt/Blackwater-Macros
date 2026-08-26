import { describe, expect, it } from "vitest";
import { calculateProteinRecommendation } from "../../src/lib/protein";

describe("calculateProteinRecommendation", () => {
  const WEIGHT = 80;
  const BODY_FAT = 15;

  it("maintain: 1.2–1.6 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, null, "maintain");
    expect(rec.bwRange).toEqual({ min: 96, max: 128 });
    expect(rec.bwPerKg).toEqual({ min: 1.2, max: 1.6 });
    expect(rec.ffmRange).toBeNull();
    expect(rec.ffmPerKg).toBeNull();
  });

  it("build: 1.6–2.0 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, null, "build");
    expect(rec.bwRange).toEqual({ min: 128, max: 160 });
    expect(rec.bwPerKg).toEqual({ min: 1.6, max: 2.0 });
    expect(rec.ffmRange).toBeNull();
    expect(rec.ffmPerKg).toBeNull();
  });

  it("cut: 1.6–2.2 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, null, "cut");
    expect(rec.bwRange).toEqual({ min: 128, max: 176 });
    expect(rec.bwPerKg).toEqual({ min: 1.6, max: 2.2 });
    expect(rec.ffmRange).toBeNull();
  });

  it("cut with body fat: includes FFM range (2.3–3.1 g/kg FFM)", () => {
    const rec = calculateProteinRecommendation(WEIGHT, BODY_FAT, "cut");
    // FFM = 80 * (1 - 15/100) = 68 kg
    expect(rec.ffmRange).toEqual({ min: 156, max: 211 });
    expect(rec.ffmPerKg).toEqual({ min: 2.3, max: 3.1 });
    expect(rec.bodyFatPct).toBe(BODY_FAT);
  });

  it("maintain with body fat: no FFM range (not relevant)", () => {
    const rec = calculateProteinRecommendation(WEIGHT, BODY_FAT, "maintain");
    expect(rec.ffmRange).toBeNull();
    expect(rec.ffmPerKg).toBeNull();
  });

  it("build with body fat: no FFM range (not relevant)", () => {
    const rec = calculateProteinRecommendation(WEIGHT, BODY_FAT, "build");
    expect(rec.ffmRange).toBeNull();
  });

  it("rounds to nearest integer", () => {
    // 75 kg × 1.2 = 90, 75 × 1.6 = 120
    const rec = calculateProteinRecommendation(75, null, "maintain");
    expect(rec.bwRange).toEqual({ min: 90, max: 120 });
  });

  it("handles very lean athlete (5% BF)", () => {
    const rec = calculateProteinRecommendation(80, 5, "cut");
    // FFM = 80 * 0.95 = 76 kg
    expect(rec.ffmRange!.min).toBe(175); // 76 × 2.3 = 174.8 → 175
    expect(rec.ffmRange!.max).toBe(236); // 76 × 3.1 = 235.6 → 236
  });

  it("handles high body fat (40%)", () => {
    const rec = calculateProteinRecommendation(100, 40, "cut");
    // FFM = 100 * 0.6 = 60 kg
    expect(rec.ffmRange!.min).toBe(138); // 60 × 2.3 = 138
    expect(rec.ffmRange!.max).toBe(186); // 60 × 3.1 = 186
  });

  it("returns metadata correctly", () => {
    const rec = calculateProteinRecommendation(82.5, 18.5, "build");
    expect(rec.goal).toBe("build");
    expect(rec.bodyWeightKg).toBe(82.5);
    expect(rec.bodyFatPct).toBe(18.5);
  });

  it("null body fat returns null ffmRange", () => {
    const rec = calculateProteinRecommendation(70, null, "cut");
    expect(rec.ffmRange).toBeNull();
    expect(rec.ffmPerKg).toBeNull();
  });

  it("body fat 0% returns null ffmRange (edge case)", () => {
    const rec = calculateProteinRecommendation(80, 0, "cut");
    expect(rec.ffmRange).toBeNull();
  });

  it("body fat 100% returns null ffmRange (edge case)", () => {
    const rec = calculateProteinRecommendation(80, 100, "cut");
    expect(rec.ffmRange).toBeNull();
  });
});
