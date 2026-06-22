"use client"

import * as React from "react"
import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowRight01Icon,
  BookOpen01Icon,
  Calendar03Icon,
  CheckmarkCircle02Icon,
  Flag03Icon,
  PuzzleIcon,
  RefreshIcon,
  RoadIcon,
} from "@hugeicons/core-free-icons"

import { Response } from "@/components/ai-elements/response"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Spinner } from "@/components/ui/spinner"
import {
  type LearningPlan,
  type PlanActivity,
  type PlanStep,
  type StepLesson,
} from "@/lib/plan"
import { useT } from "@/lib/i18n/provider"
import type { TopicResponse } from "@/lib/topics"
import { cn } from "@/lib/utils"

function stepKey(planId: string, dayIndex: number) {
  return `${planId}:${dayIndex}`
}

type StepState = "done" | "current" | "upcoming"

type PlanRoadmapProps = {
  plan: LearningPlan
  topics: TopicResponse[]
  today: string
  completingKey: string | null
  lessons: Record<string, StepLesson>
  expandedKey: string | null
  lessonLoadingKey: string | null
  onComplete: (planId: string, dayIndex: number) => void
  onToggleLesson: (planId: string, dayIndex: number) => void
  onRegenerate: (planId: string, dayIndex: number) => void
}

type PlanModule = {
  week: number
  steps: PlanStep[]
}

export function PlanRoadmap({
  plan,
  topics,
  completingKey,
  lessons,
  expandedKey,
  lessonLoadingKey,
  onComplete,
  onToggleLesson,
  onRegenerate,
}: PlanRoadmapProps) {
  const t = useT()
  const topicNameById = React.useMemo(
    () => new Map(topics.map((tp) => [tp.id, tp.name])),
    [topics]
  )
  const steps = React.useMemo(
    () => [...plan.steps].sort((a, b) => a.dayIndex - b.dayIndex),
    [plan.steps]
  )
  const currentDay = React.useMemo(() => {
    const next = steps.find((s) => !s.done)
    return next ? next.dayIndex : null
  }, [steps])

  const modules = React.useMemo<PlanModule[]>(() => {
    const byWeek = new Map<number, PlanStep[]>()
    for (const step of steps) {
      const week = Math.floor((step.dayIndex - 1) / 7) + 1
      const bucket = byWeek.get(week)
      if (bucket) bucket.push(step)
      else byWeek.set(week, [step])
    }
    return [...byWeek.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([week, items]) => ({ week, steps: items }))
  }, [steps])

  const currentWeek =
    currentDay != null
      ? Math.floor((currentDay - 1) / 7) + 1
      : (modules[0]?.week ?? 1)

  const [openOverrides, setOpenOverrides] = React.useState<
    Record<number, boolean>
  >({})
  const [focusedDay, setFocusedDay] = React.useState<number | null>(null)

  const isModuleOpen = (week: number) =>
    openOverrides[week] ?? week === currentWeek
  const toggleModule = (week: number) =>
    setOpenOverrides((prev) => ({
      ...prev,
      [week]: !(prev[week] ?? week === currentWeek),
    }))

  const stateFor = (step: PlanStep): StepState => {
    if (step.done) return "done"
    if (step.dayIndex === currentDay) return "current"
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
              focusedDay={focusedDay}
              onFocus={(dayIndex) =>
                setFocusedDay((prev) => (prev === dayIndex ? null : dayIndex))
              }
              planId={plan.id}
              topicNameById={topicNameById}
              completingKey={completingKey}
              lessons={lessons}
              expandedKey={expandedKey}
              lessonLoadingKey={lessonLoadingKey}
              onComplete={onComplete}
              onToggleLesson={onToggleLesson}
              onRegenerate={onRegenerate}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function ModuleSection({
  module,
  open,
  onToggle,
  stateFor,
  focusedDay,
  onFocus,
  planId,
  topicNameById,
  completingKey,
  lessons,
  expandedKey,
  lessonLoadingKey,
  onComplete,
  onToggleLesson,
  onRegenerate,
}: {
  module: PlanModule
  open: boolean
  onToggle: () => void
  stateFor: (step: PlanStep) => StepState
  focusedDay: number | null
  onFocus: (dayIndex: number) => void
  planId: string
  topicNameById: Map<string, string>
  completingKey: string | null
  lessons: Record<string, StepLesson>
  expandedKey: string | null
  lessonLoadingKey: string | null
  onComplete: (planId: string, dayIndex: number) => void
  onToggleLesson: (planId: string, dayIndex: number) => void
  onRegenerate: (planId: string, dayIndex: number) => void
}) {
  const t = useT()
  const total = module.steps.length
  const done = module.steps.filter((s) => s.done).length
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
        <ul className="flex flex-col divide-y divide-border border-t border-border">
          {module.steps.map((step) => {
            const key = stepKey(planId, step.dayIndex)
            const state = stateFor(step)
            const focused = focusedDay === step.dayIndex
            return (
              <li key={step.dayIndex} className="flex flex-col">
                <StepRow
                  step={step}
                  state={state}
                  expanded={focused}
                  onToggle={() => onFocus(step.dayIndex)}
                />
                {focused ? (
                  <div className="px-3 pb-3">
                    <StepCard
                      step={step}
                      isToday={state === "current"}
                      completing={completingKey === key}
                      topicNameById={topicNameById}
                      onComplete={() => onComplete(planId, step.dayIndex)}
                      lesson={lessons[key]}
                      lessonOpen={expandedKey === key}
                      lessonLoading={lessonLoadingKey === key}
                      onToggleLesson={() =>
                        onToggleLesson(planId, step.dayIndex)
                      }
                      onRegenerateLesson={() =>
                        onRegenerate(planId, step.dayIndex)
                      }
                    />
                  </div>
                ) : null}
              </li>
            )
          })}
        </ul>
      ) : null}
    </div>
  )
}

function StepRow({
  step,
  state,
  expanded,
  onToggle,
}: {
  step: PlanStep
  state: StepState
  expanded: boolean
  onToggle: () => void
}) {
  const t = useT()
  const activityLabel = labelForAction(step.activity, t)
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={expanded}
      className={cn(
        "flex w-full items-center gap-3 px-4 py-3 text-start transition-colors hover:bg-accent/40",
        state === "current" && !expanded && "bg-primary/5"
      )}
    >
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
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <span>{t.plan.dayLabel.replace("{n}", String(step.dayIndex))}</span>
          <span aria-hidden>·</span>
          <span>{activityLabel}</span>
        </span>
      </div>
      {state === "current" ? (
        <Badge variant="default" className="shrink-0">
          {t.plan.youAreHere}
        </Badge>
      ) : null}
      <HugeiconsIcon
        icon={ArrowRight01Icon}
        strokeWidth={2}
        className={cn(
          "size-4 shrink-0 text-muted-foreground transition-transform",
          expanded ? "rotate-90" : "rtl:rotate-180"
        )}
      />
    </button>
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
  isToday,
  completing,
  topicNameById,
  onComplete,
  lesson,
  lessonOpen,
  lessonLoading,
  onToggleLesson,
  onRegenerateLesson,
}: {
  step: PlanStep
  isToday: boolean
  completing: boolean
  topicNameById: Map<string, string>
  onComplete: () => void
  lesson: StepLesson | undefined
  lessonOpen: boolean
  lessonLoading: boolean
  onToggleLesson: () => void
  onRegenerateLesson: () => void
}) {
  const t = useT()
  const activityLabel = labelForAction(step.activity, t)
  const lessonButtonLabel = lessonOpen
    ? t.plan.hideLesson
    : step.hasLesson || lesson
      ? t.plan.openLesson
      : t.plan.startLesson
  return (
    <div
      className={cn(
        "rounded-xl border border-border p-4",
        step.done && "opacity-70",
        isToday && "border-primary/40 bg-primary/5"
      )}
    >
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <HugeiconsIcon
            icon={Calendar03Icon}
            strokeWidth={2}
            className="size-4 text-muted-foreground"
          />
          <span className="text-sm font-medium">
            {t.plan.dayLabel.replace("{n}", String(step.dayIndex))}
          </span>
          {step.scheduledDate ? (
            <span className="text-xs text-muted-foreground">
              {step.scheduledDate}
            </span>
          ) : null}
          {isToday ? (
            <Badge variant="default">{t.plan.todayBadge}</Badge>
          ) : null}
        </div>
        <Badge
          variant="outline"
          className={cn(
            "gap-1",
            step.activity === "READ"
              ? "text-primary"
              : step.activity === "QUIZ"
                ? "text-amber-600 dark:text-amber-400"
                : "text-emerald-600 dark:text-emerald-400"
          )}
        >
          <HugeiconsIcon
            icon={iconForAction(step.activity)}
            strokeWidth={2}
            className="size-3.5"
          />
          {activityLabel}
        </Badge>
      </div>

      <p className={cn("text-sm font-medium", step.done && "line-through")}>
        {step.title}
      </p>
      {step.note ? (
        <p className="mt-0.5 text-xs text-muted-foreground">{step.note}</p>
      ) : null}

      {step.materials.length > 0 ? (
        <ul className="mt-2 flex flex-col gap-1">
          {step.materials.map((material) => {
            const topicName = material.topicId
              ? topicNameById.get(material.topicId)
              : undefined
            const label = material.title ?? topicName ?? activityLabel
            const inner = (
              <>
                <HugeiconsIcon
                  icon={BookOpen01Icon}
                  strokeWidth={2}
                  className="size-3.5 shrink-0 text-muted-foreground"
                />
                <span className="truncate">{label}</span>
                {material.topicId ? (
                  <HugeiconsIcon
                    icon={ArrowRight01Icon}
                    strokeWidth={2}
                    className="size-3.5 shrink-0 text-muted-foreground rtl:rotate-180"
                  />
                ) : null}
              </>
            )
            return (
              <li key={material.id}>
                {material.topicId ? (
                  <Link
                    href={`/data/${material.topicId}`}
                    className="flex items-center gap-1.5 rounded-md px-1 py-0.5 text-xs text-muted-foreground hover:bg-accent/40 hover:text-foreground"
                  >
                    {inner}
                  </Link>
                ) : (
                  <span className="flex items-center gap-1.5 px-1 py-0.5 text-xs text-muted-foreground">
                    {inner}
                  </span>
                )}
              </li>
            )
          })}
        </ul>
      ) : null}

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
        {step.activity === "QUIZ" ? (
          <Button
            nativeButton={false}
            variant="outline"
            size="sm"
            render={
              <Link
                href={`/companion?seed=${encodeURIComponent(
                  `${t.plan.startQuiz}: ${step.title}`
                )}`}
              >
                <HugeiconsIcon
                  icon={PuzzleIcon}
                  strokeWidth={2}
                  data-icon="inline-start"
                />
                {t.plan.startQuiz}
              </Link>
            }
          />
        ) : (
          <Button
            variant="outline"
            size="sm"
            onClick={onToggleLesson}
            disabled={lessonLoading}
          >
            {lessonLoading ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <HugeiconsIcon
                icon={BookOpen01Icon}
                strokeWidth={2}
                data-icon="inline-start"
              />
            )}
            {lessonButtonLabel}
          </Button>
        )}

        {step.done ? (
          <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
            <HugeiconsIcon
              icon={CheckmarkCircle02Icon}
              strokeWidth={2}
              className="size-4"
            />
            {t.plan.completed}
          </span>
        ) : (
          <Button size="sm" onClick={onComplete} disabled={completing}>
            {completing ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <HugeiconsIcon
                icon={CheckmarkCircle02Icon}
                strokeWidth={2}
                data-icon="inline-start"
              />
            )}
            {t.plan.markDone}
          </Button>
        )}
      </div>

      {step.activity !== "QUIZ" && lessonOpen && lesson ? (
        <LessonPanel
          lesson={lesson}
          regenerating={lessonLoading}
          onRegenerate={onRegenerateLesson}
        />
      ) : null}
    </div>
  )
}

function LessonPanel({
  lesson,
  regenerating,
  onRegenerate,
}: {
  lesson: StepLesson
  regenerating: boolean
  onRegenerate: () => void
}) {
  const t = useT()
  return (
    <div className="mt-3 rounded-lg border border-border bg-muted/30 p-4">
      <div className="mb-2 flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          <HugeiconsIcon
            icon={BookOpen01Icon}
            strokeWidth={2}
            className="size-3.5"
          />
          {t.plan.lessonHeading}
        </span>
        <Button
          variant="ghost"
          size="sm"
          onClick={onRegenerate}
          disabled={regenerating}
        >
          {regenerating ? (
            <Spinner data-icon="inline-start" />
          ) : (
            <HugeiconsIcon
              icon={RefreshIcon}
              strokeWidth={2}
              data-icon="inline-start"
            />
          )}
          {t.plan.regenerateLesson}
        </Button>
      </div>

      <Response>{lesson.content}</Response>

      {lesson.citations.length > 0 ? (
        <div className="mt-3 border-t border-border pt-2">
          <p className="mb-1 text-xs font-medium text-muted-foreground">
            {t.plan.lessonSources}
          </p>
          <ul className="flex flex-col gap-1">
            {lesson.citations.map((citation, index) => (
              <li
                key={`${citation.materialId ?? "m"}-${index}`}
                className="flex items-start gap-1.5 text-xs text-muted-foreground"
              >
                <span className="font-medium text-foreground/70">
                  [{index + 1}]
                </span>
                <span className="truncate">
                  {citation.materialName ?? "material"}
                  {typeof citation.chunkIndex === "number"
                    ? ` · #${citation.chunkIndex}`
                    : ""}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  )
}

function iconForAction(action: PlanActivity) {
  if (action === "READ") return BookOpen01Icon
  if (action === "QUIZ") return PuzzleIcon
  return RoadIcon
}

function labelForAction(action: PlanActivity, t: ReturnType<typeof useT>) {
  if (action === "READ") return t.plan.actionRead
  if (action === "QUIZ") return t.plan.actionQuiz
  return t.plan.actionReview
}
