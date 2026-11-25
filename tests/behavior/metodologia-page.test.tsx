import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import MetodologiaPage from "@/app/metodologia/page";
import { applyLanguage, formatTemplate, t } from "@/i18n";
import { en } from "@/i18n/en";

/**
 * Requisitos de «Metodología»: se lee en el idioma elegido (antes salía siempre
 * en español porque la página se generaba en el servidor), y explica la
 * comprobación del perfil con sus fuentes en el ancla que abre su «¿Por qué?»,
 * por qué los días pasados conservan sus objetivos (enlazado desde Perfil) y por qué
 * pesarse en ayunas (enlazado desde el pesaje). Cada
 * sección es un encabezado con su ancla, y un índice arriba (cerrado, en grupos) lleva a todas.
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
    const index = screen.getByRole("navigation", { name: t.metodologia.onThisPage });
    const links = [...index.querySelectorAll("a")];
    // One link per section, in page order.
    expect(links).toHaveLength(headings.length);
    links.forEach((link, i) => {
      const section = container.querySelector(link.getAttribute("href")!);
      expect(section!.querySelector("h2")).toBe(headings[i]);
      expect(headings[i].textContent!.trim()).toBe(link.textContent!.trim());
    });
    expect(container.querySelector("#calories h2")).toHaveTextContent(t.metodologia.tdeeTitle);
  });

  it("el índice empieza cerrado en una línea con cuántas secciones hay, y abierto las agrupa", () => {
    render(<MetodologiaPage />);
    const details = screen.getByRole("navigation", { name: t.metodologia.onThisPage }).closest("details")!;
    // Closed: the first section shows at once instead of a screen of links.
    expect(details.open).toBe(false);
    const sections = screen.getAllByRole("heading", { level: 2 }).length;
    expect(details.querySelector("summary")).toHaveTextContent(
      `${t.metodologia.onThisPage} · ${formatTemplate(t.metodologia.indexCount, { n: sections })}`,
    );
    const groups = Object.values(t.metodologia.indexGroups);
    expect(groups).toHaveLength(4);
    for (const group of groups) expect(within(details).getByText(group)).toBeInTheDocument();
  });

  it("explica por qué pesarse en ayunas y qué hacer si no, en /metodologia#fasted (enlazado desde el pesaje)", () => {
    const { container } = render(<MetodologiaPage />);
    const section = container.querySelector("#fasted");
    expect(section!.querySelector("h2")).toHaveTextContent(t.metodologia.fastedTitle);
    expect(section!.textContent).toContain(t.metodologia.fastedBody);
    expect(section!.textContent).toContain(t.metodologia.fastedRule);
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
