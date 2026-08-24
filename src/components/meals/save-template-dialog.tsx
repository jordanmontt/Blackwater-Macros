"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, ApiError } from "@/lib/api";
import type { IngredientInput, MealDTO, MealTemplateDTO } from "@/lib/types";
import { t } from "@/i18n";

interface SaveTemplateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  meal: MealDTO | null;
  onSaved: (template: MealTemplateDTO) => void;
}

/** Saves a logged meal as a reusable template (title + ingredients). */
export function SaveTemplateDialog({ open, onOpenChange, meal, onSaved }: SaveTemplateDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>{t.meal.saveAsTemplate}</DialogTitle>
          <DialogDescription>{t.hoy.templates}</DialogDescription>
        </DialogHeader>
        {open && meal ? (
          <SaveTemplateForm
            key={meal.id}
            meal={meal}
            onClose={() => onOpenChange(false)}
            onSaved={onSaved}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

interface SaveTemplateFormProps {
  meal: MealDTO;
  onClose: () => void;
  onSaved: (template: MealTemplateDTO) => void;
}

/** Rendered only while the dialog is open; state initializes from the meal. */
function SaveTemplateForm({ meal, onClose, onSaved }: SaveTemplateFormProps) {
  const [name, setName] = useState(() => meal.title);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const ingredients: IngredientInput[] = meal.ingredients.map((ingredient) => ({
      name: ingredient.name,
      quantity: ingredient.quantity,
      calories: ingredient.calories,
      protein: ingredient.protein,
    }));
    setPending(true);
    try {
      const template = await api.createTemplate({
        name: name.trim(),
        title: meal.title,
        notes: meal.notes,
        ingredients,
      });
      onClose();
      onSaved(template);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="template-name">{t.meal.templateNameLabel}</Label>
        <Input
          id="template-name"
          required
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onClose}>
          {t.meal.cancel}
        </Button>
        <Button type="submit" disabled={pending}>
          {pending ? t.common.loading : t.meal.save}
        </Button>
      </DialogFooter>
    </form>
  );
}
