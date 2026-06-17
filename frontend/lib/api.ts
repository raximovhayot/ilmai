export type ApiError = {
  field?: string | null
  code: string
  message: string
}

export type ApiResponse<T> = {
  data?: T | null
  errors?: ApiError[] | null
}

export class ApiClientError extends Error {
  readonly status: number
  readonly errors: ApiError[]

  constructor(status: number, errors: ApiError[], message?: string) {
    super(message ?? errors[0]?.message ?? `Request failed (${status})`)
    this.name = "ApiClientError"
    this.status = status
    this.errors = errors
  }
}

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ??
  "http://localhost:8080"

export const PROXY_PREFIX = "/api/backend"

type RequestOptions = Omit<RequestInit, "body" | "headers"> & {
  body?: unknown
  headers?: Record<string, string>
  accessToken?: string | null
}

async function setupServerMocks(): Promise<boolean> {
  if (typeof window !== "undefined") return false
  const enabled =
    process.env.NEXT_PUBLIC_MOCK_API === "1" ||
    process.env.NEXT_PUBLIC_DEMO_MODE === "1"
  if (!enabled) return false
  try {
    const { ensureMockServerStarted } =
      await import("../mocks/server-bootstrap")
    await ensureMockServerStarted()
    return true
  } catch {
    return false
  }
}

export async function apiFetch<T>(
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  const isServer = typeof window === "undefined"
  const { body, headers, accessToken, ...rest } = options

  const finalHeaders: Record<string, string> = {
    Accept: "application/json",
    ...headers,
  }

  let payload: BodyInit | undefined
  if (body !== undefined) {
    if (body instanceof FormData) {
      payload = body
    } else {
      finalHeaders["Content-Type"] = "application/json"
      payload = JSON.stringify(body)
    }
  }

  let url: string
  if (isServer) {
    await setupServerMocks()
    if (accessToken) {
      finalHeaders.Authorization = `Bearer ${accessToken}`
    }
    url = `${API_BASE_URL}${path}`
  } else {
    url = `${PROXY_PREFIX}${path}`
  }

  let response: Response
  try {
    response = await fetch(url, {
      ...rest,
      body: payload,
      headers: finalHeaders,
    })
  } catch {
    throw new ApiClientError(0, [
      { code: "NETWORK_ERROR", message: "Network error" },
    ])
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  let parsed: ApiResponse<T> | null = null
  if (text) {
    try {
      parsed = JSON.parse(text) as ApiResponse<T>
    } catch {
      parsed = null
    }
  }

  if (!response.ok) {
    const errors = parsed?.errors ?? [
      { code: "HTTP_" + response.status, message: response.statusText },
    ]
    throw new ApiClientError(response.status, errors)
  }

  if (parsed && "data" in parsed) {
    return (parsed.data ?? null) as T
  }
  return (parsed ?? null) as T
}
