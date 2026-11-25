import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { DemoBanner } from "@/components/demo-banner";
import { enterDemoMode, exitDemoMode } from "@/lib/demo-store";
import { t } from "@/i18n";

/**
 * Requisitos del aviso de demo: en el móvil cabe en una línea (la frase entera ocupaba
 * un 16 % de la pantalla en cada página); desde 640 px se lee la frase entera.
 */

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn(), refresh: vi.fn() }) }));

describe("aviso de modo demo", () => {
  afterEach(() => exitDemoMode());

  it("una línea en el móvil, la frase entera en pantallas anchas, y siempre la salida", () => {
    enterDemoMode();
    render(<DemoBanner />);
    expect(screen.getByText(t.demo.bannerShort)).toHaveClass("sm:hidden");
    expect(screen.getByText(t.demo.banner)).toHaveClass("hidden", "sm:inline");
    expect(screen.getByRole("button", { name: t.demo.exit })).toBeInTheDocument();
  });
});
