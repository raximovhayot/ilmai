"use client"

import * as React from "react"
import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  BookOpen01Icon,
  CheckmarkCircle02Icon,
  Compass01Icon,
  FireIcon,
  PuzzleIcon,
  RoadIcon,
} from "@hugeicons/core-free-icons"

import { AddGoalDialog } from "@/components/home/add-goal-dialog"
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
import { useT } from "@/lib/i18n/provider"
import type { LearningPlan, PlanStep } from "@/lib/plan"
import type { Stats } from "@/lib/stats"
import type { TopicResponse } from "@/lib/topics"
import { cn } from "@/lib/utils"

type TodayItem = {
  planId: string
  goal: string | null
  step: PlanStep
}

type GoalOption = {
  id: string
  title: string | null
}

type Props = {
  stats: Stats | null
  plans: LearningPlan[]
  topics: TopicResponse[]
  greetingName: string | null
  loading?: boolean
  onCompleteStep?: (planId: string, dayIndex: number) => void
}

export function HomeDashboard({
  stats,
  plans,
  topics,
  greetingName,
  loading = false,
  onCompleteStep,
}: Props) {
  const streakDays = stats?.streakDays ?? 0
  const weeklyMinutes = stats?.weeklyMinutes ?? 0

  const activePlans = React.useMemo(
    () => plans.filter((p) => p.status === "ACTIVE"),
    [plans]
  )

  const goalOptions = React.useMemo<GoalOption[]>(
    () => activePlans.map((p) => ({ id: p.id, title: p.goal })),
    [activePlans]
  )

  const today = new Date().toISOString().slice(0, 10)

  const todayItems = React.useMemo<TodayItem[]>(
    () => buildTodayItems(activePlans, today),
    [activePlans, today]
  )

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
        items={todayItems}
        goals={goalOptions}
        hasPlans={activePlans.length > 0}
        topicNameById={topicNameById}
        loading={loading}
        onCompleteStep={onCompleteStep}
      />
    </div>
  )
}

function buildTodayItems(plans: LearningPlan[], today: string): TodayItem[] {
  const scheduled: TodayItem[] = []
  for (const plan of plans) {
    for (const step of plan.steps) {
      if (step.scheduledDate === today) {
        scheduled.push({ planId: plan.id, goal: plan.goal, step })
      }
    }
  }
  if (scheduled.length > 0) {
    return scheduled
  }
  // Fallback: the next undone step from each goal so the day is never empty.
  const fallback: TodayItem[] = []
  for (const plan of plans) {
    const next =
      plan.steps.find((s) => !s.done) ?? null
    if (next) {
      fallback.push({ planId: plan.id, goal: plan.goal, step: next })
    }
  }
  return fallback
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
  items,
  goals,
  hasPlans,
  topicNameById,
  loading,
  onCompleteStep,
}: {
  items: TodayItem[]
  goals: GoalOption[]
  hasPlans: boolean
  topicNameById: Map<string, string>
  loading: boolean
  onCompleteStep?: (planId: string, dayIndex: number) => void
}) {
  const t = useT()
  const [selectedGoal, setSelectedGoal] = React.useState<string | null>(null)

  const effectiveSelected =
    selectedGoal && goals.some((g) => g.id === selectedGoal)
      ? selectedGoal
      : null

  const visibleItems = React.useMemo(
    () =>
      effectiveSelected
        ? items.filter((it) => it.planId === effectiveSelected)
        : items,
    [items, effectiveSelected]
  )

  const itemsTotal = visibleItems.length
  const itemsDone = visibleItems.filter((it) => it.step.done).length
  const dayPercent =
    itemsTotal > 0 ? Math.round((itemsDone / itemsTotal) * 100) : 0
  const allDone = itemsTotal > 0 && itemsDone === itemsTotal
  const showGoalTag = goals.length > 1

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
        <CardDescription>{t.home.today.subtitle}</CardDescription>
        {hasPlans ? (
          <CardAction className="flex items-center gap-2">
            <AddGoalDialog />
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
        ) : !hasPlans ? (
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
              <AddGoalDialog variant="default" size="default" />
            </EmptyContent>
          </Empty>
        ) : (
          <>
            {goals.length > 1 ? (
              <GoalFilter
                goals={goals}
                selected={effectiveSelected}
                onSelect={setSelectedGoal}
                allLabel={t.home.today.filterAll}
              />
            ) : null}

            {itemsTotal === 0 ? (
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
                  {visibleItems.map((item) => (
                    <PlanItemRow
                      key={`${item.planId}-${item.step.dayIndex}`}
                      step={item.step}
                      goalLabel={showGoalTag ? item.goal : null}
                      topicName={
                        item.step.materials[0]?.topicId
                          ? (topicNameById.get(item.step.materials[0].topicId) ??
                            "—")
                          : "—"
                      }
                      onComplete={
                        onCompleteStep
                          ? () =>
                              onCompleteStep(item.planId, item.step.dayIndex)
                          : undefined
                      }
                    />
                  ))}
                </ul>
              </>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function GoalFilter({
  goals,
  selected,
  onSelect,
  allLabel,
}: {
  goals: GoalOption[]
  selected: string | null
  onSelect: (id: string | null) => void
  allLabel: string
}) {
  const t = useT()
  return (
    <div className="-mx-1 flex flex-wrap items-center gap-1.5 px-1">
      <HugeiconsIcon
        icon={Compass01Icon}
        strokeWidth={2}
        className="size-3.5 text-muted-foreground"
        aria-hidden
      />
      <FilterChip
        active={selected === null}
        onClick={() => onSelect(null)}
        label={allLabel}
      />
      {goals.map((goal) => (
        <FilterChip
          key={goal.id}
          active={selected === goal.id}
          onClick={() => onSelect(goal.id)}
          label={goal.title ?? t.home.today.goalLabel}
        />
      ))}
    </div>
  )
}

function FilterChip({
  active,
  onClick,
  label,
}: {
  active: boolean
  onClick: () => void
  label: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "max-w-[12rem] truncate rounded-full border px-2.5 py-1 text-xs font-medium transition-colors",
        active
          ? "border-primary/40 bg-primary/10 text-primary"
          : "border-border bg-muted/40 text-muted-foreground hover:bg-muted"
      )}
      aria-pressed={active}
    >
      {label}
    </button>
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
  goalLabel,
  topicName,
  onComplete,
}: {
  step: PlanStep
  goalLabel: string | null
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
          {goalLabel ? (
            <>
              <span className="text-foreground/70">{goalLabel}</span>
              {" · "}
            </>
          ) : null}
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
