import { cn } from "@/lib/utils";

/** One entry of a chart's legend: how the series is drawn, then its name. */
export interface LegendItem {
  label: string;
  color: string;
  /** "line": a thick stroke; "dot": a point; "band": a translucent block (a target range). */
  shape: "line" | "dot" | "band";
}

/**
 * The legend under Progreso's charts, drawn by us rather than Recharts' own: it
 * also names the target band (a `ReferenceArea` has no legend entry) and keeps
 * the same look on every chart.
 */
export function ChartLegend({ items }: { items: LegendItem[] }) {
  return (
    <ul className="flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs text-muted-foreground" data-testid="chart-legend">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-1.5">
          <span
            aria-hidden
            className={cn(
              "inline-block shrink-0",
              item.shape === "line" && "h-1 w-4 rounded-full",
              item.shape === "dot" && "size-2 rounded-full",
              item.shape === "band" && "h-2.5 w-4 rounded-sm opacity-30",
            )}
            style={{ background: item.color }}
          />
          {item.label}
        </li>
      ))}
    </ul>
  );
}
