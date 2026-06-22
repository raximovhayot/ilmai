"use client"

import * as React from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { CheckmarkCircle02Icon, TelegramIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import {
  createLinkCode,
  telegramDeepLink,
  type TelegramLink,
} from "@/lib/telegram"
import { useT } from "@/lib/i18n/provider"

type Props = {
  onBack: () => void
  onNext: () => void
}

export function TelegramStep({ onBack, onNext }: Props) {
  const t = useT()
  const c = t.onboarding.telegram
  const [loading, setLoading] = React.useState(false)
  const [link, setLink] = React.useState<TelegramLink | null>(null)

  const deepLink = telegramDeepLink(link)

  async function connect() {
    if (loading) return
    setLoading(true)
    try {
      const created = await createLinkCode()
      setLink(created)
      const url = telegramDeepLink(created)
      if (url) window.open(url, "_blank", "noopener,noreferrer")
    } catch {
      // ignore — user can retry
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col items-center gap-4 text-center">
        <span className="flex size-16 items-center justify-center rounded-3xl bg-sky-500/10 text-sky-600 dark:text-sky-400">
          <HugeiconsIcon
            icon={TelegramIcon}
            strokeWidth={2}
            className="size-7"
          />
        </span>
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight">{c.title}</h1>
          <p className="mx-auto max-w-md text-balance text-muted-foreground">
            {c.subtitle}
          </p>
        </div>
      </div>

      <ul className="mx-auto flex max-w-sm flex-col gap-2">
        {[c.benefit1, c.benefit2].map((benefit) => (
          <li key={benefit} className="flex items-center gap-2 text-sm">
            <HugeiconsIcon
              icon={CheckmarkCircle02Icon}
              strokeWidth={2}
              className="size-4 shrink-0 text-emerald-600 dark:text-emerald-400"
            />
            <span>{benefit}</span>
          </li>
        ))}
      </ul>

      <div className="flex flex-col gap-2">
        {deepLink ? (
          <Button
            type="button"
            size="lg"
            nativeButton={false}
            render={
              <a href={deepLink} target="_blank" rel="noopener noreferrer">
                {c.openTelegram}
              </a>
            }
          />
        ) : (
          <Button type="button" size="lg" disabled={loading} onClick={connect}>
            {loading && <Spinner className="size-4" />}
            {loading ? c.connecting : c.connect}
          </Button>
        )}
      </div>

      <div className="flex items-center justify-between gap-3 pt-2">
        <Button type="button" variant="ghost" onClick={onBack}>
          {t.onboarding.back}
        </Button>
        <Button type="button" variant="ghost" onClick={onNext}>
          {deepLink ? c.continue : c.skip}
        </Button>
      </div>
    </div>
  )
}
