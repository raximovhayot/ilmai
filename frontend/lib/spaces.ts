import { apiFetch } from "@/lib/api"

export type SpaceResponse = {
  id: string
  name: string
}

export async function listSpaces(): Promise<SpaceResponse[]> {
  const data = await apiFetch<SpaceResponse[]>("/rooms", {
    cache: "no-store",
  })
  return data ?? []
}
