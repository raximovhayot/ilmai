"use client"

import * as React from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowDown01Icon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

type ConversationContextValue = {
  scrollRef: React.RefObject<HTMLDivElement | null>
  isAtBottom: boolean
  scrollToBottom: () => void
}

const ConversationContext =
  React.createContext<ConversationContextValue | null>(null)

function useConversation(): ConversationContextValue {
  const ctx = React.useContext(ConversationContext)
  if (!ctx) {
    throw new Error(
      "Conversation components must be used within <Conversation>"
    )
  }
  return ctx
}

export function Conversation({
  className,
  children,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  const scrollRef = React.useRef<HTMLDivElement | null>(null)
  const isAtBottomRef = React.useRef(true)
  const [isAtBottom, setIsAtBottom] = React.useState(true)

  const scrollToBottom = React.useCallback(() => {
    const node = scrollRef.current
    if (node) node.scrollTo({ top: node.scrollHeight, behavior: "smooth" })
  }, [])

  React.useEffect(() => {
    const node = scrollRef.current
    if (!node) return

    const handleScroll = () => {
      const distance = node.scrollHeight - node.scrollTop - node.clientHeight
      const atBottom = distance < 64
      isAtBottomRef.current = atBottom
      setIsAtBottom(atBottom)
    }
    handleScroll()
    node.addEventListener("scroll", handleScroll, { passive: true })

    const observer = new MutationObserver(() => {
      if (isAtBottomRef.current) {
        node.scrollTop = node.scrollHeight
      }
    })
    observer.observe(node, { childList: true, subtree: true })

    return () => {
      node.removeEventListener("scroll", handleScroll)
      observer.disconnect()
    }
  }, [])

  return (
    <ConversationContext.Provider
      value={{ scrollRef, isAtBottom, scrollToBottom }}
    >
      <div className="relative min-h-0 flex-1">
        <div
          ref={scrollRef}
          className={cn("h-full overflow-y-auto", className)}
          {...props}
        >
          {children}
        </div>
      </div>
    </ConversationContext.Provider>
  )
}

export function ConversationContent({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("mx-auto w-full max-w-3xl px-4 py-8 md:px-6", className)}
      {...props}
    />
  )
}

export function ConversationScrollButton({
  className,
  label,
  ...props
}: React.ComponentProps<typeof Button> & { label?: string }) {
  const { isAtBottom, scrollToBottom } = useConversation()
  if (isAtBottom) return null
  return (
    <Button
      type="button"
      variant="outline"
      size="icon"
      onClick={scrollToBottom}
      className={cn(
        "absolute bottom-4 left-1/2 size-9 -translate-x-1/2 rounded-full shadow-md",
        className
      )}
      {...props}
    >
      <HugeiconsIcon
        icon={ArrowDown01Icon}
        strokeWidth={2.5}
        className="size-4"
      />
      <span className="sr-only">{label ?? "Scroll to bottom"}</span>
    </Button>
  )
}
