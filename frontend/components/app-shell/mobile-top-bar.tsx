"use client"

import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import { SparklesIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { UserAvatar, UserMenu, type UserSummary } from "./user-menu"
import { useT } from "@/lib/i18n/provider"

export function MobileTopBar({ user }: { user: UserSummary }) {
  const t = useT()
  const displayName = user.name ?? user.email ?? t.settings.account.unknownName

  return (
    <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-border bg-background/95 px-3 backdrop-blur supports-[backdrop-filter]:bg-background/80 lg:hidden">
      <Link
        href="/home"
        className="flex items-center gap-2 text-sm font-semibold"
      >
        <span
          className="flex size-7 items-center justify-center rounded-md bg-primary text-primary-foreground"
          aria-hidden
        >
          <HugeiconsIcon
            icon={SparklesIcon}
            strokeWidth={2}
            className="size-4"
          />
        </span>
        <span className="truncate">{t.brand.name}</span>
      </Link>

      <UserMenu
        user={user}
        side="bottom"
        align="end"
        trigger={
          <Button
            variant="ghost"
            size="icon-sm"
            className="rounded-full"
            aria-label={t.nav.userMenu}
          >
            <UserAvatar
              name={user.name}
              email={user.email}
              image={user.image}
              size="sm"
            />
            <span className="sr-only">{displayName}</span>
          </Button>
        }
      />
    </header>
  )
}
