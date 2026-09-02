"use client";

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVerticalIcon, PencilIcon, Trash2Icon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { formatNumberEs } from "@/lib/core/dates";
import { formatTemplate } from "@/i18n";
import type { MealDTO } from "@/lib/core/types";
import { t } from "@/i18n";

interface MealCardProps {
  meal: MealDTO;
  onEdit: (meal: MealDTO) => void;
  onDelete: (meal: MealDTO) => void;
}

export function MealCard({ meal, onEdit, onDelete }: MealCardProps) {
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

  const hasIngredientNutrition = meal.ingredients.some(
    (ingredient) =>
      ingredient.calories !== undefined ||
      ingredient.protein !== undefined ||
      ingredient.carbs !== undefined ||
      ingredient.fat !== undefined,
  );

  return (
    <div ref={setNodeRef} style={style}>
      <Card size="sm">
        <CardHeader className="flex items-center justify-between gap-2 py-2 pl-2 pr-2">
          <div className="flex min-w-0 items-center gap-1.5">
            <button
              type="button"
              className="shrink-0 touch-none text-muted-foreground hover:text-foreground"
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
                <li key={index} className="flex items-start justify-between gap-2 px-2 py-1">
                  <span className="min-w-0 truncate pt-px">
                    {ingredient.name}
                    {ingredient.quantity ? (
                      <span className="text-muted-foreground"> · {ingredient.quantity}</span>
                    ) : null}
                  </span>
                  {hasIngredientNutrition ? (
                    <span className="flex shrink-0 flex-wrap justify-end gap-x-2.5 gap-y-0.5 tabular-nums text-muted-foreground sm:gap-x-3">
                      {ingredient.calories !== undefined ? (
                        <span className="whitespace-nowrap">
                          {formatNumberEs(ingredient.calories)} {t.hoy.kcalUnit}
                        </span>
                      ) : null}
                      {ingredient.protein !== undefined ? (
                        <span className="whitespace-nowrap">
                          {formatNumberEs(ingredient.protein)} g {t.hoy.protein}
                        </span>
                      ) : null}
                      {ingredient.carbs !== undefined ? (
                        <span className="whitespace-nowrap">
                          {formatNumberEs(ingredient.carbs)} g {t.hoy.carbs}
                        </span>
                      ) : null}
                      {ingredient.fat !== undefined ? (
                        <span className="whitespace-nowrap">
                          {formatNumberEs(ingredient.fat)} g {t.hoy.fat}
                        </span>
                      ) : null}
                    </span>
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
            {formatNumberEs(meal.resolvedCalories)} {t.hoy.kcalUnit}
          </Badge>
          <Badge variant="outline" className="tabular-nums text-[11px]">
            {formatNumberEs(meal.resolvedProtein)} g · {t.hoy.protein}
          </Badge>
          <Badge variant="outline" className="tabular-nums text-[11px]">
            {formatNumberEs(meal.resolvedCarbs)} g · {t.hoy.carbs}
          </Badge>
          <Badge variant="outline" className="tabular-nums text-[11px]">
            {formatNumberEs(meal.resolvedFat)} g · {t.hoy.fat}
          </Badge>
        </CardFooter>
      </Card>
    </div>
  );
}