"use client";

import { useSortable } from "@dnd-kit/sortable";
import { formatNumber } from "@/i18n/format";
import { CSS } from "@dnd-kit/utilities";
import { BookmarkPlusIcon, GripVerticalIcon, PencilIcon, Trash2Icon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { formatTemplate } from "@/i18n";
import type { MealDTO } from "@/lib/core/types";
import { t } from "@/i18n";

interface MealCardProps {
  meal: MealDTO;
  onEdit: (meal: MealDTO) => void;
  onSaveAsTemplate: (meal: MealDTO) => void;
  onDelete: (meal: MealDTO) => void;
}

export function MealCard({ meal, onEdit, onSaveAsTemplate, onDelete }: MealCardProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: meal.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    zIndex: isDragging ? 10 : undefined,
  };

  // With a single ingredient its numbers equal the meal's totals shown below (as on Android).
  const showIngredientNumbers =
    meal.ingredients.length > 1 &&
    meal.ingredients.some((ingredient) => ingredient.calories !== undefined || ingredient.protein !== undefined);

  return (
    <div ref={setNodeRef} style={style}>
      <Card size="sm">
        <CardHeader className="flex items-center justify-between gap-2 py-2 pl-2 pr-2">
          <div className="flex min-w-0 items-center gap-1.5">
            <button
              type="button"
              // A 44 px target around the 16 px grip; the negative margin keeps the card's layout.
              className="-m-3.5 flex size-11 shrink-0 touch-none items-center justify-center text-muted-foreground hover:text-foreground"
              aria-label={t.meal.reorder}
              {...attributes}
              {...listeners}
            >
              <GripVerticalIcon className="h-4 w-4" />
            </button>
            <div className="min-w-0">
              <CardTitle className="truncate text-sm leading-tight">{meal.title}</CardTitle>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {meal.entryMode === "total_only"
                  ? t.meal.totalOnlyBadge
                  : formatTemplate(t.meal.perIngredientSummary, { n: meal.ingredients.length })}
              </p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-0.5">
            <Button
              variant="ghost"
              size="icon"
              aria-label={t.meal.edit}
              className="size-7"
              onClick={() => onEdit(meal)}
            >
              <PencilIcon className="size-4 text-muted-foreground" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={t.meal.saveAsTemplate}
              title={t.meal.saveAsTemplate}
              className="size-7"
              onClick={() => onSaveAsTemplate(meal)}
            >
              <BookmarkPlusIcon className="size-4 text-muted-foreground" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={t.meal.delete}
              className="size-7"
              onClick={() => onDelete(meal)}
            >
              <Trash2Icon className="size-4 text-muted-foreground" />
            </Button>
          </div>
        </CardHeader>

        {meal.entryMode === "per_ingredient" && meal.ingredients.length > 0 ? (
          <CardContent className="pt-0">
            <ul className="divide-y rounded-lg border text-xs">
              {meal.ingredients.map((ingredient, index) => (
                <li key={index} className="flex items-baseline gap-1.5 px-2 py-1">
                  <span className="min-w-0 flex-1 truncate">{ingredient.name}</span>
                  {/* Fixed-width, end-aligned columns (rem, so they grow with the font size), as on
                      Android: the name keeps the room; carbs and fat are in the meal's totals below. */}
                  <span className="w-13 shrink-0 truncate text-right text-muted-foreground">{ingredient.quantity ?? ""}</span>
                  {showIngredientNumbers ? (
                    <>
                      <span className="w-16 shrink-0 text-right whitespace-nowrap tabular-nums text-muted-foreground">
                        {ingredient.calories !== undefined ? `${formatNumber(ingredient.calories)} ${t.hoy.kcalUnit}` : ""}
                      </span>
                      <span className="w-19 shrink-0 text-right whitespace-nowrap tabular-nums text-muted-foreground">
                        {ingredient.protein !== undefined
                          ? formatTemplate(t.hoy.proteinShort, { n: formatNumber(ingredient.protein, 1) })
                          : ""}
                      </span>
                    </>
                  ) : null}
                </li>
              ))}
            </ul>
          </CardContent>
        ) : null}

        {meal.notes ? (
          <CardContent className="pt-0 pb-0">
            <p className="rounded-md bg-muted px-2.5 py-1 text-xs whitespace-pre-line text-muted-foreground">
              {meal.notes}
            </p>
          </CardContent>
        ) : null}

        <CardFooter className="flex-wrap gap-1">
          <Badge variant="secondary" className="tabular-nums text-[11px]">
            {formatNumber(meal.resolvedCalories)} {t.hoy.kcalUnit}
          </Badge>
          <Badge variant="outline" className="tabular-nums text-[11px]">
            {formatNumber(meal.resolvedProtein)} g · {t.hoy.protein}
          </Badge>
          <Badge variant="outline" className="tabular-nums text-[11px]">
            {formatNumber(meal.resolvedCarbs)} g · {t.hoy.carbs}
          </Badge>
          <Badge variant="outline" className="tabular-nums text-[11px]">
            {formatNumber(meal.resolvedFat)} g · {t.hoy.fat}
          </Badge>
        </CardFooter>
      </Card>
    </div>
  );
}