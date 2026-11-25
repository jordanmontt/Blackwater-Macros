import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DayNavigator } from "@/components/meals/day-navigator";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import { t } from "@/i18n";

/**
 * Requisitos: estando en otro día, la línea bajo la fecha dice «Volver a hoy» y lleva
 * a hoy (en hoy dice «Hoy»); un doble clic (doble toque) en la fecha también vuelve.
 */
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

  it("en otro día, «Volver a hoy» vuelve a hoy; en hoy la línea dice «Hoy»", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const { rerender } = render(<DayNavigator value={addDaysToKey(todayKey(), -3)} onChange={onChange} />);

    await user.click(screen.getByRole("button", { name: t.common.backToToday }));
    expect(onChange).toHaveBeenCalledWith(todayKey());

    rerender(<DayNavigator value={todayKey()} onChange={onChange} />);
    expect(screen.queryByRole("button", { name: t.common.backToToday })).toBeNull();
    expect(screen.getByText(t.common.today)).toBeInTheDocument();
  });
});
