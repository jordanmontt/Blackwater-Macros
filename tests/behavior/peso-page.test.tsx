import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PesoPage from "@/app/peso/page";
import { formatNumberEs } from "@/lib/core/dates";
import { t } from "@/i18n";
import type { WeightDTO } from "@/lib/core/types";

/**
 * Requisitos visibles en la pantalla "Peso":
 *  - el usuario ve su peso actual (el último registro), el cambio de peso y de
 *    grasa de los últimos 7 días, o "—" si aún no hay datos suficientes,
 *  - puede registrar un pesaje (peso, fecha, grasa corporal y nota),
 *  - puede editar y borrar un pesaje existente,
 *  - con más de un registro se dibuja el gráfico de evolución.
 */

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    listWeights: vi.fn(async () => [] as WeightDTO[]),
    createWeight: vi.fn(async () => weightDto({ id: "w-new", weightKg: 77.4 })),
    updateWeight: vi.fn(async () => weightDto({ id: "w-1", weightKg: 78 })),
    deleteWeight: vi.fn(async () => ({ ok: true as const })),
  } satisfies Pick<
    typeof import("@/lib/api").api,
    "listWeights" | "createWeight" | "updateWeight" | "deleteWeight"
  >,
}));

vi.mock("@/components/weight-fat-chart", async () => {
  const React = await import("react");
  return {
    WeightFatChart: ({
      data,
    }: {
      data: { date: string; weight: number }[];
    }) =>
      React.createElement("div", {
        "data-testid": "grafico-peso",
        "data-puntos": String(data.length),
      }),
  };
});

import { api } from "@/lib/api";
import { weightDto } from "../helpers/repos";
import { clearCache } from "@/lib/client-cache";

describe("pantalla Peso", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    sessionStorage.clear();
  });

  it("sin registros invita a añadir el primer pesaje", async () => {
    vi.mocked(api.listWeights).mockResolvedValue([]);
    render(<PesoPage />);

    expect((await screen.findByText(t.peso.emptyList)).tagName).toBe("BUTTON");
    expect(screen.getAllByText("—").length).toBe(4);
  });

  it("muestra el peso actual como el último registro", async () => {
    vi.mocked(api.listWeights).mockResolvedValue([
      weightDto({ id: "w-1", measuredAt: "2026-06-10T08:00:00.000Z", weightKg: 80 }),
      weightDto({ id: "w-2", measuredAt: "2026-06-15T08:00:00.000Z", weightKg: 79.5 }),
    ]);
    render(<PesoPage />);

    const currentWeightMatches = await screen.findAllByText(
      (_, element) => element?.textContent === `${formatNumberEs(79.5, 1)} kg`,
    );
    expect(currentWeightMatches.length).toBeGreaterThan(0);
  });

  it("muestra el cambio de peso de los últimos 7 días", async () => {
    vi.mocked(api.listWeights).mockResolvedValue([
      weightDto({ id: "w-1", measuredAt: "2026-06-03T08:00:00.000Z", weightKg: 78 }),
      weightDto({ id: "w-2", measuredAt: "2026-06-15T08:00:00.000Z", weightKg: 79.5 }),
    ]);
    render(<PesoPage />);

    expect(await screen.findByText(t.peso.changeWeight7d)).toBeInTheDocument();
    expect(screen.getByText(`+${formatNumberEs(1.5, 1)} kg`)).toBeInTheDocument();
  });

  it("muestra '—' en el cambio de grasa si no hay dos medidas con 7 días de diferencia", async () => {
    vi.mocked(api.listWeights).mockResolvedValue([
      weightDto({ id: "w-1", bodyFatPct: 18.5 }),
    ]);
    render(<PesoPage />);

    expect(await screen.findByText(t.peso.changeFat7d)).toBeInTheDocument();
    // una sola medición → no hay cambio que calcular
    expect(screen.getAllByText("—").length).toBeGreaterThan(0);
  });

  it("agrupa los pesajes por día y muestra nota, editar y borrar", async () => {
    vi.mocked(api.listWeights).mockResolvedValue([
      weightDto({ id: "w-1", measuredAt: "2026-06-15T08:00:00.000Z", weightKg: 80, note: "tras ayuno" }),
      weightDto({ id: "w-2", measuredAt: "2026-06-10T08:00:00.000Z", weightKg: 81, bodyFatPct: 19 }),
    ]);
    render(<PesoPage />);

    expect(await screen.findByText("tras ayuno")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: t.peso.edit }).length).toBe(2);
    expect(screen.getAllByRole("button", { name: t.peso.delete }).length).toBe(2);
  });

  it("dibuja el gráfico cuando hay más de un registro", async () => {
    vi.mocked(api.listWeights).mockResolvedValue([
      weightDto({ id: "w-1", measuredAt: "2026-06-10T08:00:00.000Z", weightKg: 80 }),
      weightDto({ id: "w-2", measuredAt: "2026-06-15T08:00:00.000Z", weightKg: 79.5 }),
    ]);
    render(<PesoPage />);

    expect(await screen.findByTestId("grafico-peso")).toHaveAttribute("data-puntos", "2");
  });

  it("registra un pesaje nuevo y refresca la lista", async () => {
    const user = userEvent.setup();
    vi.mocked(api.listWeights).mockResolvedValueOnce([]);
    render(<PesoPage />);

    await user.click(screen.getByRole("button", { name: t.peso.addTitle }));
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toBeInTheDocument();

    await user.type(screen.getByLabelText(t.peso.weightLabel), "77,4");
    await user.type(screen.getByLabelText(t.peso.bodyFatLabel), "18,5");
    await user.clear(screen.getByLabelText(t.peso.datetimeLabel));
    await user.type(screen.getByLabelText(t.peso.datetimeLabel), "2026-06-15T08:00");

    await user.click(screen.getByRole("button", { name: t.peso.save }));

    expect(vi.mocked(api.createWeight)).toHaveBeenCalledWith(
      expect.objectContaining({
        weightKg: 77.4,
        bodyFatPct: 18.5,
        measuredAt: expect.any(String),
        note: null,
      }),
    );
    expect(vi.mocked(api.listWeights)).toHaveBeenCalledTimes(2); // carga inicial + refresh
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("rechaza un peso no válido sin llamar a la API", async () => {
    const user = userEvent.setup();
    vi.mocked(api.listWeights).mockResolvedValue([]);
    render(<PesoPage />);

    await user.click(screen.getByRole("button", { name: t.peso.addTitle }));
    await screen.findByRole("dialog");

    await user.type(screen.getByLabelText(t.peso.weightLabel), "0");
    await user.click(screen.getByRole("button", { name: t.peso.save }));

    expect(vi.mocked(api.createWeight)).not.toHaveBeenCalled();
  });

  it("edita un pesaje existente", async () => {
    const user = userEvent.setup();
    vi.mocked(api.listWeights).mockResolvedValue([
      weightDto({ id: "w-1", weightKg: 80, bodyFatPct: 18.5 }),
    ]);
    render(<PesoPage />);

    await user.click(await screen.findByRole("button", { name: t.peso.edit }));
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByLabelText(t.peso.weightLabel)).toHaveValue("80");

    const weightInput = within(dialog).getByLabelText(t.peso.weightLabel);
    await user.clear(weightInput);
    await user.type(weightInput, "79,8");
    await user.click(within(dialog).getByRole("button", { name: t.peso.save }));

    expect(vi.mocked(api.updateWeight)).toHaveBeenCalledWith(
      "w-1",
      expect.objectContaining({ weightKg: 79.8 }),
    );
  });

  it("borra un pesaje tras confirmar", async () => {
    const user = userEvent.setup();
    vi.mocked(api.listWeights).mockResolvedValue([weightDto({ id: "w-1" })]);
    render(<PesoPage />);

    await user.click(await screen.findByRole("button", { name: t.peso.delete }));
    const confirmDialog = await screen.findByRole("alertdialog");
    expect(confirmDialog).toBeInTheDocument();

    await user.click(within(confirmDialog).getByRole("button", { name: t.peso.delete }));

    expect(vi.mocked(api.deleteWeight)).toHaveBeenCalledWith("w-1");
    expect(vi.mocked(api.listWeights)).toHaveBeenCalledTimes(2);
  });
});