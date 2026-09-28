import { describe, expect, it } from "vitest";
import { calculateProteinRecommendation, normalBodyFatMax, proteinReferenceWeight } from "../../src/lib/core/protein";

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

  describe("above BMI 25 the ranges use the weight at BMI 25", () => {
    const tall = { heightCm: 180, gender: "male" as const, age: 30 };

    it("110 kg at 1.80 m: reference 81 kg for every goal without body fat", () => {
      const maintain = calculateProteinRecommendation(110, "maintain", null, tall);
      expect(maintain.basis).toBe("referenceWeight");
      expect(maintain.basisKg).toBe(81);
      expect(maintain.range).toEqual({ min: 113, max: 162 });
      const cut = calculateProteinRecommendation(110, "cut", null, tall);
      expect(cut.range).toEqual({ min: 146, max: 219 });
      expect(cut.perKg).toEqual({ min: 1.8, max: 2.7 });
    });

    it("a normal body fat means the high BMI is muscle: actual weight", () => {
      const lifter = calculateProteinRecommendation(95, "surplus", 12, tall);
      expect(lifter.basis).toBe("bodyWeight");
      expect(lifter.range).toEqual({ min: 152, max: 209 });
      expect(calculateProteinRecommendation(110, "maintain", 30, tall).basis).toBe("referenceWeight");
    });

    it("women under 40: muscle below 33 % body fat", () => {
      const woman = { heightCm: 165, gender: "female" as const, age: 30 };
      expect(calculateProteinRecommendation(80, "maintain", 30, woman).basis).toBe("bodyWeight");
      const higher = calculateProteinRecommendation(80, "maintain", 35, woman);
      expect(higher.basis).toBe("referenceWeight");
      expect(higher.basisKg).toBe(68.1);
    });

    it("the body fat at BMI 25 rises with age (Gallagher et al. 2000)", () => {
      expect([19, 39, 40, 59, 60, 80, null].map((age) => normalBodyFatMax("male", age))).toEqual([20, 20, 22, 22, 25, 25, 20]);
      expect([19, 39, 40, 59, 60, 80, null].map((age) => normalBodyFatMax("female", age))).toEqual([33, 33, 34, 34, 36, 36, 33]);
    });

    it("men: 21 % body fat is fat at 30, muscle from 40", () => {
      const at = (age: number | null) => calculateProteinRecommendation(90, "maintain", 21, { ...tall, age }).basis;
      expect(at(30)).toBe("referenceWeight");
      expect(at(null)).toBe("referenceWeight");
      expect(at(45)).toBe("bodyWeight");
      expect(at(65)).toBe("bodyWeight");
    });

    it("women: 35 % body fat is muscle only from 60", () => {
      const at = (age: number) =>
        calculateProteinRecommendation(80, "maintain", 35, { heightCm: 165, gender: "female", age }).basis;
      expect(at(50)).toBe("referenceWeight");
      expect(at(65)).toBe("bodyWeight");
    });

    it("cutting with body fat keeps the lean-mass rule", () => {
      const cut = calculateProteinRecommendation(110, "cut", 30, tall);
      expect(cut.basis).toBe("leanMass");
      expect(cut.basisKg).toBe(77);
    });

    it("no change at BMI 25 or below, or without height", () => {
      expect(calculateProteinRecommendation(80, "maintain", null, tall).basis).toBe("bodyWeight");
      expect(calculateProteinRecommendation(110, "maintain", null, { heightCm: null, gender: "male", age: 30 }).basis).toBe(
        "bodyWeight",
      );
      expect(proteinReferenceWeight(81, null, tall)).toBeNull();
      expect(proteinReferenceWeight(82, null, tall)).toBeCloseTo(81);
    });
  });
});
