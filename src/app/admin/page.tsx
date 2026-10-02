"use client";

import Link from "next/link";
import { formatDateMedium } from "@/i18n/format";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeftIcon, PencilIcon, PlusIcon, ShieldIcon, Trash2Icon } from "lucide-react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
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
import { t } from "@/i18n";
import type { AdminUserDTO } from "@/lib/core/types";
import { cn } from "@/lib/utils";


interface UserFormState {
  username: string;
  password: string;
  isAdmin: boolean;
}

export default function AdminPage() {
  const [username, setUsername] = useState("");
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null);
  const [users, setUsers] = useState<AdminUserDTO[] | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<AdminUserDTO | null>(null);
  const [deleting, setDeleting] = useState<AdminUserDTO | null>(null);
  const [form, setForm] = useState<UserFormState>({ username: "", password: "", isAdmin: false });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  const refresh = useCallback(async () => {
    try {
      setUsers(await api.adminUsers());
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) toast.error(t.common.errorGeneric);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    api.session().then((session) => {
      if (cancelled) return;
      setUsername(session.username);
      setIsAdmin(session.isAdmin);
      if (session.isAdmin) void refresh();
    });
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  function openCreate() {
    setForm({ username: "", password: "", isAdmin: false });
    setError("");
    setCreateOpen(true);
  }

  function openEdit(user: AdminUserDTO) {
    setForm({ username: user.username, password: "", isAdmin: user.isAdmin });
    setError("");
    setEditing(user);
  }

  function closeCreate() {
    setCreateOpen(false);
  }

  function closeEdit() {
    setEditing(null);
  }

  async function handleCreate() {
    setPending(true);
    setError("");
    try {
      await api.adminCreateUser({ username: form.username, password: form.password });
      toast.success(t.admin.userCreated);
      setCreateOpen(false);
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  async function handleUpdate() {
    if (!editing) return;
    setPending(true);
    setError("");
    try {
      await api.adminUpdateUser(editing.id, {
        username: form.username,
        password: form.password || undefined,
        isAdmin: editing.username === username ? undefined : form.isAdmin,
      });
      toast.success(t.admin.userUpdated);
      setEditing(null);
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  async function handleDelete() {
    if (!deleting) return;
    try {
      await api.adminDeleteUser(deleting.id);
      toast.success(t.admin.userDeleted);
      setDeleting(null);
      await refresh();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : t.common.errorGeneric);
      setDeleting(null);
    }
  }

  const denied = isAdmin === false;
  const loading = isAdmin === null || (isAdmin && users === null);

  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h1 className="text-lg font-semibold">{t.admin.title}</h1>
        </div>
        <Button
          variant="ghost"
          size="icon-sm"
          nativeButton={false}
          render={<Link href="/ajustes" aria-label={t.admin.backToSettings} />}
        >
          <ArrowLeftIcon />
        </Button>
      </header>

      {loading ? (
        <Card>
          <CardContent className="py-6 text-sm text-muted-foreground">
            {t.common.loading}
          </CardContent>
        </Card>
      ) : denied ? (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <ShieldIcon className="size-4" /> {t.admin.forAdminsOnly}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Button
              variant="outline"
              nativeButton={false}
              render={<Link href="/ajustes" />}
            >
              <ArrowLeftIcon /> {t.admin.backToSettings}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center justify-between gap-2 text-base">
              {t.admin.usersTitle}
              <Button size="sm" onClick={openCreate}>
                <PlusIcon /> {t.admin.newUser}
              </Button>
            </CardTitle>
            <CardDescription>{t.admin.usersDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            {users && users.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t.admin.noUsers}</p>
            ) : (
              <ul className="divide-y">
                {users?.map((user) => (
                  <li key={user.id} className="py-2.5">
                    <div className="flex items-center gap-2">
                      <div className="min-w-0 flex-1">
                        <p className="flex items-center gap-1.5 truncate text-sm font-medium">
                          {user.username}
                          {user.username === username && (
                            <span className="text-xs font-normal text-muted-foreground">
                              ({t.admin.you})
                            </span>
                          )}
                          {user.isAdmin && <Badge variant="secondary">{t.admin.adminBadge}</Badge>}
                        </p>
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {t.admin.memberSince.replace(
                            "{date}",
                            formatDateMedium(new Date(user.createdAt)),
                          )}
                        </p>
                      </div>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`${t.admin.edit} ${user.username}`}
                        onClick={() => openEdit(user)}
                      >
                        <PencilIcon className="text-muted-foreground" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`${t.admin.delete} ${user.username}`}
                        onClick={() => setDeleting(user)}
                        className={cn(user.username === username && "hidden")}
                      >
                        <Trash2Icon className="text-muted-foreground" />
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      )}

      <Dialog open={createOpen} onOpenChange={(open) => (open ? undefined : closeCreate())}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t.admin.createTitle}</DialogTitle>
            <DialogDescription>{t.admin.createDescription}</DialogDescription>
          </DialogHeader>
          <form
            className="space-y-4"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void handleCreate();
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="admin-username">{t.admin.usernameLabel}</Label>
              <Input
                id="admin-username"
                autoComplete="off"
                autoFocus
                value={form.username}
                onChange={(event) => setForm({ ...form, username: event.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="admin-password">{t.admin.passwordLabel}</Label>
              <Input
                id="admin-password"
                type="password"
                autoComplete="new-password"
                placeholder={t.admin.passwordPlaceholder}
                value={form.password}
                onChange={(event) => setForm({ ...form, password: event.target.value })}
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <DialogFooter>
              <Button type="button" variant="ghost" onClick={closeCreate}>
                {t.admin.cancel}
              </Button>
              <Button type="submit" disabled={pending}>
                {pending ? t.common.loading : t.admin.save}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => (open ? undefined : closeEdit())}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t.admin.editTitle}</DialogTitle>
            <DialogDescription>{t.admin.editDescription}</DialogDescription>
          </DialogHeader>
          <form
            className="space-y-4"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void handleUpdate();
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="edit-username">{t.admin.usernameLabel}</Label>
              <Input
                id="edit-username"
                autoComplete="off"
                autoFocus
                value={form.username}
                onChange={(event) => setForm({ ...form, username: event.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-password">{t.admin.newPasswordLabel}</Label>
              <Input
                id="edit-password"
                type="password"
                autoComplete="new-password"
                placeholder={t.admin.passwordPlaceholder}
                value={form.password}
                onChange={(event) => setForm({ ...form, password: event.target.value })}
              />
            </div>
            <div className="flex items-center justify-between gap-3 rounded-lg border p-3">
              <Label htmlFor="edit-is-admin" className="cursor-pointer">
                {t.admin.isAdminLabel}
              </Label>
              {/* Native checkbox with switch semantics; Safari 17.4+ even draws it as an iOS switch. */}
              <input
                id="edit-is-admin"
                type="checkbox"
                role="switch"
                {...{ switch: "" }}
                className="size-5"
                checked={form.isAdmin}
                disabled={editing?.username === username}
                onChange={(event) => setForm({ ...form, isAdmin: event.target.checked })}
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <DialogFooter>
              <Button type="button" variant="ghost" onClick={closeEdit}>
                {t.admin.cancel}
              </Button>
              <Button type="submit" disabled={pending}>
                {pending ? t.common.loading : t.admin.save}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        title={t.admin.deleteConfirmTitle}
        description={t.admin.deleteConfirmBody}
        cancelLabel={t.admin.cancel}
        confirmLabel={t.admin.delete}
        onCancel={() => setDeleting(null)}
        onConfirm={() => void handleDelete()}
      />
    </main>
  );
}