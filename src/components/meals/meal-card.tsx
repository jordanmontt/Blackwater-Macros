"use client";

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVerticalIcon, MoreHorizontalIcon, PencilIcon, BookmarkPlusIcon, Trash2Icon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { formatNumberEs } from "@/lib/dates";
import { formatTemplate } from "@/i18n";
import type { MealDTO } from "@/lib/types";
import { t } from "@/i18n";

interface MealCardProps {
  meal: MealDTO;
  onEdit: (meal: MealDTO) => void;
  onDelete: (meal: MealDTO) => void;
  onSaveAsTemplate: (meal: MealDTO) => void;
}

export function MealCard({ meal, onEdit, onDelete, onSaveAsTemplate }: MealCardProps) {
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
      <Card>
        <CardHeader className="flex-row items-start justify-between space-y-0">
          <div className="flex min-w-0 items-start gap-2">
            <button
              type="button"
              className="mt-0.5 shrink-0 touch-none text-muted-foreground hover:text-foreground"
              aria-label={t.meal.reorder}
              {...attributes}
              {...listeners}
            >
              <GripVerticalIcon className="h-4 w-4" />
            </button>
            <div className="min-w-0">
              <CardTitle className="truncate text-base">{meal.title}</CardTitle>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {meal.entryMode === "total_only"
                  ? t.meal.totalOnlyBadge
                  : formatTemplate(t.meal.perIngredientSummary, { n: meal.ingredients.length })}
              </p>
            </div>
          </div>
          <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <Button variant="ghost" size="icon" aria-label={t.meal.edit}>
                <MoreHorizontalIcon />
              </Button>
            }
          />
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => onEdit(meal)}>
                <PencilIcon /> {t.meal.edit}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onSaveAsTemplate(meal)}>
                <BookmarkPlusIcon /> {t.meal.saveAsTemplate}
              </DropdownMenuItem>
              <DropdownMenuItem variant="destructive" onClick={() => onDelete(meal)}>
                <Trash2Icon /> {t.meal.delete}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </CardHeader>

        {meal.entryMode === "per_ingredient" && meal.ingredients.length > 0 ? (
          <CardContent className="pt-0">
            <ul className="divide-y rounded-lg border text-sm">
              {meal.ingredients.map((ingredient, index) => (
                <li key={index} className="flex items-baseline justify-between gap-2 px-2.5 py-1.5">
                  <span className="min-w-0 truncate">
                    {ingredient.name}
                    {ingredient.quantity ? (
                      <span className="text-muted-foreground"> · {ingredient.quantity}</span>
                    ) : null}
                  </span>
                  {hasIngredientNutrition ? (
                    <span className="shrink-0 tabular-nums text-xs text-muted-foreground">
                      {ingredient.calories !== undefined
                        ? `${formatNumberEs(ingredient.calories)} ${t.hoy.kcalUnit}`
                        : "—"}
                      {" · "}
                      {ingredient.protein !== undefined
                        ? `${formatNumberEs(ingredient.protein)} ${t.hoy.proteinUnit}`
                        : "—"}
                      {" · "}
                      {ingredient.carbs !== undefined
                        ? `${formatNumberEs(ingredient.carbs)} ${t.hoy.proteinUnit}`
                        : "—"}
                      {" · "}
                      {ingredient.fat !== undefined
                        ? `${formatNumberEs(ingredient.fat)} ${t.hoy.proteinUnit}`
                        : "—"}
                    </span>
                  ) : null}
                </li>
              ))}
            </ul>
          </CardContent>
        ) : null}

        {meal.notes ? (
          <CardContent className="pt-0 pb-0">
            <p className="rounded-md bg-muted px-2.5 py-1.5 text-sm whitespace-pre-line text-muted-foreground">
              {meal.notes}
            </p>
          </CardContent>
        ) : null}

        <CardFooter className="gap-2 pt-3">
          <Badge variant="secondary" className="tabular-nums">
            {formatNumberEs(meal.resolvedCalories)} {t.hoy.kcalUnit}
          </Badge>
          <Badge variant="outline" className="tabular-nums">
            {formatNumberEs(meal.resolvedProtein)} g · {t.hoy.protein}
          </Badge>
          <Badge variant="outline" className="tabular-nums">
            {formatNumberEs(meal.resolvedCarbs)} g · {t.hoy.carbs}
          </Badge>
          <Badge variant="outline" className="tabular-nums">
            {formatNumberEs(meal.resolvedFat)} g · {t.hoy.fat}
          </Badge>
        </CardFooter>
      </Card>
    </div>
  );
}
