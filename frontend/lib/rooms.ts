import { apiFetch } from "@/lib/api"

export type RoomRole = "OWNER" | "MEMBER"

export type RoomResponse = {
  id: string
  name: string
}

export type RoomMemberResponse = {
  userId: string
  username: string
  role: RoomRole
  self: boolean
}

export type RoomInviteResponse = {
  roomId: string
  code: string
}

export async function listRooms(): Promise<RoomResponse[]> {
  const data = await apiFetch<RoomResponse[]>("/rooms", {
    cache: "no-store",
  })
  return data ?? []
}

export async function createRoom(name: string): Promise<RoomResponse> {
  return await apiFetch<RoomResponse>("/rooms", {
    method: "POST",
    body: { name },
  })
}

export async function renameRoom(
  roomId: string,
  name: string
): Promise<RoomResponse> {
  return await apiFetch<RoomResponse>(`/rooms/${roomId}`, {
    method: "PATCH",
    body: { name },
  })
}

export async function joinRoom(code: string): Promise<RoomResponse> {
  return await apiFetch<RoomResponse>("/rooms/join", {
    method: "POST",
    body: { code },
  })
}

export async function createRoomInvite(
  roomId: string
): Promise<RoomInviteResponse> {
  return await apiFetch<RoomInviteResponse>(`/rooms/${roomId}/invite`, {
    method: "POST",
  })
}

export async function revokeRoomInvite(roomId: string): Promise<void> {
  await apiFetch<void>(`/rooms/${roomId}/invite`, {
    method: "DELETE",
  })
}

export async function listRoomMembers(
  roomId: string
): Promise<RoomMemberResponse[]> {
  const data = await apiFetch<RoomMemberResponse[]>(
    `/rooms/${roomId}/members`,
    {
      cache: "no-store",
    }
  )
  return data ?? []
}

export async function leaveRoom(roomId: string): Promise<void> {
  await apiFetch<void>(`/rooms/${roomId}/membership`, {
    method: "DELETE",
  })
}

export async function removeRoomMember(
  roomId: string,
  userId: string
): Promise<void> {
  await apiFetch<void>(`/rooms/${roomId}/members/${userId}`, {
    method: "DELETE",
  })
}
