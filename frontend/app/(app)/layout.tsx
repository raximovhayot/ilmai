import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { AppShellClient } from "@/components/app-shell/app-shell-client"

export default async function AppLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  return (
    <AppShellClient
      user={{
        name: session.user?.name ?? null,
        email: session.user?.email ?? null,
        image: session.user?.image ?? null,
      }}
    >
      {children}
    </AppShellClient>
  )
}
