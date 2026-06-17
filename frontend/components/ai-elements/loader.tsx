"use client"

import * as React from "react"

import { Spinner } from "@/components/ui/spinner"
import { cn } from "@/lib/utils"

export type LoaderProps = React.HTMLAttributes<HTMLDivElement> & {
  label?: string
}

export function Loader({ className, label, ...props }: LoaderProps) {
  return (
    <div
      className={cn(
        "flex animate-pulse items-center gap-2 text-sm font-medium text-muted-foreground",
        className
      )}
      {...props}
    >
      <Spinner className="size-4" />
      {label ? <span>{label}</span> : null}
    </div>
  )
}
