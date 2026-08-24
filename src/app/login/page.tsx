"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ThemeToggle } from "@/components/theme-toggle";
import { api, ApiError } from "@/lib/api";
import { t } from "@/i18n";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  // Logged-in users never see this page: the proxy redirects on full loads,
  // and this check covers client-side restores (e.g. back/forward cache) that
  // bypass the server. Raw fetch instead of api.session() to avoid the global
  // 401 handler forcing a reload of /login.
  useEffect(() => {
    let cancelled = false;
    async function redirectToHomeIfAuthenticated() {
      try {
        const res = await fetch("/api/auth/session");
        if (!cancelled && res.ok) router.replace("/");
      } catch {
        // Network error: stay on the login page.
      }
    }
    void redirectToHomeIfAuthenticated();

    function handlePageShow(event: PageTransitionEvent) {
      if (event.persisted) void redirectToHomeIfAuthenticated();
    }
    window.addEventListener("pageshow", handlePageShow);
    return () => {
      cancelled = true;
      window.removeEventListener("pageshow", handlePageShow);
    };
  }, [router]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await api.login(username, password);
      router.replace("/");
      router.refresh();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError(t.auth.invalidCredentials);
      } else {
        setError(t.auth.genericError);
      }
      setPending(false);
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4">
      <div className="w-full max-w-sm space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold">{t.appName}</h1>
          <ThemeToggle />
        </div>
        <Card>
          <CardHeader>
            <CardTitle>{t.auth.loginTitle}</CardTitle>
            <CardDescription>{t.auth.loginDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">{t.auth.username}</Label>
                <Input
                  id="username"
                  name="username"
                  autoComplete="username"
                  autoCapitalize="none"
                  required
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">{t.auth.password}</Label>
                <Input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </div>
              {error ? (
                <p role="alert" className="text-sm text-destructive">
                  {error}
                </p>
              ) : null}
              <Button type="submit" className="w-full" disabled={pending}>
                {pending ? t.common.loading : t.auth.submit}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
