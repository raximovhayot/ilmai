"use client"

import { HugeiconsIcon } from "@hugeicons/react"
import { CheckmarkCircle02Icon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import { useT } from "@/lib/i18n/provider"

type Props = {
  saving: boolean
  onStartChat: () => void
  onExplore: () => void
}

export function FinishStep({ saving, onStartChat, onExplore }: Props) {
  const t = useT()
  const c = t.onboarding.finish

  return (
    <div className="flex flex-col items-center gap-7 text-center">
      <span className="flex size-16 items-center justify-center rounded-3xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
        <HugeiconsIcon
          icon={CheckmarkCircle02Icon}
          strokeWidth={2}
          className="size-7"
        />
      </span>
      <div className="space-y-3">
        <h1 className="text-2xl font-semibold tracking-tight">{c.title}</h1>
        <p className="mx-auto max-w-md text-balance text-muted-foreground">
          {c.subtitle}
        </p>
      </div>

      <div className="w-full space-y-3">
        <p className="text-sm font-medium text-muted-foreground">
          {c.askPrompt}
        </p>
        <div className="flex flex-wrap justify-center gap-2">
          {c.starters.map((starter) => (
            <Button
              key={starter}
              type="button"
              variant="outline"
              size="sm"
              disabled={saving}
              onClick={onStartChat}
            >
              {starter}
            </Button>
          ))}
        </div>
      </div>

      <div className="flex w-full flex-col gap-2 sm:flex-row sm:justify-center">
        <Button type="button" size="lg" disabled={saving} onClick={onStartChat}>
          {saving && <Spinner className="size-4" />}
          {c.startChat}
        </Button>
        <Button
          type="button"
          size="lg"
          variant="ghost"
          disabled={saving}
          onClick={onExplore}
        >
          {c.explore}
        </Button>
      </div>
    </div>
  )
}
