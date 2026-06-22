"use client"

import { HugeiconsIcon } from "@hugeicons/react"
import { CheckmarkCircle02Icon, Crown02Icon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import { useT } from "@/lib/i18n/provider"

type Props = {
  saving: boolean
  onGoPremium: () => void
  onMaybeLater: () => void
  onBack: () => void
}

export function PremiumStep({
  saving,
  onGoPremium,
  onMaybeLater,
  onBack,
}: Props) {
  const t = useT()
  const c = t.onboarding.premium

  return (
    <div className="space-y-6">
      <div className="flex flex-col items-center gap-4 text-center">
        <span className="flex size-16 items-center justify-center rounded-3xl bg-amber-500/10 text-amber-600 dark:text-amber-400">
          <HugeiconsIcon
            icon={Crown02Icon}
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
        {[c.benefit1, c.benefit2, c.benefit3].map((benefit) => (
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
        <Button type="button" size="lg" disabled={saving} onClick={onGoPremium}>
          {saving && <Spinner className="size-4" />}
          {c.goPremium}
        </Button>
        <Button
          type="button"
          size="lg"
          variant="ghost"
          disabled={saving}
          onClick={onMaybeLater}
        >
          {c.maybeLater}
        </Button>
      </div>

      <div className="flex items-center justify-start pt-2">
        <Button
          type="button"
          variant="ghost"
          disabled={saving}
          onClick={onBack}
        >
          {t.onboarding.back}
        </Button>
      </div>
    </div>
  )
}
