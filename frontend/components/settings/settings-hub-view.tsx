"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  AlertCircleIcon,
  ArrowRight01Icon,
  CheckmarkCircle02Icon,
  Crown02Icon,
  TelegramIcon,
  UserCircleIcon,
  UserGroup03Icon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useT } from "@/lib/i18n/provider"
import { getPremium, type PremiumStatus } from "@/lib/premium"
import { getTelegram, isLinked, type TelegramLink } from "@/lib/telegram"
import { cn } from "@/lib/utils"
import { getInitials } from "./settings-shared"

export type SettingsHubViewProps = {
  sessionUser: {
    name?: string | null
    email?: string | null
    image?: string | null
  }
}

export function SettingsHubView({ sessionUser }: SettingsHubViewProps) {
  const t = useT()
  const { status } = useSession()

  const [premium, setPremium] = React.useState<PremiumStatus | null>(null)
  const [telegram, setTelegram] = React.useState<TelegramLink | null>(null)
  const [loading, setLoading] = React.useState(true)
  const [imageFailed, setImageFailed] = React.useState(false)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [p, tg] = await Promise.all([getPremium(), getTelegram()])
        if (!cancelled) {
          setPremium(p)
          setTelegram(tg)
        }
      } catch {
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  const displayName =
    (sessionUser.name && sessionUser.name.trim()) ||
    (sessionUser.email && sessionUser.email.split("@")[0]) ||
    t.settings.account.unknownName

  const initials = getInitials(sessionUser.name, sessionUser.email)
  const showImage = !!sessionUser.image && !imageFailed

  const sections = [
    {
      href: "/settings/account",
      icon: UserCircleIcon,
      title: t.settings.account.title,
      description: t.settings.hub.accountDescription,
      badge: (
        <Badge variant="outline" className="max-w-44 truncate">
          {displayName}
        </Badge>
      ),
    },
    {
      href: "/settings/rooms",
      icon: UserGroup03Icon,
      title: t.settings.rooms.title,
      description: t.settings.hub.roomsDescription,
    },
    {
      href: "/settings/telegram",
      icon: TelegramIcon,
      title: t.settings.telegram.title,
      description: t.settings.hub.telegramDescription,
      badge: loading ? (
        <Skeleton className="h-6 w-28" />
      ) : isLinked(telegram) ? (
        <Badge
          variant="outline"
          className="border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400"
        >
          <span className="flex items-center gap-1.5">
            <HugeiconsIcon
              icon={CheckmarkCircle02Icon}
              strokeWidth={2}
              className="size-3.5"
            />
            {t.settings.telegram.statusConnected}
          </span>
        </Badge>
      ) : (
        <Badge variant="outline">
          {t.settings.telegram.statusNotConnected}
        </Badge>
      ),
    },
    {
      href: "/settings/subscription",
      icon: Crown02Icon,
      title: t.settings.subscription.title,
      description: t.settings.hub.subscriptionDescription,
      badge: loading ? (
        <Skeleton className="h-6 w-24" />
      ) : (
        <Badge
          variant="outline"
          className={cn(
            premium?.tier === "PREMIUM" &&
              "border-amber-400/40 bg-amber-400/10 text-amber-700 dark:text-amber-400"
          )}
        >
          <span className="flex items-center gap-1.5">
            {premium?.tier === "PREMIUM" && (
              <HugeiconsIcon
                icon={Crown02Icon}
                strokeWidth={2}
                className="size-3.5"
              />
            )}
            {premium?.tier === "PREMIUM"
              ? t.settings.subscription.tierPremium
              : t.settings.subscription.tierFree}
          </span>
        </Badge>
      ),
    },
    {
      href: "/settings/data-privacy",
      icon: AlertCircleIcon,
      title: t.settings.danger.title,
      description: t.settings.hub.dataPrivacyDescription,
    },
  ]

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <header className="flex flex-col gap-1.5">
        <h1 className="font-heading text-2xl font-semibold tracking-tight">
          {t.settings.title}
        </h1>
        <p className="text-sm text-muted-foreground">
          {t.settings.hub.subtitle}
        </p>
      </header>

      {/* User profile summary banner */}
      <Card className="overflow-hidden border-border/80 shadow-sm">
        <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
          <div className="flex items-center gap-4">
            <span
              className={cn(
                "relative flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary/10 text-base font-semibold text-primary",
                "ring-1 ring-foreground/10"
              )}
            >
              {showImage ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={sessionUser.image!}
                  alt={displayName}
                  referrerPolicy="no-referrer"
                  onError={() => setImageFailed(true)}
                  className="h-full w-full object-cover"
                />
              ) : (
                initials
              )}
            </span>
            <div className="flex min-w-0 flex-col gap-0.5">
              <span className="truncate text-base font-semibold text-foreground">
                {displayName}
              </span>
              <span className="truncate text-xs text-muted-foreground">
                {sessionUser.email || t.settings.account.unknownName}
              </span>
            </div>
          </div>
          {!loading && premium?.tier === "PREMIUM" && (
            <Badge className="w-fit gap-1 border-amber-400/40 bg-amber-400/10 px-3 py-1 text-xs text-amber-700 hover:bg-amber-400/20 dark:text-amber-400">
              <HugeiconsIcon
                icon={Crown02Icon}
                strokeWidth={2.5}
                className="size-3.5"
              />
              {t.settings.subscription.tierPremium}
            </Badge>
          )}
        </CardContent>
      </Card>

      {/* Unified options card group */}
      <Card className="overflow-hidden border-border/80 shadow-sm">
        <div className="divide-y divide-border/50">
          {sections.map((s) => (
            <SectionLink key={s.href} {...s} />
          ))}
        </div>
      </Card>
    </div>
  )
}

function SectionLink({
  href,
  icon,
  title,
  description,
  badge,
}: {
  href: string
  icon: typeof UserCircleIcon
  title: string
  description: string
  badge?: React.ReactNode
}) {
  return (
    <Link
      href={href}
      className="group block focus-visible:bg-accent/40 focus-visible:outline-none"
    >
      <div className="flex items-center gap-4 px-5 py-4.5 transition-colors group-hover:bg-accent/40">
        <span
          className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary transition-colors group-hover:bg-primary/15"
          aria-hidden
        >
          <HugeiconsIcon icon={icon} strokeWidth={2} className="size-5" />
        </span>
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="text-sm font-medium text-foreground transition-colors group-hover:text-primary">
            {title}
          </span>
          <span className="line-clamp-1 text-xs text-muted-foreground">
            {description}
          </span>
        </div>
        {badge && (
          <div className="hidden shrink-0 items-center gap-3 sm:flex">
            {badge}
          </div>
        )}
        <HugeiconsIcon
          icon={ArrowRight01Icon}
          strokeWidth={2}
          className="size-4 shrink-0 text-muted-foreground/80 transition-transform group-hover:translate-x-0.5 group-hover:text-foreground rtl:rotate-180"
        />
      </div>
    </Link>
  )
}
