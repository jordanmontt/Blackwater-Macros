import { afterEach, describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import AiSettingsPage from "@/app/ajustes/ia/page";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos de la página «Inteligencia artificial»: desde Ajustes, la flecha vuelve
 * a Ajustes; abierta desde el Coach o desde «Foto o texto» (`?from=`), vuelve allí y
 * lo dice, en vez de dejar al usuario en Ajustes.
 */

describe("página de IA: volver a donde se abrió", () => {
  afterEach(() => window.history.pushState({}, "", "/"));

  it("desde Ajustes vuelve a Ajustes", () => {
    window.history.pushState({}, "", "/ajustes/ia");
    render(<AiSettingsPage />);
    expect(screen.getByRole("button", { name: t.perfil.backToSettings })).toHaveAttribute("href", "/ajustes");
  });

  it("desde el Coach vuelve al Coach", () => {
    window.history.pushState({}, "", "/ajustes/ia?from=coach");
    render(<AiSettingsPage />);
    expect(screen.getByRole("button", { name: formatTemplate(t.common.backTo, { page: t.nav.coach }) })).toHaveAttribute(
      "href",
      "/coach",
    );
  });

  it("desde «Foto o texto» vuelve a Comidas; un origen desconocido, a Ajustes", () => {
    window.history.pushState({}, "", "/ajustes/ia?from=hoy");
    const { unmount } = render(<AiSettingsPage />);
    expect(screen.getByRole("button", { name: formatTemplate(t.common.backTo, { page: t.nav.hoy }) })).toHaveAttribute("href", "/");
    unmount();

    window.history.pushState({}, "", "/ajustes/ia?from=elsewhere");
    render(<AiSettingsPage />);
    expect(screen.getByRole("button", { name: t.perfil.backToSettings })).toHaveAttribute("href", "/ajustes");
  });
});
