"use client"

import * as React from "react"

import { HugeiconsIcon } from "@hugeicons/react"
import {
  AlertCircleIcon,
  CheckmarkCircle02Icon,
  CloudUploadIcon,
  Crown02Icon,
  Delete02Icon,
} from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { ApiClientError } from "@/lib/api"
import { useT } from "@/lib/i18n/provider"
import {
  deleteMaterial,
  getMaterial,
  uploadMaterialFile,
  uploadMaterialPaste,
  type MaterialStatus,
} from "@/lib/materials"
import { listRooms } from "@/lib/rooms"
import { cn } from "@/lib/utils"

type UploadPhase = "uploading" | "processing" | "ready" | "failed"

type UploadItem = {
  key: string
  id: string | null
  name: string
  phase: UploadPhase
  error: string | null
}

const POLL_INTERVAL_MS = 1500
const POLL_MAX_ATTEMPTS = 60
const COLLAPSED_COUNT = 3

type Props = {
  onReady: () => void
  onBack: () => void
  onUpgrade: () => void
}

export function UploadStep({ onReady, onBack, onUpgrade }: Props) {
  const t = useT()
  const c = t.onboarding.upload

  const [spaceId, setSpaceId] = React.useState<string | null>(null)
  const [mode, setMode] = React.useState<"file" | "paste">("file")
  const [items, setItems] = React.useState<UploadItem[]>([])
  const [expanded, setExpanded] = React.useState(false)
  const [dragOver, setDragOver] = React.useState(false)
  const [pasteTitle, setPasteTitle] = React.useState("")
  const [pasteContent, setPasteContent] = React.useState("")
  const [quotaReached, setQuotaReached] = React.useState(false)
  const fileInputRef = React.useRef<HTMLInputElement>(null)
  const cancelledRef = React.useRef(false)
  const keyRef = React.useRef(0)

  React.useEffect(() => {
    cancelledRef.current = false
    void (async () => {
      try {
        const rooms = await listRooms()
        if (!cancelledRef.current) setSpaceId(rooms[0]?.id ?? null)
      } catch {
        // ignore — handled when uploading
      }
    })()
    return () => {
      cancelledRef.current = true
    }
  }, [])

  function describeError(error: unknown): string {
    if (error instanceof ApiClientError) {
      if (error.status === 402) {
        setQuotaReached(true)
        return c.limitTitle
      }
      return error.errors[0]?.message ?? t.materials.errors.generic
    }
    return t.materials.errors.generic
  }

  function patchItem(key: string, patch: Partial<UploadItem>) {
    setItems((prev) =>
      prev.map((item) => (item.key === key ? { ...item, ...patch } : item))
    )
  }

  function removeItem(item: UploadItem) {
    setItems((prev) => prev.filter((entry) => entry.key !== item.key))
    if (item.id) {
      void deleteMaterial(item.id).catch(() => {
        // best-effort; row is already removed locally
      })
    }
  }

  async function pollUntilDone(key: string, materialId: string) {
    for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt += 1) {
      if (cancelledRef.current) return
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS))
      if (cancelledRef.current) return
      let status: MaterialStatus | undefined
      try {
        const material = await getMaterial(materialId)
        status = material?.status
      } catch {
        continue
      }
      if (status === "READY") {
        patchItem(key, { phase: "ready" })
        return
      }
      if (status === "FAILED") {
        patchItem(key, { phase: "failed", error: c.failed })
        return
      }
    }
    patchItem(key, { phase: "ready" })
  }

  async function startFileUpload(file: File) {
    if (!spaceId) return
    const key = `item-${(keyRef.current += 1)}`
    setItems((prev) => [
      ...prev,
      { key, id: null, name: file.name, phase: "uploading", error: null },
    ])
    try {
      const created = await uploadMaterialFile(spaceId, file, null)
      if (!created) {
        patchItem(key, { phase: "failed", error: t.materials.errors.generic })
        return
      }
      if (created.status === "READY") {
        patchItem(key, { id: created.id, phase: "ready" })
        return
      }
      patchItem(key, { id: created.id, phase: "processing" })
      await pollUntilDone(key, created.id)
    } catch (error) {
      patchItem(key, { phase: "failed", error: describeError(error) })
    }
  }

  async function startPasteUpload() {
    const title = pasteTitle.trim()
    const content = pasteContent.trim()
    if (!spaceId || !title || !content) return
    const key = `item-${(keyRef.current += 1)}`
    setItems((prev) => [
      ...prev,
      { key, id: null, name: title, phase: "uploading", error: null },
    ])
    setPasteTitle("")
    setPasteContent("")
    try {
      const created = await uploadMaterialPaste(spaceId, title, content, null)
      if (!created) {
        patchItem(key, { phase: "failed", error: t.materials.errors.generic })
        return
      }
      if (created.status === "READY") {
        patchItem(key, { id: created.id, phase: "ready" })
        return
      }
      patchItem(key, { id: created.id, phase: "processing" })
      await pollUntilDone(key, created.id)
    } catch (error) {
      patchItem(key, { phase: "failed", error: describeError(error) })
    }
  }

  const hasReady = items.some((item) => item.phase === "ready")
  const ordered = [...items].reverse()
  const visible = expanded ? ordered : ordered.slice(0, COLLAPSED_COUNT)
  const hiddenCount = ordered.length - visible.length

  return (
    <div className="space-y-6">
      <div className="space-y-2 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">{c.title}</h1>
        <p className="text-muted-foreground">{c.subtitle}</p>
      </div>

      <div className="flex gap-2">
        <Button
          type="button"
          variant={mode === "file" ? "secondary" : "ghost"}
          className="flex-1"
          onClick={() => setMode("file")}
        >
          {c.fileTab}
        </Button>
        <Button
          type="button"
          variant={mode === "paste" ? "secondary" : "ghost"}
          className="flex-1"
          onClick={() => setMode("paste")}
        >
          {c.pasteTab}
        </Button>
      </div>

      {mode === "file" ? (
        <div
          className={cn(
            "flex flex-col items-center justify-center gap-3 rounded-3xl border border-dashed border-input px-6 py-10 text-center transition-colors",
            dragOver && "border-primary bg-primary/5"
          )}
          onDragOver={(event) => {
            event.preventDefault()
            setDragOver(true)
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(event) => {
            event.preventDefault()
            setDragOver(false)
            const file = event.dataTransfer.files?.[0]
            if (file) void startFileUpload(file)
          }}
        >
          <HugeiconsIcon
            icon={CloudUploadIcon}
            strokeWidth={2}
            className="size-8 text-muted-foreground"
          />
          <p className="text-sm font-medium">{c.dropZone}</p>
          <p className="text-xs text-muted-foreground">{c.formatsHint}</p>
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.doc,.docx,.txt,.md"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) void startFileUpload(file)
              event.target.value = ""
            }}
          />
          <Button
            type="button"
            variant="outline"
            disabled={!spaceId}
            onClick={() => fileInputRef.current?.click()}
          >
            {c.browse}
          </Button>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="onboarding-paste-title">
              {c.pasteTitlePlaceholder}
            </Label>
            <Input
              id="onboarding-paste-title"
              value={pasteTitle}
              placeholder={c.pasteTitlePlaceholder}
              onChange={(event) => setPasteTitle(event.target.value)}
            />
          </div>
          <Textarea
            value={pasteContent}
            rows={6}
            placeholder={c.pasteContentPlaceholder}
            onChange={(event) => setPasteContent(event.target.value)}
          />
          <Button
            type="button"
            variant="outline"
            className="w-full"
            disabled={
              !spaceId ||
              pasteTitle.trim().length === 0 ||
              pasteContent.trim().length === 0
            }
            onClick={() => void startPasteUpload()}
          >
            {c.pasteSubmit}
          </Button>
        </div>
      )}

      {quotaReached && (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-amber-500/30 bg-amber-500/10 px-4 py-4 text-center">
          <HugeiconsIcon
            icon={Crown02Icon}
            strokeWidth={2}
            className="size-6 text-amber-600 dark:text-amber-400"
          />
          <p className="text-sm font-medium">{c.limitTitle}</p>
          <p className="text-xs text-muted-foreground">{c.limitDescription}</p>
          <Button type="button" size="sm" onClick={onUpgrade}>
            {c.upgrade}
          </Button>
        </div>
      )}

      {ordered.length > 0 && (
        <div className="space-y-2">
          {visible.map((item) => (
            <div
              key={item.key}
              className="flex items-center gap-3 rounded-2xl bg-muted/50 px-4 py-3 text-sm"
            >
              {(item.phase === "uploading" || item.phase === "processing") && (
                <Spinner className="size-4" />
              )}
              {item.phase === "ready" && (
                <HugeiconsIcon
                  icon={CheckmarkCircle02Icon}
                  strokeWidth={2}
                  className="size-4 text-emerald-600 dark:text-emerald-400"
                />
              )}
              {item.phase === "failed" && (
                <HugeiconsIcon
                  icon={AlertCircleIcon}
                  strokeWidth={2}
                  className="size-4 text-destructive"
                />
              )}
              <span className="min-w-0 flex-1 truncate">
                {`${item.name} · `}
                {item.phase === "uploading" && c.uploading}
                {item.phase === "processing" && c.processing}
                {item.phase === "ready" && c.ready}
                {item.phase === "failed" && (item.error ?? c.failed)}
              </span>
              {item.phase === "failed" && (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => removeItem(item)}
                >
                  {c.retry}
                </Button>
              )}
              {item.phase !== "failed" && (
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  className="size-8 shrink-0"
                  aria-label={c.delete}
                  title={c.delete}
                  onClick={() => removeItem(item)}
                >
                  <HugeiconsIcon
                    icon={Delete02Icon}
                    strokeWidth={2}
                    className="size-4"
                  />
                </Button>
              )}
            </div>
          ))}

          {(hiddenCount > 0 || expanded) &&
            ordered.length > COLLAPSED_COUNT && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="w-full"
                onClick={() => setExpanded((prev) => !prev)}
              >
                {expanded
                  ? t.onboarding.upload.showLess
                  : c.showAll.replace("{count}", String(ordered.length))}
              </Button>
            )}
        </div>
      )}

      <p className="text-center text-xs text-muted-foreground">
        {c.privacyNote}
      </p>

      <div className="flex items-center justify-between gap-3 pt-2">
        <Button type="button" variant="ghost" onClick={onBack}>
          {t.onboarding.back}
        </Button>
        <Button
          type="button"
          onClick={onReady}
          disabled={!hasReady}
          title={!hasReady ? c.waiting : undefined}
        >
          {c.continue}
        </Button>
      </div>
    </div>
  )
}
