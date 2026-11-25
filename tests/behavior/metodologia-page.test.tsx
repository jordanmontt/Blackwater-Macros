import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import MetodologiaPage from "@/app/metodologia/page";
import { applyLanguage, t } from "@/i18n";
import { en } from "@/i18n/en";

/**
 * Requisitos de «Metodología»: se lee en el idioma elegido (antes salía siempre
 * en español porque la página se generaba en el servidor), y explica la
 * comprobación del perfil con sus fuentes en el ancla que abre su «¿Por qué?»,
 * y por qué los días pasados conservan sus objetivos (enlazado desde Perfil). Cada
 * sección es un encabezado con su ancla, y un índice arriba lleva a todas.
 */

vi.mock("next/navigation", () => ({ useRouter: () => ({ back: vi.fn(), push: vi.fn() }) }));

describe("Metodología", () => {
  afterEach(() => applyLanguage("es"));

  it("sigue el idioma de la app", () => {
    applyLanguage("en");
    render(<MetodologiaPage />);
    expect(screen.getByRole("heading", { level: 1, name: en.metodologia.title })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: en.metodologia.scaleVsTrendTitle })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: en.metodologia.scaleVsTrendTitle })).toHaveAttribute("href", "#scale");
    expect(t.metodologia.title).toBe(en.metodologia.title);
  });

  it("se sale con ← al principio, como Perfil e IA, no con un ✕", () => {
    render(<MetodologiaPage />);
    expect(screen.getByRole("button", { name: t.addFood.back })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: t.common.close })).toBeNull();
  });

  it("cada sección es un encabezado con su ancla, y el índice de arriba lleva a todas", () => {
    const { container } = render(<MetodologiaPage />);
    const headings = screen.getAllByRole("heading", { level: 2 });
    // The index's own title, then one per section.
    expect(headings[0]).toHaveTextContent(t.metodologia.onThisPage);
    const index = screen.getByRole("heading", { name: t.metodologia.onThisPage }).closest("[data-slot=card]")!;
    const links = [...index.querySelectorAll("a")];
    expect(links).toHaveLength(headings.length - 1);
    for (const link of links) {
      const section = container.querySelector(link.getAttribute("href")!);
      expect(section).not.toBeNull();
      expect(section!.querySelector("h2")!.textContent!.trim()).toBe(link.textContent!.trim());
    }
    expect(container.querySelector("#calories h2")).toHaveTextContent(t.metodologia.tdeeTitle);
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

  it("explica en /metodologia#past-days por qué los días pasados conservan sus objetivos", () => {
    const { container } = render(<MetodologiaPage />);
    const section = container.querySelector("#past-days");
    expect(section).not.toBeNull();
    expect(section!.textContent).toContain(t.metodologia.pastDaysTitle);
    expect(section!.textContent).toContain(t.metodologia.pastDaysScale);
  });

  it("explica en /metodologia#fat el rango de grasa del 20–35 %, con sus fuentes", () => {
    const { container } = render(<MetodologiaPage />);
    const section = container.querySelector("#fat");
    expect(section).not.toBeNull();
    expect(section!.textContent).toContain(t.metodologia.fatTitle);
    expect(section!.textContent).toContain(t.metodologia.fatRange);
    for (const source of ["Institute of Medicine", "Hall KD, Guo J", "Gardner CD", "Whittaker J", "World Health Organization", "Hooper L"]) {
      expect(screen.getByText((content) => content.startsWith(source))).toBeInTheDocument();
    }
  });
});
