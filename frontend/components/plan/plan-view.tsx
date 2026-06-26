"use client"

import * as React from "react"
import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowRight01Icon,
  BookOpen01Icon,
  CheckmarkCircle02Icon,
  Flag03Icon,
  PuzzleIcon,
  RoadIcon,
  SparklesIcon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { type LearningPlan, type PlanActivity, type PlanStep } from "@/lib/plan"
import { useT } from "@/lib/i18n/provider"
import type { TopicResponse } from "@/lib/topics"
import { cn } from "@/lib/utils"

function focusKey(dayIndex: number, orderInDay: number) {
  return `${dayIndex}:${orderInDay}`
}

type StepState = "done" | "current" | "upcoming"

type PlanRoadmapProps = {
  plan: LearningPlan
  topics: TopicResponse[]
  today: string
}

type PlanDay = {
  dayIndex: number
  scheduledDate: string | null
  steps: PlanStep[]
}

type PlanModule = {
  week: number
  days: PlanDay[]
}

export function PlanRoadmap({ plan }: PlanRoadmapProps) {
  const t = useT()
  const steps = React.useMemo(
    () =>
      [...plan.steps].sort(
        (a, b) => a.dayIndex - b.dayIndex || a.orderInDay - b.orderInDay
      ),
    [plan.steps]
  )
  const currentStep = React.useMemo(
    () => steps.find((s) => !s.done) ?? null,
    [steps]
  )
  const currentKey =
    currentStep != null
      ? focusKey(currentStep.dayIndex, currentStep.orderInDay)
      : null

  const modules = React.useMemo<PlanModule[]>(() => {
    const byWeek = new Map<number, Map<number, PlanDay>>()
    for (const step of steps) {
      const week = Math.floor((step.dayIndex - 1) / 7) + 1
      let days = byWeek.get(week)
      if (!days) {
        days = new Map<number, PlanDay>()
        byWeek.set(week, days)
      }
      const day = days.get(step.dayIndex)
      if (day) {
        day.steps.push(step)
      } else {
        days.set(step.dayIndex, {
          dayIndex: step.dayIndex,
          scheduledDate: step.scheduledDate,
          steps: [step],
        })
      }
    }
    return [...byWeek.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([week, days]) => ({
        week,
        days: [...days.values()].sort((a, b) => a.dayIndex - b.dayIndex),
      }))
  }, [steps])

  const currentWeek =
    currentStep != null
      ? Math.floor((currentStep.dayIndex - 1) / 7) + 1
      : (modules[0]?.week ?? 1)

  const [openOverrides, setOpenOverrides] = React.useState<
    Record<number, boolean>
  >({})
  const [focusedKey, setFocusedKey] = React.useState<string | null>(null)

  const isModuleOpen = (week: number) =>
    openOverrides[week] ?? week === currentWeek
  const toggleModule = (week: number) =>
    setOpenOverrides((prev) => ({
      ...prev,
      [week]: !(prev[week] ?? week === currentWeek),
    }))

  const stateFor = (step: PlanStep): StepState => {
    if (step.done) return "done"
    if (focusKey(step.dayIndex, step.orderInDay) === currentKey)
      return "current"
    return "upcoming"
  }

  return (
    <div id={`plan-${plan.id}`} className="flex scroll-mt-24 flex-col gap-4">
      <JourneyHeader plan={plan} />

      {steps.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t.plan.empty}</p>
      ) : (
        <div className="flex flex-col gap-3">
          {modules.map((module) => (
            <ModuleSection
              key={module.week}
              module={module}
              open={isModuleOpen(module.week)}
              onToggle={() => toggleModule(module.week)}
              stateFor={stateFor}
              focusedKey={focusedKey}
              onFocus={(key) =>
                setFocusedKey((prev) => (prev === key ? null : key))
              }
              planId={plan.id}
            />
          ))}
        </div>
      )}
    </div>
  )
}

type DaySectionProps = {
  stateFor: (step: PlanStep) => StepState
  focusedKey: string | null
  onFocus: (key: string) => void
  planId: string
}

function ModuleSection({
  module,
  open,
  onToggle,
  ...rest
}: DaySectionProps & {
  module: PlanModule
  open: boolean
  onToggle: () => void
}) {
  const t = useT()
  const moduleSteps = module.days.flatMap((d) => d.steps)
  const total = moduleSteps.length
  const done = moduleSteps.filter((s) => s.done).length
  const progress = total > 0 ? Math.round((done / total) * 100) : 0
  const allDone = total > 0 && done >= total

  return (
    <div className="overflow-hidden rounded-xl border border-border">
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="flex w-full items-center gap-3 bg-muted/30 p-4 text-start transition-colors hover:bg-muted/50"
      >
        <span
          className={cn(
            "flex size-8 shrink-0 items-center justify-center rounded-full",
            allDone
              ? "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
              : "bg-primary/10 text-primary"
          )}
        >
          {allDone ? (
            <HugeiconsIcon
              icon={CheckmarkCircle02Icon}
              strokeWidth={2}
              className="size-4"
            />
          ) : (
            <HugeiconsIcon
              icon={BookOpen01Icon}
              strokeWidth={2}
              className="size-4"
            />
          )}
        </span>
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex items-center justify-between gap-2">
            <span className="truncate text-sm font-semibold">
              {t.plan.moduleLabel.replace("{n}", String(module.week))}
            </span>
            <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
              {done}/{total}
            </span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-border">
            <div
              className={cn(
                "h-full rounded-full transition-all",
                allDone ? "bg-emerald-500" : "bg-primary"
              )}
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
        <HugeiconsIcon
          icon={ArrowRight01Icon}
          strokeWidth={2}
          className={cn(
            "size-4 shrink-0 text-muted-foreground transition-transform",
            open ? "rotate-90" : "rtl:rotate-180"
          )}
        />
      </button>

      {open ? (
        <div className="flex flex-col gap-3 border-t border-border p-3">
          {module.days.map((day) => (
            <DayBlock key={day.dayIndex} day={day} {...rest} />
          ))}
        </div>
      ) : null}
    </div>
  )
}

function DayBlock({ day, ...rest }: DaySectionProps & { day: PlanDay }) {
  const t = useT()
  const { stateFor, focusedKey, onFocus, planId } = rest
  const total = day.steps.length
  const done = day.steps.filter((s) => s.done).length
  const dayDone = total > 0 && done >= total

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <div className="flex items-center justify-between gap-2 border-b border-border bg-muted/20 px-3 py-2">
        <div className="flex min-w-0 items-center gap-2">
          <span
            className={cn(
              "flex size-6 shrink-0 items-center justify-center rounded-full text-[0.7rem] font-bold tabular-nums",
              dayDone
                ? "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
                : "bg-primary/10 text-primary"
            )}
          >
            {day.dayIndex}
          </span>
          <span className="text-sm font-semibold">
            {t.plan.dayLabel.replace("{n}", String(day.dayIndex))}
          </span>
          {day.scheduledDate ? (
            <span className="truncate text-xs text-muted-foreground">
              {day.scheduledDate}
            </span>
          ) : null}
        </div>
        <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
          {t.plan.tasksCount.replace("{n}", String(total))} · {done}/{total}
        </span>
      </div>
      <ul className="flex flex-col divide-y divide-border">
        {day.steps.map((step) => {
          const fkey = focusKey(step.dayIndex, step.orderInDay)
          const state = stateFor(step)
          const focused = focusedKey === fkey
          const hasDetail =
            Boolean(step.note) ||
            Boolean(
              step.done &&
              step.activity === "INDEPENDENT" &&
              step.reflectionNote
            )
          const href = `/task/${planId}/${step.dayIndex}/${step.orderInDay}`
          return (
            <li key={step.orderInDay} className="flex flex-col">
              <StepRow
                step={step}
                state={state}
                expandable={hasDetail}
                expanded={hasDetail && focused}
                onToggle={() => onFocus(fkey)}
                href={href}
              />
              {hasDetail && focused ? (
                <div className="px-3 pb-3">
                  <StepCard
                    step={step}
                    planId={planId}
                    isToday={state === "current"}
                  />
                </div>
              ) : null}
            </li>
          )
        })}
      </ul>
    </div>
  )
}

function StepRow({
  step,
  state,
  expandable,
  expanded,
  onToggle,
  href,
}: {
  step: PlanStep
  state: StepState
  expandable: boolean
  expanded: boolean
  onToggle: () => void
  href: string
}) {
  const t = useT()
  const activityLabel = labelForAction(step.activity, t)
  const className = cn(
    "flex w-full items-center gap-3 px-3 py-3 text-start transition-colors hover:bg-accent/40",
    state === "current" && !expanded && "bg-primary/5"
  )
  const inner = (
    <>
      <span
        className={cn(
          "flex size-7 shrink-0 items-center justify-center rounded-full",
          state === "done" &&
            "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400",
          state === "current" && "bg-primary text-primary-foreground",
          state === "upcoming" && "bg-muted text-muted-foreground"
        )}
      >
        {state === "done" ? (
          <HugeiconsIcon
            icon={CheckmarkCircle02Icon}
            strokeWidth={2.5}
            className="size-4"
          />
        ) : (
          <HugeiconsIcon
            icon={iconForAction(step.activity)}
            strokeWidth={2}
            className="size-4"
          />
        )}
      </span>
      <div className="flex min-w-0 flex-1 flex-col">
        <span
          className={cn(
            "truncate text-sm font-medium",
            step.done && "text-muted-foreground line-through"
          )}
        >
          {step.title}
        </span>
        <span className="text-xs text-muted-foreground">{activityLabel}</span>
      </div>
      {step.activity === "QUIZ" && step.quizScore != null ? (
        <Badge
          variant={step.quizPassed ? "default" : "secondary"}
          className="shrink-0"
        >
          {(step.quizPassed
            ? t.plan.quizPassedBadge
            : t.plan.quizFailedBadge
          ).replace("{n}", String(step.quizScore))}
        </Badge>
      ) : state === "current" ? (
        <Badge variant="default" className="shrink-0">
          {t.plan.youAreHere}
        </Badge>
      ) : null}
      <HugeiconsIcon
        icon={ArrowRight01Icon}
        strokeWidth={2}
        className={cn(
          "size-4 shrink-0 text-muted-foreground transition-transform",
          expandable && expanded ? "rotate-90" : "rtl:rotate-180"
        )}
      />
    </>
  )

  if (expandable) {
    return (
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        className={className}
      >
        {inner}
      </button>
    )
  }

  return (
    <Link href={href} className={className}>
      {inner}
    </Link>
  )
}

function JourneyHeader({ plan }: { plan: LearningPlan }) {
  const t = useT()
  const progress =
    plan.daysTotal > 0
      ? Math.min(100, Math.round((plan.daysCompleted / plan.daysTotal) * 100))
      : 0
  const allDone = plan.daysTotal > 0 && plan.daysCompleted >= plan.daysTotal
  const currentNumber = Math.min(plan.daysCompleted + 1, plan.daysTotal)
  const toGo = Math.max(plan.daysTotal - plan.daysCompleted, 0)

  return (
    <div
      className={cn(
        "sticky top-2 z-10 flex flex-col gap-3 rounded-2xl border-b-4 p-4 shadow-sm",
        allDone
          ? "border-emerald-700 bg-emerald-500 text-white dark:text-emerald-950"
          : "border-primary/70 bg-primary text-primary-foreground"
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="text-[0.7rem] font-semibold tracking-wide uppercase opacity-80">
            {allDone
              ? t.plan.journeyComplete
              : t.plan.dayOf
                  .replace("{n}", String(currentNumber))
                  .replace("{total}", String(plan.daysTotal))}
          </span>
          <span className="truncate text-lg font-bold">
            {plan.goal ?? t.plan.goal}
          </span>
        </div>
        <Button
          nativeButton={false}
          variant="secondary"
          size="sm"
          className="shrink-0"
          render={
            <Link href="/profile">
              <HugeiconsIcon
                icon={Flag03Icon}
                strokeWidth={2}
                data-icon="inline-start"
              />
              {plan.goal ? t.plan.editGoal : t.plan.setGoal}
            </Link>
          }
        />
      </div>
      <div className="flex flex-col gap-1">
        <div className="h-2.5 overflow-hidden rounded-full bg-black/20">
          <div
            className="h-full rounded-full bg-white/90 transition-all"
            style={{ width: `${progress}%` }}
          />
        </div>
        <div className="flex items-center justify-between text-[0.7rem] font-medium opacity-90">
          <span className="tabular-nums">
            {allDone ? "100%" : t.plan.stepsToGo.replace("{n}", String(toGo))}
          </span>
          {plan.targetDate ? (
            <span className="tabular-nums">
              {t.plan.target}: {plan.targetDate}
            </span>
          ) : null}
        </div>
      </div>
    </div>
  )
}

export function StepCard({
  step,
  planId,
  isToday,
}: {
  step: PlanStep
  planId: string
  isToday: boolean
}) {
  const t = useT()
  const isIndependent = step.activity === "INDEPENDENT"
  const completedOn = step.completedAt ? step.completedAt.slice(0, 10) : null

  return (
    <div
      className={cn(
        "rounded-xl border border-border p-4",
        step.done && "opacity-70",
        isToday && "border-primary/40 bg-primary/5"
      )}
    >
      <p className="text-sm text-foreground">
        {descForAction(step.activity, t)}
      </p>

      {step.note ? (
        <p className="mt-2 text-xs text-muted-foreground">{step.note}</p>
      ) : null}

      {!step.done && step.hasLesson ? (
        <p className="mt-2 inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          <HugeiconsIcon
            icon={SparklesIcon}
            strokeWidth={2}
            className="size-3.5 shrink-0 text-primary"
          />
          {t.plan.lessonReady}
        </p>
      ) : null}

      {step.done && isIndependent && step.reflectionNote ? (
        <p className="mt-2 rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
          {step.reflectionNote}
        </p>
      ) : null}

      <div className="mt-3 flex items-center justify-end gap-3">
        {step.done ? (
          <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
            <HugeiconsIcon
              icon={CheckmarkCircle02Icon}
              strokeWidth={2}
              className="size-4"
            />
            {completedOn
              ? t.plan.completedOn.replace("{date}", completedOn)
              : t.plan.completed}
          </span>
        ) : (
          <Link
            href={`/task/${planId}/${step.dayIndex}/${step.orderInDay}`}
            className={buttonVariants({ size: "sm" })}
          >
            {t.plan.openTask}
            <HugeiconsIcon
              icon={ArrowRight01Icon}
              strokeWidth={2}
              data-icon="inline-end"
              className="rtl:rotate-180"
            />
          </Link>
        )}
      </div>
    </div>
  )
}

function iconForAction(action: PlanActivity) {
  if (action === "READ") return BookOpen01Icon
  if (action === "QUIZ") return PuzzleIcon
  if (action === "INDEPENDENT") return Flag03Icon
  return RoadIcon
}

function labelForAction(action: PlanActivity, t: ReturnType<typeof useT>) {
  if (action === "READ") return t.plan.actionRead
  if (action === "QUIZ") return t.plan.actionQuiz
  if (action === "INDEPENDENT") return t.plan.actionIndependent
  return t.plan.actionReview
}

function descForAction(action: PlanActivity, t: ReturnType<typeof useT>) {
  if (action === "READ") return t.plan.descRead
  if (action === "QUIZ") return t.plan.descQuiz
  if (action === "INDEPENDENT") return t.plan.descIndependent
  return t.plan.descReview
}
