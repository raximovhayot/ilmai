import { apiFetch } from "@/lib/api"

export type RoomResponse = {
  id: string
  name: string
}

export async function listRooms(): Promise<RoomResponse[]> {
  const data = await apiFetch<RoomResponse[]>("/rooms", {
    cache: "no-store",
  })
  return data ?? []
}
