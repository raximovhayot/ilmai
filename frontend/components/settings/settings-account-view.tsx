"use client"

import * as React from "react"
import {
  Mail01Icon,
  UserCircleIcon,
  UserIcon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { useLanguage, useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

import {
  SettingsPageShell,
  SettingsField,
  formatDate,
  getInitials,
} from "./settings-shared"

type AccountStatus = "ACTIVE" | "DISABLED" | string

export type SettingsAccountViewProps = {
  sessionUser: {
    name?: string | null
    email?: string | null
    image?: string | null
  }
  account: {
    id: string
    username: string
    status: AccountStatus
    createdAt: string
  } | null
}

export function SettingsAccountView({
  sessionUser,
  account,
}: SettingsAccountViewProps) {
  const t = useT()
  const { lang } = useLanguage()
  const initials = getInitials(sessionUser.name, sessionUser.email)
  const [failed, setFailed] = React.useState(false)
  const showImage = !!sessionUser.image && !failed

  const displayName =
    (sessionUser.name && sessionUser.name.trim()) ||
    (account?.username ? account.username.split("@")[0] : null) ||
    t.settings.account.unknownName
  const email = sessionUser.email ?? account?.username ?? ""
  const memberSince = formatDate(account?.createdAt, lang)
  const statusKey: AccountStatus = account?.status ?? "ACTIVE"
  const statusLabel =
    statusKey === "ACTIVE"
      ? t.settings.account.statusActive
      : statusKey === "DISABLED"
        ? t.settings.account.statusDisabled
        : String(statusKey)

  return (
    <SettingsPageShell
      title={t.settings.account.title}
      subtitle={t.settings.account.subtitle}
      icon={UserCircleIcon}
    >
      <Card>
        <CardHeader className="flex flex-row items-center gap-4">
          <span
            className={cn(
              "relative flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary/10 text-base font-semibold text-primary",
              "ring-1 ring-foreground/10"
            )}
            aria-hidden={!showImage}
          >
            {showImage ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={sessionUser.image!}
                alt={sessionUser.name ?? sessionUser.email ?? ""}
                referrerPolicy="no-referrer"
                onError={() => setFailed(true)}
                className="h-full w-full object-cover"
              />
            ) : (
              initials
            )}
          </span>
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <CardTitle className="truncate text-base">{displayName}</CardTitle>
            <CardDescription className="truncate">{email}</CardDescription>
          </div>
          <Badge
            variant="outline"
            className={cn(
              "shrink-0",
              statusKey === "ACTIVE" &&
                "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400"
            )}
          >
            <span className="flex items-center gap-1.5">
              <span
                className={cn(
                  "size-1.5 rounded-full",
                  statusKey === "ACTIVE"
                    ? "bg-emerald-500"
                    : "bg-muted-foreground"
                )}
                aria-hidden
              />
              {statusLabel}
            </span>
          </Badge>
        </CardHeader>
        <Separator />
        <CardContent className="grid gap-5 sm:grid-cols-2">
          <SettingsField
            icon={UserIcon}
            label={t.settings.account.name}
            value={displayName}
          />
          <SettingsField
            icon={Mail01Icon}
            label={t.settings.account.email}
            value={email}
          />
          <SettingsField
            icon={UserCircleIcon}
            label={t.settings.account.accountId}
            value={account?.id ?? ""}
            mono
          />
          <SettingsField
            label={t.settings.account.memberSince}
            value={memberSince}
          />
        </CardContent>
      </Card>
    </SettingsPageShell>
  )
}
