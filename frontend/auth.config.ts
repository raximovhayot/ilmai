import type { NextAuthConfig } from "next-auth"
import Credentials from "next-auth/providers/credentials"
import Google from "next-auth/providers/google"

const DEMO_MODE = process.env.NEXT_PUBLIC_DEMO_MODE === "1"
const DEV_LOGIN = process.env.NEXT_PUBLIC_DEV_LOGIN === "1"

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
  ...(DEV_LOGIN
    ? [
        Credentials({
          id: "dev",
          name: "Developer",
          credentials: {
            email: { label: "Email", type: "email" },
            name: { label: "Name", type: "text" },
          },
          authorize: (credentials) => {
            const email =
              typeof credentials?.email === "string" && credentials.email
                ? credentials.email
                : "dev@ilmai.dev"
            const name =
              typeof credentials?.name === "string" && credentials.name
                ? credentials.name
                : "Dev User"
            return { id: `dev:${email}`, email, name, image: null }
          },
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
