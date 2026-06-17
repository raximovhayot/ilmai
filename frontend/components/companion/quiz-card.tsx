"use client"

import * as React from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  Cancel01Icon,
  CheckmarkCircle02Icon,
  PuzzleIcon,
} from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { useT } from "@/lib/i18n/provider"
import {
  answerQuizCard,
  type QuizAnswerResult,
  type QuizCardPart,
} from "@/lib/agent"
import { cn } from "@/lib/utils"

export function QuizCard({ part }: { part: QuizCardPart }) {
  const t = useT().companion
  const [answer, setAnswer] = React.useState("")
  const [submitting, setSubmitting] = React.useState(false)
  const [result, setResult] = React.useState<QuizAnswerResult | null>(null)

  const options = part.options ?? []
  const answered = result !== null
  const isCorrect = result?.isCorrect === true

  async function submit(value: string) {
    const trimmed = value.trim()
    if (submitting || answered || !trimmed) return
    setSubmitting(true)
    try {
      const res = await answerQuizCard(part.sessionId, part.questionId, trimmed)
      setResult(res ?? { id: part.questionId, position: part.position })
    } catch {
      setSubmitting(false)
      return
    }
    setSubmitting(false)
  }

  return (
    <div className="rounded-xl border border-border/50 bg-card p-4 text-card-foreground shadow-sm">
      <div className="mb-2.5 flex items-center gap-2 text-[10px] font-bold tracking-wider text-muted-foreground/80 uppercase">
        <HugeiconsIcon
          icon={PuzzleIcon}
          className="size-4 text-primary"
          strokeWidth={2}
        />
        <span>{part.concept ?? `#${part.position}`}</span>
      </div>
      <p className="mb-4 text-sm leading-relaxed font-bold text-foreground">
        {part.prompt}
      </p>

      {options.length > 0 ? (
        <div className="flex flex-col gap-2">
          {options.map((option) => {
            const selected = answer === option
            const correctOption = answered && result?.correctAnswer === option
            const wrongChoice = answered && selected && !isCorrect
            return (
              <button
                key={option}
                type="button"
                disabled={submitting || answered}
                onClick={() => {
                  setAnswer(option)
                  void submit(option)
                }}
                className={cn(
                  "flex w-full items-center justify-between gap-2.5 rounded-xl border border-border/70 px-4 py-3 text-start text-sm font-medium shadow-sm transition-all duration-150 disabled:cursor-default",
                  !answered && "hover:border-primary/25 hover:bg-secondary",
                  correctOption &&
                    "border-emerald-500/50 bg-emerald-500/5 font-semibold text-emerald-800 shadow-inner dark:text-emerald-300",
                  wrongChoice &&
                    "border-destructive/50 bg-destructive/5 font-semibold text-destructive shadow-inner"
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
        <div className="flex flex-col gap-2">
          <Textarea
            value={answer}
            onChange={(event) => setAnswer(event.target.value)}
            placeholder={t.quizAnswerPlaceholder}
            disabled={submitting || answered}
            rows={2}
            className="rounded-xl border-border/75 bg-background focus-visible:border-primary focus-visible:ring-primary/25"
          />
          {!answered ? (
            <div className="flex justify-end">
              <Button
                size="sm"
                onClick={() => void submit(answer)}
                disabled={submitting || answer.trim().length === 0}
                className="rounded-xl px-4 py-2 text-xs font-semibold shadow-sm transition-all duration-150 hover:bg-primary/95 hover:shadow-md"
              >
                {submitting ? <Spinner className="size-4" /> : null}
                <span>{t.quizSubmit}</span>
              </Button>
            </div>
          ) : null}
        </div>
      )}

      {answered ? (
        <div className="mt-4 flex flex-col gap-2 rounded-xl border border-border/40 bg-secondary/30 p-3.5 text-xs shadow-inner">
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
            <p className="mt-1 font-medium text-muted-foreground">
              <span className="font-bold text-foreground">
                {t.quizCorrectAnswer}:
              </span>{" "}
              {result.correctAnswer}
            </p>
          ) : null}
          {result?.explanation || result?.feedback ? (
            <p className="mt-1 leading-relaxed font-medium text-muted-foreground">
              <span className="font-bold text-foreground">
                {t.quizExplanation}:
              </span>{" "}
              {result.explanation ?? result.feedback}
            </p>
          ) : null}
        </div>
      ) : null}

      {part.materialName ? (
        <p className="mt-3 text-[11px] font-medium text-muted-foreground/80">
          {part.materialName}
        </p>
      ) : null}
    </div>
  )
}
