"use client";

import { useRef, useState } from "react";
import { formatNumber } from "@/i18n/format";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { ChevronDownIcon, DownloadIcon, Loader2Icon, UploadIcon, InfoIcon, LogOutIcon, PencilIcon, PlayCircleIcon, PlusIcon, ShieldIcon, SparklesIcon, Trash2Icon, UserIcon } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { TemplateForm } from "@/components/meals/template-form";
import { parseBackupCsv } from "@/lib/csv-import";
import { LanguageCard } from "@/components/settings/language-card";
import { api, ApiError, errorText, UNDO_TOAST_MS } from "@/lib/api";
import { exitDemoMode } from "@/lib/demo-store";
import { useDemoMode } from "@/lib/use-demo-mode";
import { useCachedResource } from "@/lib/use-cached-resource";
import { formatTemplate } from "@/i18n";
import type { MealTemplateDTO } from "@/lib/core/types";
import { cn } from "@/lib/utils";
import { useMounted } from "@/lib/use-mounted";
import { t } from "@/i18n";

export default function AjustesPage() {
  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const demoMode = useDemoMode();
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<MealTemplateDTO | null>(null);
  const [deletingTemplate, setDeletingTemplate] = useState<MealTemplateDTO | null>(null);
  const [templatesOpen, setTemplatesOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const importInput = useRef<HTMLInputElement>(null);
  const mounted = useMounted();

  const sessionRes = useCachedResource<
    Awaited<ReturnType<typeof api.session>>
  >("session", () => api.session());
  const templatesRes = useCachedResource<MealTemplateDTO[]>("templates", () =>
    api.listTemplates(),
  );

  const session = sessionRes.data;
  const username = session?.username ?? "";
  const isAdmin = session?.isAdmin ?? false;
  const templates = templatesRes.data ?? [];

  const themeOptions = [
    { value: "light", label: t.ajustes.themeLight },
    { value: "dark", label: t.ajustes.themeDark },
    { value: "system", label: t.ajustes.themeSystem },
  ];

  async function handleLogout() {
    if (demoMode) {
      exitDemoMode();
      router.replace("/login");
      router.refresh();
      return;
    }
    try {
      await api.logout();
      router.replace("/login");
      router.refresh();
    } catch {
      toast.error(t.common.errorGeneric);
    }
  }

  async function handleImport(file: File) {
    setImporting(true);
    try {
      const parsed = parseBackupCsv(await file.text());
      if (parsed.kind === "unknown") {
        toast.error(t.ajustes.importUnknown);
        return;
      }
      const result =
        parsed.kind === "meals" ? await api.importMeals(parsed.meals) : await api.importWeights(parsed.weights);
      const parts = [
        formatTemplate(parsed.kind === "meals" ? t.ajustes.importedMeals : t.ajustes.importedWeights, { n: result.added }),
      ];
      if (result.skipped > 0) parts.push(formatTemplate(t.ajustes.importSkipped, { n: result.skipped }));
      if (parsed.invalidRows > 0) parts.push(formatTemplate(t.ajustes.importInvalid, { n: parsed.invalidRows }));
      toast.success(parts.join(" · "));
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : t.ajustes.importFailed);
    } finally {
      setImporting(false);
    }
  }

  /** Asked first (the dialog), then deleted with an Undo. */
  async function handleDeleteTemplate() {
    if (!deletingTemplate) return;
    const template = deletingTemplate;
    try {
      await api.deleteTemplate(template.id);
      setDeletingTemplate(null);
      await templatesRes.trigger();
      toast.success(t.ajustes.templateDeleted, {
        duration: UNDO_TOAST_MS,
        action: {
          label: t.common.undo,
          onClick: () => {
            api
              .restoreTemplate(template)
              .then(() => templatesRes.trigger())
              .catch((error) => toast.error(errorText(error)));
          },
        },
      });
    } catch (error) {
      toast.error(errorText(error));
    }
  }

  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h1 className="text-lg font-semibold">{t.ajustes.title}</h1>
        </div>
      </header>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.ajustes.appearance}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-3 gap-1 rounded-lg border p-1">
            {themeOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={mounted && theme === option.value}
                onClick={() => setTheme(option.value)}
                className={cn(
                  "rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent",
                  mounted && theme === option.value && "bg-primary text-primary-foreground",
                )}
              >
                {option.label}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      <LanguageCard />

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.ajustes.session}</CardTitle>
          <CardDescription>
            {demoMode ? t.demo.banner : `${t.ajustes.loggedInAs} ${username || "…"}`}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Separator className="mb-3" />
          <Button variant="outline" onClick={() => void handleLogout()}>
            <LogOutIcon /> {t.auth.logout}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.perfil.title}</CardTitle>
          <CardDescription>{t.perfil.description}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="outline"
            nativeButton={false}
            render={<Link href="/ajustes/perfil" />}
          >
            <UserIcon /> {t.perfil.open}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.ai.title}</CardTitle>
          <CardDescription>{t.ai.linkSubtitle}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="outline" nativeButton={false} render={<Link href="/ajustes/ia" />}>
            <SparklesIcon /> {t.ai.open}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          {/* Collapsed by default: with many templates the list would take the whole page. */}
          <button
            type="button"
            aria-expanded={templatesOpen}
            onClick={() => setTemplatesOpen((open) => !open)}
            className="flex w-full items-center justify-between gap-2 text-left"
          >
            <span>
              <CardTitle className="text-base">{t.hoy.templates}</CardTitle>
              <CardDescription>{templates.length === 1
                  ? t.ajustes.templatesCountOne
                  : formatTemplate(t.ajustes.templatesCount, { n: templates.length })}</CardDescription>
            </span>
            <ChevronDownIcon className={cn("size-4 text-muted-foreground transition-transform", templatesOpen && "rotate-180")} />
          </button>
        </CardHeader>
        <CardContent hidden={!templatesOpen} className="space-y-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setEditingTemplate(null);
              setTemplateDialogOpen(true);
            }}
          >
            <PlusIcon /> {t.ajustes.newTemplate}
          </Button>
          {templates.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t.hoy.noTemplates}</p>
          ) : (
            <ul className="divide-y">
              {templates.map((template) => (
                <li key={template.id} className="py-2">
                  <div className="flex items-center gap-2">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{template.title}</p>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {template.entryMode === "total_only"
                          ? t.meal.totalOnlyBadge
                          : formatTemplate(t.meal.perIngredientSummary, {
                              n: template.ingredients.length,
                            })}
                      </p>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={t.meal.edit}
                      onClick={() => {
                        setEditingTemplate(template);
                        setTemplateDialogOpen(true);
                      }}
                    >
                      <PencilIcon className="text-muted-foreground" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={t.meal.delete}
                      onClick={() => setDeletingTemplate(template)}
                    >
                      <Trash2Icon className="text-muted-foreground" />
                    </Button>
                  </div>
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    <Badge variant="secondary" className="tabular-nums text-[11px]">
                      {formatNumber(template.resolvedCalories)} {t.hoy.kcalUnit}
                    </Badge>
                    <Badge variant="outline" className="tabular-nums text-[11px]">
                      {formatNumber(template.resolvedProtein)} g · {t.hoy.protein}
                    </Badge>
                    <Badge variant="outline" className="tabular-nums text-[11px]">
                      {formatNumber(template.resolvedCarbs)} g · {t.hoy.carbs}
                    </Badge>
                    <Badge variant="outline" className="tabular-nums text-[11px]">
                      {formatNumber(template.resolvedFat)} g · {t.hoy.fat}
                    </Badge>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <TemplateForm
        open={templateDialogOpen}
        onOpenChange={(open) => {
          setTemplateDialogOpen(open);
          if (!open) setEditingTemplate(null);
        }}
        template={editingTemplate}
        onSaved={() => {
          void templatesRes.trigger().catch(() => undefined);
          setTemplateDialogOpen(false);
          setEditingTemplate(null);
        }}
      />

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.ajustes.exportSection}</CardTitle>
          <CardDescription>{t.ajustes.exportHint}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            disabled={demoMode}
            nativeButton={false}
            render={<a href="/api/export/meals" download />}
          >
            <DownloadIcon /> {t.ajustes.exportMeals}
          </Button>
          <Button
            variant="outline"
            disabled={demoMode}
            nativeButton={false}
            render={<a href="/api/export/weights" download />}
          >
            <DownloadIcon /> {t.ajustes.exportWeights}
          </Button>
          <Button variant="outline" disabled={demoMode || importing} onClick={() => importInput.current?.click()}>
            {importing ? <Loader2Icon className="animate-spin" /> : <UploadIcon />}
            {importing ? t.ajustes.importing : t.ajustes.importCsv}
          </Button>
          <input
            ref={importInput}
            type="file"
            accept=".csv,text/csv"
            hidden
            data-testid="import-csv-input"
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (file) void handleImport(file);
            }}
          />
        </CardContent>
        {demoMode ? (
          <CardContent className="pt-0">
            <p className="text-xs text-muted-foreground">{t.ajustes.exportDemoDisabled}</p>
          </CardContent>
        ) : null}
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.metodologia.title}</CardTitle>
          <CardDescription>{t.ajustes.methodologyLink}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="outline"
            nativeButton={false}
            render={<Link href="/metodologia" />}
          >
            <InfoIcon /> {t.metodologia.title}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.onboarding.replay}</CardTitle>
          <CardDescription>{t.onboarding.replayHint}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="outline" nativeButton={false} render={<Link href="/bienvenida" />}>
            <PlayCircleIcon /> {t.onboarding.replay}
          </Button>
        </CardContent>
      </Card>

      {isAdmin && !demoMode ? (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <ShieldIcon className="size-4" /> {t.ajustes.administration}
            </CardTitle>
            <CardDescription>{t.ajustes.administrationDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button
              variant="outline"
              nativeButton={false}
              render={<Link href="/admin" />}
            >
              <ShieldIcon /> {t.ajustes.openAdmin}
            </Button>
          </CardContent>
        </Card>
      ) : null}


      <AlertDialog
        open={deletingTemplate !== null}
        onOpenChange={(open) => {
          if (!open) setDeletingTemplate(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t.ajustes.deleteTemplateTitle}</AlertDialogTitle>
            <AlertDialogDescription>
              {formatTemplate(t.ajustes.deleteTemplateBody, { name: deletingTemplate?.name ?? "" })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t.meal.cancel}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => void handleDeleteTemplate()}>
              {t.meal.delete}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </main>
  );
}
