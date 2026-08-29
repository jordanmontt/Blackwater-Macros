import type { Metadata, Viewport } from "next";
import { Toaster } from "@/components/ui/sonner";
import { ThemeProvider } from "@/components/theme-provider";
import { AppNav } from "@/components/app-nav";
import { DemoBanner } from "@/components/demo-banner";
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
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f5f4ef" },
    { media: "(prefers-color-scheme: dark)", color: "#151810" },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="es"
      suppressHydrationWarning
      className="h-full antialiased"
    >
      <body className="min-h-full flex flex-col">
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <DemoBanner />
          <AuthRedirect />
          <div className="flex-1 pb-20">{children}</div>
          <AppNav />
          <Toaster position="top-center" richColors />
        </ThemeProvider>
      </body>
    </html>
  );
}
