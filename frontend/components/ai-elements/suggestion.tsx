"use client"

import * as React from "react"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export function Suggestions({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("flex flex-wrap gap-2", className)} {...props} />
}

export type SuggestionProps = Omit<
  React.ComponentProps<typeof Button>,
  "onClick"
> & {
  suggestion: string
  onSuggestionClick?: (suggestion: string) => void
}

export function Suggestion({
  className,
  suggestion,
  onSuggestionClick,
  children,
  ...props
}: SuggestionProps) {
  return (
    <Button
      type="button"
      variant="outline"
      size="sm"
      onClick={() => onSuggestionClick?.(suggestion)}
      className={cn(
        "rounded-xl border-border/70 px-3.5 py-1.5 text-xs font-medium hover:bg-secondary",
        className
      )}
      {...props}
    >
      {children ?? suggestion}
    </Button>
  )
}
