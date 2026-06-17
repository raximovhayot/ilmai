"use client"

import * as React from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowDown01Icon, BookOpen01Icon } from "@hugeicons/core-free-icons"

import { cn } from "@/lib/utils"

type SourcesContextValue = {
  open: boolean
  setOpen: (value: boolean) => void
}

const SourcesContext = React.createContext<SourcesContextValue | null>(null)

function useSources(): SourcesContextValue {
  const ctx = React.useContext(SourcesContext)
  if (!ctx) {
    throw new Error("Sources components must be used within <Sources>")
  }
  return ctx
}

export function Sources({
  className,
  defaultOpen = false,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & { defaultOpen?: boolean }) {
  const [open, setOpen] = React.useState(defaultOpen)
  return (
    <SourcesContext.Provider value={{ open, setOpen }}>
      <div className={cn("flex flex-col gap-2.5", className)} {...props} />
    </SourcesContext.Provider>
  )
}

export function SourcesTrigger({
  className,
  count,
  label,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  count: number
  label: string
}) {
  const { open, setOpen } = useSources()
  return (
    <button
      type="button"
      onClick={() => setOpen(!open)}
      className={cn(
        "flex w-fit items-center gap-2 rounded-full border border-border/50 bg-secondary/30 px-3 py-1.5 text-xs font-semibold text-muted-foreground transition-colors hover:bg-secondary/60",
        className
      )}
      {...props}
    >
      <HugeiconsIcon
        icon={BookOpen01Icon}
        className="size-3.5 text-primary/70"
        strokeWidth={2}
      />
      <span>
        {label} · {count}
      </span>
      <HugeiconsIcon
        icon={ArrowDown01Icon}
        strokeWidth={2.5}
        className={cn("size-3.5 transition-transform", open && "rotate-180")}
      />
    </button>
  )
}

export function SourcesContent({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  const { open } = useSources()
  if (!open) return null
  return (
    <div
      className={cn("grid grid-cols-1 gap-2.5 sm:grid-cols-2", className)}
      {...props}
    />
  )
}

export function Source({
  className,
  locator,
  snippet,
  score,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  locator: string
  snippet: string
  score: number
}) {
  return (
    <div
      className={cn(
        "group relative flex flex-col gap-1.5 rounded-xl border border-border/45 bg-background p-3 transition-all hover:border-primary/20 hover:shadow-sm",
        className
      )}
      {...props}
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-bold text-primary">{locator}</span>
        <span className="text-[10px] font-semibold text-muted-foreground/75">
          {Math.round(score * 100)}%
        </span>
      </div>
      <span className="line-clamp-2 text-xs leading-relaxed text-muted-foreground italic">
        &ldquo;{snippet}&rdquo;
      </span>
    </div>
  )
}
