import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, act } from "@testing-library/react";
import { AuthRedirect } from "@/components/auth-redirect";
import { AUTH_EXPIRED_EVENT } from "@/lib/api";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

/**
 * Requisitos del AuthRedirect:
 *  - al recibir el evento "app:unauthorized" (emitido por el cliente API ante
 *    un 401) navega a la pantalla de acceso,
 *  - una vez desmontado deja de reaccionar al evento.
 */
describe("AuthRedirect", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("navega a /login al recibir el evento de sesión expirada", () => {
    render(<AuthRedirect />);
    act(() => {
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
    });
    expect(routerMock.push).toHaveBeenCalledWith("/login");
  });

  it("deja de responder al evento después de desmontarse", () => {
    const { unmount } = render(<AuthRedirect />);
    unmount();
    act(() => {
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
    });
    expect(routerMock.push).not.toHaveBeenCalled();
  });
});