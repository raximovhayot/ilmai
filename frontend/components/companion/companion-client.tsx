"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  AlertCircleIcon,
  Cancel01Icon,
  CheckmarkCircle02Icon,
  File01Icon,
  Loading03Icon,
  PlusSignIcon,
  SparklesIcon,
} from "@hugeicons/core-free-icons"
import { useChat } from "@ai-sdk/react"

import { Button } from "@/components/ui/button"
import { QuizCard } from "@/components/companion/quiz-card"
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation"
import { Message, MessageContent } from "@/components/ai-elements/message"
import { Response } from "@/components/ai-elements/response"
import { Loader } from "@/components/ai-elements/loader"
import {
  Sources,
  SourcesContent,
  SourcesTrigger,
  Source,
} from "@/components/ai-elements/sources"
import { Suggestion, Suggestions } from "@/components/ai-elements/suggestion"
import {
  PromptInput,
  PromptInputButton,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputToolbar,
  PromptInputTools,
} from "@/components/ai-elements/prompt-input"
import { useApi } from "@/hooks/use-api"
import { listSpaces } from "@/lib/spaces"
import {
  uploadMaterialFile,
  getMaterial,
  type MaterialStatus,
} from "@/lib/materials"
import { useT } from "@/lib/i18n/provider"
import { notifyError } from "@/lib/notify"
import { cn } from "@/lib/utils"
import {
  createSession,
  createCoachTransport,
  listSessions,
  messageText,
  type ChatSession,
  type CoachDataParts,
  type CoachUIMessage,
} from "@/lib/agent"

type ChatAttachment = {
  localId: string
  name: string
  status: "UPLOADING" | MaterialStatus
}

type Block =
  | { kind: "text"; text: string; low: boolean }
  | { kind: "citations"; items: CoachDataParts["citation"][] }
  | { kind: "quiz"; data: CoachDataParts["quiz"] }
  | { kind: "actions"; items: CoachDataParts["action"][] }

function buildBlocks(parts: CoachUIMessage["parts"]): Block[] {
  const blocks: Block[] = []
  for (const part of parts) {
    if (part.type === "dynamic-tool" || part.type === "step-start") {
      continue
    }
    const last = blocks[blocks.length - 1]
    if (part.type === "text") {
      if (last?.kind === "text") {
        last.text += part.text
      } else {
        blocks.push({ kind: "text", text: part.text, low: false })
      }
    } else if (part.type === "data-citation") {
      if (last?.kind === "citations") {
        last.items.push(part.data)
      } else {
        blocks.push({ kind: "citations", items: [part.data] })
      }
    } else if (part.type === "data-quiz") {
      blocks.push({ kind: "quiz", data: part.data })
    } else if (part.type === "data-action") {
      if (last?.kind === "actions") {
        last.items.push(part.data)
      } else {
        blocks.push({ kind: "actions", items: [part.data] })
      }
    } else if (part.type === "data-confidence") {
      if (part.data.level === "low") {
        const lastText = [...blocks]
          .reverse()
          .find((block) => block.kind === "text")
        if (lastText?.kind === "text") lastText.low = true
      }
    }
  }
  return blocks
}

const sessionMessagesCache = new Map<string, CoachUIMessage[]>()

export function CompanionClient({
  initialSessions,
  seed,
  activeSessionId,
}: {
  initialSessions: ChatSession[]
  seed?: string
  activeSessionId?: string
}) {
  const dict = useT()
  const t = dict.companion
  const errors = dict.errors
  const router = useRouter()
  const { authenticated, run } = useApi()

  const activeId = activeSessionId ?? null
  const [input, setInput] = React.useState("")
  const [sessions, setSessions] = React.useState<ChatSession[]>(initialSessions)
  const [attachments, setAttachments] = React.useState<ChatAttachment[]>([])
  const fileInputRef = React.useRef<HTMLInputElement>(null)

  const transport = React.useMemo(() => {
    if (!authenticated || !activeId) return undefined
    return createCoachTransport(activeId)
  }, [authenticated, activeId])

  const { messages, sendMessage, status, stop, error, clearError } =
    useChat<CoachUIMessage>({
      id: activeId ?? undefined,
      transport,
      messages: activeId ? (sessionMessagesCache.get(activeId) ?? []) : [],
    })
  const isBusy = status === "streaming" || status === "submitted"

  React.useEffect(() => {
    if (activeId) {
      sessionMessagesCache.set(activeId, messages)
    }
  }, [messages, activeId])

  const pendingPromptRef = React.useRef<string | null>(null)

  React.useEffect(() => {
    if (activeId && pendingPromptRef.current) {
      const promptToSend = pendingPromptRef.current
      pendingPromptRef.current = null
      void sendMessage({ text: promptToSend })
    }
  }, [activeId, sendMessage])

  React.useEffect(() => {
    if (!authenticated || !activeId) return
    let cancelled = false
    void run(() => listSessions())
      .then((list) => {
        if (!cancelled && list) setSessions(list)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [authenticated, activeId, run])

  const [spaceId, setSpaceId] = React.useState<string | null>(null)

  React.useEffect(() => {
    if (!authenticated) return
    let cancelled = false
    void run(() => listSpaces())
      .then((spaces) => {
        if (!cancelled && spaces.length > 0) setSpaceId(spaces[0].id)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [authenticated, run])

  const attachmentBlocking = attachments.some((a) => a.status !== "READY")

  const clearAttachments = React.useCallback(() => {
    setAttachments([])
    if (fileInputRef.current) fileInputRef.current.value = ""
  }, [])

  const removeAttachment = React.useCallback((localId: string) => {
    setAttachments((prev) => prev.filter((a) => a.localId !== localId))
  }, [])

  const handleFiles = React.useCallback(
    (files: File[]) => {
      if (!authenticated) {
        router.push("/login")
        return
      }
      if (!spaceId) return
      for (const file of files) {
        const localId = crypto.randomUUID()
        setAttachments((prev) => [
          ...prev,
          { localId, name: file.name, status: "UPLOADING" },
        ])
        void (async () => {
          try {
            const created = await run(() => uploadMaterialFile(spaceId, file))
            if (!created) {
              removeAttachment(localId)
              return
            }
            let current = created
            setAttachments((prev) =>
              prev.map((a) =>
                a.localId === localId
                  ? { ...a, name: current.title, status: current.status }
                  : a
              )
            )
            while (
              current.status === "PENDING" ||
              current.status === "PROCESSING"
            ) {
              await new Promise((resolve) => setTimeout(resolve, 1500))
              const next = await run(() => getMaterial(current.id))
              if (!next) break
              current = next
              setAttachments((prev) =>
                prev.map((a) =>
                  a.localId === localId ? { ...a, status: current.status } : a
                )
              )
            }
          } catch (err) {
            notifyError(err, errors)
            removeAttachment(localId)
          }
        })()
      }
    },
    [authenticated, spaceId, run, router, errors, removeAttachment]
  )

  const send = React.useCallback(
    async (promptText: string) => {
      const text = promptText.trim()
      if (!text || isBusy || attachmentBlocking) return
      if (!authenticated) {
        router.push("/login")
        return
      }

      if (!activeId) {
        try {
          const created = await run(() => createSession(text.slice(0, 60)))
          if (!created) return
          pendingPromptRef.current = text
          setInput("")
          clearAttachments()
          router.replace(`/companion?session=${created.id}`)
        } catch (err) {
          notifyError(err, errors)
        }
        return
      }

      setInput("")
      clearAttachments()
      clearError()
      void sendMessage({ text })
    },
    [
      authenticated,
      activeId,
      isBusy,
      attachmentBlocking,
      clearAttachments,
      clearError,
      run,
      router,
      sendMessage,
      errors,
    ]
  )

  const sendRef = React.useRef(send)
  const seededRef = React.useRef(false)
  React.useEffect(() => {
    sendRef.current = send
  })
  React.useEffect(() => {
    if (!seed || seededRef.current) return
    seededRef.current = true
    void sendRef.current(seed)
  }, [seed])

  function startNewSession() {
    stop()
    setInput("")
    router.push("/companion")
  }

  const isEmpty = messages.length === 0

  const activeSession = activeId
    ? sessions.find((session) => session.id === activeId)
    : undefined
  const sessionTitle = activeSession?.title?.trim() || t.untitled

  const composer = (
    <PromptInput
      onSubmit={(event) => {
        event.preventDefault()
        void send(input)
      }}
    >
      <PromptInputTextarea
        value={input}
        onChange={(event) => setInput(event.target.value)}
        placeholder={t.inputPlaceholder}
        onSubmitMessage={() => void send(input)}
      />
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="hidden"
        accept=".pdf,.doc,.docx,.txt,.md"
        onChange={(event) => {
          const files = Array.from(event.target.files ?? [])
          if (files.length > 0) handleFiles(files)
          event.target.value = ""
        }}
      />
      {attachments.length > 0 ? (
        <div className="flex flex-wrap gap-1.5 px-3 pt-1">
          {attachments.map((item) => {
            const message =
              item.status === "READY"
                ? t.attachmentReady.replace("{name}", item.name)
                : item.status === "FAILED"
                  ? t.attachmentFailed.replace("{name}", item.name)
                  : t.attachmentProcessing.replace("{name}", item.name)
            return (
              <div
                key={item.localId}
                className={cn(
                  "inline-flex max-w-full items-center gap-2 rounded-xl border px-3 py-1.5 text-xs",
                  item.status === "FAILED"
                    ? "border-destructive/30 bg-destructive/5 text-destructive"
                    : item.status === "READY"
                      ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400"
                      : "border-border/60 bg-secondary/50 text-muted-foreground"
                )}
              >
                {item.status === "READY" ? (
                  <HugeiconsIcon
                    icon={CheckmarkCircle02Icon}
                    strokeWidth={2}
                    className="size-3.5 shrink-0"
                  />
                ) : item.status === "FAILED" ? (
                  <HugeiconsIcon
                    icon={AlertCircleIcon}
                    strokeWidth={2}
                    className="size-3.5 shrink-0"
                  />
                ) : (
                  <HugeiconsIcon
                    icon={Loading03Icon}
                    strokeWidth={2}
                    className="size-3.5 shrink-0 animate-spin"
                  />
                )}
                <HugeiconsIcon
                  icon={File01Icon}
                  strokeWidth={2}
                  className="size-3.5 shrink-0"
                />
                <span className="truncate font-medium">{message}</span>
                <button
                  type="button"
                  onClick={() => removeAttachment(item.localId)}
                  aria-label={t.attachmentRemove}
                  className="ml-1 shrink-0 rounded-full p-0.5 opacity-70 transition-opacity hover:opacity-100"
                >
                  <HugeiconsIcon
                    icon={Cancel01Icon}
                    strokeWidth={2.5}
                    className="size-3.5"
                  />
                </button>
              </div>
            )
          })}
        </div>
      ) : null}
      <PromptInputToolbar>
        <PromptInputTools>
          <PromptInputButton
            onClick={() => fileInputRef.current?.click()}
            aria-label={t.attachFile}
            title={t.attachFile}
          >
            <HugeiconsIcon
              icon={PlusSignIcon}
              strokeWidth={2}
              className="size-4"
            />
          </PromptInputButton>
        </PromptInputTools>
        <PromptInputTools>
          <PromptInputSubmit
            status={status}
            onStop={stop}
            disabled={input.trim().length === 0 || attachmentBlocking}
            sendLabel={t.send}
            stopLabel={t.stop}
            title={attachmentBlocking ? t.attachmentGate : undefined}
          />
        </PromptInputTools>
      </PromptInputToolbar>
    </PromptInput>
  )

  return (
    <div className="flex h-[calc(100dvh-7.5rem)] flex-col bg-background lg:h-[calc(100dvh-3.5rem)]">
      {isEmpty ? (
        <div className="flex flex-1 flex-col items-center justify-center px-4">
          <div className="w-full max-w-2xl">
            <div className="mb-8 flex flex-col items-center gap-3 text-center">
              <span className="flex size-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                <HugeiconsIcon
                  icon={SparklesIcon}
                  strokeWidth={1.8}
                  className="size-6"
                />
              </span>
              <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
                {t.emptyTitle}
              </h1>
              <p className="max-w-md text-sm leading-relaxed text-muted-foreground">
                {t.emptyDescription}
              </p>
            </div>
            {composer}
            <p className="pt-3 text-center text-xs text-muted-foreground/70">
              {t.disclaimer}
            </p>
          </div>
        </div>
      ) : (
        <>
          <header className="flex h-12 shrink-0 items-center justify-between gap-3 px-4">
            <span
              className="min-w-0 truncate rounded-lg px-2 py-1 text-sm font-medium text-foreground"
              title={sessionTitle}
            >
              {sessionTitle}
            </span>
            <Button
              variant="ghost"
              size="sm"
              onClick={startNewSession}
              className="h-9 gap-1.5 rounded-xl px-3 text-muted-foreground hover:bg-secondary hover:text-foreground lg:hidden"
            >
              <HugeiconsIcon
                icon={PlusSignIcon}
                strokeWidth={2.5}
                className="size-3.5"
              />
              <span className="text-xs font-semibold">{t.newSession}</span>
            </Button>
          </header>

          <Conversation>
            <ConversationContent>
              <ul className="flex flex-col gap-8">
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
                            <AssistantMessage
                              message={message}
                              streaming={isBusy && isLast}
                              onAction={(label) => void send(label)}
                            />
                          </MessageContent>
                        </Message>
                      )}
                    </li>
                  )
                })}
              </ul>
              {error ? (
                <div className="mt-6 flex items-center gap-2 rounded-xl border border-destructive/20 bg-destructive/5 px-3 py-2 text-xs font-medium text-destructive">
                  <HugeiconsIcon
                    icon={AlertCircleIcon}
                    className="size-3.5 shrink-0"
                    strokeWidth={2}
                  />
                  <span>{error.message || errors.generic}</span>
                </div>
              ) : null}
            </ConversationContent>
            <ConversationScrollButton />
          </Conversation>

          <div className="bg-background px-4 pt-2 pb-3">
            <div className="mx-auto w-full max-w-3xl">
              {composer}
              <p className="pt-2 text-center text-xs text-muted-foreground/70">
                {t.disclaimer}
              </p>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function AssistantMessage({
  message,
  streaming,
  onAction,
}: {
  message: CoachUIMessage
  streaming: boolean
  onAction: (label: string) => void
}) {
  const t = useT().companion
  const blocks = buildBlocks(message.parts)
  const hasText = message.parts.some(
    (part) => part.type === "text" && part.text.length > 0
  )
  const hasCitation = blocks.some(
    (block) => block.kind === "citations" && block.items.length > 0
  )

  return (
    <>
      {streaming && !hasText ? <Loader label={t.thinking} /> : null}

      {blocks.map((block, index) => {
        if (block.kind === "text") {
          const isUngrounded =
            (!streaming && !hasCitation && block.text.trim().length > 0) ||
            block.low
          return (
            <div key={index} className="flex flex-col gap-2">
              <Response>{block.text}</Response>
              {isUngrounded ? (
                <span className="inline-flex w-fit items-center gap-1.5 rounded-full border border-amber-500/20 bg-amber-500/10 px-2.5 py-1 text-xs font-semibold text-amber-700 dark:text-amber-400">
                  <HugeiconsIcon
                    icon={AlertCircleIcon}
                    className="size-3.5"
                    strokeWidth={2}
                  />
                  {t.ungrounded}
                </span>
              ) : null}
            </div>
          )
        }
        if (block.kind === "citations") {
          return (
            <Sources key={index} defaultOpen>
              <SourcesTrigger count={block.items.length} label={t.citations} />
              <SourcesContent>
                {block.items.map((citation) => (
                  <Source
                    key={citation.id}
                    locator={citation.locator}
                    snippet={citation.snippet}
                    score={citation.score}
                  />
                ))}
              </SourcesContent>
            </Sources>
          )
        }
        if (block.kind === "quiz") {
          return (
            <QuizCard key={index} part={{ ...block.data, type: "quiz_card" }} />
          )
        }
        return (
          <Suggestions key={index} className="mt-1">
            {block.items.map((action, actionIndex) => (
              <Suggestion
                key={actionIndex}
                suggestion={action.label}
                onSuggestionClick={onAction}
              />
            ))}
          </Suggestions>
        )
      })}
    </>
  )
}
