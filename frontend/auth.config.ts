import type { NextAuthConfig } from "next-auth"
import Credentials from "next-auth/providers/credentials"
import Google from "next-auth/providers/google"

const DEMO_MODE = process.env.NEXT_PUBLIC_DEMO_MODE === "1"

const providers = [
  Google,
  ...(DEMO_MODE
    ? [
        Credentials({
          id: "demo",
          name: "Demo",
          credentials: {},
          authorize: () => ({
            id: "demo-user",
            email: "demo@ilmai.dev",
            name: "Aziza Karimova",
            image: null,
          }),
        }),
      ]
    : []),
]

export default {
  providers,
  pages: {
    signIn: "/login",
    error: "/login",
  },
} satisfies NextAuthConfig
