"use client"

import * as React from "react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  Cancel01Icon,
  CheckmarkCircle02Icon,
  PuzzleIcon,
} from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { useT } from "@/lib/i18n/provider"
import { completePlanTask, type LearningPlan, type PlanStep } from "@/lib/plan"
import {
  answerQuizQuestion,
  startQuizSession,
  type QuizQuestion,
  type QuizSession,
} from "@/lib/quiz"
import { cn } from "@/lib/utils"

type Phase = "intro" | "running" | "result"

type Graded = {
  isCorrect: boolean
  userAnswer: string
  correctAnswer?: string | null
  explanation?: string | null
  feedback?: string | null
}

export function TaskQuiz({
  planId,
  step,
  topicId,
  onCompleted,
}: {
  planId: string
  step: PlanStep
  topicId: string | null
  onCompleted: (plan: LearningPlan) => void
}) {
  const t = useT().plan
  const errors = useT().errors

  const [phase, setPhase] = React.useState<Phase>(
    step.done ? "result" : "intro"
  )
  const [starting, setStarting] = React.useState(false)
  const [session, setSession] = React.useState<QuizSession | null>(null)
  const [answers, setAnswers] = React.useState<Record<string, string>>({})
  const [graded, setGraded] = React.useState<Record<string, Graded>>({})
  const [submittingId, setSubmittingId] = React.useState<string | null>(null)
  const [finishing, setFinishing] = React.useState(false)

  const questions = React.useMemo(
    () =>
      session
        ? [...(session.questions ?? [])].sort((a, b) => a.position - b.position)
        : [],
    [session]
  )
  const total = questions.length
  const answeredCount = questions.filter((q) => graded[q.id]).length

  const start = React.useCallback(async () => {
    if (!topicId) return
    setStarting(true)
    try {
      const next = await startQuizSession({ topicId })
      if (next && (next.questions?.length ?? 0) > 0) {
        setSession(next)
        setAnswers({})
        setGraded({})
        setPhase("running")
      } else {
        toast.error(errors.generic)
      }
    } catch {
      toast.error(errors.generic)
    } finally {
      setStarting(false)
    }
  }, [topicId, errors.generic])

  const submitAnswer = React.useCallback(
    async (q: QuizQuestion) => {
      if (!session) return
      const answer = (answers[q.id] ?? "").trim()
      if (!answer || graded[q.id]) return
      setSubmittingId(q.id)
      try {
        const res = await answerQuizQuestion(session.id, q.id, answer)
        if (res) {
          setGraded((prev) => ({
            ...prev,
            [q.id]: {
              isCorrect: res.isCorrect === true,
              userAnswer: res.userAnswer ?? answer,
              correctAnswer: res.correctAnswer,
              explanation: res.explanation,
              feedback: res.feedback,
            },
          }))
        }
      } catch {
        toast.error(errors.generic)
      } finally {
        setSubmittingId(null)
      }
    },
    [session, answers, graded, errors.generic]
  )

  const finish = React.useCallback(async () => {
    if (!session) return
    setFinishing(true)
    try {
      const plan = await completePlanTask(
        planId,
        step.dayIndex,
        step.orderInDay,
        { quizSessionId: session.id }
      )
      if (plan) {
        onCompleted(plan)
        setPhase("result")
      }
    } catch {
      toast.error(errors.generic)
    } finally {
      setFinishing(false)
    }
  }, [
    session,
    planId,
    step.dayIndex,
    step.orderInDay,
    onCompleted,
    errors.generic,
  ])

  if (phase === "result") {
    const passed = step.quizPassed === true
    const score = step.quizScore ?? 0
    return (
      <div className="flex flex-col items-center gap-3 rounded-2xl border border-border p-6 text-center">
        <span
          className={cn(
            "flex size-12 items-center justify-center rounded-full",
            passed
              ? "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
              : "bg-destructive/15 text-destructive"
          )}
        >
          <HugeiconsIcon
            icon={passed ? CheckmarkCircle02Icon : Cancel01Icon}
            strokeWidth={2}
            className="size-6"
          />
        </span>
        <p className="text-lg font-semibold">
          {passed ? t.wsQuizPassed : t.wsQuizFailed}
        </p>
        <p className="text-sm text-muted-foreground">
          {t.wsQuizResult}: <span className="font-semibold">{score}%</span>
        </p>
        {!passed ? (
          <Button
            onClick={() => {
              setSession(null)
              setPhase("intro")
            }}
            variant="secondary"
          >
            {t.wsQuizRetry}
          </Button>
        ) : null}
      </div>
    )
  }

  if (phase === "intro") {
    return (
      <div className="flex flex-col gap-4">
        <article className="flex flex-col gap-2">
          <h1 className="text-xl font-semibold tracking-tight">{step.title}</h1>
          {step.note ? (
            <p className="text-sm text-muted-foreground">{step.note}</p>
          ) : null}
        </article>
        <div className="flex flex-col items-start gap-3 rounded-2xl border border-border bg-muted/30 p-5">
          <p className="inline-flex items-center gap-2 text-sm font-medium">
            <HugeiconsIcon
              icon={PuzzleIcon}
              strokeWidth={2}
              className="size-4 text-primary"
            />
            {t.wsQuizIntro}
          </p>
          {topicId ? (
            <Button onClick={() => void start()} disabled={starting}>
              {starting ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <HugeiconsIcon
                  icon={PuzzleIcon}
                  strokeWidth={2}
                  data-icon="inline-start"
                />
              )}
              {starting ? t.wsQuizStarting : t.wsQuizStart}
            </Button>
          ) : (
            <p className="text-sm text-muted-foreground">{t.wsQuizNoTopic}</p>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-xl font-semibold tracking-tight">{step.title}</h1>
        <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
          {t.wsQuizProgress
            .replace("{answered}", String(answeredCount))
            .replace("{total}", String(total))}
        </span>
      </div>
      <Progress
        value={total > 0 ? Math.round((answeredCount / total) * 100) : 0}
      />

      <ul className="flex flex-col gap-4">
        {questions.map((q, index) => (
          <li key={q.id} className="flex flex-col gap-2">
            <span className="text-[0.7rem] font-bold tracking-wider text-muted-foreground/80 uppercase">
              {index + 1} / {total}
            </span>
            <QuestionCard
              question={q}
              value={answers[q.id] ?? ""}
              graded={graded[q.id]}
              submitting={submittingId === q.id}
              onChange={(value) =>
                setAnswers((prev) => ({ ...prev, [q.id]: value }))
              }
              onSubmit={() => void submitAnswer(q)}
            />
          </li>
        ))}
      </ul>

      <div className="flex items-center justify-end border-t border-border pt-4">
        <Button
          onClick={() => void finish()}
          disabled={finishing || answeredCount < total || total === 0}
        >
          {finishing ? (
            <Spinner data-icon="inline-start" />
          ) : (
            <HugeiconsIcon
              icon={CheckmarkCircle02Icon}
              strokeWidth={2}
              data-icon="inline-start"
            />
          )}
          {finishing ? t.wsQuizGrading : t.wsQuizFinish}
        </Button>
      </div>
    </div>
  )
}

function QuestionCard({
  question,
  value,
  graded,
  submitting,
  onChange,
  onSubmit,
}: {
  question: QuizQuestion
  value: string
  graded?: Graded
  submitting: boolean
  onChange: (value: string) => void
  onSubmit: () => void
}) {
  const t = useT().plan
  const options = question.options ?? []
  const answered = graded != null

  return (
    <div className="flex flex-col gap-2 rounded-xl border border-border p-4">
      <p className="text-sm font-semibold text-foreground">{question.prompt}</p>

      {options.length > 0 ? (
        <div className="flex flex-col gap-2">
          {options.map((option) => {
            const selected = value === option
            const correctOption = answered && graded?.correctAnswer === option
            const wrongChoice =
              answered && selected && graded?.isCorrect !== true
            return (
              <button
                key={option}
                type="button"
                disabled={answered}
                onClick={() => onChange(option)}
                aria-pressed={selected}
                className={cn(
                  "flex w-full items-center justify-between gap-2 rounded-lg border px-4 py-2.5 text-start text-sm font-medium transition-colors disabled:cursor-default",
                  !answered && selected && "border-primary bg-primary/5",
                  !answered && !selected && "border-border hover:bg-accent/40",
                  correctOption &&
                    "border-emerald-500/50 bg-emerald-500/5 text-emerald-700 dark:text-emerald-300",
                  wrongChoice &&
                    "border-destructive/50 bg-destructive/5 text-destructive"
                )}
              >
                <span>{option}</span>
                {correctOption ? (
                  <HugeiconsIcon
                    icon={CheckmarkCircle02Icon}
                    strokeWidth={2}
                    className="size-4 shrink-0 text-emerald-600"
                  />
                ) : null}
                {wrongChoice ? (
                  <HugeiconsIcon
                    icon={Cancel01Icon}
                    strokeWidth={2}
                    className="size-4 shrink-0 text-destructive"
                  />
                ) : null}
              </button>
            )
          })}
        </div>
      ) : (
        <Textarea
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={t.wsQuizAnswerPlaceholder}
          disabled={answered}
          rows={2}
        />
      )}

      {answered ? (
        <div className="flex flex-col gap-1 rounded-lg border border-border/60 bg-muted/30 p-3 text-xs">
          <span
            className={cn(
              "inline-flex items-center gap-1.5 font-semibold",
              graded?.isCorrect
                ? "text-emerald-600 dark:text-emerald-400"
                : "text-destructive"
            )}
          >
            <HugeiconsIcon
              icon={graded?.isCorrect ? CheckmarkCircle02Icon : Cancel01Icon}
              strokeWidth={2}
              className="size-4"
            />
            {graded?.isCorrect ? t.wsQuizCorrect : t.wsQuizIncorrect}
          </span>
          {graded?.isCorrect !== true && graded?.correctAnswer ? (
            <p className="text-muted-foreground">
              <span className="font-semibold text-foreground">
                {t.wsQuizCorrectAnswer}:
              </span>{" "}
              {graded.correctAnswer}
            </p>
          ) : null}
          {graded?.explanation || graded?.feedback ? (
            <p className="leading-relaxed text-muted-foreground">
              {graded?.explanation ?? graded?.feedback}
            </p>
          ) : null}
        </div>
      ) : (
        <div className="flex justify-end">
          <Button
            size="sm"
            variant="secondary"
            onClick={onSubmit}
            disabled={submitting || !value.trim()}
          >
            {submitting ? <Spinner data-icon="inline-start" /> : null}
            {t.wsQuizSubmit}
          </Button>
        </div>
      )}
    </div>
  )
}
