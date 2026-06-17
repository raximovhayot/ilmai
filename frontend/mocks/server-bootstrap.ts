const ENABLED =
  process.env.NEXT_PUBLIC_MOCK_API === "1" ||
  process.env.NEXT_PUBLIC_DEMO_MODE === "1"

type GlobalWithMock = typeof globalThis & {
  __ilmaiMswStarted?: boolean
}
const globalScope = globalThis as GlobalWithMock

export async function ensureMockServerStarted(): Promise<void> {
  if (!ENABLED) return
  if (typeof window !== "undefined") return
  if (globalScope.__ilmaiMswStarted) return
  globalScope.__ilmaiMswStarted = true
  try {
    const { server } = await import("./node")
    server.listen({ onUnhandledRequest: "bypass" })
  } catch {
    globalScope.__ilmaiMswStarted = false
  }
}
