"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Crown02Icon, ArrowRight01Icon } from "@hugeicons/core-free-icons"

import { getPremium, type PremiumStatus } from "@/lib/premium"
import { useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

type Props = {
  kind: "quizzes" | "uploads"
}

export function QuotaStrip({ kind }: Props) {
  const t = useT()
  const { status } = useSession()
  const [premium, setPremium] = React.useState<PremiumStatus | null>(null)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const p = await getPremium()
        if (!cancelled) setPremium(p)
      } catch {
        // ignore
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  if (!premium || premium.tier !== "FREE") return null

  const message =
    kind === "quizzes" ? t.premium.quotaQuizzes : t.premium.quotaUploads

  return (
    <Link
      href="/premium"
      className={cn(
        "flex items-center gap-3 rounded-xl border px-3 py-2 text-sm transition-colors",
        "border-border bg-card text-muted-foreground hover:bg-accent/40 hover:text-foreground"
      )}
    >
      <HugeiconsIcon
        icon={Crown02Icon}
        strokeWidth={2}
        className="size-4 shrink-0 text-amber-500"
      />
      <span className="min-w-0 flex-1 truncate">{message}</span>
      <span className="shrink-0 text-xs font-medium">{t.nav.upgrade}</span>
      <HugeiconsIcon
        icon={ArrowRight01Icon}
        strokeWidth={2}
        className="size-4 shrink-0 rtl:rotate-180"
      />
    </Link>
  )
}
