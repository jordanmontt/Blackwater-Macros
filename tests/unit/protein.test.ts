import { describe, expect, it } from "vitest";
import { calculateProteinRecommendation } from "../../src/lib/core/protein";

describe("calculateProteinRecommendation", () => {
  const WEIGHT = 80;

  it("maintain: 1.4–2.0 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, "maintain");
    expect(rec.basis).toBe("bodyWeight");
    expect(rec.range).toEqual({ min: 112, max: 160 });
    expect(rec.perKg).toEqual({ min: 1.4, max: 2.0 });
  });

  it("surplus: 1.6–2.2 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, "surplus");
    expect(rec.range).toEqual({ min: 128, max: 176 });
    expect(rec.perKg).toEqual({ min: 1.6, max: 2.2 });
  });

  it("cut without body fat: 1.8–2.7 g/kg BW", () => {
    const rec = calculateProteinRecommendation(WEIGHT, "cut");
    expect(rec.basis).toBe("bodyWeight");
    expect(rec.basisKg).toBe(80);
    expect(rec.range).toEqual({ min: 144, max: 216 });
    expect(rec.perKg).toEqual({ min: 1.8, max: 2.7 });
  });

  it("cut with body fat: 2.3–3.1 g/kg of lean mass", () => {
    // 80 kg at 20 % → 64 kg lean mass → 147.2–198.4 g
    const rec = calculateProteinRecommendation(WEIGHT, "cut", 20);
    expect(rec.basis).toBe("leanMass");
    expect(rec.basisKg).toBe(64);
    expect(rec.range).toEqual({ min: 147, max: 198 });
    expect(rec.perKg).toEqual({ min: 2.3, max: 3.1 });
    expect(rec.target).toBe(173);
  });

  it("body fat only changes the cut recommendation", () => {
    expect(calculateProteinRecommendation(WEIGHT, "maintain", 20)).toEqual(
      calculateProteinRecommendation(WEIGHT, "maintain"),
    );
    expect(calculateProteinRecommendation(WEIGHT, "surplus", 20).basis).toBe("bodyWeight");
  });

  it("ignores unusable body fat values", () => {
    expect(calculateProteinRecommendation(WEIGHT, "cut", 0).basis).toBe("bodyWeight");
    expect(calculateProteinRecommendation(WEIGHT, "cut", null).basis).toBe("bodyWeight");
    expect(calculateProteinRecommendation(WEIGHT, "cut", 100).basis).toBe("bodyWeight");
  });

  it("rounds to nearest integer", () => {
    const rec = calculateProteinRecommendation(75, "maintain");
    expect(rec.range).toEqual({ min: 105, max: 150 });
  });

  it("returns metadata correctly", () => {
    const rec = calculateProteinRecommendation(82.5, "surplus");
    expect(rec.goal).toBe("surplus");
    expect(rec.basisKg).toBe(82.5);
  });

  it("target is the midpoint of the range", () => {
    expect(calculateProteinRecommendation(80, "maintain").target).toBe(136);
    expect(calculateProteinRecommendation(80, "surplus").target).toBe(152);
    expect(calculateProteinRecommendation(80, "cut").target).toBe(180);
  });
});
