import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { TaskWorkspace } from "@/components/task/task-workspace"

export default async function TaskPage({
  params,
}: {
  params: Promise<{ planId: string; dayIndex: string; orderInDay: string }>
}) {
  const session = await auth()
  if (!session?.user) {
    redirect("/login")
  }

  const { planId, dayIndex, orderInDay } = await params

  return (
    <TaskWorkspace
      planId={planId}
      dayIndex={Number.parseInt(dayIndex, 10)}
      orderInDay={Number.parseInt(orderInDay, 10)}
    />
  )
}
