"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  BookOpen01Icon,
  Clock01Icon,
  Flag03Icon,
  PuzzleIcon,
} from "@hugeicons/core-free-icons"

import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { useT } from "@/lib/i18n/provider"
import { getPlans, type LearningPlan } from "@/lib/plan"
import { getProfile } from "@/lib/profile"
import { getStats, type Stats } from "@/lib/stats"
import { cn } from "@/lib/utils"

export function ProfileView() {
  const t = useT()
  const { status } = useSession()

  const [stats, setStats] = React.useState<Stats | null>(null)
  const [plans, setPlans] = React.useState<LearningPlan[]>([])
  const [dailyMinutes, setDailyMinutes] = React.useState<number | null>(null)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [s, p, profile] = await Promise.all([
          getStats(),
          getPlans(),
          getProfile().catch(() => null),
        ])
        if (!cancelled) {
          setStats(s)
          setPlans(p)
          setDailyMinutes(profile?.dailyStudyMinutes ?? null)
        }
      } catch {
        // ignore
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold tracking-tight md:text-3xl">
          {t.profile.title}
        </h1>
        <p className="text-sm text-muted-foreground">{t.profile.subtitle}</p>
      </header>

      {stats && (
        <section className="flex flex-col gap-3">
          <h2 className="text-sm font-medium tracking-wider text-muted-foreground uppercase">
            {t.profile.statsTitle}
          </h2>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
            <StatTile
              label={t.stats.streakDays}
              value={`${stats.streakDays}`}
              icon={Flag03Icon}
              accent="bg-amber-500/15 text-amber-600 dark:text-amber-400"
            />
            <StatTile
              label={t.stats.sessionsCompleted}
              value={`${stats.sessionsCompleted}`}
              icon={PuzzleIcon}
              accent="bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
            />
            <StatTile
              label={t.stats.weeklyMinutes}
              value={`${stats.weeklyMinutes}`}
              icon={Clock01Icon}
              accent="bg-rose-500/15 text-rose-600 dark:text-rose-400"
            />
          </div>
        </section>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium tracking-wider text-muted-foreground uppercase">
          {t.profile.allGoalsTitle}
        </h2>
        {plans.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t.profile.noGoals}</p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {plans.map((p) => (
              <GoalCard key={p.id} plan={p} dailyMinutes={dailyMinutes} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function GoalCard({
  plan,
  dailyMinutes,
}: {
  plan: LearningPlan
  dailyMinutes: number | null
}) {
  const t = useT()
  const progress =
    plan.daysTotal > 0
      ? Math.min(100, Math.round((plan.daysCompleted / plan.daysTotal) * 100))
      : 0
  const done = plan.status === "COMPLETED" || progress >= 100
  return (
    <Link
      href={`/plan#plan-${plan.id}`}
      className="rounded-4xl outline-none transition-shadow hover:ring-2 hover:ring-primary/40 focus-visible:ring-2 focus-visible:ring-primary/60"
    >
      <Card className="h-full">
      <CardContent className="flex flex-col gap-2.5 p-4">
        <div className="flex items-start gap-2.5">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-base">
            {done ? "🏆" : "🎯"}
          </span>
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="truncate text-sm font-medium">
              {plan.goal ?? t.plan.empty}
            </span>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
              {plan.targetDate && (
                <span className="flex items-center gap-1">
                  <HugeiconsIcon
                    icon={Flag03Icon}
                    strokeWidth={2}
                    className="size-3.5"
                  />
                  {plan.targetDate}
                </span>
              )}
              {dailyMinutes != null && dailyMinutes > 0 && (
                <span className="flex items-center gap-1">
                  <HugeiconsIcon
                    icon={Clock01Icon}
                    strokeWidth={2}
                    className="size-3.5"
                  />
                  {dailyMinutes} {t.onboarding.goal.minutesSuffix}
                </span>
              )}
            </div>
          </div>
        </div>
        {plan.daysTotal > 0 && (
          <div>
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span className="tabular-nums">
                {plan.daysCompleted}/{plan.daysTotal}
              </span>
              <span className="tabular-nums">{progress}%</span>
            </div>
            <Progress value={progress} className="mt-1" />
          </div>
        )}
      </CardContent>
      </Card>
    </Link>
  )
}

function StatTile({
  label,
  value,
  hint,
  icon,
  accent,
}: {
  label: string
  value: string
  hint?: string
  icon: typeof BookOpen01Icon
  accent: string
}) {
  return (
    <Card>
      <CardContent className="flex items-center gap-3 p-4">
        <span
          className={cn(
            "flex size-10 shrink-0 items-center justify-center rounded-xl",
            accent
          )}
        >
          <HugeiconsIcon icon={icon} strokeWidth={2} className="size-5" />
        </span>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-xs text-muted-foreground">
            {label}
          </span>
          <span className="text-xl font-semibold tabular-nums">{value}</span>
          {hint && (
            <span className="truncate text-xs text-muted-foreground">
              {hint}
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
