import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DayNavigator } from "@/components/meals/day-navigator";
import { addDaysToKey, todayKey } from "@/lib/core/dates";

/** Requisito: estando en otro día, un doble clic (doble toque) en la fecha vuelve a hoy. */
describe("navegador de días", () => {
  it("doble clic en la fecha vuelve a hoy", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<DayNavigator value={addDaysToKey(todayKey(), -7)} onChange={onChange} />);

    await user.dblClick(screen.getByTestId("day-navigator-date"));
    expect(onChange).toHaveBeenCalledWith(todayKey());
  });

  it("si ya es hoy, el doble clic no hace nada", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<DayNavigator value={todayKey()} onChange={onChange} />);

    await user.dblClick(screen.getByTestId("day-navigator-date"));
    expect(onChange).not.toHaveBeenCalled();
  });
});
