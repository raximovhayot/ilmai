"use client"

import * as React from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Cancel01Icon, Sent02Icon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "@/lib/utils"

export type ChatStatus = "ready" | "submitted" | "streaming" | "error"

export type PromptInputProps = React.FormHTMLAttributes<HTMLFormElement>

export function PromptInput({ className, ...props }: PromptInputProps) {
  return (
    <form
      className={cn(
        "relative flex w-full flex-col rounded-3xl border border-border/70 bg-background shadow-sm transition-all duration-200 focus-within:border-border focus-within:shadow-md",
        className
      )}
      {...props}
    />
  )
}

export type PromptInputTextareaProps = React.ComponentProps<typeof Textarea> & {
  onSubmitMessage?: () => void
}

export function PromptInputTextarea({
  className,
  onSubmitMessage,
  onKeyDown,
  ...props
}: PromptInputTextareaProps) {
  return (
    <Textarea
      rows={1}
      className={cn(
        "max-h-72 min-h-[52px] w-full resize-none border-0 bg-transparent px-5 pt-4 pb-1 text-base leading-relaxed placeholder:text-muted-foreground/55 focus:outline-none focus-visible:ring-0 focus-visible:ring-offset-0",
        className
      )}
      onKeyDown={(event) => {
        if (event.key === "Enter" && !event.shiftKey) {
          event.preventDefault()
          onSubmitMessage?.()
        }
        onKeyDown?.(event)
      }}
      {...props}
    />
  )
}

export function PromptInputToolbar({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("flex items-center justify-between px-3 pb-3", className)}
      {...props}
    />
  )
}

export function PromptInputTools({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("flex items-center gap-2", className)} {...props} />
}

export function PromptInputButton({
  className,
  variant = "ghost",
  ...props
}: React.ComponentProps<typeof Button>) {
  return (
    <Button
      type="button"
      variant={variant}
      size="icon"
      className={cn(
        "size-8 rounded-full text-muted-foreground hover:bg-secondary hover:text-foreground",
        className
      )}
      {...props}
    />
  )
}

export type PromptInputSubmitProps = React.ComponentProps<typeof Button> & {
  status?: ChatStatus
  stopLabel?: string
  sendLabel?: string
  onStop?: () => void
}

export function PromptInputSubmit({
  className,
  status = "ready",
  stopLabel,
  sendLabel,
  onStop,
  disabled,
  ...props
}: PromptInputSubmitProps) {
  const isStreaming = status === "streaming" || status === "submitted"

  if (isStreaming) {
    return (
      <Button
        type="button"
        variant="outline"
        size="icon"
        onClick={onStop}
        className={cn(
          "size-8 rounded-full hover:border-destructive/30 hover:bg-destructive/10 hover:text-destructive",
          className
        )}
        {...props}
      >
        <HugeiconsIcon
          icon={Cancel01Icon}
          strokeWidth={2.5}
          className="size-4"
        />
        <span className="sr-only">{stopLabel ?? "Stop"}</span>
      </Button>
    )
  }

  return (
    <Button
      type="submit"
      size="icon"
      disabled={disabled}
      className={cn(
        "size-8 rounded-full bg-primary transition-transform hover:bg-primary/95 active:scale-95",
        className
      )}
      {...props}
    >
      <HugeiconsIcon icon={Sent02Icon} strokeWidth={2.5} className="size-4" />
      <span className="sr-only">{sendLabel ?? "Send"}</span>
    </Button>
  )
}
