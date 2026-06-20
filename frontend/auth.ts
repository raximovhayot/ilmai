import NextAuth from "next-auth"

import authConfig from "./auth.config"

const BACKEND_BASE_URL = (
  process.env.AUTH_BACKEND_URL ??
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8080"
).replace(/\/$/, "")

const ACCESS_TOKEN_REFRESH_THRESHOLD_MS = 60_000

type BackendTokenPair = {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessExpiresAt: string
  refreshExpiresAt: string
}

type BackendEnvelope<T> = {
  data?: T | null
  errors?: { code: string; message: string }[] | null
}

async function postBackend<T>(
  path: string,
  body: Record<string, unknown>
): Promise<T | null> {
  try {
    const response = await fetch(`${BACKEND_BASE_URL}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(body),
      cache: "no-store",
    })
    if (!response.ok) {
      return null
    }
    const envelope = (await response.json()) as BackendEnvelope<T>
    return envelope.data ?? null
  } catch {
    return null
  }
}

function exchangeGoogleIdToken(idToken: string) {
  return postBackend<BackendTokenPair>("/auth/google", { idToken })
}

function refreshBackendTokens(refreshToken: string) {
  return postBackend<BackendTokenPair>("/auth/refresh", { refreshToken })
}

function revokeBackendTokens(refreshToken: string) {
  return postBackend<unknown>("/auth/logout", { refreshToken })
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  ...authConfig,
  session: { strategy: "jwt" },
  callbacks: {
    async jwt({ token, account }) {
      if (
        account?.provider === "google" &&
        typeof account.id_token === "string"
      ) {
        const tokens = await exchangeGoogleIdToken(account.id_token)
        if (!tokens) {
          throw new Error("Backend rejected the Google sign-in")
        }
        token.accessToken = tokens.accessToken
        token.refreshToken = tokens.refreshToken
        token.accessExpiresAt = tokens.accessExpiresAt
        token.refreshExpiresAt = tokens.refreshExpiresAt
        delete token.error
        return token
      }

      if (account && account.provider !== "google") {
        token.accessToken = "demo-access-token"
        token.refreshToken = "demo-refresh-token"
        token.accessExpiresAt = new Date(Date.now() + 3600_000).toISOString()
        token.refreshExpiresAt = new Date(
          Date.now() + 30 * 86_400_000
        ).toISOString()
        delete token.error
        return token
      }

      if (
        typeof token.accessExpiresAt === "string" &&
        typeof token.refreshToken === "string" &&
        token.accessToken !== "demo-access-token"
      ) {
        const expiresAtMs = Date.parse(token.accessExpiresAt)
        if (
          Number.isFinite(expiresAtMs) &&
          Date.now() > expiresAtMs - ACCESS_TOKEN_REFRESH_THRESHOLD_MS
        ) {
          const refreshed = await refreshBackendTokens(token.refreshToken)
          if (refreshed) {
            token.accessToken = refreshed.accessToken
            token.refreshToken = refreshed.refreshToken
            token.accessExpiresAt = refreshed.accessExpiresAt
            token.refreshExpiresAt = refreshed.refreshExpiresAt
            delete token.error
          } else {
            token.error = "RefreshAccessTokenError"
          }
        }
      }

      return token
    },
    async session({ session, token }) {
      session.error = typeof token.error === "string" ? token.error : undefined
      return session
    },
  },
  events: {
    async signOut(message) {
      const token = "token" in message ? message.token : null
      if (
        token &&
        typeof token.refreshToken === "string" &&
        token.refreshToken !== "demo-refresh-token"
      ) {
        await revokeBackendTokens(token.refreshToken)
      }
    },
  },
})
