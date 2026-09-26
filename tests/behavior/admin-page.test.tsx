import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminPage from "@/app/admin/page";
import { t } from "@/i18n";
import type { CalorieProfile } from "@/lib/core/types";
import { emptyCalorieProfile } from "../helpers/repos";

/**
 * Requisitos de la pantalla de administración:
 *  - solo los administradores ven el contenido; el resto ve «No tienes permiso»,
 *  - lista los usuarios con su rol,
 *  - crea usuarios, edita nombre/contraseña/rol y borra cuentas.
 */

const profile = (overrides: Partial<CalorieProfile> = {}): CalorieProfile => ({
  ...emptyCalorieProfile(),
  ...overrides,
});

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    session: vi.fn(async () => ({
      username: "jefe",
      isAdmin: true,
      calorieProfile: profile(),
    })),
    adminUsers: vi.fn(async () => [
      { id: "u-1", username: "jefe", isAdmin: true, createdAt: "2026-01-10T08:00:00.000Z" },
      { id: "u-2", username: "ana", isAdmin: false, createdAt: "2026-03-20T08:00:00.000Z" },
    ]),
    adminCreateUser: vi.fn(async () => undefined),
    adminUpdateUser: vi.fn(async () => undefined),
    adminDeleteUser: vi.fn(async () => undefined),
  } satisfies Pick<
    typeof import("@/lib/api").api,
    "session" | "adminUsers" | "adminCreateUser" | "adminUpdateUser" | "adminDeleteUser"
  >,
}));

import { api } from "@/lib/api";

describe("pantalla de administración", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.session).mockResolvedValue({
      username: "jefe",
      isAdmin: true,
      calorieProfile: profile(),
    });
  });

  it("niega el acceso a usuarios sin rol de administrador", async () => {
    vi.mocked(api.session).mockResolvedValue({
      username: "ana",
      isAdmin: false,
      calorieProfile: profile(),
    });
    render(<AdminPage />);

    expect(await screen.findByText(t.admin.forAdminsOnly)).toBeInTheDocument();
    expect(vi.mocked(api.adminUsers)).not.toHaveBeenCalled();
  });

  it("lista los usuarios y marca a los administradores", async () => {
    render(<AdminPage />);

    expect(await screen.findByText("jefe")).toBeInTheDocument();
    expect(screen.getByText("ana")).toBeInTheDocument();
    expect(screen.getAllByText(t.admin.adminBadge)).toHaveLength(1);
    const jefeRow = screen.getByText("jefe").closest("li")!;
    expect(within(jefeRow).getByText(t.admin.adminBadge)).toBeInTheDocument();
  });

  it("crea un usuario nuevo desde el diálogo", async () => {
    const user = userEvent.setup();
    render(<AdminPage />);
    await screen.findByText("ana");

    await user.click(screen.getByRole("button", { name: t.admin.newUser }));
    await user.type(await screen.findByLabelText(t.admin.usernameLabel), "luis");
    await user.type(screen.getByLabelText(t.admin.passwordLabel), "mi-clave-123");
    await user.click(screen.getByRole("button", { name: t.admin.save }));

    await waitFor(() =>
      expect(vi.mocked(api.adminCreateUser)).toHaveBeenCalledWith({
        username: "luis",
        password: "mi-clave-123",
      }),
    );
    expect(vi.mocked(api.adminUsers).mock.calls.length).toBeGreaterThan(1);
  });

  it("edita el nombre de un usuario", async () => {
    const user = userEvent.setup();
    render(<AdminPage />);
    await screen.findByText("ana");

    await user.click(screen.getByRole("button", { name: `${t.admin.edit} ana` }));
    const usernameInput = await screen.findByLabelText(t.admin.usernameLabel);
    await user.clear(usernameInput);
    await user.type(usernameInput, "analia");
    await user.click(screen.getByRole("button", { name: t.admin.save }));

await waitFor(() =>
        expect(vi.mocked(api.adminUpdateUser)).toHaveBeenCalledWith("u-2", {
          username: "analia",
          password: undefined,
          isAdmin: false,
        }),
      );
    });

    it("marca al usuario actual con la etiqueta «(tú)»", async () => {
      render(<AdminPage />);
      await screen.findByText("jefe");

      expect(screen.getByText(`(${t.admin.you})`)).toBeInTheDocument();
      const anaRow = screen.getByText("ana").closest("li")!;
      expect(within(anaRow).queryByText(`(${t.admin.you})`)).not.toBeInTheDocument();
    });

    it("edita el rol de otro usuario con el interruptor", async () => {
      const user = userEvent.setup();
      render(<AdminPage />);
      await screen.findByText("ana");

      await user.click(screen.getByRole("button", { name: `${t.admin.edit} ana` }));
      const roleSwitch = await screen.findByRole("switch");
      expect(roleSwitch).not.toBeDisabled();
      await user.click(roleSwitch);
      await user.click(screen.getByRole("button", { name: t.admin.save }));

      await waitFor(() =>
        expect(vi.mocked(api.adminUpdateUser)).toHaveBeenCalledWith("u-2", {
          username: "ana",
          password: undefined,
          isAdmin: true,
        }),
      );
    });

    it("impide cambiar el rol del propio usuario", async () => {
      const user = userEvent.setup();
      render(<AdminPage />);
      await screen.findByText("jefe");

      await user.click(screen.getByRole("button", { name: `${t.admin.edit} jefe` }));
      const roleSwitch = await screen.findByRole("switch");
      expect(roleSwitch).toBeDisabled();

      await user.click(screen.getByRole("button", { name: t.admin.save }));
      await waitFor(() =>
        expect(vi.mocked(api.adminUpdateUser)).toHaveBeenCalledWith("u-1", {
          username: "jefe",
          password: undefined,
          isAdmin: undefined,
        }),
      );
    });

    it("borra un usuario tras confirmar", async () => {
    const user = userEvent.setup();
    render(<AdminPage />);
    await screen.findByText("ana");

    await user.click(screen.getByRole("button", { name: `${t.admin.delete} ana` }));
    await user.click(await screen.findByRole("button", { name: t.admin.delete }));

    await waitFor(() =>
      expect(vi.mocked(api.adminDeleteUser)).toHaveBeenCalledWith("u-2"),
    );
  });
});