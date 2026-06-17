"use client"

import * as React from "react"
import { useT } from "@/lib/i18n/provider"
import type { MaterialStatus } from "@/lib/materials"

type StatusPillProps = {
  status: MaterialStatus
}

export function MaterialStatusPill({ status }: StatusPillProps) {
  const t = useT().materials.status
  const label =
    status === "PENDING"
      ? t.pending
      : status === "PROCESSING"
        ? t.processing
        : status === "READY"
          ? t.ready
          : t.failed
  const tone =
    status === "READY"
      ? "bg-emerald-500/10 text-emerald-600"
      : status === "FAILED"
        ? "bg-destructive/10 text-destructive"
        : "bg-muted text-muted-foreground"
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${tone}`}
    >
      {label}
    </span>
  )
}
