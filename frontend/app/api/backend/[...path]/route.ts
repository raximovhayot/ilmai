import { type NextRequest } from "next/server"

import { getServerAccessToken } from "@/lib/server-auth"
import { ensureMockServerStarted } from "@/mocks/server-bootstrap"

export const dynamic = "force-dynamic"

const BACKEND_BASE_URL = (
  process.env.AUTH_BACKEND_URL ??
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8080"
).replace(/\/$/, "")

function unauthorized(): Response {
  return Response.json(
    {
      errors: [
        { code: "AUTH_UNAUTHENTICATED", message: "Sign in to continue." },
      ],
    },
    { status: 401 }
  )
}

function badGateway(): Response {
  return Response.json(
    {
      errors: [
        { code: "BACKEND_UNREACHABLE", message: "Upstream request failed." },
      ],
    },
    { status: 502 }
  )
}

async function handle(
  request: NextRequest,
  context: { params: Promise<{ path?: string[] }> }
): Promise<Response> {
  try {
    await ensureMockServerStarted()

    const accessToken = await getServerAccessToken()
    if (!accessToken) {
      return unauthorized()
    }

    const { path } = await context.params
    const suffix = (path ?? []).map(encodeURIComponent).join("/")
    const target = `${BACKEND_BASE_URL}/${suffix}${request.nextUrl.search}`

    const forwardHeaders = new Headers()
    forwardHeaders.set("authorization", `Bearer ${accessToken}`)
    forwardHeaders.set(
      "accept",
      request.headers.get("accept") ?? "application/json"
    )
    const contentType = request.headers.get("content-type")
    if (contentType) forwardHeaders.set("content-type", contentType)
    const acceptLanguage = request.headers.get("accept-language")
    if (acceptLanguage) forwardHeaders.set("accept-language", acceptLanguage)

    const method = request.method
    const hasBody = method !== "GET" && method !== "HEAD"
    const body = hasBody ? await request.arrayBuffer() : undefined

    let upstream: Response
    try {
      upstream = await fetch(target, {
        method,
        headers: forwardHeaders,
        body: body && body.byteLength > 0 ? body : undefined,
        redirect: "manual",
        cache: "no-store",
      })
    } catch {
      return badGateway()
    }

    const responseHeaders = new Headers()
    const upstreamContentType = upstream.headers.get("content-type")
    if (upstreamContentType) {
      responseHeaders.set("content-type", upstreamContentType)
    }
    const cacheControl = upstream.headers.get("cache-control")
    if (cacheControl) responseHeaders.set("cache-control", cacheControl)

    return new Response(upstream.body, {
      status: upstream.status,
      headers: responseHeaders,
    })
  } catch {
    return badGateway()
  }
}

export const GET = handle
export const POST = handle
export const PUT = handle
export const PATCH = handle
export const DELETE = handle
