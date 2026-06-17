"use client"

import * as React from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  BookOpen01Icon,
  Chatting01Icon,
  Home03Icon,
  PuzzleIcon,
  UserCircleIcon,
} from "@hugeicons/core-free-icons"

import { useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

type Tab = {
  href: string
  label: string
  icon: typeof Home03Icon
  matchPrefix?: string
  primary?: boolean
}

export function BottomTabBar() {
  const t = useT()
  const pathname = usePathname()

  const tabs: Tab[] = [
    { href: "/home", label: t.nav.home, icon: Home03Icon },
    {
      href: "/data",
      label: t.nav.topics,
      icon: BookOpen01Icon,
      matchPrefix: "/data",
    },
    {
      href: "/companion",
      label: t.nav.companion,
      icon: Chatting01Icon,
      matchPrefix: "/companion",
      primary: true,
    },
    { href: "/gaps", label: t.nav.gaps, icon: PuzzleIcon },
    { href: "/profile", label: t.nav.profile, icon: UserCircleIcon },
  ]

  return (
    <nav
      aria-label={t.nav.workspace}
      className={cn(
        "fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background/95 backdrop-blur",
        "supports-[backdrop-filter]:bg-background/80",
        "pb-[max(env(safe-area-inset-bottom),0px)]",
        "lg:hidden"
      )}
    >
      <ul className="flex items-stretch justify-between px-1">
        {tabs.map((tab) => {
          const active = tab.matchPrefix
            ? pathname.startsWith(tab.matchPrefix)
            : pathname === tab.href
          return (
            <li key={tab.href} className="flex-1">
              <Link
                href={tab.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "relative flex flex-col items-center justify-center gap-0.5 px-1 pt-2 pb-1.5 text-[10px] leading-tight font-medium",
                  active
                    ? "text-foreground"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                <span
                  className={cn(
                    "flex items-center justify-center rounded-full transition-colors",
                    tab.primary
                      ? "size-12 -translate-y-2 bg-primary text-primary-foreground shadow-lg"
                      : "size-9",
                    !tab.primary && active && "bg-primary/10 text-primary"
                  )}
                >
                  <HugeiconsIcon
                    icon={tab.icon}
                    strokeWidth={2}
                    className={cn(tab.primary ? "size-6" : "size-5")}
                  />
                </span>
                <span className="max-w-full truncate">{tab.label}</span>
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
