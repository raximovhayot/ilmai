"use client"

import * as React from "react"
import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowLeft01Icon,
  ArrowRight01Icon,
  Chatting01Icon,
  Crown02Icon,
  DatabaseIcon,
  Home03Icon,
  MoreHorizontalIcon,
  PlusSignIcon,
  PuzzleIcon,
  RoadIcon,
  SparklesIcon,
} from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { useT } from "@/lib/i18n/provider"
import type { TopicResponse } from "@/lib/topics"
import type { PremiumStatus } from "@/lib/premium"
import type { ChatSession } from "@/lib/agent"
import { cn } from "@/lib/utils"
import { UserAvatar, UserMenu, type UserSummary } from "./user-menu"

type NavItem = {
  href: string
  label: string
  icon: typeof Home03Icon
  matchPrefix?: string
}

export function AppSidebar({
  sessions,
  premium,
  user,
  collapsed,
  onToggle,
}: {
  topics: TopicResponse[]
  sessions: ChatSession[]
  premium: PremiumStatus | null
  user: UserSummary
  collapsed: boolean
  onToggle: () => void
}) {
  const t = useT()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const activeSessionId = searchParams.get("session")

  const items: NavItem[] = [
    { href: "/home", label: t.nav.home, icon: Home03Icon },
    {
      href: "/data",
      label: t.nav.topics,
      icon: DatabaseIcon,
      matchPrefix: "/data",
    },
    { href: "/gaps", label: t.nav.gaps, icon: PuzzleIcon },
    { href: "/plan", label: t.nav.plan, icon: RoadIcon },
  ]

  return (
    <aside
      className={cn(
        "fixed inset-y-0 start-0 z-40 hidden flex-col border-e border-sidebar-border bg-sidebar text-sidebar-foreground transition-[width] duration-200 lg:flex",
        collapsed ? "lg:w-16" : "lg:w-64"
      )}
    >
      <div className="flex h-14 items-center justify-between border-b border-sidebar-border px-3">
        <Link
          href="/home"
          className="flex items-center gap-2 text-sm font-semibold"
        >
          <span
            className="flex size-7 items-center justify-center rounded-md bg-primary text-primary-foreground"
            aria-hidden
          >
            <HugeiconsIcon icon={SparklesIcon} strokeWidth={2} />
          </span>
          {!collapsed && <span className="truncate">{t.brand.name}</span>}
        </Link>
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={onToggle}
          aria-label="Toggle sidebar"
        >
          <HugeiconsIcon
            icon={collapsed ? ArrowRight01Icon : ArrowLeft01Icon}
            strokeWidth={2}
          />
        </Button>
      </div>

      <nav className="flex flex-1 flex-col gap-1 overflow-y-auto p-2">
        <SectionHeader label={t.nav.workspace} collapsed={collapsed} />
        {items.map((item) => {
          const active = item.matchPrefix
            ? pathname.startsWith(item.matchPrefix)
            : pathname === item.href
          return (
            <SidebarItem
              key={item.href}
              href={item.href}
              label={item.label}
              icon={item.icon}
              active={active}
              collapsed={collapsed}
            />
          )
        })}

        {!collapsed && (
          <div className="mt-2 flex flex-col">
            <div className="flex items-center justify-between px-2">
              <span className="text-xs font-medium tracking-wider text-muted-foreground uppercase">
                {t.companion.sessionsTitle}
              </span>
              <Link
                href="/companion"
                className="rounded-md p-1 text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
                aria-label={t.companion.newSession}
              >
                <HugeiconsIcon
                  icon={PlusSignIcon}
                  strokeWidth={2}
                  className="size-4"
                />
              </Link>
            </div>
            {sessions.length === 0 ? (
              <p className="px-2 py-2 text-xs text-muted-foreground">
                {t.companion.noSessions}
              </p>
            ) : (
              <ul className="mt-1 flex flex-col gap-0.5">
                {sessions.slice(0, 20).map((session) => {
                  const active =
                    pathname.startsWith("/companion") &&
                    activeSessionId === session.id
                  return (
                    <li key={session.id}>
                      <Link
                        href={`/companion?session=${session.id}`}
                        className={cn(
                          "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors",
                          active
                            ? "bg-sidebar-accent text-sidebar-accent-foreground"
                            : "text-sidebar-foreground/80 hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground"
                        )}
                        title={session.title ?? t.companion.untitled}
                      >
                        <HugeiconsIcon
                          icon={Chatting01Icon}
                          strokeWidth={2}
                          className="size-4 shrink-0 text-muted-foreground"
                        />
                        <span className="truncate">
                          {session.title?.trim() || t.companion.untitled}
                        </span>
                      </Link>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        )}
      </nav>

      <div className="mt-auto flex flex-col gap-1 border-t border-sidebar-border p-2">
        <Link
          href="/premium"
          className={cn(
            "flex items-center gap-2 rounded-md px-2 py-2 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
            collapsed && "lg:justify-center lg:px-0"
          )}
        >
          <HugeiconsIcon
            icon={Crown02Icon}
            strokeWidth={2}
            className="size-4 text-amber-500"
          />
          {!collapsed && (
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="truncate text-sm">
                {premium?.tier === "PREMIUM"
                  ? t.premium.tierPremium
                  : t.premium.tierFree}
              </span>
              {premium?.tier === "FREE" && (
                <span className="truncate text-xs text-muted-foreground">
                  {t.nav.upgrade}
                </span>
              )}
            </div>
          )}
        </Link>
        <UserMenu
          user={user}
          side="top"
          align="start"
          trigger={
            <Button
              variant="ghost"
              className={cn(
                "h-auto w-full justify-start gap-2 rounded-md px-2 py-2 text-start hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
                collapsed && "lg:justify-center lg:px-0"
              )}
              aria-label={t.nav.userMenu}
            >
              <UserAvatar
                name={user.name}
                email={user.email}
                image={user.image}
              />
              {!collapsed && (
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate text-sm font-medium">
                    {user.name ?? user.email ?? t.settings.account.unknownName}
                  </span>
                  {user.email && user.email !== user.name && (
                    <span className="truncate text-xs text-muted-foreground">
                      {user.email}
                    </span>
                  )}
                </span>
              )}
              {!collapsed && (
                <HugeiconsIcon
                  icon={MoreHorizontalIcon}
                  strokeWidth={2}
                  className="size-4 shrink-0 text-muted-foreground"
                />
              )}
            </Button>
          }
        />
      </div>
    </aside>
  )
}

function SectionHeader({
  label,
  collapsed,
}: {
  label: string
  collapsed: boolean
}) {
  if (collapsed) return null
  return (
    <span className="px-2 pb-1 text-xs font-medium tracking-wider text-muted-foreground uppercase">
      {label}
    </span>
  )
}

function SidebarItem({
  href,
  label,
  icon,
  active,
  collapsed,
}: {
  href: string
  label: string
  icon: typeof Home03Icon
  active: boolean
  collapsed: boolean
}) {
  return (
    <Link
      href={href}
      title={label}
      className={cn(
        "flex items-center gap-2 rounded-md px-2 py-2 text-sm transition-colors",
        active
          ? "bg-sidebar-accent text-sidebar-accent-foreground"
          : "text-sidebar-foreground/85 hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
        collapsed && "lg:justify-center lg:px-0"
      )}
    >
      <HugeiconsIcon icon={icon} strokeWidth={2} className="size-4 shrink-0" />
      {!collapsed && <span className="truncate">{label}</span>}
    </Link>
  )
}
