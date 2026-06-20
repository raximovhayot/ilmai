"use client"

import * as React from "react"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  BookOpen01Icon,
  Clock01Icon,
  Flag03Icon,
  PuzzleIcon,
  Target02Icon,
} from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"
import { Spinner } from "@/components/ui/spinner"
import { UserAvatar, type UserSummary } from "@/components/app-shell/user-menu"
import { apiFetch } from "@/lib/api"
import { useT } from "@/lib/i18n/provider"
import { saveOnboarding } from "@/lib/onboarding"
import { getPlan, type LearningPlan } from "@/lib/plan"
import { getStats, type Stats } from "@/lib/stats"
import { cn } from "@/lib/utils"

type Props = {
  user: UserSummary
}

type AuthMe = {
  id: string
  username: string
  status: string
  createdAt: string
}

export function ProfileView({ user }: Props) {
  const t = useT()
  const { status } = useSession()

  const [stats, setStats] = React.useState<Stats | null>(null)
  const [plan, setPlan] = React.useState<LearningPlan | null>(null)
  const [memberSinceIso, setMemberSinceIso] = React.useState<string | null>(
    null
  )
  const [goal, setGoal] = React.useState("")
  const [target, setTarget] = React.useState("")
  const [saving, setSaving] = React.useState(false)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [s, p, me] = await Promise.all([
          getStats(),
          getPlan(),
          apiFetch<AuthMe>("/auth/me").catch(() => null),
        ])
        if (!cancelled) {
          setStats(s)
          setPlan(p)
          setGoal(p?.goal ?? "")
          setTarget(p?.targetDate ?? "")
          setMemberSinceIso(me?.createdAt ?? null)
        }
      } catch {
        // ignore
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  const onSaveGoal = async () => {
    if (status !== "authenticated" || !goal.trim() || !target) return
    setSaving(true)
    try {
      await saveOnboarding({
        goal: goal.trim(),
        targetDate: target,
      })
      toast.success(t.profile.goalSaved)
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold tracking-tight md:text-3xl">
          {t.profile.title}
        </h1>
        <p className="text-sm text-muted-foreground">{t.profile.subtitle}</p>
      </header>

      <Card>
        <CardContent className="flex items-center gap-4 p-5">
          <UserAvatar
            name={user.name}
            email={user.email}
            image={user.image}
            size="md"
          />
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="truncate text-base font-semibold">
              {user.name ?? t.settings.account.unknownName}
            </span>
            {user.email && (
              <span className="truncate text-sm text-muted-foreground">
                {user.email}
              </span>
            )}
            {memberSinceIso && (
              <span className="text-xs text-muted-foreground">
                {t.profile.memberSince} {formatMonth(memberSinceIso)}
              </span>
            )}
          </div>
        </CardContent>
      </Card>

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

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <HugeiconsIcon
              icon={Target02Icon}
              strokeWidth={2}
              className="size-5"
            />
            {t.profile.goalTitle}
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            {t.profile.goalSubtitle}
          </p>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-[1fr_180px_auto]">
          <Field>
            <FieldLabel htmlFor="profile-goal">
              {t.profile.goalLabel}
            </FieldLabel>
            <Input
              id="profile-goal"
              value={goal}
              onChange={(e) => setGoal(e.target.value)}
              placeholder={t.profile.goalPlaceholder}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="profile-target">
              {t.profile.targetLabel}
            </FieldLabel>
            <Input
              id="profile-target"
              type="date"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
            />
          </Field>
          <div className="flex items-end">
            <Button
              onClick={onSaveGoal}
              disabled={saving || !goal.trim() || !target}
              className="w-full md:w-auto"
            >
              {saving ? <Spinner data-icon="inline-start" /> : null}
              {t.profile.saveGoal}
            </Button>
          </div>
        </CardContent>
        {plan && plan.daysTotal > 0 && (
          <CardContent>
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>{t.plan.progress}</span>
              <span className="tabular-nums">
                {plan.daysCompleted}/{plan.daysTotal}
              </span>
            </div>
            <Progress
              value={Math.min(
                100,
                Math.round((plan.daysCompleted / plan.daysTotal) * 100)
              )}
              className="mt-1"
            />
          </CardContent>
        )}
      </Card>
    </div>
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

function formatMonth(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: "numeric",
      month: "long",
    })
  } catch {
    return iso
  }
}
