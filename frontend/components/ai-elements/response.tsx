"use client"

import * as React from "react"
import { Streamdown } from "streamdown"
import { createMathPlugin } from "@streamdown/math"
import "katex/dist/katex.min.css"

import { cn } from "@/lib/utils"

const mathPlugin = createMathPlugin({ singleDollarTextMath: true })

export type ResponseProps = React.ComponentProps<typeof Streamdown>

export function Response({ className, plugins, ...props }: ResponseProps) {
  return (
    <Streamdown
      plugins={{ math: mathPlugin, ...plugins }}
      className={cn(
        "space-y-3 font-serif text-[16px] leading-7 text-foreground/90",
        "[&_pre]:my-2 [&_pre]:rounded-xl [&_pre]:font-sans [&_pre]:text-sm",
        "[&_code]:rounded [&_code]:bg-secondary/60 [&_code]:px-1 [&_code]:py-0.5 [&_code]:text-[0.9em]",
        "[&_a]:font-medium [&_a]:text-primary [&_a]:underline-offset-2 hover:[&_a]:underline",
        "[&_ol]:list-decimal [&_ol]:pl-5 [&_ul]:list-disc [&_ul]:pl-5",
        "[&_h1]:text-lg [&_h1]:font-semibold [&_h2]:text-base [&_h2]:font-semibold [&_h3]:font-semibold",
        "[&_blockquote]:border-l-2 [&_blockquote]:border-border [&_blockquote]:pl-3 [&_blockquote]:text-muted-foreground",
        className
      )}
      {...props}
    />
  )
}
