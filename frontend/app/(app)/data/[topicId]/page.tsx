import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { DataDetailClient } from "@/components/data-detail/data-detail-client"

type PageProps = {
  params: Promise<{ topicId: string }>
}

export default async function DataDetailPage({ params }: PageProps) {
  const { topicId } = await params
  const session = await auth()
  if (!session?.user) {
    redirect("/login")
  }

  return <DataDetailClient topicId={topicId} />
}
