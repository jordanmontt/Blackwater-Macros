"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  NutritionEntryFields,
  rawNutritionDraft,
  resultToNutritionDraft,
  type NutritionPayload,
} from "@/components/meals/nutrition-fields";
import { api, ApiError, type TemplatePayload } from "@/lib/api";
import type { MealTemplateDTO } from "@/lib/core/types";
import { t } from "@/i18n";

interface TemplateFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Template being edited, or null to create a new one. */
  template: MealTemplateDTO | null;
  onSaved: (template: MealTemplateDTO) => void;
}

/**
 * Create/edit form for meal templates. Reuses the same nutrition-entry fields
 * as a meal: per-ingredient nutrition or a single manual total.
 */
export function TemplateForm({ open, onOpenChange, template, onSaved }: TemplateFormProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{template ? t.ajustes.editTemplate : t.ajustes.newTemplate}</DialogTitle>
        </DialogHeader>
        {open ? (
          <TemplateFormFields
            key={template?.id ?? "new"}
            template={template}
            onClose={() => onOpenChange(false)}
            onSaved={onSaved}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

interface TemplateFormFieldsProps {
  template: MealTemplateDTO | null;
  onClose: () => void;
  onSaved: (template: MealTemplateDTO) => void;
}

/**
 * Rendered only while the dialog is open, so its state initializes
 * directly from the template being edited — no reset effects needed.
 */
function TemplateFormFields({ template, onClose, onSaved }: TemplateFormFieldsProps) {
  const [pending, setPending] = useState(false);

  async function handleSubmit(payload: NutritionPayload) {
    const templatePayload: TemplatePayload = {
      name: payload.title,
      title: payload.title,
      notes: payload.notes,
      entryMode: payload.entryMode,
      ingredients: payload.ingredients,
      totalCalories: payload.totalCalories,
      totalProtein: payload.totalProtein,
      totalCarbs: payload.totalCarbs,
      totalFat: payload.totalFat,
    };
    setPending(true);
    try {
      const saved = template
        ? await api.updateTemplate(template.id, templatePayload)
        : await api.createTemplate(templatePayload);
      onClose();
      onSaved(saved);
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  const draft = template ? resultToNutritionDraft(template) : rawNutritionDraft();

  return (
    <NutritionEntryFields
      draft={draft}
      pending={pending}
      submitLabel={t.meal.save}
      onCancel={onClose}
      onSubmit={handleSubmit}
    />
  );
}