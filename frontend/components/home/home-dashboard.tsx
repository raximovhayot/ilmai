"use client"

import * as React from "react"
import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  BookOpen01Icon,
  Calendar03Icon,
  CheckmarkCircle02Icon,
  Compass01Icon,
  FireIcon,
  PlusSignIcon,
  PuzzleIcon,
  RoadIcon,
  Target02Icon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import type { Goal } from "@/lib/goals"
import type { Lang } from "@/lib/i18n/dictionary"
import { useLanguage, useT } from "@/lib/i18n/provider"
import type { LearningPlan, PlanStep } from "@/lib/plan"
import type { Stats } from "@/lib/stats"
import type { TopicResponse } from "@/lib/topics"
import { cn } from "@/lib/utils"

type Props = {
  stats: Stats | null
  goals: Goal[]
  plan: LearningPlan | null
  topics: TopicResponse[]
  greetingName: string | null
  loading?: boolean
  onCompleteStep?: (dayIndex: number) => void
}

export function HomeDashboard({
  stats,
  goals,
  plan,
  topics,
  greetingName,
  loading = false,
  onCompleteStep,
}: Props) {
  const { lang } = useLanguage()

  const streakDays = stats?.streakDays ?? 0
  const weeklyMinutes = stats?.weeklyMinutes ?? 0

  const aggregateDaysTotal = goals.reduce(
    (acc, g) => acc + (g.daysTotal ?? 0),
    0
  )
  const aggregateDaysCompleted = goals.reduce(
    (acc, g) => acc + (g.daysCompleted ?? 0),
    0
  )
  const aggregatePercent =
    aggregateDaysTotal > 0
      ? Math.min(
          100,
          Math.round((aggregateDaysCompleted / aggregateDaysTotal) * 100)
        )
      : 0

  const today = new Date().toISOString().slice(0, 10)
  const todayStep =
    plan?.steps.find((s) => s.scheduledDate === today) ??
    plan?.steps.find((s) => !s.done) ??
    plan?.steps[0] ??
    null
  const topicNameById = React.useMemo(
    () => new Map(topics.map((tp) => [tp.id, tp.name])),
    [topics]
  )

  return (
    <div className="flex flex-col gap-4 sm:gap-5">
      <GreetingRow
        name={greetingName}
        streakDays={streakDays}
        weeklyMinutes={weeklyMinutes}
        loading={loading}
      />

      <TodayCard
        plan={plan}
        step={todayStep}
        topicNameById={topicNameById}
        loading={loading}
        onCompleteStep={onCompleteStep}
      />

      <PathsCard
        goals={goals}
        lang={lang}
        loading={loading}
        aggregatePercent={aggregatePercent}
        aggregateDaysCompleted={aggregateDaysCompleted}
        aggregateDaysTotal={aggregateDaysTotal}
      />
    </div>
  )
}

function GreetingRow({
  name,
  streakDays,
  weeklyMinutes,
  loading,
}: {
  name: string | null
  streakDays: number
  weeklyMinutes: number
  loading: boolean
}) {
  const t = useT()
  const display = name?.split(/\s+/)[0] || t.settings.account.unknownName
  const hour = new Date().getHours()
  const template =
    hour < 12
      ? t.home.greetingMorning
      : hour < 18
        ? t.home.greetingAfternoon
        : t.home.greetingEvening

  const dayWord = streakDays === 1 ? t.home.streak.day : t.home.streak.days

  return (
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div className="flex min-w-0 flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold tracking-tight sm:text-3xl">
          {template.replace("{name}", display)}
        </h1>
        <p className="text-sm text-muted-foreground">{t.brand.tagline}</p>
      </div>

      {loading ? (
        <Skeleton className="h-8 w-24 rounded-full" />
      ) : (
        <StreakChip
          streakDays={streakDays}
          dayWord={dayWord}
          weeklyMinutes={weeklyMinutes}
          weeklyMinutesLabel={t.home.streak.weeklyMinutes}
        />
      )}
    </header>
  )
}

function StreakChip({
  streakDays,
  dayWord,
  weeklyMinutes,
  weeklyMinutesLabel,
}: {
  streakDays: number
  dayWord: string
  weeklyMinutes: number
  weeklyMinutesLabel: string
}) {
  const isActive = streakDays > 0
  return (
    <Link
      href="/profile"
      className={cn(
        "group inline-flex shrink-0 items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition-colors",
        isActive
          ? "border-amber-500/30 bg-amber-500/10 text-amber-700 hover:bg-amber-500/15 dark:text-amber-300"
          : "border-border bg-muted/50 text-muted-foreground hover:bg-muted"
      )}
      aria-label={`${streakDays} ${dayWord}`}
    >
      <HugeiconsIcon
        icon={FireIcon}
        strokeWidth={2}
        className={cn(
          "size-4",
          isActive
            ? "text-amber-500 drop-shadow-[0_1px_3px_rgba(245,158,11,0.5)]"
            : "opacity-60"
        )}
      />
      <span className="font-heading text-base leading-none font-semibold tabular-nums">
        {streakDays}
      </span>
      <span className="text-xs font-medium opacity-80">{dayWord}</span>
      <span aria-hidden className="mx-0.5 h-3 w-px bg-current opacity-25" />
      <span className="text-xs tabular-nums opacity-70">
        {weeklyMinutesLabel.replace("{minutes}", String(weeklyMinutes))}
      </span>
    </Link>
  )
}

function TodayCard({
  plan,
  step,
  topicNameById,
  loading,
  onCompleteStep,
}: {
  plan: LearningPlan | null
  step: PlanStep | null
  topicNameById: Map<string, string>
  loading: boolean
  onCompleteStep?: (dayIndex: number) => void
}) {
  const t = useT()
  const { lang } = useLanguage()

  const dateLabel = step?.scheduledDate
    ? formatTodayDate(step.scheduledDate, lang)
    : ""
  const itemsTotal = plan?.daysTotal ?? 0
  const itemsDone = plan?.daysCompleted ?? 0
  const dayPercent =
    itemsTotal > 0 ? Math.round((itemsDone / itemsTotal) * 100) : 0
  const allDone = !!step?.done

  return (
    <Card
      className={cn(
        "relative overflow-hidden",
        allDone &&
          "border-emerald-500/30 bg-gradient-to-br from-emerald-500/10 via-emerald-500/5 to-transparent dark:from-emerald-500/15"
      )}
    >
      <CardHeader>
        <CardTitle className="inline-flex items-center gap-2">
          <HugeiconsIcon icon={RoadIcon} strokeWidth={2} className="size-4" />
          {t.home.today.title}
        </CardTitle>
        <CardDescription>
          {step ? dateLabel : t.home.today.subtitle}
        </CardDescription>
        {step ? (
          <CardAction>
            <Button
              variant="ghost"
              size="sm"
              nativeButton={false}
              className="hidden sm:inline-flex"
              render={<Link href="/plan">{t.home.today.viewPlan}</Link>}
            />
          </CardAction>
        ) : null}
      </CardHeader>

      <CardContent className="flex flex-col gap-3">
        {loading ? (
          <TodaySkeleton />
        ) : !plan || !step ? (
          <Empty>
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <HugeiconsIcon icon={RoadIcon} strokeWidth={2} />
              </EmptyMedia>
              <EmptyTitle>{t.home.today.emptyTitle}</EmptyTitle>
              <EmptyDescription>
                {t.home.today.emptyDescription}
              </EmptyDescription>
            </EmptyHeader>
            <EmptyContent>
              <Button
                nativeButton={false}
                render={
                  <Link href="/profile">
                    <HugeiconsIcon
                      icon={PlusSignIcon}
                      strokeWidth={2}
                      data-icon="inline-start"
                    />
                    {t.home.today.createPlan}
                  </Link>
                }
              />
            </EmptyContent>
          </Empty>
        ) : itemsTotal === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t.home.today.restDay}
          </p>
        ) : (
          <>
            <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
              <div className="flex items-baseline gap-2">
                <span className="font-heading text-2xl font-semibold tabular-nums sm:text-3xl">
                  {itemsDone}
                  <span className="text-base text-muted-foreground sm:text-xl">
                    /{itemsTotal}
                  </span>
                </span>
                <span className="text-sm text-muted-foreground">
                  {t.home.today.doneLabel}
                </span>
              </div>
              <span className="text-xs text-muted-foreground tabular-nums sm:text-sm">
                {allDone ? t.home.today.allDone : ""}
              </span>
            </div>
            <Progress
              value={dayPercent}
              className={cn("h-1.5", allDone && "[&>div]:bg-emerald-500")}
            />

            <ul className="flex flex-col gap-1.5">
              <PlanItemRow
                step={step}
                topicName={
                  step.materials[0]?.topicId
                    ? (topicNameById.get(step.materials[0].topicId) ?? "—")
                    : "—"
                }
                onComplete={
                  onCompleteStep
                    ? () => onCompleteStep(step.dayIndex)
                    : undefined
                }
              />
            </ul>

            {plan.goal ? (
              <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <HugeiconsIcon
                  icon={Compass01Icon}
                  strokeWidth={2}
                  className="size-3.5"
                />
                <span className="truncate">
                  {t.home.today.pathLine.replace("{goal}", plan.goal)}
                </span>
              </p>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function TodaySkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-baseline justify-between gap-3">
        <Skeleton className="h-8 w-20" />
        <Skeleton className="h-3 w-24" />
      </div>
      <Skeleton className="h-1.5 w-full" />
      <div className="flex flex-col gap-1.5">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-12 w-full rounded-lg" />
        ))}
      </div>
    </div>
  )
}

function PlanItemRow({
  step,
  topicName,
  onComplete,
}: {
  step: PlanStep
  topicName: string
  onComplete?: () => void
}) {
  const t = useT()
  const action = step.activity
  const icon =
    action === "READ"
      ? BookOpen01Icon
      : action === "QUIZ"
        ? PuzzleIcon
        : RoadIcon
  const actionLabel =
    action === "READ"
      ? t.plan.actionRead
      : action === "QUIZ"
        ? t.plan.actionQuiz
        : t.plan.actionReview
  const accentClass =
    action === "READ"
      ? "bg-primary/10 text-primary"
      : action === "QUIZ"
        ? "bg-amber-500/15 text-amber-600 dark:text-amber-400"
        : "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
  const materialTitle = step.materials[0]?.title ?? null

  return (
    <li
      className={cn(
        "flex items-center gap-3 rounded-lg border border-border bg-card p-2.5 text-sm transition-opacity",
        step.done && "opacity-60"
      )}
    >
      <span
        className={cn(
          "flex size-7 shrink-0 items-center justify-center rounded-md",
          accentClass
        )}
        aria-hidden
      >
        <HugeiconsIcon icon={icon} strokeWidth={2} className="size-4" />
      </span>
      <span
        className={cn(
          "flex min-w-0 flex-1 flex-col",
          step.done && "line-through"
        )}
      >
        <span className="truncate font-medium">{step.title}</span>
        <span className="truncate text-xs text-muted-foreground">
          {materialTitle ?? actionLabel} · {topicName}
        </span>
      </span>
      {step.done ? (
        <span className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-emerald-600 dark:text-emerald-400">
          <HugeiconsIcon
            icon={CheckmarkCircle02Icon}
            strokeWidth={2}
            className="size-4"
          />
          {t.plan.completed}
        </span>
      ) : onComplete ? (
        <Button
          size="sm"
          variant="outline"
          onClick={onComplete}
          className="shrink-0"
        >
          {t.plan.markDone}
        </Button>
      ) : null}
    </li>
  )
}

function PathsCard({
  goals,
  lang,
  loading,
  aggregatePercent,
  aggregateDaysCompleted,
  aggregateDaysTotal,
}: {
  goals: Goal[]
  lang: Lang
  loading: boolean
  aggregatePercent: number
  aggregateDaysCompleted: number
  aggregateDaysTotal: number
}) {
  const t = useT()

  const orderedGoals = React.useMemo(() => {
    const rank = (g: Goal) =>
      g.status === "ACTIVE" ? 0 : g.status === "PAUSED" ? 1 : 2
    return [...goals].sort((a, b) => {
      const r = rank(a) - rank(b)
      if (r !== 0) return r
      const aDate = a.targetDate ?? ""
      const bDate = b.targetDate ?? ""
      return aDate.localeCompare(bDate)
    })
  }, [goals])

  const activeCount = goals.filter((g) => g.status === "ACTIVE").length
  const completedCount = goals.filter((g) => g.status === "COMPLETED").length

  return (
    <Card>
      <CardHeader>
        <CardTitle className="inline-flex items-center gap-2">
          <HugeiconsIcon
            icon={Compass01Icon}
            strokeWidth={2}
            className="size-4"
          />
          {t.home.path.title}
        </CardTitle>
        <CardDescription>{t.home.path.subtitle}</CardDescription>
        <CardAction>
          <Button
            variant="outline"
            size="sm"
            nativeButton={false}
            className="hidden sm:inline-flex"
            render={
              <Link href="/plan">
                <HugeiconsIcon
                  icon={PlusSignIcon}
                  strokeWidth={2}
                  data-icon="inline-start"
                />
                {t.home.path.addGoal}
              </Link>
            }
          />
        </CardAction>
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        {loading ? null : goals.length > 0 ? (
          <PathsAggregate
            aggregatePercent={aggregatePercent}
            aggregateDaysCompleted={aggregateDaysCompleted}
            aggregateDaysTotal={aggregateDaysTotal}
            activeCount={activeCount}
            completedCount={completedCount}
          />
        ) : null}

        {loading ? (
          <PathListSkeleton />
        ) : orderedGoals.length === 0 ? (
          <Empty>
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <HugeiconsIcon icon={Compass01Icon} strokeWidth={2} />
              </EmptyMedia>
              <EmptyTitle>{t.home.goals.emptyTitle}</EmptyTitle>
              <EmptyDescription>
                {t.home.goals.emptyDescription}
              </EmptyDescription>
            </EmptyHeader>
            <EmptyContent>
              <Button
                nativeButton={false}
                render={
                  <Link href="/plan">
                    <HugeiconsIcon
                      icon={PlusSignIcon}
                      strokeWidth={2}
                      data-icon="inline-start"
                    />
                    {t.home.path.addGoal}
                  </Link>
                }
              />
            </EmptyContent>
          </Empty>
        ) : (
          <ul className="flex flex-col gap-2">
            {orderedGoals.map((goal) => (
              <PathRow key={goal.id} goal={goal} lang={lang} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function PathsAggregate({
  aggregatePercent,
  aggregateDaysCompleted,
  aggregateDaysTotal,
  activeCount,
  completedCount,
}: {
  aggregatePercent: number
  aggregateDaysCompleted: number
  aggregateDaysTotal: number
  activeCount: number
  completedCount: number
}) {
  const t = useT()
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <div className="flex items-baseline gap-1.5">
          <span className="font-heading text-3xl font-semibold tabular-nums sm:text-4xl">
            {aggregatePercent}
            <span className="text-xl text-muted-foreground sm:text-2xl">%</span>
          </span>
          <span className="text-xs text-muted-foreground sm:text-sm">
            {t.home.path.percentLabel}
          </span>
        </div>
        <div className="flex items-center gap-3 text-xs text-muted-foreground sm:text-sm">
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2 rounded-full bg-primary" aria-hidden />
            <span className="font-medium text-foreground tabular-nums">
              {activeCount}
            </span>
            <span>{t.home.path.activeLabel}</span>
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2 rounded-full bg-emerald-500" aria-hidden />
            <span className="font-medium text-foreground tabular-nums">
              {completedCount}
            </span>
            <span>{t.home.path.completedLabel}</span>
          </span>
        </div>
      </div>
      <Progress value={aggregatePercent} className="h-1.5" />
      <span className="text-[11px] text-muted-foreground tabular-nums">
        {t.home.path.aggregate
          .replace("{completed}", String(aggregateDaysCompleted))
          .replace("{total}", String(aggregateDaysTotal))}
      </span>
    </div>
  )
}

function PathRow({ goal, lang }: { goal: Goal; lang: Lang }) {
  const t = useT()
  const percent =
    typeof goal.progress === "number"
      ? Math.max(0, Math.min(100, Math.round(goal.progress)))
      : goal.daysTotal > 0
        ? Math.min(100, Math.round((goal.daysCompleted / goal.daysTotal) * 100))
        : 0

  const isCompleted = goal.status === "COMPLETED"
  const isPaused = goal.status === "PAUSED"
  const statusLabel = isCompleted
    ? t.home.goal.statusCompleted
    : isPaused
      ? t.home.goal.statusPaused
      : t.home.goal.statusActive
  const statusVariant: React.ComponentProps<typeof Badge>["variant"] =
    isCompleted ? "default" : isPaused ? "secondary" : "outline"

  const nodeClass = isCompleted
    ? "bg-emerald-500 text-white"
    : isPaused
      ? "bg-muted text-muted-foreground"
      : "bg-primary text-primary-foreground"
  const nodeIcon = isCompleted ? CheckmarkCircle02Icon : Target02Icon

  const targetLabel = goal.targetDate
    ? t.home.goal.targetDate.replace(
        "{date}",
        formatTargetDate(goal.targetDate, lang)
      )
    : t.home.goal.noTargetDate
  const daysLabel = t.home.goal.daysProgress
    .replace("{completed}", String(goal.daysCompleted))
    .replace("{total}", String(goal.daysTotal))

  return (
    <li>
      <Link
        href="/plan"
        className="group flex items-center gap-3 rounded-2xl border border-border bg-card p-3 transition-colors hover:bg-accent/40 active:scale-[0.99] sm:p-3.5"
      >
        <span
          aria-hidden
          className={cn(
            "inline-flex size-9 shrink-0 items-center justify-center rounded-full shadow-sm",
            nodeClass
          )}
        >
          <HugeiconsIcon icon={nodeIcon} strokeWidth={2} className="size-5" />
        </span>

        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <div className="flex items-start justify-between gap-2">
            <h3 className="truncate font-heading text-sm font-semibold sm:text-base">
              {goal.title}
            </h3>
            <Badge
              variant={statusVariant}
              className="shrink-0 text-[10px] sm:text-xs"
            >
              {statusLabel}
            </Badge>
          </div>

          <div className="flex items-center gap-2">
            <Progress value={percent} className="h-1 flex-1" />
            <span className="shrink-0 text-[11px] font-medium text-muted-foreground tabular-nums sm:text-xs">
              {percent}%
            </span>
          </div>

          <p className="flex items-center gap-1.5 text-[11px] text-muted-foreground sm:text-xs">
            <HugeiconsIcon
              icon={Calendar03Icon}
              strokeWidth={2}
              className="size-3"
            />
            <span className="truncate">{targetLabel}</span>
            <span aria-hidden>·</span>
            <span className="shrink-0 tabular-nums">{daysLabel}</span>
          </p>
        </div>
      </Link>
    </li>
  )
}

function PathListSkeleton() {
  return (
    <ul className="flex flex-col gap-2">
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="flex items-center gap-3 rounded-2xl border border-border bg-card p-3"
        >
          <Skeleton className="size-9 shrink-0 rounded-full" />
          <div className="flex flex-1 flex-col gap-1.5">
            <Skeleton className="h-4 w-3/5" />
            <Skeleton className="h-1 w-full" />
            <Skeleton className="h-3 w-2/5" />
          </div>
        </li>
      ))}
    </ul>
  )
}

function formatTargetDate(iso: string, lang: Lang): string {
  const locale = lang === "uz" ? "uz-UZ" : lang === "ru" ? "ru-RU" : "en-GB"
  try {
    return new Date(iso).toLocaleDateString(locale, {
      day: "numeric",
      month: "short",
      year: "numeric",
    })
  } catch {
    return iso
  }
}

function formatTodayDate(iso: string, lang: Lang): string {
  const locale = lang === "uz" ? "uz-UZ" : lang === "ru" ? "ru-RU" : "en-GB"
  try {
    return new Date(iso).toLocaleDateString(locale, {
      weekday: "long",
      day: "numeric",
      month: "short",
    })
  } catch {
    return iso
  }
}
