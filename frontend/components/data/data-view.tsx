"use client"

import * as React from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  Add01Icon,
  Delete02Icon,
  DocumentAttachmentIcon,
  Folder01Icon,
  PencilEdit02Icon,
  Upload04Icon,
} from "@hugeicons/core-free-icons"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import {
  Field,
  FieldContent,
  FieldError,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { MaterialStatusPill } from "@/components/materials/status-pill"
import { ApiClientError } from "@/lib/api"
import { useT } from "@/lib/i18n/provider"
import {
  deleteMaterial,
  getMaterial,
  getSpaceContents,
  type MaterialResponse,
  type MaterialStatus,
  moveMaterial,
  uploadMaterialFile,
} from "@/lib/materials"
import { listRooms } from "@/lib/rooms"
import {
  createTopic,
  deleteTopic,
  renameTopic,
  type TopicResponse,
} from "@/lib/topics"

const PAGE_SIZE = 24

type DataViewProps = {
  initialTopics: TopicResponse[]
  loadError: boolean
}

function topicErrorMessageKey(
  errorCode: string | undefined
): "nameBlank" | "nameTaken" | "notFound" | "generic" {
  switch (errorCode) {
    case "TOPIC_NAME_BLANK":
      return "nameBlank"
    case "TOPIC_NAME_TAKEN":
      return "nameTaken"
    case "TOPIC_NOT_FOUND":
      return "notFound"
    default:
      return "generic"
  }
}

function materialErrorMessageKey(
  errorCode: string | undefined
): keyof ReturnType<typeof useT>["materials"]["errors"] {
  switch (errorCode) {
    case "MATERIAL_NOT_FOUND":
      return "notFound"
    case "MATERIAL_TOPIC_NOT_FOUND":
      return "topicNotFound"
    case "MATERIAL_CONTENT_REQUIRED":
      return "contentRequired"
    case "MATERIAL_TITLE_BLANK":
      return "titleBlank"
    case "MATERIAL_UNSUPPORTED_TYPE":
      return "unsupportedType"
    case "MATERIAL_TOO_LARGE":
      return "tooLarge"
    case "MATERIAL_STORAGE_FAILED":
      return "storageFailed"
    default:
      return "generic"
  }
}

function isProcessing(status: MaterialStatus): boolean {
  return status === "PENDING" || status === "PROCESSING"
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function DataView({ initialTopics, loadError }: DataViewProps) {
  const dict = useT()
  const t = dict.topics
  const m = dict.materials
  const router = useRouter()
  const { status } = useSession()

  const fileInputRef = React.useRef<HTMLInputElement>(null)

  const [topics, setTopics] = React.useState<TopicResponse[]>(initialTopics)
  const [createOpen, setCreateOpen] = React.useState(false)
  const [name, setName] = React.useState("")
  const [submitting, setSubmitting] = React.useState(false)
  const [createError, setCreateError] = React.useState<string | null>(null)
  const [renameTarget, setRenameTarget] = React.useState<TopicResponse | null>(
    null
  )
  const [renameName, setRenameName] = React.useState("")
  const [renaming, setRenaming] = React.useState(false)
  const [renameError, setRenameError] = React.useState<string | null>(null)

  const [spaceId, setSpaceId] = React.useState<string | null>(null)
  const [items, setItems] = React.useState<MaterialResponse[]>([])
  const [page, setPage] = React.useState(0)
  const [hasMore, setHasMore] = React.useState(false)
  const [loading, setLoading] = React.useState(true)
  const [fetchError, setFetchError] = React.useState(false)
  const [reloadKey, setReloadKey] = React.useState(0)
  const [loadingMore, setLoadingMore] = React.useState(false)
  const [uploadError, setUploadError] = React.useState<string | null>(null)
  const [uploading, setUploading] = React.useState(false)
  const [dragActive, setDragActive] = React.useState(false)
  const [moveTarget, setMoveTarget] = React.useState<MaterialResponse | null>(
    null
  )
  const [moving, setMoving] = React.useState(false)

  function retryLoad() {
    setLoading(true)
    setFetchError(false)
    setReloadKey((key) => key + 1)
  }

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [rooms, contents] = await Promise.all([
          listRooms(),
          getSpaceContents(0, PAGE_SIZE),
        ])
        if (cancelled) return
        setSpaceId(rooms[0]?.id ?? null)
        setTopics(contents.topics)
        setItems(contents.items)
        setPage(0)
        setHasMore(contents.hasMore)
        setFetchError(false)
      } catch {
        if (!cancelled) setFetchError(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status, reloadKey])

  async function loadMore() {
    if (!hasMore || loadingMore) return
    setLoadingMore(true)
    try {
      const next = page + 1
      const contents = await getSpaceContents(next, PAGE_SIZE)
      setItems((prev) => [...prev, ...contents.items])
      setPage(next)
      setHasMore(contents.hasMore)
    } catch {
      // ignore
    } finally {
      setLoadingMore(false)
    }
  }

  React.useEffect(() => {
    if (status !== "authenticated") return
    const inflight = items.some((item) => isProcessing(item.status))
    if (!inflight) return
    const handle = setInterval(async () => {
      const updates = await Promise.all(
        items.map(async (item) => {
          if (!isProcessing(item.status)) return item
          try {
            const fresh = await getMaterial(item.id)
            return fresh ?? item
          } catch {
            return item
          }
        })
      )
      setItems(updates)
    }, 2000)
    return () => clearInterval(handle)
  }, [status, items])

  function openCreate() {
    setName("")
    setCreateError(null)
    setCreateOpen(true)
  }

  async function handleCreate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    const trimmed = name.trim()
    if (!trimmed) {
      setCreateError(t.errors.nameBlank)
      return
    }
    setSubmitting(true)
    setCreateError(null)
    try {
      const created = await createTopic(trimmed)
      if (created) {
        setTopics((prev) => [...prev, created])
      }
      setName("")
      setCreateOpen(false)
      toast.success(created?.name ?? trimmed)
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        setCreateError(t.errors[topicErrorMessageKey(code)])
      } else {
        setCreateError(t.errors.generic)
      }
    } finally {
      setSubmitting(false)
    }
  }

  function openRename(topic: TopicResponse) {
    setRenameTarget(topic)
    setRenameName(topic.name)
    setRenameError(null)
  }

  async function handleRename(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!renameTarget) return
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    const trimmed = renameName.trim()
    if (!trimmed) {
      setRenameError(t.errors.nameBlank)
      return
    }
    setRenaming(true)
    setRenameError(null)
    try {
      const updated = await renameTopic(renameTarget.id, trimmed)
      if (updated) {
        setTopics((prev) =>
          prev.map((existing) =>
            existing.id === updated.id ? updated : existing
          )
        )
      }
      setRenameTarget(null)
      toast.success(updated?.name ?? trimmed)
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        setRenameError(t.errors[topicErrorMessageKey(code)])
      } else {
        setRenameError(t.errors.generic)
      }
    } finally {
      setRenaming(false)
    }
  }

  async function handleDeleteTopic(topic: TopicResponse) {
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    if (typeof window !== "undefined" && !window.confirm(t.confirmDelete)) {
      return
    }
    try {
      await deleteTopic(topic.id)
      setTopics((prev) => prev.filter((existing) => existing.id !== topic.id))
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        toast.error(t.errors[topicErrorMessageKey(code)])
      } else {
        toast.error(t.errors.generic)
      }
    }
  }

  async function handleFileUpload(file: File | null) {
    if (!file) return
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    if (!spaceId) return
    setUploading(true)
    setUploadError(null)
    try {
      const created = await uploadMaterialFile(spaceId, file, null)
      if (created) {
        setItems((prev) => [created, ...prev])
      }
      toast.success(file.name)
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        setUploadError(m.errors[materialErrorMessageKey(code)])
      } else {
        setUploadError(m.errors.generic)
      }
    } finally {
      setUploading(false)
    }
  }

  async function handleDeleteItem(material: MaterialResponse) {
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    if (typeof window !== "undefined" && !window.confirm(m.confirmDelete)) {
      return
    }
    try {
      await deleteMaterial(material.id)
      setItems((prev) => prev.filter((item) => item.id !== material.id))
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        toast.error(m.errors[materialErrorMessageKey(code)])
      } else {
        toast.error(m.errors.generic)
      }
    }
  }

  async function handleMove(
    material: MaterialResponse,
    topicId: string | null
  ) {
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    setMoving(true)
    try {
      await moveMaterial(material.id, topicId)
      if (topicId) {
        setItems((prev) => prev.filter((item) => item.id !== material.id))
      }
      setMoveTarget(null)
      toast.success(m.move.success)
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        toast.error(m.errors[materialErrorMessageKey(code)])
      } else {
        toast.error(m.errors.generic)
      }
    } finally {
      setMoving(false)
    }
  }

  const isEmpty = topics.length === 0 && items.length === 0

  const dragStateRef = React.useRef<{
    uploading: boolean
    spaceId: string | null
  }>({ uploading, spaceId })
  const handleFileUploadRef = React.useRef(handleFileUpload)

  React.useEffect(() => {
    dragStateRef.current = { uploading, spaceId }
  }, [uploading, spaceId])

  React.useEffect(() => {
    handleFileUploadRef.current = handleFileUpload
  }, [handleFileUpload])

  React.useEffect(() => {
    let depth = 0
    function hasFiles(event: DragEvent) {
      return Array.from(event.dataTransfer?.types ?? []).includes("Files")
    }
    function canAccept(event: DragEvent) {
      if (dragStateRef.current.uploading || !dragStateRef.current.spaceId) {
        return false
      }
      return hasFiles(event)
    }
    function onDragEnter(event: DragEvent) {
      if (!canAccept(event)) return
      event.preventDefault()
      depth += 1
      setDragActive(true)
    }
    function onDragOver(event: DragEvent) {
      if (!canAccept(event)) return
      event.preventDefault()
    }
    function onDragLeave(event: DragEvent) {
      if (!hasFiles(event)) return
      depth -= 1
      if (depth <= 0) {
        depth = 0
        setDragActive(false)
      }
    }
    function onDrop(event: DragEvent) {
      if (!hasFiles(event)) return
      event.preventDefault()
      depth = 0
      setDragActive(false)
      if (dragStateRef.current.uploading || !dragStateRef.current.spaceId)
        return
      void handleFileUploadRef.current(event.dataTransfer?.files?.[0] ?? null)
    }
    window.addEventListener("dragenter", onDragEnter)
    window.addEventListener("dragover", onDragOver)
    window.addEventListener("dragleave", onDragLeave)
    window.addEventListener("drop", onDrop)
    return () => {
      window.removeEventListener("dragenter", onDragEnter)
      window.removeEventListener("dragover", onDragOver)
      window.removeEventListener("dragleave", onDragLeave)
      window.removeEventListener("drop", onDrop)
    }
  }, [])

  return (
    <div className="relative flex flex-col gap-6">
      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf,.docx,.txt,.md,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0] ?? null
          event.target.value = ""
          void handleFileUpload(file)
        }}
        disabled={uploading || !spaceId}
      />

      {dragActive ? (
        <div className="pointer-events-none fixed inset-0 z-50 flex flex-col items-center justify-center gap-2 bg-background/80 text-sm font-medium text-foreground backdrop-blur-sm">
          <div className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-primary p-12">
            <HugeiconsIcon icon={Upload04Icon} size={28} />
            <span>{m.uploadDropZone}</span>
          </div>
        </div>
      ) : null}

      <header className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">{t.title}</h1>
          <p className="text-sm text-muted-foreground">{t.subtitle}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" onClick={openCreate}>
            <HugeiconsIcon icon={Add01Icon} size={16} />
            <span>{t.createSubmit}</span>
          </Button>
          <Button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading || !spaceId}
          >
            {uploading ? (
              <Spinner className="size-4" />
            ) : (
              <HugeiconsIcon icon={Upload04Icon} size={16} />
            )}
            <span>{m.uploadTitle}</span>
          </Button>
        </div>
      </header>

      {loadError ? (
        <Alert variant="destructive">
          <AlertDescription>{t.failedToLoad}</AlertDescription>
        </Alert>
      ) : null}

      {uploadError ? (
        <Alert variant="destructive">
          <AlertDescription>{uploadError}</AlertDescription>
        </Alert>
      ) : null}

      {loading ? (
        <TopicsGridSkeleton />
      ) : fetchError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex flex-col items-start gap-3">
            <span>{t.failedToLoad}</span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={retryLoad}
            >
              {dict.common.retry}
            </Button>
          </AlertDescription>
        </Alert>
      ) : isEmpty ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <HugeiconsIcon icon={Folder01Icon} strokeWidth={2} />
            </EmptyMedia>
            <EmptyTitle>{t.title}</EmptyTitle>
            <EmptyDescription>{t.empty}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {topics.map((topic) => (
            <li key={`topic-${topic.id}`}>
              <Card>
                <CardContent className="flex items-start justify-between gap-3 p-4">
                  <Link
                    href={`/data/${topic.id}`}
                    className="flex min-w-0 flex-1 items-start gap-3 hover:opacity-80"
                  >
                    <span className="mt-0.5 inline-flex size-9 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
                      <HugeiconsIcon icon={Folder01Icon} size={18} />
                    </span>
                    <div className="flex min-w-0 flex-col">
                      <span className="truncate font-medium">{topic.name}</span>
                      <span className="text-xs text-muted-foreground">
                        {new Date(topic.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </Link>
                  <div className="flex shrink-0 items-center gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={t.rename}
                      onClick={() => openRename(topic)}
                    >
                      <HugeiconsIcon icon={PencilEdit02Icon} size={16} />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={t.delete}
                      onClick={() => handleDeleteTopic(topic)}
                    >
                      <HugeiconsIcon icon={Delete02Icon} size={16} />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}

          {items.map((material) => (
            <li key={`item-${material.id}`}>
              <Card>
                <CardContent className="flex items-start justify-between gap-3 p-4">
                  <div className="flex min-w-0 flex-1 items-start gap-3">
                    <span className="mt-0.5 inline-flex size-9 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground">
                      <HugeiconsIcon icon={DocumentAttachmentIcon} size={18} />
                    </span>
                    <div className="flex min-w-0 flex-col gap-1">
                      <span className="truncate font-medium">
                        {material.title}
                      </span>
                      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <MaterialStatusPill status={material.status} />
                        <span>
                          {new Date(material.createdAt).toLocaleDateString()}
                        </span>
                        {material.sizeBytes ? (
                          <span>· {formatSize(material.sizeBytes)}</span>
                        ) : null}
                      </div>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={m.move.action}
                      onClick={() => setMoveTarget(material)}
                    >
                      <HugeiconsIcon icon={Folder01Icon} size={16} />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={m.delete}
                      onClick={() => handleDeleteItem(material)}
                    >
                      <HugeiconsIcon icon={Delete02Icon} size={16} />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}

      {hasMore ? (
        <div className="flex justify-center">
          <Button
            type="button"
            variant="outline"
            onClick={loadMore}
            disabled={loadingMore}
          >
            {loadingMore ? <Spinner className="size-4" /> : null}
            <span>{t.loadMore}</span>
          </Button>
        </div>
      ) : null}

      <Dialog
        open={moveTarget !== null}
        onOpenChange={(open) => {
          if (!open) setMoveTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{m.move.title}</DialogTitle>
            <DialogDescription>{m.move.subtitle}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Button
              type="button"
              variant="outline"
              className="justify-between"
              disabled
            >
              <span className="flex items-center gap-2">
                <HugeiconsIcon icon={DocumentAttachmentIcon} size={16} />
                {m.move.root}
              </span>
              <span className="text-xs text-muted-foreground">
                {m.move.current}
              </span>
            </Button>
            {topics.length === 0 ? (
              <p className="text-sm text-muted-foreground">{m.move.empty}</p>
            ) : (
              topics.map((topic) => (
                <Button
                  key={`move-${topic.id}`}
                  type="button"
                  variant="outline"
                  className="justify-start"
                  disabled={moving}
                  onClick={() => moveTarget && handleMove(moveTarget, topic.id)}
                >
                  <HugeiconsIcon icon={Folder01Icon} size={16} />
                  <span className="truncate">{topic.name}</span>
                </Button>
              ))
            )}
          </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open)
          if (!open) setCreateError(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t.createTitle}</DialogTitle>
            <DialogDescription>{t.createSubtitle}</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-3" onSubmit={handleCreate}>
            <Field>
              <FieldLabel htmlFor="topic-name" className="sr-only">
                {t.createTitle}
              </FieldLabel>
              <FieldContent>
                <Input
                  id="topic-name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder={t.createPlaceholder}
                  maxLength={120}
                  disabled={submitting}
                  autoComplete="off"
                  autoFocus
                  aria-invalid={createError ? true : undefined}
                />
                {createError ? <FieldError>{createError}</FieldError> : null}
              </FieldContent>
            </Field>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setCreateOpen(false)}
                disabled={submitting}
              >
                {t.createCancel}
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? (
                  <Spinner className="size-4" />
                ) : (
                  <HugeiconsIcon icon={Add01Icon} size={16} />
                )}
                <span>{t.createSubmit}</span>
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={renameTarget !== null}
        onOpenChange={(open) => {
          if (!open) setRenameTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t.rename}</DialogTitle>
            <DialogDescription>{t.createSubtitle}</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-3" onSubmit={handleRename}>
            <Field>
              <FieldLabel htmlFor="rename-topic" className="sr-only">
                {t.rename}
              </FieldLabel>
              <FieldContent>
                <Input
                  id="rename-topic"
                  value={renameName}
                  onChange={(event) => setRenameName(event.target.value)}
                  placeholder={t.createPlaceholder}
                  maxLength={120}
                  disabled={renaming}
                  autoComplete="off"
                  aria-invalid={renameError ? true : undefined}
                />
                {renameError ? <FieldError>{renameError}</FieldError> : null}
              </FieldContent>
            </Field>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setRenameTarget(null)}
                disabled={renaming}
              >
                {t.createCancel}
              </Button>
              <Button type="submit" disabled={renaming}>
                {renaming ? <Spinner className="size-4" /> : null}
                <span>{t.rename}</span>
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function TopicsGridSkeleton() {
  return (
    <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {[0, 1, 2, 3].map((i) => (
        <li key={i}>
          <Card>
            <CardContent className="flex items-start gap-3 p-4">
              <Skeleton className="size-9 shrink-0 rounded-md" />
              <div className="flex flex-1 flex-col gap-2">
                <Skeleton className="h-4 w-2/5" />
                <Skeleton className="h-3 w-1/4" />
              </div>
            </CardContent>
          </Card>
        </li>
      ))}
    </ul>
  )
}
