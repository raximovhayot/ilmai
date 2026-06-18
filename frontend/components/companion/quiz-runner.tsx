"use client"

import * as React from "react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  Cancel01Icon,
  CheckmarkCircle02Icon,
  Clock01Icon,
  PuzzleIcon,
  Tick02Icon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { useT } from "@/lib/i18n/provider"
import { answerQuizQuestion } from "@/lib/quiz"
import type { CoachDataParts } from "@/lib/agent"
import { cn } from "@/lib/utils"

type QuizData = CoachDataParts["quiz"]
type QuizQuestionData = QuizData["questions"][number]
type QuizMode = "TIMED" | "EXPLAIN" | "SINGLE"
type Phase = "intro" | "running" | "results"

type GradedResult = {
  isCorrect: boolean
  userAnswer: string
  correctAnswer?: string | null
  explanation?: string | null
  feedback?: string | null
}

function normalizeMode(mode?: string | null): QuizMode {
  switch ((mode ?? "").toUpperCase()) {
    case "TIMED":
      return "TIMED"
    case "EXPLAIN":
      return "EXPLAIN"
    default:
      return "SINGLE"
  }
}

function formatClock(seconds: number): string {
  const safe = Math.max(0, seconds)
  const m = Math.floor(safe / 60)
  const s = safe % 60
  return `${m}:${s.toString().padStart(2, "0")}`
}

export function QuizRunner({
  quiz,
  onActiveChange,
}: {
  quiz: QuizData
  onActiveChange: (sessionId: string, active: boolean) => void
}) {
  const dict = useT()
  const t = dict.companion
  const errors = dict.errors
  const mode = normalizeMode(quiz.mode)
  const questions = React.useMemo(
    () => [...quiz.questions].sort((a, b) => a.position - b.position),
    [quiz.questions]
  )
  const total = questions.length
  const timeLimit = quiz.timeLimitSeconds ?? 0

  const [phase, setPhase] = React.useState<Phase>("intro")
  const [answers, setAnswers] = React.useState<Record<string, string>>({})
  const [graded, setGraded] = React.useState<Record<string, GradedResult>>({})
  const [step, setStep] = React.useState(0)
  const [submitting, setSubmitting] = React.useState(false)
  const [secondsLeft, setSecondsLeft] = React.useState<number | null>(null)

  const sessionId = quiz.sessionId
  const finalizedRef = React.useRef(false)

  React.useEffect(() => {
    onActiveChange(sessionId, phase !== "results")
  }, [sessionId, phase, onActiveChange])

  React.useEffect(() => {
    return () => onActiveChange(sessionId, false)
  }, [sessionId, onActiveChange])

  const setAnswer = React.useCallback((questionId: string, value: string) => {
    setAnswers((prev) => ({ ...prev, [questionId]: value }))
  }, [])

  const gradeAnswered = React.useCallback(
    async (only?: QuizQuestionData) => {
      const target = only ? [only] : questions
      const next: Record<string, GradedResult> = {}
      for (const q of target) {
        if (graded[q.questionId]) continue
        const answer = (answers[q.questionId] ?? "").trim()
        if (!answer) continue
        try {
          const res = await answerQuizQuestion(sessionId, q.questionId, answer)
          if (res) {
            next[q.questionId] = {
              isCorrect: res.isCorrect === true,
              userAnswer: res.userAnswer ?? answer,
              correctAnswer: res.correctAnswer,
              explanation: res.explanation,
              feedback: res.feedback,
            }
          }
        } catch {
          toast.error(errors.generic)
        }
      }
      if (Object.keys(next).length > 0) {
        setGraded((prev) => ({ ...prev, ...next }))
      }
      return next
    },
    [answers, graded, questions, sessionId, errors.generic]
  )

  const finalize = React.useCallback(async () => {
    if (finalizedRef.current) return
    finalizedRef.current = true
    setSubmitting(true)
    await gradeAnswered()
    setSubmitting(false)
    setSecondsLeft(null)
    setPhase("results")
  }, [gradeAnswered])

  const finalizeRef = React.useRef(finalize)
  React.useEffect(() => {
    finalizeRef.current = finalize
  }, [finalize])

  React.useEffect(() => {
    if (phase !== "running" || mode !== "TIMED" || timeLimit <= 0) return
    const id = window.setInterval(() => {
      setSecondsLeft((prev) => {
        const current = prev ?? timeLimit
        if (current <= 1) {
          window.clearInterval(id)
          void finalizeRef.current()
          return 0
        }
        return current - 1
      })
    }, 1000)
    return () => window.clearInterval(id)
  }, [phase, mode, timeLimit])

  function start() {
    finalizedRef.current = false
    setPhase("running")
    if (mode === "TIMED" && timeLimit > 0) setSecondsLeft(timeLimit)
  }

  const modeLabel =
    mode === "TIMED"
      ? t.quizModeTimed
      : mode === "EXPLAIN"
        ? t.quizModeExplain
        : t.quizModeSingle

  if (phase === "intro") {
    return (
      <QuizShell modeLabel={modeLabel}>
        <p className="text-sm font-medium text-foreground">
          {t.quizQuestionsLabel.replace("{count}", String(total))}
        </p>
        <p className="text-xs text-muted-foreground">{t.quizStartHint}</p>
        <Button onClick={start} className="mt-1 w-full sm:w-auto">
          {t.quizStart}
        </Button>
      </QuizShell>
    )
  }

  if (phase === "results") {
    const correct = questions.filter(
      (q) => graded[q.questionId]?.isCorrect
    ).length
    const pct = total > 0 ? Math.round((correct / total) * 100) : 0
    return (
      <QuizShell modeLabel={modeLabel}>
        <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <HugeiconsIcon
            icon={CheckmarkCircle02Icon}
            className="size-4 text-emerald-600 dark:text-emerald-400"
            strokeWidth={2}
          />
          <span>{t.quizResultsTitle}</span>
        </div>
        <p className="text-sm text-muted-foreground">
          {t.quizScore}: <span className="font-semibold">{pct}%</span> ({correct}{" "}
          / {total})
        </p>
        <ul className="flex flex-col gap-2">
          {questions.map((q, index) => {
            const result = graded[q.questionId]
            const answered = result != null
            const isCorrect = result?.isCorrect === true
            return (
              <li
                key={q.questionId}
                className="flex items-start gap-2 rounded-xl border border-border/50 bg-background p-3 text-sm"
              >
                <span
                  className={cn(
                    "mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full",
                    !answered
                      ? "bg-muted text-muted-foreground"
                      : isCorrect
                        ? "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
                        : "bg-destructive/15 text-destructive"
                  )}
                >
                  <HugeiconsIcon
                    icon={isCorrect ? Tick02Icon : Cancel01Icon}
                    strokeWidth={2}
                    className="size-3.5"
                  />
                </span>
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <p className="font-medium text-foreground">
                    {index + 1}. {q.prompt}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {t.quizYourAnswer}:{" "}
                    {answered ? result.userAnswer : t.quizSkipped}
                  </p>
                  {!isCorrect && result?.correctAnswer ? (
                    <p className="text-xs text-muted-foreground">
                      <span className="font-semibold text-foreground">
                        {t.quizCorrectAnswer}:
                      </span>{" "}
                      {result.correctAnswer}
                    </p>
                  ) : null}
                  {result?.explanation || result?.feedback ? (
                    <p className="text-xs text-muted-foreground">
                      {result.explanation ?? result.feedback}
                    </p>
                  ) : null}
                </div>
              </li>
            )
          })}
        </ul>
      </QuizShell>
    )
  }

  if (mode === "EXPLAIN") {
    const q = questions[step]
    if (!q) return null
    const result = graded[q.questionId]
    const answered = result != null
    const isLast = step === total - 1
    return (
      <QuizShell modeLabel={modeLabel}>
        <div className="flex items-center justify-between gap-2">
          <span className="text-[10px] font-bold tracking-wider text-muted-foreground/80 uppercase">
            {t.quizQuestionOf
              .replace("{current}", String(step + 1))
              .replace("{total}", String(total))}
          </span>
        </div>
        <Progress value={Math.round(((step + (answered ? 1 : 0)) / total) * 100)} />
        <QuizQuestion
          question={q}
          value={answers[q.questionId] ?? ""}
          disabled={answered || submitting}
          onChange={(value) => setAnswer(q.questionId, value)}
          result={result}
        />
        <div className="flex justify-end gap-2">
          {!answered ? (
            <Button
              onClick={() => void gradeAnswered(q)}
              disabled={submitting || !(answers[q.questionId] ?? "").trim()}
              size="sm"
            >
              {submitting ? <Spinner className="size-4" /> : null}
              {t.quizSubmit}
            </Button>
          ) : isLast ? (
            <Button onClick={() => void finalize()} size="sm">
              {t.quizFinish}
            </Button>
          ) : (
            <Button onClick={() => setStep((s) => s + 1)} size="sm">
              {t.quizNext}
            </Button>
          )}
        </div>
      </QuizShell>
    )
  }

  const answeredCount = questions.filter((q) =>
    (answers[q.questionId] ?? "").trim()
  ).length
  return (
    <QuizShell
      modeLabel={modeLabel}
      timer={
        mode === "TIMED" && secondsLeft != null ? (
          <Badge
            variant="outline"
            className="gap-1 font-mono tabular-nums"
            aria-label={t.quizTimeLeft}
          >
            <HugeiconsIcon icon={Clock01Icon} className="size-3.5" strokeWidth={2} />
            {formatClock(secondsLeft)}
          </Badge>
        ) : undefined
      }
    >
      <ul className="flex flex-col gap-4">
        {questions.map((q, index) => (
          <li key={q.questionId} className="flex flex-col gap-2">
            <span className="text-[10px] font-bold tracking-wider text-muted-foreground/80 uppercase">
              {t.quizQuestionOf
                .replace("{current}", String(index + 1))
                .replace("{total}", String(total))}
            </span>
            <QuizQuestion
              question={q}
              value={answers[q.questionId] ?? ""}
              disabled={submitting}
              onChange={(value) => setAnswer(q.questionId, value)}
            />
          </li>
        ))}
      </ul>
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs text-muted-foreground">
          {answeredCount} / {total}
        </span>
        <Button
          onClick={() => void finalize()}
          disabled={submitting || answeredCount === 0}
          size="sm"
        >
          {submitting ? <Spinner className="size-4" /> : null}
          {submitting ? t.quizGrading : t.quizFinish}
        </Button>
      </div>
    </QuizShell>
  )
}

function QuizShell({
  modeLabel,
  timer,
  children,
}: {
  modeLabel: string
  timer?: React.ReactNode
  children: React.ReactNode
}) {
  const t = useT().companion
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border/50 bg-card p-4 text-card-foreground shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-[10px] font-bold tracking-wider text-muted-foreground/80 uppercase">
          <HugeiconsIcon
            icon={PuzzleIcon}
            className="size-4 text-primary"
            strokeWidth={2}
          />
          <span>{t.quizTitle}</span>
          <Badge variant="secondary" className="text-[10px] uppercase">
            {modeLabel}
          </Badge>
        </div>
        {timer}
      </div>
      {children}
    </div>
  )
}

function QuizQuestion({
  question,
  value,
  disabled,
  onChange,
  result,
}: {
  question: QuizQuestionData
  value: string
  disabled: boolean
  onChange: (value: string) => void
  result?: GradedResult
}) {
  const t = useT().companion
  const options = question.options ?? []
  const answered = result != null
  const isCorrect = result?.isCorrect === true

  return (
    <div className="flex flex-col gap-2">
      <p className="text-sm leading-relaxed font-semibold text-foreground">
        {question.prompt}
      </p>
      {options.length > 0 ? (
        <div className="flex flex-col gap-2">
          {options.map((option) => {
            const selected = value === option
            const correctOption = answered && result?.correctAnswer === option
            const wrongChoice = answered && selected && !isCorrect
            return (
              <button
                key={option}
                type="button"
                disabled={disabled}
                onClick={() => onChange(option)}
                className={cn(
                  "flex w-full items-center justify-between gap-2.5 rounded-xl border border-border/70 px-4 py-2.5 text-start text-sm font-medium transition-all duration-150 disabled:cursor-default",
                  !answered && selected && "border-primary/60 bg-secondary",
                  !answered && !selected && "hover:border-primary/25 hover:bg-secondary",
                  correctOption &&
                    "border-emerald-500/50 bg-emerald-500/5 font-semibold text-emerald-800 dark:text-emerald-300",
                  wrongChoice &&
                    "border-destructive/50 bg-destructive/5 font-semibold text-destructive"
                )}
                aria-pressed={selected}
              >
                <span>{option}</span>
                {correctOption ? (
                  <HugeiconsIcon
                    icon={CheckmarkCircle02Icon}
                    className="size-4 shrink-0 text-emerald-600"
                    strokeWidth={2}
                  />
                ) : null}
                {wrongChoice ? (
                  <HugeiconsIcon
                    icon={Cancel01Icon}
                    className="size-4 shrink-0 text-destructive"
                    strokeWidth={2}
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
          placeholder={t.quizAnswerPlaceholder}
          disabled={disabled}
          rows={2}
          className="rounded-xl border-border/75 bg-background focus-visible:border-primary focus-visible:ring-primary/25"
        />
      )}

      {answered ? (
        <div className="flex flex-col gap-1.5 rounded-xl border border-border/40 bg-secondary/30 p-3 text-xs">
          <div
            className={cn(
              "flex items-center gap-1.5 text-[10px] font-bold tracking-wider uppercase",
              isCorrect
                ? "text-emerald-700 dark:text-emerald-400"
                : "text-destructive"
            )}
          >
            <HugeiconsIcon
              icon={isCorrect ? CheckmarkCircle02Icon : Cancel01Icon}
              className="size-4"
              strokeWidth={2}
            />
            <span>{isCorrect ? t.quizCorrect : t.quizIncorrect}</span>
          </div>
          {!isCorrect && result?.correctAnswer ? (
            <p className="font-medium text-muted-foreground">
              <span className="font-bold text-foreground">
                {t.quizCorrectAnswer}:
              </span>{" "}
              {result.correctAnswer}
            </p>
          ) : null}
          {result?.explanation || result?.feedback ? (
            <p className="leading-relaxed font-medium text-muted-foreground">
              <span className="font-bold text-foreground">
                {t.quizExplanation}:
              </span>{" "}
              {result.explanation ?? result.feedback}
            </p>
          ) : null}
        </div>
      ) : null}

      {question.materialName ? (
        <p className="text-[11px] font-medium text-muted-foreground/80">
          {question.materialName}
        </p>
      ) : null}
    </div>
  )
}
