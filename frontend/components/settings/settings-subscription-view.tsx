"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Crown02Icon } from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { useLanguage, useT } from "@/lib/i18n/provider"
import { getPremium, type PremiumStatus } from "@/lib/premium"
import { cn } from "@/lib/utils"

import {
  SettingsPageShell,
  SettingsRow,
  SettingsSummaryStat,
  formatDate,
} from "./settings-shared"

export function SettingsSubscriptionView() {
  const t = useT()
  const { lang } = useLanguage()
  const { status } = useSession()

  const [premium, setPremium] = React.useState<PremiumStatus | null>(null)
  const [loading, setLoading] = React.useState(true)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const p = await getPremium()
        if (!cancelled) setPremium(p)
      } catch {
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  const isPremium = premium?.tier === "PREMIUM"
  const periodEnd = premium?.subscription?.currentPeriodEnd ?? null

  return (
    <SettingsPageShell
      title={t.settings.subscription.title}
      subtitle={t.settings.subscription.subtitle}
      icon={Crown02Icon}
    >
      <Card>
        <CardContent className="flex flex-col p-0">
          <SettingsRow
            icon={Crown02Icon}
            label={t.settings.subscription.planLabel}
            description={
              isPremium
                ? t.settings.subscription.premiumDescription
                : t.settings.subscription.freeDescription
            }
          >
            {loading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <Badge
                variant="outline"
                className={cn(
                  isPremium &&
                    "border-amber-400/40 bg-amber-400/10 text-amber-700 dark:text-amber-400"
                )}
              >
                <span className="flex items-center gap-1.5">
                  {isPremium && (
                    <HugeiconsIcon
                      icon={Crown02Icon}
                      strokeWidth={2}
                      className="size-3.5"
                    />
                  )}
                  {isPremium
                    ? t.settings.subscription.tierPremium
                    : t.settings.subscription.tierFree}
                </span>
              </Badge>
            )}
          </SettingsRow>
          {!loading && isPremium && periodEnd && (
            <>
              <Separator />
              <div className="px-4 py-4 sm:px-6">
                <SettingsSummaryStat
                  label={t.settings.subscription.renewsOn}
                  value={formatDate(periodEnd, lang)}
                />
              </div>
            </>
          )}
        </CardContent>
        <CardFooter className="justify-end gap-2">
          <Button
            size="sm"
            variant={isPremium ? "outline" : "default"}
            nativeButton={false}
            render={<Link href="/premium" />}
          >
            <HugeiconsIcon
              icon={Crown02Icon}
              strokeWidth={2}
              data-icon="inline-start"
            />
            {isPremium
              ? t.settings.subscription.managePlan
              : t.settings.subscription.upgrade}
          </Button>
        </CardFooter>
      </Card>
    </SettingsPageShell>
  )
}
