"use client"

import * as React from "react"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  BotIcon,
  RefreshIcon,
  Sent02Icon,
  SparklesIcon,
  UserIcon,
} from "@hugeicons/core-free-icons"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { askChat, type ChatMessage, type Citation } from "@/lib/chat"
import { useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

type Props = {
  topicId: string
  topicName: string
}

export function ChatPane({ topicId }: Props) {
  const t = useT()
  const { status } = useSession()

  const [messages, setMessages] = React.useState<ChatMessage[]>([])
  const [conversationId, setConversationId] = React.useState<
    string | undefined
  >()
  const [input, setInput] = React.useState("")
  const [sending, setSending] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)
  const [openCitation, setOpenCitation] = React.useState<Citation | null>(null)
  const listRef = React.useRef<HTMLDivElement>(null)

  React.useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [messages.length, sending])

  const submit = React.useCallback(async () => {
    const text = input.trim()
    if (!text || status !== "authenticated") return
    setSending(true)
    setError(null)

    const userMsg: ChatMessage = {
      id: `local-${Date.now()}`,
      conversationId: conversationId ?? "",
      role: "USER",
      content: text,
      citations: [],
      createdAt: new Date().toISOString(),
    }
    setMessages((prev) => [...prev, userMsg])
    setInput("")

    try {
      const res = await askChat({
        topicId,
        message: text,
        conversationId,
      })
      setConversationId(res.conversationId)
      const assistantMsg: ChatMessage = {
        id: `assistant-${Date.now()}`,
        conversationId: res.conversationId,
        role: "ASSISTANT",
        content: res.answer,
        citations: res.citations,
        createdAt: new Date().toISOString(),
      }
      setMessages((prev) => [...prev, assistantMsg])
    } catch {
      setError(t.errors.generic)
      toast.error(t.errors.generic)
    } finally {
      setSending(false)
    }
  }, [status, conversationId, input, t.errors.generic, topicId])

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
      e.preventDefault()
      void submit()
    }
  }

  const reset = () => {
    setMessages([])
    setConversationId(undefined)
    setError(null)
  }

  return (
    <div className="flex flex-col gap-3">
      <Card className="overflow-hidden">
        <div
          ref={listRef}
          className="flex min-h-[55vh] flex-col gap-3 overflow-y-auto p-4 lg:max-h-[55vh] lg:min-h-[40vh]"
        >
          {messages.length === 0 ? (
            <div className="m-auto flex w-full max-w-md flex-col items-center gap-3 text-center">
              <span className="flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
                <HugeiconsIcon icon={SparklesIcon} strokeWidth={2} />
              </span>
              <div className="flex flex-col gap-1">
                <p className="text-base font-medium">{t.chat.emptyTitle}</p>
                <p className="text-sm text-muted-foreground">
                  {t.chat.emptySubtitle}
                </p>
              </div>
              <div className="flex flex-wrap justify-center gap-2 pt-1">
                {t.chat.starterPrompts.map((prompt) => (
                  <button
                    key={prompt}
                    type="button"
                    onClick={() => setInput(prompt)}
                    className="rounded-full border border-border bg-card px-3 py-1.5 text-xs text-foreground transition-colors hover:bg-accent/40"
                  >
                    {prompt}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            messages.map((m) => (
              <MessageBubble
                key={m.id}
                message={m}
                onCitationClick={setOpenCitation}
              />
            ))
          )}
          {sending && (
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Spinner className="size-3" />
              {t.chat.typing}
            </div>
          )}
        </div>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="sticky bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-10 -mx-4 flex items-end gap-2 border-t border-border bg-background/95 px-4 py-2 backdrop-blur supports-[backdrop-filter]:bg-background/80 md:-mx-6 md:px-6 lg:static lg:mx-0 lg:rounded-2xl lg:border lg:bg-card lg:p-2 lg:backdrop-blur-none">
        <Textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
          rows={2}
          placeholder={t.chat.placeholder}
          disabled={sending}
          className="min-h-12 resize-none border-0 bg-transparent shadow-none focus-visible:ring-0"
        />
        <div className="flex shrink-0 items-center gap-1.5 pe-1 pb-1">
          {messages.length > 0 && (
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={reset}
              type="button"
              aria-label={t.chat.newConversation}
              title={t.chat.newConversation}
            >
              <HugeiconsIcon icon={RefreshIcon} strokeWidth={2} />
            </Button>
          )}
          <Button
            onClick={() => void submit()}
            disabled={sending || !input.trim()}
            size="sm"
          >
            {sending ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <HugeiconsIcon
                icon={Sent02Icon}
                strokeWidth={2}
                data-icon="inline-start"
              />
            )}
            {t.chat.send}
          </Button>
        </div>
      </div>

      <CitationDialog
        citation={openCitation}
        onClose={() => setOpenCitation(null)}
      />
    </div>
  )
}

function MessageBubble({
  message,
  onCitationClick,
}: {
  message: ChatMessage
  onCitationClick: (c: Citation) => void
}) {
  const t = useT()
  const isUser = message.role === "USER"
  return (
    <div
      className={cn(
        "flex max-w-full items-start gap-2",
        isUser ? "flex-row-reverse self-end" : "self-start"
      )}
    >
      <span
        className={cn(
          "flex size-7 shrink-0 items-center justify-center rounded-full",
          isUser
            ? "bg-secondary text-secondary-foreground"
            : "bg-primary/10 text-primary"
        )}
        aria-hidden
      >
        <HugeiconsIcon
          icon={isUser ? UserIcon : BotIcon}
          strokeWidth={2}
          className="size-4"
        />
      </span>
      <div
        className={cn(
          "flex max-w-[85%] min-w-0 flex-col gap-2 rounded-2xl border px-3.5 py-2.5 text-sm",
          isUser
            ? "border-secondary bg-secondary text-secondary-foreground"
            : "border-border bg-card text-foreground"
        )}
      >
        <p className="break-words whitespace-pre-wrap">{message.content}</p>
        {!isUser && message.citations.length === 0 && message.content && (
          <p className="text-xs text-amber-600 dark:text-amber-400">
            {t.chat.ungrounded}
          </p>
        )}
        {!isUser && message.citations.length > 0 && (
          <div className="flex flex-col gap-1.5 pt-1">
            <span className="text-xs font-medium tracking-wider text-muted-foreground uppercase">
              {t.chat.citations}
            </span>
            <div className="flex flex-wrap gap-1.5">
              {message.citations.map((c) => (
                <button
                  key={`${c.materialId}-${c.chunkIndex}`}
                  type="button"
                  onClick={() => onCitationClick(c)}
                  className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2 py-0.5 text-xs hover:bg-accent"
                >
                  <span className="font-medium">{c.materialName}</span>
                  <span className="text-muted-foreground">
                    · #{c.chunkIndex + 1}
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function CitationDialog({
  citation,
  onClose,
}: {
  citation: Citation | null
  onClose: () => void
}) {
  const t = useT()
  return (
    <Dialog
      open={!!citation}
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
    >
      <DialogContent className="max-w-lg">
        {citation && (
          <>
            <DialogHeader className="flex flex-row items-start justify-between gap-3 pe-8">
              <div className="flex flex-col gap-0.5">
                <span className="text-xs tracking-wider text-muted-foreground uppercase">
                  {t.chat.chunkLabel} #{citation.chunkIndex + 1}
                </span>
                <DialogTitle className="font-heading text-base font-semibold">
                  {citation.materialName}
                </DialogTitle>
              </div>
              <Badge variant="outline" className="shrink-0">
                {Math.round(citation.score * 100)}%
              </Badge>
            </DialogHeader>
            <p className="rounded-lg bg-muted px-3 py-3 text-sm leading-relaxed">
              {citation.preview}
            </p>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
