"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowRight01Icon,
  CheckmarkCircle02Icon,
  TelegramIcon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { useLanguage, useT } from "@/lib/i18n/provider"
import { getTelegram, isLinked, type TelegramLink } from "@/lib/telegram"

import {
  SettingsPageShell,
  SettingsRow,
  SettingsSummaryStat,
  formatDate,
} from "./settings-shared"

export function SettingsNotificationsView() {
  const t = useT()
  const { lang } = useLanguage()
  const { status } = useSession()

  const [telegram, setTelegram] = React.useState<TelegramLink | null>(null)
  const [loading, setLoading] = React.useState(true)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const tg = await getTelegram()
        if (!cancelled) setTelegram(tg)
      } catch {
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  const connected = isLinked(telegram)

  return (
    <SettingsPageShell
      title={t.settings.telegram.title}
      subtitle={t.settings.telegram.subtitle}
      icon={TelegramIcon}
    >
      <Card>
        <CardContent className="flex flex-col p-0">
          <SettingsRow
            icon={TelegramIcon}
            label={t.settings.telegram.telegramLabel}
            description={t.settings.telegram.telegramDescription}
          >
            {loading ? (
              <Skeleton className="h-8 w-28" />
            ) : connected ? (
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
            )}
          </SettingsRow>
          {connected && telegram && (
            <>
              <Separator />
              <div className="grid gap-4 px-4 py-4 sm:grid-cols-2 sm:px-6">
                <SettingsSummaryStat
                  label={t.telegram.linkedAs}
                  value={
                    telegram.telegramUsername
                      ? `@${telegram.telegramUsername}`
                      : telegram.chatId != null
                        ? String(telegram.chatId)
                        : "—"
                  }
                />
                <SettingsSummaryStat
                  label={t.telegram.linkedOn}
                  value={
                    telegram.linkedAt
                      ? formatDate(telegram.linkedAt, lang)
                      : "—"
                  }
                />
              </div>
            </>
          )}
        </CardContent>
        <CardFooter className="justify-end">
          <Button
            size="sm"
            variant="outline"
            nativeButton={false}
            render={<Link href="/telegram" />}
          >
            <HugeiconsIcon
              icon={ArrowRight01Icon}
              strokeWidth={2}
              data-icon="inline-end"
            />
            {t.settings.telegram.manage}
          </Button>
        </CardFooter>
      </Card>
    </SettingsPageShell>
  )
}
