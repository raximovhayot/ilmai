"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  Alert02Icon,
  CheckmarkCircle02Icon,
  PuzzleIcon,
  RefreshIcon,
  StarIcon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Spinner } from "@/components/ui/spinner"
import { refreshGaps, type GapItem, type GapsReport } from "@/lib/gaps"
import { useT } from "@/lib/i18n/provider"
import type { TopicResponse } from "@/lib/topics"

type Props = {
  initialGaps: GapsReport | null
  topics: TopicResponse[]
}

export function GapsView({ initialGaps }: Props) {
  const t = useT()
  const { status } = useSession()
  const [gaps, setGaps] = React.useState<GapsReport | null>(initialGaps)
  const [refreshing, setRefreshing] = React.useState(false)

  const onRefresh = async () => {
    if (status !== "authenticated") return
    setRefreshing(true)
    try {
      const fresh = await refreshGaps()
      if (fresh) setGaps(fresh)
      toast.success(t.gaps.regenerate)
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setRefreshing(false)
    }
  }

  if (!gaps || (gaps.gaps.length === 0 && gaps.strengths.length === 0)) {
    return (
      <Card>
        <CardContent className="p-8 text-center text-sm text-muted-foreground">
          <HugeiconsIcon
            icon={PuzzleIcon}
            strokeWidth={2}
            className="mx-auto mb-2 size-8 text-muted-foreground/60"
          />
          {t.gaps.empty}
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-2xl font-semibold tracking-tight md:text-3xl">
            {t.gaps.title}
          </h1>
          <p className="text-sm text-muted-foreground">{t.gaps.subtitle}</p>
        </div>
        <div className="flex items-center gap-2">
          {gaps.generatedAt ? (
            <span className="hidden text-xs text-muted-foreground sm:inline">
              {t.gaps.generatedAt} {formatDateTime(gaps.generatedAt)}
            </span>
          ) : null}
          <Button
            variant="outline"
            size="sm"
            onClick={onRefresh}
            disabled={refreshing}
          >
            {refreshing ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <HugeiconsIcon
                icon={RefreshIcon}
                strokeWidth={2}
                data-icon="inline-start"
              />
            )}
            {t.gaps.regenerate}
          </Button>
        </div>
      </header>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-2">
          <CardTitle className="text-base">{t.gaps.summary}</CardTitle>
          <Badge variant="outline" className="tabular-nums">
            {Math.round(gaps.overallAccuracy * 100)}% ·{" "}
            {gaps.totalQuestionsAnswered}
          </Badge>
        </CardHeader>
        <CardContent>
          <p className="text-sm leading-relaxed">
            {gaps.summary ?? t.gaps.subtitle}
          </p>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader className="flex flex-row items-center gap-2">
            <HugeiconsIcon
              icon={StarIcon}
              strokeWidth={2}
              className="size-5 text-emerald-600 dark:text-emerald-400"
            />
            <CardTitle className="text-base">{t.gaps.strongTitle}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {gaps.strengths.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t.gaps.empty}</p>
            ) : (
              gaps.strengths.map((item) => (
                <ConceptRow key={item.id} item={item} tone="strong" />
              ))
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center gap-2">
            <HugeiconsIcon
              icon={Alert02Icon}
              strokeWidth={2}
              className="size-5 text-amber-600 dark:text-amber-400"
            />
            <CardTitle className="text-base">{t.gaps.weakTitle}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {gaps.gaps.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t.gaps.empty}</p>
            ) : (
              gaps.gaps.map((item) => (
                <ConceptRow key={item.id} item={item} tone="weak" />
              ))
            )}
          </CardContent>
        </Card>
      </div>

      {gaps.recommendedNext ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {t.gaps.recommendationsTitle}
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
              <HugeiconsIcon
                icon={CheckmarkCircle02Icon}
                strokeWidth={2}
                className="size-4"
              />
            </span>
            <p className="min-w-0 flex-1 text-sm">{gaps.recommendedNext}</p>
            <Button
              size="sm"
              nativeButton={false}
              render={
                <Link
                  href={`/companion?seed=${encodeURIComponent(gaps.recommendedNext)}`}
                >
                  <HugeiconsIcon
                    icon={PuzzleIcon}
                    strokeWidth={2}
                    data-icon="inline-start"
                  />
                  {t.gaps.quizMeCta}
                </Link>
              }
            />
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}

function ConceptRow({
  item,
  tone,
}: {
  item: GapItem
  tone: "strong" | "weak"
}) {
  const pct = Math.round(item.accuracy * 100)
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-start justify-between gap-2">
        <span className="text-sm font-medium">{item.concept}</span>
        <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
          {pct}%
        </span>
      </div>
      <Progress value={pct} className="gap-0">
        <span className="sr-only">{tone}</span>
      </Progress>
      {item.suggestedMaterialName ? (
        <span className="text-xs text-muted-foreground">
          {item.suggestedMaterialName}
        </span>
      ) : null}
    </div>
  )
}

function formatDateTime(iso: string): string {
  try {
    const d = new Date(iso)
    return d.toLocaleString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    })
  } catch {
    return iso
  }
}
