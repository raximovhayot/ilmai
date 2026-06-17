import * as Sentry from "@sentry/nextjs"

export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    await import("./sentry.server.config")
  }
  if (process.env.NEXT_RUNTIME === "edge") {
    await import("./sentry.edge.config")
  }
  if (
    process.env.NEXT_RUNTIME === "nodejs" &&
    (process.env.NEXT_PUBLIC_MOCK_API === "1" ||
      process.env.NEXT_PUBLIC_DEMO_MODE === "1")
  ) {
    const { server } = await import("./mocks/node")
    server.listen({ onUnhandledRequest: "bypass" })
  }
}

export const onRequestError = Sentry.captureRequestError
