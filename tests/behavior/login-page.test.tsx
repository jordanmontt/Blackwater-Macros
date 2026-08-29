import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LoginPage from "@/app/login/page";
import { DEMO_COOKIE_NAME } from "@/lib/demo-store";
import { t } from "@/i18n";

/**
 * Requisitos de la pantalla de acceso:
 *  - autenticarse correctamente navega a la página principal,
 *  - credenciales incorrectas muestran el error y permiten reintentar,
 *  - un fallo del servidor muestra un mensaje genérico,
 *  - entrar en demo activa el modo demo local,
 *  - si ya hay sesión activa (servicio /api/auth/session) redirige a inicio.
 */

const routerMock = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    login: vi.fn(async () => ({ ok: true as const })),
  } satisfies Pick<typeof import("@/lib/api").api, "login">,
}));

import { api, ApiError } from "@/lib/api";
import { jsonResponse } from "../helpers/repos";

describe("pantalla de inicio de sesión", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
    document.cookie = `${DEMO_COOKIE_NAME}=; path=/; max-age=0`;
    vi.stubGlobal("fetch", vi.fn(async () => jsonResponse(401, {})));
  });

  it("se autentica y navega a la página principal", async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(await screen.findByLabelText(t.auth.username), "sebastian");
    await user.type(screen.getByLabelText(t.auth.password), "clave-secreta");
    await user.click(screen.getByRole("button", { name: t.auth.submit }));

    expect(vi.mocked(api.login)).toHaveBeenCalledWith("sebastian", "clave-secreta");
    expect(routerMock.replace).toHaveBeenCalledWith("/");
  });

  it("muestra el error de credenciales y deja reintentar", async () => {
    const user = userEvent.setup();
    vi.mocked(api.login).mockRejectedValueOnce(new ApiError(401, "No autenticado"));
    render(<LoginPage />);

    await user.type(await screen.findByLabelText(t.auth.username), "ana");
    await user.type(screen.getByLabelText(t.auth.password), "clave");
    await user.click(screen.getByRole("button", { name: t.auth.submit }));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.auth.invalidCredentials);
    expect(routerMock.replace).not.toHaveBeenCalled();
  });

  it("muestra un mensaje genérico si el servidor falla", async () => {
    const user = userEvent.setup();
    vi.mocked(api.login).mockRejectedValueOnce(new ApiError(500, "Error interno"));
    render(<LoginPage />);

    await user.type(await screen.findByLabelText(t.auth.username), "ana");
    await user.type(screen.getByLabelText(t.auth.password), "clave");
    await user.click(screen.getByRole("button", { name: t.auth.submit }));

    expect(await screen.findByRole("alert")).toHaveTextContent(t.auth.genericError);
  });

  it("entrar en modo demo activa la cookie y navega al inicio", async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.click(await screen.findByRole("button", { name: t.demo.enter }));

    expect(document.cookie).toContain(`${DEMO_COOKIE_NAME}=1`);
    expect(routerMock.replace).toHaveBeenCalledWith("/");
  });

  it("si ya hay sesión activa redirige al inicio sin pedir credenciales", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => jsonResponse(200, {})));
    render(<LoginPage />);

    await vi.waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/");
    });
  });
});