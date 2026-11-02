"use client";

import { useState } from "react";
import { ExternalLinkIcon, Loader2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PasswordInput } from "@/components/ui/password-input";
import { SubpageHeader } from "@/components/subpage-header";
import { OFF_SERVER, checkOffAccount, setOffAccount, useOffAccount } from "@/lib/foods/own-products";
import { formatTemplate, t } from "@/i18n";

type Check = "idle" | "checking" | "wrong" | "offline" | "required";

/**
 * Ajustes → Open Food Facts: products added from a label go there as
 * Blackwater Macros, or with the user's own account (checked with Open Food
 * Facts, then kept in this browser only, like the AI keys).
 */
export default function OffAccountPage() {
  const account = useOffAccount();
  const [userId, setUserId] = useState("");
  const [password, setPassword] = useState("");
  const [check, setCheck] = useState<Check>("idle");

  async function save() {
    const user = userId.trim();
    if (user === "" || password === "") {
      setCheck("required");
      return;
    }
    setCheck("checking");
    const result = await checkOffAccount(user, password);
    if (result === "ok") {
      setOffAccount({ userId: user, password });
      setUserId("");
      setPassword("");
      setCheck("idle");
    } else {
      setCheck(result);
    }
  }

  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 pb-12 md:pt-6">
      <SubpageHeader title={t.offAccount.title} backLabel={t.perfil.backToSettings} />
      <Card>
        <CardHeader className="pb-3">
          <CardDescription>{t.offAccount.description}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {account ? (
            <div className="space-y-3">
              <p className="text-sm" role="status">
                {formatTemplate(t.offAccount.signedIn, { user: account.userId })}
              </p>
              <Button variant="outline" onClick={() => setOffAccount(null)}>
                {t.offAccount.signOut}
              </Button>
            </div>
          ) : (
            <form
              className="space-y-3"
              onSubmit={(event) => {
                event.preventDefault();
                void save();
              }}
            >
              <div className="space-y-1.5">
                <Label htmlFor="off-user">{t.offAccount.username}</Label>
                <Input
                  id="off-user"
                  autoComplete="username"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  value={userId}
                  onChange={(event) => setUserId(event.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="off-password">{t.offAccount.password}</Label>
                <PasswordInput
                  id="off-password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </div>
              <p className="text-xs text-muted-foreground">{t.offAccount.keptHere}</p>
              {check === "wrong" || check === "offline" || check === "required" ? (
                <p className="text-sm text-destructive" role="alert">
                  {t.offAccount[check]}
                </p>
              ) : null}
              <Button type="submit" disabled={check === "checking"}>
                {check === "checking" ? <Loader2Icon className="animate-spin" /> : null}
                {check === "checking" ? t.offAccount.checking : t.offAccount.save}
              </Button>
              <p>
                <a
                  href={`${OFF_SERVER}/cgi/user.pl`}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
                >
                  {t.offAccount.create} <ExternalLinkIcon className="size-3.5" />
                </a>
              </p>
            </form>
          )}
          <p className="border-t pt-3 text-xs text-muted-foreground">{t.offAccount.photosNote}</p>
        </CardContent>
      </Card>
    </main>
  );
}
