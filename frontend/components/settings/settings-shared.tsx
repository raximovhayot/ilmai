"use client"

import * as React from "react"
import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowLeft01Icon,
  Settings01Icon,
  UserIcon,
} from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

export function getInitials(
  name: string | null | undefined,
  email: string | null | undefined
): string {
  const source = (name && name.trim()) || (email && email.trim()) || ""
  if (!source) return "??"
  const parts = source.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) {
    return (parts[0]![0]! + parts[1]![0]!).toUpperCase()
  }
  return (parts[0] ?? "").slice(0, 2).toUpperCase() || "??"
}

export function formatDate(value: string | undefined, lang: string): string {
  if (!value) return ""
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return ""
  try {
    return new Intl.DateTimeFormat(lang, { dateStyle: "long" }).format(parsed)
  } catch {
    return parsed.toDateString()
  }
}

export function SettingsPageShell({
  title,
  subtitle,
  icon = Settings01Icon,
  children,
}: {
  title: string
  subtitle?: string
  icon?: typeof Settings01Icon
  children: React.ReactNode
}) {
  const t = useT()
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <Button
        size="sm"
        variant="ghost"
        nativeButton={false}
        render={<Link href="/settings" />}
        className="-ms-2 w-fit text-muted-foreground hover:text-foreground"
      >
        <HugeiconsIcon
          icon={ArrowLeft01Icon}
          strokeWidth={2}
          data-icon="inline-start"
        />
        {t.settings.back}
      </Button>
      <header className="flex items-start gap-3">
        <span className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <HugeiconsIcon icon={icon} strokeWidth={2} className="size-5" />
        </span>
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="font-heading text-2xl font-semibold tracking-tight">
            {title}
          </h1>
          {subtitle && (
            <p className="text-sm text-muted-foreground">{subtitle}</p>
          )}
        </div>
      </header>
      <div className="flex flex-col gap-6">{children}</div>
    </div>
  )
}

export function SettingsRow({
  icon,
  label,
  description,
  children,
}: {
  icon?: typeof Settings01Icon
  label: string
  description?: string
  children?: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-6 sm:px-6">
      <div className="flex min-w-0 items-start gap-3">
        {icon && (
          <span
            className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground"
            aria-hidden
          >
            <HugeiconsIcon icon={icon} strokeWidth={2} className="size-4" />
          </span>
        )}
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="text-sm font-medium text-foreground">{label}</span>
          {description && (
            <span className="text-xs text-muted-foreground">{description}</span>
          )}
        </div>
      </div>
      {children && (
        <div className="flex shrink-0 items-center gap-2 self-start sm:self-auto">
          {children}
        </div>
      )}
    </div>
  )
}

export function SettingsField({
  icon,
  label,
  value,
  mono = false,
}: {
  icon?: typeof UserIcon
  label: string
  value: React.ReactNode
  mono?: boolean
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="flex items-center gap-1.5 text-xs font-medium tracking-wide text-muted-foreground uppercase">
        {icon && (
          <HugeiconsIcon icon={icon} strokeWidth={2} className="size-3.5" />
        )}
        {label}
      </span>
      <span
        className={cn(
          "min-h-6 text-sm break-words text-foreground",
          mono && "font-mono text-xs"
        )}
      >
        {value || "—"}
      </span>
    </div>
  )
}

export function SettingsSummaryStat({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-border bg-muted/30 px-3 py-2.5">
      <span className="text-xs tracking-wide text-muted-foreground uppercase">
        {label}
      </span>
      <span className="text-sm font-semibold text-foreground">{value}</span>
    </div>
  )
}
