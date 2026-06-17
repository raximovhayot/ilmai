"use client"

import * as React from "react"
import { useTheme } from "next-themes"
import { HugeiconsIcon } from "@hugeicons/react"
import { Moon02Icon, Sun03Icon, ComputerIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useT } from "@/lib/i18n/provider"

function useHasMounted(): boolean {
  return React.useSyncExternalStore(
    () => () => {},
    () => true,
    () => false
  )
}

function ThemeToggle() {
  const { theme, setTheme } = useTheme()
  const t = useT()
  const mounted = useHasMounted()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant="ghost" size="icon-sm" aria-label={t.common.theme}>
            <HugeiconsIcon
              icon={Sun03Icon}
              strokeWidth={2}
              className="scale-100 rotate-0 transition-all dark:scale-0 dark:-rotate-90"
            />
            <HugeiconsIcon
              icon={Moon02Icon}
              strokeWidth={2}
              className="absolute scale-0 rotate-90 transition-all dark:scale-100 dark:rotate-0"
            />
            <span className="sr-only">{t.common.theme}</span>
          </Button>
        }
      />
      <DropdownMenuContent align="end" className="min-w-40">
        <DropdownMenuItem
          onClick={() => setTheme("light")}
          data-state={mounted && theme === "light" ? "checked" : undefined}
        >
          <HugeiconsIcon icon={Sun03Icon} strokeWidth={2} />
          {t.common.themeLight}
        </DropdownMenuItem>
        <DropdownMenuItem
          onClick={() => setTheme("dark")}
          data-state={mounted && theme === "dark" ? "checked" : undefined}
        >
          <HugeiconsIcon icon={Moon02Icon} strokeWidth={2} />
          {t.common.themeDark}
        </DropdownMenuItem>
        <DropdownMenuItem
          onClick={() => setTheme("system")}
          data-state={mounted && theme === "system" ? "checked" : undefined}
        >
          <HugeiconsIcon icon={ComputerIcon} strokeWidth={2} />
          {t.common.themeSystem}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export { ThemeToggle }
