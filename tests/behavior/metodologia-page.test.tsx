import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import MetodologiaPage from "@/app/metodologia/page";
import { applyLanguage, t } from "@/i18n";
import { en } from "@/i18n/en";

/**
 * Requisitos de «Metodología»: se lee en el idioma elegido (antes salía siempre
 * en español porque la página se generaba en el servidor).
 */

vi.mock("next/navigation", () => ({ useRouter: () => ({ back: vi.fn(), push: vi.fn() }) }));

describe("Metodología", () => {
  afterEach(() => applyLanguage("es"));

  it("sigue el idioma de la app", () => {
    applyLanguage("en");
    render(<MetodologiaPage />);
    expect(screen.getByRole("heading", { level: 1, name: en.metodologia.title })).toBeInTheDocument();
    expect(screen.getByText(en.metodologia.scaleVsTrendTitle)).toBeInTheDocument();
    expect(t.metodologia.title).toBe(en.metodologia.title);
  });
});
