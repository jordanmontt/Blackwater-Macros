import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AppNav } from "@/components/app-nav";
import { t } from "@/i18n";

/**
 * Requisitos del menú principal (Comidas, Progreso, Coach, Ajustes):
 *  - la pestaña de la página actual se anuncia como tal a un lector de pantalla
 *    (`aria-current="page"`), no solo con el color,
 *  - la página de IA abierta desde el Coach (o Comidas) no salta a Ajustes.
 */

let pathname = "/progreso";
vi.mock("next/navigation", () => ({ usePathname: () => pathname }));

describe("menú principal", () => {
  it("marca la pestaña de la página actual, también en sus subpáginas", () => {
    render(<AppNav />);
    expect(screen.getByRole("link", { name: t.nav.progreso })).toHaveAttribute("aria-current", "page");
    for (const other of [t.nav.hoy, t.nav.coach, t.nav.ajustes]) {
      expect(screen.getByRole("link", { name: other })).not.toHaveAttribute("aria-current");
    }
  });

  it("en una subpágina de Ajustes, Ajustes es la actual", () => {
    pathname = "/ajustes/perfil";
    render(<AppNav />);
    expect(screen.getByRole("link", { name: t.nav.ajustes })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: t.nav.hoy })).not.toHaveAttribute("aria-current");
  });

  it("la página de IA abierta desde el Coach deja el Coach como pestaña actual", () => {
    pathname = "/ajustes/ia";
    window.history.pushState({}, "", "/ajustes/ia?from=coach");
    render(<AppNav />);
    expect(screen.getByRole("link", { name: t.nav.coach })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: t.nav.ajustes })).not.toHaveAttribute("aria-current");
    window.history.pushState({}, "", "/");
  });
});
