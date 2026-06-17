import { Geist_Mono, Inter, Source_Serif_4 } from "next/font/google"

import "./globals.css"
import { MockProvider } from "@/components/mock-provider"
import { SessionProvider } from "@/components/session-provider"
import { Toaster } from "@/components/ui/sonner"
import { TooltipProvider } from "@/components/ui/tooltip"
import { ThemeProvider } from "@/components/theme-provider"
import { LanguageProvider } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

const inter = Inter({ subsets: ["latin"], variable: "--font-sans" })

const fontMono = Geist_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
})

const fontSerif = Source_Serif_4({
  subsets: ["latin", "cyrillic"],
  variable: "--font-serif",
})

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={cn(
        "antialiased",
        fontMono.variable,
        fontSerif.variable,
        "font-sans",
        inter.variable
      )}
    >
      <body>
        <ThemeProvider>
          <LanguageProvider>
            <SessionProvider>
              <TooltipProvider delay={300}>
                <MockProvider>{children}</MockProvider>
              </TooltipProvider>
              <Toaster position="top-right" richColors closeButton />
            </SessionProvider>
          </LanguageProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
