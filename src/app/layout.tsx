import type { Metadata, Viewport } from "next";
import { Toaster } from "@/components/ui/sonner";
import { ThemeProvider } from "@/components/theme-provider";
import { AppNav } from "@/components/app-nav";
import { DemoBanner } from "@/components/demo-banner";
import { AiModelRefresh } from "@/components/ai-model-refresh";
import { PwaInstall } from "@/components/pwa-install";
import { ThemeColorSync } from "@/components/theme-color-sync";
import { OnboardingRedirect } from "@/components/onboarding-redirect";
import { AuthRedirect } from "@/components/auth-redirect";
import { cookies, headers } from "next/headers";
import { LanguageGate } from "@/components/language-gate";
import { t } from "@/i18n";
import { DEFAULT_LANGUAGE, LANGUAGE_COOKIE, resolveLanguage } from "@/i18n/languages";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: t.appName,
    template: `%s · ${t.appName}`,
  },
  description: "Registro de calorías, macros y peso.",
  openGraph: {
    title: t.appName,
    description: "Registro de calorías, macros y peso.",
    images: [
      {
        url: "/logo.png",
        width: 1024,
        height: 1024,
        alt: "Blackwater Macros",
      },
    ],
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#f5f4ef",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const language = resolveLanguage((await cookies()).get(LANGUAGE_COOKIE)?.value, (await headers()).get("accept-language"));
  return (
    <html
      lang={language}
      suppressHydrationWarning
      className="h-full antialiased"
    >
      <body className="min-h-full flex flex-col">
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){var m=document.querySelector('meta[name="theme-color"]');if(m&&window.matchMedia('(prefers-color-scheme: dark)').matches){m.setAttribute('content','#151810');}})();`,
          }}
        />
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <LanguageGate serverRendered={language === DEFAULT_LANGUAGE}>
            <ThemeColorSync />
            <DemoBanner />
            <AiModelRefresh />
            <PwaInstall />
            <AuthRedirect />
            <OnboardingRedirect />
            <div className="flex-1 pb-20 md:pb-6">{children}</div>
            <AppNav />
            <Toaster position="top-center" richColors />
          </LanguageGate>
        </ThemeProvider>
      </body>
    </html>
  );
}
