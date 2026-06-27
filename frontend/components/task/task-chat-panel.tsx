"use client"

import * as React from "react"
import { useChat } from "@ai-sdk/react"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowRight01Icon,
  Cancel01Icon,
  QuoteDownIcon,
  SparklesIcon,
} from "@hugeicons/core-free-icons"

import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation"
import { Message, MessageContent } from "@/components/ai-elements/message"
import { Response } from "@/components/ai-elements/response"
import { Loader } from "@/components/ai-elements/loader"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { useApi } from "@/hooks/use-api"
import { useT } from "@/lib/i18n/provider"
import {
  createCoachTransport,
  createSession,
  messageText,
  type CoachUIMessage,
} from "@/lib/agent"

export function TaskChatPanel({
  taskTitle,
  lessonContent,
  selection,
  onClearSelection,
}: {
  taskTitle: string
  lessonContent?: string | null
  selection?: string | null
  onClearSelection?: () => void
}) {
  const dict = useT()
  const t = dict.companion
  const { authenticated, run } = useApi()
  const [sessionId, setSessionId] = React.useState<string | null>(null)
  const [input, setInput] = React.useState("")

  const buildContext = React.useCallback(() => {
    const parts: string[] = []
    if (taskTitle) parts.push(`Lesson title: ${taskTitle}`)
    if (lessonContent && lessonContent.trim()) {
      parts.push(`Lesson content:\n${lessonContent.trim().slice(0, 16000)}`)
    }
    if (selection && selection.trim()) {
      parts.push(`Passage the learner highlighted:\n${selection.trim()}`)
    }
    return parts.join("\n\n")
  }, [taskTitle, lessonContent, selection])

  React.useEffect(() => {
    if (!authenticated || sessionId) return
    let cancelled = false
    void run(() => createSession(taskTitle.slice(0, 60)))
      .then((session) => {
        if (!cancelled && session) setSessionId(session.id)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [authenticated, sessionId, taskTitle, run])

  const transport = React.useMemo(
    () => (sessionId ? createCoachTransport(sessionId) : undefined),
    [sessionId]
  )

  const { messages, sendMessage, status, error } = useChat<CoachUIMessage>({
    id: sessionId ?? undefined,
    transport,
    messages: [],
  })
  const isBusy = status === "streaming" || status === "submitted"
  const ready = !!sessionId

  const send = React.useCallback(() => {
    const text = input.trim()
    if (!text || !ready || isBusy) return
    setInput("")
    const context = buildContext()
    void sendMessage(
      { text },
      context ? { body: { context } } : undefined
    )
  }, [input, ready, isBusy, sendMessage, buildContext])

  const isEmpty = messages.length === 0

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {isEmpty ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <span className="flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <HugeiconsIcon
              icon={SparklesIcon}
              strokeWidth={1.8}
              className="size-5"
            />
          </span>
          <p className="text-sm font-medium text-foreground">
            {dict.companion.emptyTitle}
          </p>
          <p className="max-w-xs text-xs leading-relaxed text-muted-foreground">
            {dict.companion.emptyDescription}
          </p>
        </div>
      ) : (
        <Conversation>
          <ConversationContent>
            <ul className="flex flex-col gap-6">
              {messages.map((message, index) => {
                const isLast = index === messages.length - 1
                return (
                  <li key={message.id}>
                    {message.role === "user" ? (
                      <Message from="user">
                        <MessageContent variant="contained">
                          <span className="whitespace-pre-wrap">
                            {messageText(message)}
                          </span>
                        </MessageContent>
                      </Message>
                    ) : (
                      <Message from="assistant">
                        <MessageContent>
                          {messageText(message) ? (
                            <Response>{messageText(message)}</Response>
                          ) : isBusy && isLast ? (
                            <Loader />
                          ) : null}
                        </MessageContent>
                      </Message>
                    )}
                  </li>
                )
              })}
            </ul>
            {error ? (
              <p className="mt-4 rounded-lg border border-destructive/20 bg-destructive/5 px-3 py-2 text-xs font-medium text-destructive">
                {dict.errors.generic}
              </p>
            ) : null}
          </ConversationContent>
          <ConversationScrollButton />
        </Conversation>
      )}

      <div className="shrink-0 border-t border-border p-3">
        {selection && selection.trim() ? (
          <div className="mb-2 flex items-start gap-2 rounded-lg border border-border bg-muted/40 px-2.5 py-2">
            <HugeiconsIcon
              icon={QuoteDownIcon}
              strokeWidth={2}
              className="mt-0.5 size-3.5 shrink-0 text-muted-foreground"
            />
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-medium text-muted-foreground">
                {t.selectionLabel}
              </p>
              <p className="line-clamp-2 text-xs text-foreground/80">
                {selection.trim()}
              </p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="size-6 shrink-0"
              onClick={onClearSelection}
              aria-label={t.selectionRemove}
            >
              <HugeiconsIcon
                icon={Cancel01Icon}
                strokeWidth={2}
                className="size-3.5"
              />
            </Button>
          </div>
        ) : null}
        <div className="flex items-end gap-2">
          <Textarea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault()
                send()
              }
            }}
            placeholder={t.inputPlaceholder}
            rows={1}
            disabled={!ready}
            className="max-h-32 min-h-[2.5rem] flex-1 resize-none"
          />
          <Button
            type="button"
            size="icon"
            onClick={send}
            disabled={!ready || isBusy || input.trim().length === 0}
            aria-label={t.inputPlaceholder}
          >
            <HugeiconsIcon
              icon={ArrowRight01Icon}
              strokeWidth={2}
              className="size-4 rtl:rotate-180"
            />
          </Button>
        </div>
      </div>
    </div>
  )
}
