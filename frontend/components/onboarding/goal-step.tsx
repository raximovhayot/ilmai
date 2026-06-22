"use client"

import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Spinner } from "@/components/ui/spinner"
import { ApiClientError } from "@/lib/api"
import { useT } from "@/lib/i18n/provider"

export type GoalDraft = {
  goal: string
  targetDate: string
  dailyStudyMinutes: number | null
}

const DAILY_PRESETS = [15, 30, 45, 60] as const

type Props = {
  value: GoalDraft
  onChange: (next: GoalDraft) => void
  onNext: () => Promise<void>
  onBack: () => void
}

export function GoalStep({ value, onChange, onNext, onBack }: Props) {
  const t = useT()
  const c = t.onboarding.goal
  const today = new Date().toISOString().slice(0, 10)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleNext() {
    if (submitting) return
    setError(null)
    if (value.goal.trim().length === 0) {
      setError(c.goalRequired)
      return
    }
    if (value.targetDate.length > 0 && value.targetDate < today) {
      setError(c.targetPastError)
      return
    }
    setSubmitting(true)
    try {
      await onNext()
    } catch (err) {
      if (
        err instanceof ApiClientError &&
        err.errors[0]?.code === "PROFILE_INVALID_TARGET_DATE"
      ) {
        setError(c.targetPastError)
      } else {
        setError(
          err instanceof ApiClientError
            ? (err.errors[0]?.message ?? t.errors.generic)
            : t.errors.generic
        )
      }
      setSubmitting(false)
    }
  }
  const isPreset =
    value.dailyStudyMinutes != null &&
    DAILY_PRESETS.includes(
      value.dailyStudyMinutes as (typeof DAILY_PRESETS)[number]
    )
  const [customMode, setCustomMode] = useState(
    value.dailyStudyMinutes != null && !isPreset
  )

  return (
    <div className="space-y-6">
      <div className="space-y-2 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">{c.title}</h1>
        <p className="text-muted-foreground">{c.subtitle}</p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="onboarding-goal">{c.goalLabel}</Label>
        <Input
          id="onboarding-goal"
          value={value.goal}
          placeholder={c.goalPlaceholder}
          onChange={(event) => onChange({ ...value, goal: event.target.value })}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="onboarding-target">{c.targetLabel}</Label>
        <Input
          id="onboarding-target"
          type="date"
          min={today}
          value={value.targetDate}
          onChange={(event) => {
            setError(null)
            onChange({ ...value, targetDate: event.target.value })
          }}
        />
        <p className="text-xs text-muted-foreground">{c.targetHint}</p>
      </div>

      <div className="space-y-2">
        <Label>{c.dailyLabel}</Label>
        <div className="flex flex-wrap gap-2">
          {DAILY_PRESETS.map((minutes) => (
            <Button
              key={minutes}
              type="button"
              variant={
                !customMode && value.dailyStudyMinutes === minutes
                  ? "default"
                  : "outline"
              }
              aria-pressed={!customMode && value.dailyStudyMinutes === minutes}
              onClick={() => {
                setCustomMode(false)
                onChange({
                  ...value,
                  dailyStudyMinutes:
                    value.dailyStudyMinutes === minutes && !customMode
                      ? null
                      : minutes,
                })
              }}
            >
              {minutes} {c.minutesSuffix}
            </Button>
          ))}
          <Button
            type="button"
            variant={customMode ? "default" : "outline"}
            aria-pressed={customMode}
            onClick={() => {
              setCustomMode(true)
              if (isPreset) {
                onChange({ ...value, dailyStudyMinutes: null })
              }
            }}
          >
            {c.customOption}
          </Button>
        </div>
        {customMode && (
          <Input
            type="number"
            min={1}
            inputMode="numeric"
            className="max-w-40"
            placeholder={c.customPlaceholder}
            value={value.dailyStudyMinutes ?? ""}
            onChange={(event) => {
              const raw = event.target.value
              if (raw === "") {
                onChange({ ...value, dailyStudyMinutes: null })
                return
              }
              const parsed = Number.parseInt(raw, 10)
              onChange({
                ...value,
                dailyStudyMinutes:
                  Number.isFinite(parsed) && parsed > 0 ? parsed : null,
              })
            }}
          />
        )}
        <p className="text-xs text-muted-foreground">{c.dailyHint}</p>
      </div>

      {error && (
        <p className="text-center text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <div className="flex items-center justify-between gap-3 pt-2">
        <Button
          type="button"
          variant="ghost"
          onClick={onBack}
          disabled={submitting}
        >
          {t.onboarding.back}
        </Button>
        <Button type="button" onClick={handleNext} disabled={submitting}>
          {submitting && <Spinner className="size-4" />}
          {submitting ? t.onboarding.saving : t.onboarding.next}
        </Button>
      </div>
    </div>
  )
}
