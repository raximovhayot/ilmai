import { cookies, headers } from "next/headers"
import { getToken } from "next-auth/jwt"

const SECRET = process.env.AUTH_SECRET

async function readAccessToken(
  cookieHeader: string,
  preferSecure: boolean
): Promise<string | null> {
  if (!SECRET || !cookieHeader) return null
  const attempts = preferSecure ? [true, false] : [false, true]
  for (const secureCookie of attempts) {
    const token = await getToken({
      req: { headers: new Headers({ cookie: cookieHeader }) },
      secret: SECRET,
      secureCookie,
    })
    const accessToken = token?.accessToken
    if (typeof accessToken === "string" && accessToken.length > 0) {
      return accessToken
    }
  }
  return null
}

export async function getServerAccessToken(): Promise<string | null> {
  const cookieStore = await cookies()
  const cookieHeader = cookieStore
    .getAll()
    .map((entry) => `${entry.name}=${entry.value}`)
    .join("; ")
  const headerStore = await headers()
  const proto = headerStore.get("x-forwarded-proto")?.split(",")[0]?.trim()
  return readAccessToken(cookieHeader, proto === "https")
}
