"use client"

import * as React from "react"

import { cn } from "@/lib/utils"

export type MessageProps = React.HTMLAttributes<HTMLDivElement> & {
  from: "user" | "assistant"
}

export function Message({ className, from, ...props }: MessageProps) {
  return (
    <div
      data-role={from}
      className={cn(
        "flex w-full",
        from === "user" ? "justify-end" : "justify-start",
        className
      )}
      {...props}
    />
  )
}

export type MessageContentProps = React.HTMLAttributes<HTMLDivElement> & {
  variant?: "contained" | "flat"
}

export function MessageContent({
  className,
  variant = "flat",
  ...props
}: MessageContentProps) {
  return (
    <div
      className={cn(
        "flex min-w-0 flex-col gap-3",
        variant === "contained" &&
          "max-w-[85%] rounded-2xl bg-secondary px-4 py-2.5 text-[15px] leading-relaxed text-foreground sm:max-w-[75%]",
        className
      )}
      {...props}
    />
  )
}
