"use client";

import { SOURCE_CODE_URL, WEBSITE_URL } from "@/lib/links";
import { useState } from "react";
import { formatNumber } from "@/i18n/format";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import {
  BookmarkIcon,
  ChevronDownIcon,
  CircleUserRoundIcon,
  CodeIcon,
  DownloadIcon,
  GlobeIcon,
  InfoIcon,
  LogOutIcon,
  PaletteIcon,
  PencilIcon,
  PlayCircleIcon,
  PlusIcon,
  ShieldIcon,
  SparklesIcon,
  Trash2Icon,
  UserIcon,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { TemplateForm } from "@/components/meals/template-form";
import { LanguageRow } from "@/components/settings/language-row";
import { SettingsGroup, SettingsRow } from "@/components/settings/settings-list";
import { api, errorText, UNDO_TOAST_MS } from "@/lib/api";
import { exitDemoMode } from "@/lib/demo-store";
import { useDemoMode } from "@/lib/use-demo-mode";
import { useCachedResource } from "@/lib/use-cached-resource";
import { formatTemplate } from "@/i18n";
import { todayKey } from "@/lib/core/dates";
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
    <main className="mx-auto w-full max-w-2xl space-y-6 px-4 pt-4 pb-6 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h1 className="text-lg font-semibold">{t.ajustes.title}</h1>
        </div>
      </header>

      <SettingsGroup title={t.ajustes.groupAccount}>
        <SettingsRow icon={CircleUserRoundIcon} title={demoMode ? t.demo.mode : `${t.ajustes.loggedInAs} ${username || "…"}`} />
        <SettingsRow icon={LogOutIcon} title={t.auth.logout} onClick={() => void handleLogout()} />
      </SettingsGroup>

      <SettingsGroup title={t.ajustes.groupPersonal}>
        <SettingsRow icon={UserIcon} title={t.perfil.title} description={t.perfil.description} href="/ajustes/perfil" />
        <SettingsRow icon={SparklesIcon} title={t.ai.title} description={t.ai.linkSubtitle} href="/ajustes/ia" />
        {/* Collapsed by default: with many templates the list would take the whole page. */}
        <SettingsRow
          icon={BookmarkIcon}
          title={t.hoy.templates}
          description={
            templates.length === 1 ? t.ajustes.templatesCountOne : formatTemplate(t.ajustes.templatesCount, { n: templates.length })
          }
          expanded={templatesOpen}
          onClick={() => setTemplatesOpen((open) => !open)}
          trailing={
            <ChevronDownIcon
              aria-hidden
              className={cn("size-4 shrink-0 text-muted-foreground transition-transform", templatesOpen && "rotate-180")}
            />
          }
        />
        <div hidden={!templatesOpen} className="space-y-3 px-4 pt-1 pb-3">
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
        </div>
      </SettingsGroup>

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

      <SettingsGroup
        title={t.ajustes.exportSection}
        footer={
          <>
            <p>{t.ajustes.exportHint}</p>
            {demoMode ? <p>{t.ajustes.exportDemoDisabled}</p> : null}
          </>
        }
      >
        {/* One file with everything (core/backup.ts); `day` names it after the browser's date. */}
        <SettingsRow
          icon={DownloadIcon}
          title={t.ajustes.exportData}
          href={`/api/export?day=${todayKey()}`}
          download
          disabled={demoMode}
        />
      </SettingsGroup>

      <SettingsGroup title={t.ajustes.groupApp}>
        <SettingsRow
          icon={PaletteIcon}
          title={t.ajustes.appearance}
          below={
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
          }
        />
        <LanguageRow />
        <SettingsRow icon={PlayCircleIcon} title={t.onboarding.replay} description={t.onboarding.replayHint} href="/bienvenida" />
      </SettingsGroup>

      <SettingsGroup title={t.ajustes.groupAbout}>
        <SettingsRow icon={InfoIcon} title={t.metodologia.title} description={t.ajustes.methodologyLink} href="/metodologia" />
        <SettingsRow icon={GlobeIcon} title={t.about.website} description={t.about.websiteHint} href={WEBSITE_URL} external />
        <SettingsRow icon={CodeIcon} title={t.about.sourceCode} description={t.about.sourceCodeHint} href={SOURCE_CODE_URL} external />
        {isAdmin && !demoMode ? (
          <SettingsRow icon={ShieldIcon} title={t.ajustes.administration} description={t.ajustes.administrationDescription} href="/admin" />
        ) : null}
      </SettingsGroup>

      <ConfirmDialog
        open={deletingTemplate !== null}
        title={t.ajustes.deleteTemplateTitle}
        description={formatTemplate(t.ajustes.deleteTemplateBody, { name: deletingTemplate?.name ?? "" })}
        cancelLabel={t.meal.cancel}
        confirmLabel={t.meal.delete}
        onCancel={() => setDeletingTemplate(null)}
        onConfirm={() => void handleDeleteTemplate()}
      />
    </main>
  );
}
