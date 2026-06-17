import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { DataView } from "@/components/data/data-view"

export default async function DataPage() {
  const session = await auth()
  if (!session?.user) {
    redirect("/login")
  }

  return <DataView initialTopics={[]} loadError={false} />
}
