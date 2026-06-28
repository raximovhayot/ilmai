"use client"

import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  Settings02Icon,
  Tick02Icon,
  UnfoldMoreIcon,
  UserGroup03Icon,
} from "@hugeicons/core-free-icons"

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Skeleton } from "@/components/ui/skeleton"
import { useActiveRoom } from "@/lib/active-room"
import { useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

export function RoomSwitcher({ collapsed }: { collapsed: boolean }) {
  const t = useT().roomSwitcher
  const { rooms, activeRoom, activeRoomId, setActiveRoomId, loading } =
    useActiveRoom()

  if (loading && rooms.length === 0) {
    return collapsed ? (
      <Skeleton className="mx-auto size-9 rounded-xl" />
    ) : (
      <Skeleton className="h-11 w-full rounded-xl" />
    )
  }

  if (rooms.length === 0) return null

  const label = activeRoom?.name ?? rooms[0]?.name ?? ""

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={t.label}
        title={label}
        className={cn(
          "flex w-full items-center gap-2.5 rounded-xl border border-sidebar-border bg-sidebar-accent/40 px-2.5 py-2 text-start text-sm transition-colors outline-none hover:bg-sidebar-accent focus-visible:ring-2 focus-visible:ring-ring",
          collapsed && "lg:justify-center lg:px-0"
        )}
      >
        <span
          className="flex size-6 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"
          aria-hidden
        >
          <HugeiconsIcon
            icon={UserGroup03Icon}
            strokeWidth={2}
            className="size-3.5"
          />
        </span>
        {!collapsed && (
          <>
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="truncate text-xs text-muted-foreground">
                {t.label}
              </span>
              <span className="truncate text-sm font-medium text-foreground">
                {label}
              </span>
            </span>
            <HugeiconsIcon
              icon={UnfoldMoreIcon}
              strokeWidth={2}
              className="size-4 shrink-0 text-muted-foreground"
            />
          </>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-56">
        <DropdownMenuLabel>{t.label}</DropdownMenuLabel>
        {rooms.map((room) => {
          const active = room.id === activeRoomId
          return (
            <DropdownMenuItem
              key={room.id}
              onClick={() => setActiveRoomId(room.id)}
            >
              <span className="truncate">{room.name}</span>
              {active && (
                <HugeiconsIcon
                  icon={Tick02Icon}
                  strokeWidth={2}
                  className="ms-auto size-4 text-primary"
                />
              )}
            </DropdownMenuItem>
          )
        })}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          render={
            <Link href="/settings/rooms" className="gap-2.5">
              <HugeiconsIcon icon={Settings02Icon} strokeWidth={2} />
              {t.manage}
            </Link>
          }
        />
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
