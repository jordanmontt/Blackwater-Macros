import type { Metadata, Viewport } from "next";
import { Toaster } from "@/components/ui/sonner";
import { ThemeProvider } from "@/components/theme-provider";
import { AppNav } from "@/components/app-nav";
import { DemoBanner } from "@/components/demo-banner";
import { PwaInstall } from "@/components/pwa-install";
import { ThemeColorSync } from "@/components/theme-color-sync";
import { AuthRedirect } from "@/components/auth-redirect";
import { t } from "@/i18n";
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

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="es"
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
          <ThemeColorSync />
          <DemoBanner />
          <PwaInstall />
          <AuthRedirect />
          <div className="flex-1 pb-20 md:pb-6">{children}</div>
          <AppNav />
          <Toaster position="top-center" richColors />
        </ThemeProvider>
      </body>
    </html>
  );
}
