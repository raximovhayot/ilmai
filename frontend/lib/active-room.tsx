"use client"

import * as React from "react"
import { useSession } from "next-auth/react"

import { listRooms, type RoomResponse } from "@/lib/rooms"

const STORAGE_KEY = "ilmai.activeRoomId"

type ActiveRoomContextValue = {
  rooms: RoomResponse[]
  activeRoomId: string | null
  activeRoom: RoomResponse | null
  setActiveRoomId: (id: string) => void
  loading: boolean
  refresh: () => Promise<void>
}

const ActiveRoomContext = React.createContext<ActiveRoomContextValue | null>(
  null
)

function readStoredRoomId(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export function ActiveRoomProvider({
  children,
}: {
  children: React.ReactNode
}) {
  const { status } = useSession()
  const [rooms, setRooms] = React.useState<RoomResponse[]>([])
  const [activeRoomId, setActiveRoomIdState] = React.useState<string | null>(
    null
  )
  const [loading, setLoading] = React.useState(true)

  const setActiveRoomId = React.useCallback((id: string) => {
    setActiveRoomIdState(id)
    try {
      window.localStorage.setItem(STORAGE_KEY, id)
    } catch {
      // ignore — persistence is best-effort
    }
  }, [])

  const refresh = React.useCallback(async () => {
    try {
      const list = await listRooms()
      setRooms(list)
      setActiveRoomIdState((current) => {
        const candidate = current ?? readStoredRoomId()
        if (candidate && list.some((room) => room.id === candidate)) {
          return candidate
        }
        return list[0]?.id ?? null
      })
    } catch {
      // ignore — empty state OK
    } finally {
      setLoading(false)
    }
  }, [])

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const list = await listRooms()
        if (cancelled) return
        setRooms(list)
        setActiveRoomIdState((current) => {
          const candidate = current ?? readStoredRoomId()
          if (candidate && list.some((room) => room.id === candidate)) {
            return candidate
          }
          return list[0]?.id ?? null
        })
      } catch {
        // ignore — empty state OK
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  const activeRoom = React.useMemo(
    () => rooms.find((room) => room.id === activeRoomId) ?? null,
    [rooms, activeRoomId]
  )

  const value = React.useMemo<ActiveRoomContextValue>(
    () => ({
      rooms,
      activeRoomId,
      activeRoom,
      setActiveRoomId,
      loading,
      refresh,
    }),
    [rooms, activeRoomId, activeRoom, setActiveRoomId, loading, refresh]
  )

  return (
    <ActiveRoomContext.Provider value={value}>
      {children}
    </ActiveRoomContext.Provider>
  )
}

export function useActiveRoom(): ActiveRoomContextValue {
  const ctx = React.useContext(ActiveRoomContext)
  if (!ctx) {
    throw new Error("useActiveRoom must be used within an ActiveRoomProvider")
  }
  return ctx
}
