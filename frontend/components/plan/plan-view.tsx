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
import { Spinner } from "@/components/ui/spinner"
import {
  type CompleteTaskPayload,
  type LearningPlan,
  type PlanActivity,
  type PlanStep,
  type StepLesson,
} from "@/lib/plan"
import { useT } from "@/lib/i18n/provider"
import type { TopicResponse } from "@/lib/topics"
import { cn } from "@/lib/utils"

function stepKey(planId: string, dayIndex: number, orderInDay: number) {
  return `${planId}:${dayIndex}:${orderInDay}`
}

function focusKey(dayIndex: number, orderInDay: number) {
  return `${dayIndex}:${orderInDay}`
}

type StepState = "done" | "current" | "upcoming"

type CompleteHandler = (
  planId: string,
  dayIndex: number,
  orderInDay: number,
  payload?: CompleteTaskPayload
) => void

type LessonHandler = (
  planId: string,
  dayIndex: number,
  orderInDay: number
) => void

type PlanRoadmapProps = {
  plan: LearningPlan
  topics: TopicResponse[]
  today: string
  completingKey: string | null
  lessons: Record<string, StepLesson>
  expandedKey: string | null
  lessonLoadingKey: string | null
  onComplete: CompleteHandler
  onToggleLesson: LessonHandler
  onRegenerate: LessonHandler
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

type DaySectionProps = {
  stateFor: (step: PlanStep) => StepState
  focusedKey: string | null
  onFocus: (key: string) => void
  planId: string
  topicNameById: Map<string, string>
  completingKey: string | null
  lessons: Record<string, StepLesson>
  expandedKey: string | null
  lessonLoadingKey: string | null
  onComplete: CompleteHandler
  onToggleLesson: LessonHandler
  onRegenerate: LessonHandler
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
  const {
    stateFor,
    focusedKey,
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
  } = rest
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
          const key = stepKey(planId, step.dayIndex, step.orderInDay)
          const fkey = focusKey(step.dayIndex, step.orderInDay)
          const state = stateFor(step)
          const focused = focusedKey === fkey
          return (
            <li key={step.orderInDay} className="flex flex-col">
              <StepRow
                step={step}
                state={state}
                expanded={focused}
                onToggle={() => onFocus(fkey)}
              />
              {focused ? (
                <div className="px-3 pb-3">
                  <StepCard
                    step={step}
                    isToday={state === "current"}
                    completing={completingKey === key}
                    topicNameById={topicNameById}
                    onComplete={(payload) =>
                      onComplete(
                        planId,
                        step.dayIndex,
                        step.orderInDay,
                        payload
                      )
                    }
                    lesson={lessons[key]}
                    lessonOpen={expandedKey === key}
                    lessonLoading={lessonLoadingKey === key}
                    onToggleLesson={() =>
                      onToggleLesson(planId, step.dayIndex, step.orderInDay)
                    }
                    onRegenerateLesson={() =>
                      onRegenerate(planId, step.dayIndex, step.orderInDay)
                    }
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
        "flex w-full items-center gap-3 px-3 py-3 text-start transition-colors hover:bg-accent/40",
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
        <span className="text-xs text-muted-foreground">{activityLabel}</span>
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
  onComplete: (payload?: CompleteTaskPayload) => void
  lesson: StepLesson | undefined
  lessonOpen: boolean
  lessonLoading: boolean
  onToggleLesson: () => void
  onRegenerateLesson: () => void
}) {
  const t = useT()
  const activityLabel = labelForAction(step.activity, t)
  const isQuiz = step.activity === "QUIZ"
  const isIndependent = step.activity === "INDEPENDENT"
  const isLessonActivity =
    step.activity === "READ" || step.activity === "REVIEW"

  const [reflection, setReflection] = React.useState(step.reflectionNote ?? "")
  const [scoreInput, setScoreInput] = React.useState(
    step.quizScore != null ? String(step.quizScore) : ""
  )

  const lessonReady = step.hasLesson || !!lesson
  const parsedScore = Number.parseInt(scoreInput, 10)
  const scoreValid =
    Number.isFinite(parsedScore) && parsedScore >= 70 && parsedScore <= 100
  const reflectionValid = reflection.trim().length > 0
  const canComplete = isQuiz
    ? scoreValid
    : isIndependent
      ? reflectionValid
      : lessonReady
  const lockHint = isQuiz
    ? t.plan.lockQuizHint
    : isIndependent
      ? t.plan.lockIndependentHint
      : t.plan.lockReadHint

  const handleComplete = () => {
    if (isQuiz) onComplete({ quizScore: parsedScore })
    else if (isIndependent) onComplete({ reflectionNote: reflection.trim() })
    else onComplete()
  }

  const lessonButtonLabel = lessonOpen
    ? t.plan.hideLesson
    : lessonReady
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
                : step.activity === "INDEPENDENT"
                  ? "text-violet-600 dark:text-violet-400"
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

      {!step.done && isIndependent ? (
        <div className="mt-3 flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">
            {t.plan.reflectionLabel}
          </label>
          <textarea
            value={reflection}
            onChange={(e) => setReflection(e.target.value)}
            placeholder={t.plan.reflectionPlaceholder}
            rows={3}
            className="w-full resize-y rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>
      ) : null}

      {!step.done && isQuiz ? (
        <div className="mt-3 flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">
            {t.plan.quizScoreLabel}
          </label>
          <input
            type="number"
            min={0}
            max={100}
            value={scoreInput}
            onChange={(e) => setScoreInput(e.target.value)}
            placeholder={t.plan.quizScorePlaceholder}
            className="w-28 rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>
      ) : null}

      {step.done && isIndependent && step.reflectionNote ? (
        <p className="mt-2 rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
          {step.reflectionNote}
        </p>
      ) : null}
      {step.done && isQuiz && step.quizScore != null ? (
        <p className="mt-2 text-xs text-muted-foreground">
          {t.plan.quizScoreLabel}: {step.quizScore}%
        </p>
      ) : null}

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
        {isQuiz ? (
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
        ) : isIndependent ? (
          <span />
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
          <Button
            size="sm"
            onClick={handleComplete}
            disabled={completing || !canComplete}
          >
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

      {!step.done && !canComplete ? (
        <p className="mt-2 text-xs text-muted-foreground">{lockHint}</p>
      ) : null}

      {isLessonActivity && lessonOpen && lesson ? (
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
  if (action === "INDEPENDENT") return Flag03Icon
  return RoadIcon
}

function labelForAction(action: PlanActivity, t: ReturnType<typeof useT>) {
  if (action === "READ") return t.plan.actionRead
  if (action === "QUIZ") return t.plan.actionQuiz
  if (action === "INDEPENDENT") return t.plan.actionIndependent
  return t.plan.actionReview
}
