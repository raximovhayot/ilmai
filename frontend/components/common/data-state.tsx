"use client"

import * as React from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { AlertCircleIcon, RefreshIcon } from "@hugeicons/core-free-icons"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import type { ApiClientError } from "@/lib/api"
import { useT } from "@/lib/i18n/provider"
import { describeError } from "@/lib/notify"
import { cn } from "@/lib/utils"

type DataStateProps = {
  loading?: boolean
  error?: ApiClientError | null
  onRetry?: () => void
  skeleton?: React.ReactNode
  className?: string
  children: React.ReactNode
}

function DefaultSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn("space-y-3", className)}>
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  )
}

export function DataState({
  loading = false,
  error = null,
  onRetry,
  skeleton,
  className,
  children,
}: DataStateProps) {
  const t = useT()

  if (loading) {
    return <>{skeleton ?? <DefaultSkeleton className={className} />}</>
  }

  if (error) {
    return (
      <Alert variant="destructive" className={className}>
        <HugeiconsIcon icon={AlertCircleIcon} strokeWidth={2} />
        <AlertTitle>{t.common.errorTitle}</AlertTitle>
        <AlertDescription>{describeError(error, t.errors)}</AlertDescription>
        {onRetry ? (
          <div className="mt-3">
            <Button variant="outline" size="sm" onClick={onRetry}>
              <HugeiconsIcon icon={RefreshIcon} strokeWidth={2} />
              {t.common.retry}
            </Button>
          </div>
        ) : null}
      </Alert>
    )
  }

  return <>{children}</>
}
