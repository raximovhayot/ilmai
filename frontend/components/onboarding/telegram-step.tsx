"use client"

import * as React from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { CheckmarkCircle02Icon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import {
  createLinkCode,
  getTelegram,
  isLinked,
  telegramDeepLink,
  type TelegramLink,
} from "@/lib/telegram"
import { useT } from "@/lib/i18n/provider"

type Props = {
  onBack: () => void
  onNext: () => void
}

const POLL_INTERVAL_MS = 2500

export function TelegramStep({ onBack, onNext }: Props) {
  const t = useT()
  const c = t.onboarding.telegram
  const [checking, setChecking] = React.useState(true)
  const [loading, setLoading] = React.useState(false)
  const [link, setLink] = React.useState<TelegramLink | null>(null)
  const cancelledRef = React.useRef(false)

  const linked = isLinked(link)
  const deepLink = telegramDeepLink(link)

  React.useEffect(() => {
    cancelledRef.current = false
    void (async () => {
      try {
        const current = await getTelegram()
        if (!cancelledRef.current) setLink(current)
      } catch {
        // ignore — treat as not linked
      } finally {
        if (!cancelledRef.current) setChecking(false)
      }
    })()
    return () => {
      cancelledRef.current = true
    }
  }, [])

  React.useEffect(() => {
    if (linked || !deepLink) return
    const timer = setInterval(() => {
      void (async () => {
        try {
          const current = await getTelegram()
          if (!cancelledRef.current && current) setLink(current)
        } catch {
          // ignore — keep polling
        }
      })()
    }, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [linked, deepLink])

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
        {linked ? (
          <span className="text-5xl motion-safe:animate-bounce" aria-hidden>
            🎉
          </span>
        ) : (
          <span className="text-5xl motion-safe:animate-pulse" aria-hidden>
            📲
          </span>
        )}
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight">
            {linked ? c.connected : c.title}
          </h1>
          <p className="mx-auto max-w-md text-balance text-muted-foreground">
            {linked ? c.connectedHint : c.subtitle}
          </p>
        </div>
      </div>

      {!linked && (
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
      )}

      {linked ? (
        <div className="flex items-center justify-center gap-2 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm font-medium text-emerald-700 dark:text-emerald-300">
          <HugeiconsIcon
            icon={CheckmarkCircle02Icon}
            strokeWidth={2}
            className="size-5"
          />
          <span>{c.connected}</span>
        </div>
      ) : checking ? (
        <div className="flex items-center justify-center gap-2 text-sm text-muted-foreground">
          <Spinner className="size-4" />
          <span>{c.checking}</span>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {deepLink ? (
            <>
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
              <div className="flex items-center justify-center gap-2 text-xs text-muted-foreground">
                <Spinner className="size-3" />
                <span>{c.checking}</span>
              </div>
            </>
          ) : (
            <Button
              type="button"
              size="lg"
              disabled={loading}
              onClick={connect}
            >
              {loading && <Spinner className="size-4" />}
              {loading ? c.connecting : c.connect}
            </Button>
          )}
        </div>
      )}

      <div className="flex items-center justify-between gap-3 pt-2">
        <Button type="button" variant="ghost" onClick={onBack}>
          {t.onboarding.back}
        </Button>
        <Button
          type="button"
          variant={linked ? "default" : "ghost"}
          onClick={onNext}
        >
          {linked || deepLink ? c.continue : c.skip}
        </Button>
      </div>
    </div>
  )
}
