"use client"

import * as React from "react"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  Copy01Icon,
  Crown02Icon,
  Delete02Icon,
  Link01Icon,
  Logout03Icon,
  PencilEdit02Icon,
  PlusSignIcon,
  Unlink01Icon,
  UserAdd01Icon,
  UserGroup03Icon,
  UserMultipleIcon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { ApiClientError } from "@/lib/api"
import { useT } from "@/lib/i18n/provider"
import { getPremium } from "@/lib/premium"
import {
  createRoom,
  createRoomInvite,
  joinRoom,
  leaveRoom,
  listRoomMembers,
  listRooms,
  removeRoomMember,
  renameRoom,
  revokeRoomInvite,
  type RoomMemberResponse,
  type RoomResponse,
  type RoomRole,
} from "@/lib/rooms"

import { SettingsPageShell } from "./settings-shared"

type RoomMeta = {
  room: RoomResponse
  members: RoomMemberResponse[]
  viewerRole: RoomRole | null
}

function viewerRoleOf(members: RoomMemberResponse[]): RoomRole | null {
  const self = members.find((m) => m.self)
  return self ? self.role : null
}

export function SettingsRoomsView() {
  const t = useT().settings.rooms
  const { status } = useSession()

  const [rooms, setRooms] = React.useState<RoomMeta[]>([])
  const [premium, setPremium] = React.useState(false)
  const [loading, setLoading] = React.useState(true)
  const [failed, setFailed] = React.useState(false)

  const load = React.useCallback(async () => {
    setFailed(false)
    try {
      const [list, prem] = await Promise.all([listRooms(), getPremium()])
      const metas = await Promise.all(
        list.map(async (room) => {
          let members: RoomMemberResponse[] = []
          try {
            members = await listRoomMembers(room.id)
          } catch {
            members = []
          }
          return { room, members, viewerRole: viewerRoleOf(members) }
        })
      )
      setRooms(metas)
      setPremium(prem.tier === "PREMIUM")
    } catch {
      setFailed(true)
    } finally {
      setLoading(false)
    }
  }, [])

  React.useEffect(() => {
    if (status !== "authenticated") return
    void (async () => {
      await load()
    })()
  }, [status, load])

  return (
    <SettingsPageShell
      title={t.title}
      subtitle={t.subtitle}
      icon={UserGroup03Icon}
    >
      <div className="flex flex-wrap items-center gap-2">
        <CreateRoomButton premium={premium} onCreated={load} />
        <JoinRoomButton onJoined={load} />
      </div>

      {loading ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : failed ? (
        <Card className="border-border/80">
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <p className="text-sm text-muted-foreground">{t.failedToLoad}</p>
          </CardContent>
        </Card>
      ) : rooms.length === 0 ? (
        <Card className="border-border/80">
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <p className="text-sm text-muted-foreground">{t.empty}</p>
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {rooms.map((meta) => (
            <RoomCard key={meta.room.id} meta={meta} onChanged={load} />
          ))}
        </div>
      )}
    </SettingsPageShell>
  )
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiClientError) {
    return error.errors[0]?.message ?? fallback
  }
  return fallback
}

function CreateRoomButton({
  premium,
  onCreated,
}: {
  premium: boolean
  onCreated: () => Promise<void> | void
}) {
  const t = useT().settings.rooms
  const [open, setOpen] = React.useState(false)
  const [name, setName] = React.useState("")
  const [busy, setBusy] = React.useState(false)

  const trimmed = name.trim()

  const handleOpenChange = (next: boolean) => {
    if (next && !premium) {
      toast.error(t.premiumRequired)
      return
    }
    setOpen(next)
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!trimmed || busy) return
    setBusy(true)
    try {
      await createRoom(trimmed)
      setOpen(false)
      setName("")
      await onCreated()
    } catch (error) {
      toast.error(errorMessage(error, t.errors.generic))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={<Button size="sm" />}>
        <HugeiconsIcon
          icon={PlusSignIcon}
          strokeWidth={2}
          data-icon="inline-start"
        />
        {t.createCta}
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-6">
          <DialogHeader>
            <DialogTitle>{t.createTitle}</DialogTitle>
            <DialogDescription>{t.createSubtitle}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="create-room-name">{t.renamePlaceholder}</Label>
            <Input
              id="create-room-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t.createPlaceholder}
              autoFocus
              required
            />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
            >
              {t.cancel}
            </Button>
            <Button type="submit" disabled={!trimmed || busy}>
              {busy ? <Spinner /> : null}
              {t.createSubmit}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function JoinRoomButton({
  onJoined,
}: {
  onJoined: () => Promise<void> | void
}) {
  const t = useT().settings.rooms
  const [open, setOpen] = React.useState(false)
  const [code, setCode] = React.useState("")
  const [busy, setBusy] = React.useState(false)

  const trimmed = code.trim()

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!trimmed || busy) return
    setBusy(true)
    try {
      await joinRoom(trimmed)
      setOpen(false)
      setCode("")
      await onJoined()
    } catch (error) {
      toast.error(errorMessage(error, t.errors.invalidCode))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button size="sm" variant="outline" />}>
        <HugeiconsIcon
          icon={UserAdd01Icon}
          strokeWidth={2}
          data-icon="inline-start"
        />
        {t.join}
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-6">
          <DialogHeader>
            <DialogTitle>{t.joinTitle}</DialogTitle>
            <DialogDescription>{t.joinSubtitle}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="join-room-code">{t.joinPlaceholder}</Label>
            <Input
              id="join-room-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder={t.joinPlaceholder}
              autoFocus
              required
            />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
            >
              {t.cancel}
            </Button>
            <Button type="submit" disabled={!trimmed || busy}>
              {busy ? <Spinner /> : null}
              {t.joinSubmit}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function RoomCard({
  meta,
  onChanged,
}: {
  meta: RoomMeta
  onChanged: () => Promise<void> | void
}) {
  const t = useT().settings.rooms
  const isOwner = meta.viewerRole === "OWNER"

  return (
    <Card className="border-border/80">
      <CardContent className="flex flex-col gap-4 p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <span
              className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"
              aria-hidden
            >
              <HugeiconsIcon
                icon={UserMultipleIcon}
                strokeWidth={2}
                className="size-5"
              />
            </span>
            <div className="flex min-w-0 flex-col gap-0.5">
              <span className="truncate text-sm font-semibold text-foreground">
                {meta.room.name}
              </span>
              <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <HugeiconsIcon
                  icon={UserGroup03Icon}
                  strokeWidth={2}
                  className="size-3.5"
                />
                {meta.members.length} · {t.members}
              </span>
            </div>
          </div>
          {meta.viewerRole === "OWNER" ? (
            <Badge
              variant="outline"
              className="shrink-0 border-amber-400/40 bg-amber-400/10 text-amber-700 dark:text-amber-400"
            >
              <span className="flex items-center gap-1">
                <HugeiconsIcon
                  icon={Crown02Icon}
                  strokeWidth={2}
                  className="size-3.5"
                />
                {t.ownerBadge}
              </span>
            </Badge>
          ) : meta.viewerRole === "MEMBER" ? (
            <Badge variant="outline" className="shrink-0">
              {t.memberBadge}
            </Badge>
          ) : null}
        </div>

        <Separator />

        <div className="flex flex-wrap items-center gap-2">
          {isOwner ? (
            <>
              <RenameRoomButton
                roomId={meta.room.id}
                currentName={meta.room.name}
                onRenamed={onChanged}
              />
              <InviteButton roomId={meta.room.id} />
              <MembersButton
                roomId={meta.room.id}
                members={meta.members}
                onChanged={onChanged}
              />
            </>
          ) : (
            <LeaveRoomButton roomId={meta.room.id} onLeft={onChanged} />
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function RenameRoomButton({
  roomId,
  currentName,
  onRenamed,
}: {
  roomId: string
  currentName: string
  onRenamed: () => Promise<void> | void
}) {
  const t = useT().settings.rooms
  const [open, setOpen] = React.useState(false)
  const [name, setName] = React.useState(currentName)
  const [busy, setBusy] = React.useState(false)

  const handleOpenChange = (next: boolean) => {
    if (next) setName(currentName)
    setOpen(next)
  }

  const trimmed = name.trim()

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!trimmed || busy) return
    setBusy(true)
    try {
      await renameRoom(roomId, trimmed)
      setOpen(false)
      await onRenamed()
    } catch (error) {
      toast.error(errorMessage(error, t.errors.generic))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={<Button size="sm" variant="outline" />}>
        <HugeiconsIcon
          icon={PencilEdit02Icon}
          strokeWidth={2}
          data-icon="inline-start"
        />
        {t.rename}
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-6">
          <DialogHeader>
            <DialogTitle>{t.renameTitle}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`rename-room-${roomId}`}>
              {t.renamePlaceholder}
            </Label>
            <Input
              id={`rename-room-${roomId}`}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t.renamePlaceholder}
              autoFocus
              required
            />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
            >
              {t.cancel}
            </Button>
            <Button type="submit" disabled={!trimmed || busy}>
              {busy ? <Spinner /> : null}
              {t.renameSubmit}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function InviteButton({ roomId }: { roomId: string }) {
  const t = useT().settings.rooms
  const [open, setOpen] = React.useState(false)
  const [code, setCode] = React.useState<string | null>(null)
  const [busy, setBusy] = React.useState(false)
  const [copied, setCopied] = React.useState(false)

  const handleCreate = async () => {
    if (busy) return
    setBusy(true)
    try {
      const invite = await createRoomInvite(roomId)
      setCode(invite.code)
    } catch (error) {
      toast.error(errorMessage(error, t.errors.generic))
    } finally {
      setBusy(false)
    }
  }

  const handleRevoke = async () => {
    if (busy) return
    setBusy(true)
    try {
      await revokeRoomInvite(roomId)
      setCode(null)
    } catch (error) {
      toast.error(errorMessage(error, t.errors.generic))
    } finally {
      setBusy(false)
    }
  }

  const handleCopy = async () => {
    if (!code) return
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error(t.errors.generic)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button size="sm" variant="outline" />}>
        <HugeiconsIcon
          icon={Link01Icon}
          strokeWidth={2}
          data-icon="inline-start"
        />
        {t.invite}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t.inviteTitle}</DialogTitle>
          <DialogDescription>{t.inviteSubtitle}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          {code ? (
            <>
              <div className="flex items-center gap-2">
                <Input value={code} readOnly className="font-mono" />
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={handleCopy}
                >
                  <HugeiconsIcon
                    icon={Copy01Icon}
                    strokeWidth={2}
                    data-icon="inline-start"
                  />
                  {copied ? t.inviteCopied : t.inviteCopy}
                </Button>
              </div>
              <Button
                type="button"
                variant="outline"
                onClick={handleRevoke}
                disabled={busy}
                className="w-fit text-destructive"
              >
                <HugeiconsIcon
                  icon={Unlink01Icon}
                  strokeWidth={2}
                  data-icon="inline-start"
                />
                {t.inviteRevoke}
              </Button>
            </>
          ) : (
            <Button type="button" onClick={handleCreate} disabled={busy}>
              {busy ? <Spinner /> : null}
              {t.inviteCreate}
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

function MembersButton({
  roomId,
  members,
  onChanged,
}: {
  roomId: string
  members: RoomMemberResponse[]
  onChanged: () => Promise<void> | void
}) {
  const t = useT().settings.rooms
  const [open, setOpen] = React.useState(false)
  const [busyId, setBusyId] = React.useState<string | null>(null)

  const handleRemove = async (userId: string) => {
    if (busyId) return
    setBusyId(userId)
    try {
      await removeRoomMember(roomId, userId)
      await onChanged()
    } catch (error) {
      toast.error(errorMessage(error, t.errors.generic))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button size="sm" variant="outline" />}>
        <HugeiconsIcon
          icon={UserGroup03Icon}
          strokeWidth={2}
          data-icon="inline-start"
        />
        {t.members}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t.membersTitle}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col divide-y divide-border/50">
          {members.map((member) => (
            <div
              key={member.userId}
              className="flex items-center justify-between gap-3 py-3"
            >
              <div className="flex min-w-0 flex-col gap-0.5">
                <span className="truncate text-sm font-medium text-foreground">
                  {member.username}
                  {member.self ? ` (${t.you})` : ""}
                </span>
                <span className="text-xs text-muted-foreground">
                  {member.role === "OWNER" ? t.ownerBadge : t.memberBadge}
                </span>
              </div>
              {member.role !== "OWNER" && !member.self ? (
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => handleRemove(member.userId)}
                  disabled={busyId === member.userId}
                  className="text-destructive"
                >
                  {busyId === member.userId ? (
                    <Spinner />
                  ) : (
                    <HugeiconsIcon
                      icon={Delete02Icon}
                      strokeWidth={2}
                      data-icon="inline-start"
                    />
                  )}
                  {t.removeMember}
                </Button>
              ) : null}
            </div>
          ))}
        </div>
        <DialogFooter>
          <DialogClose render={<Button variant="outline" />}>
            {t.cancel}
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function LeaveRoomButton({
  roomId,
  onLeft,
}: {
  roomId: string
  onLeft: () => Promise<void> | void
}) {
  const t = useT().settings.rooms
  const [open, setOpen] = React.useState(false)
  const [busy, setBusy] = React.useState(false)

  const handleLeave = async () => {
    if (busy) return
    setBusy(true)
    try {
      await leaveRoom(roomId)
      setOpen(false)
      await onLeft()
    } catch (error) {
      toast.error(errorMessage(error, t.errors.generic))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        render={
          <Button size="sm" variant="outline" className="text-destructive" />
        }
      >
        <HugeiconsIcon
          icon={Logout03Icon}
          strokeWidth={2}
          data-icon="inline-start"
        />
        {t.leave}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t.leave}</DialogTitle>
          <DialogDescription>{t.confirmLeave}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => setOpen(false)}
          >
            {t.cancel}
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={handleLeave}
            disabled={busy}
          >
            {busy ? <Spinner /> : null}
            {t.leave}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
