import type { ReactNode } from "react"

import { AuthBrandPanel } from "@/components/auth/auth-brand-panel"
import { LanguageSwitcher } from "@/components/auth/language-switcher"
import { ThemeToggle } from "@/components/auth/theme-toggle"

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative grid min-h-svh w-full bg-background lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
      <AuthBrandPanel />
      <div className="relative flex flex-col">
        <div className="absolute end-4 top-4 z-10 flex items-center gap-1.5">
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
        <main className="flex flex-1 items-center justify-center px-4 py-10 sm:px-8">
          <div className="w-full max-w-md">{children}</div>
        </main>
      </div>
    </div>
  )
}
