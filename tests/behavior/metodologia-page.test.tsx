import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import MetodologiaPage from "@/app/metodologia/page";
import { applyLanguage, t } from "@/i18n";
import { en } from "@/i18n/en";

/**
 * Requisitos de «Metodología»: se lee en el idioma elegido (antes salía siempre
 * en español porque la página se generaba en el servidor), y explica la
 * comprobación del perfil con sus fuentes en el ancla que abre su «¿Por qué?».
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

  it("explica cómo se compara el perfil con los datos, en /metodologia#check, con sus fuentes", () => {
    const { container } = render(<MetodologiaPage />);
    const section = container.querySelector("#check");
    expect(section).not.toBeNull();
    expect(section!.textContent).toContain(t.metodologia.checkTitle);
    expect(section!.textContent).toContain(t.metodologia.checkWhy10);
    for (const source of ["Lichtman SW", "Schoeller DA", "Rosenbaum M", "Kreitzman SN", "Frankenfield D"]) {
      expect(screen.getByText((content) => content.startsWith(source))).toBeInTheDocument();
    }
  });
});
