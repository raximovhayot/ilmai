import { setupWorker } from "msw/browser"

import { handlers } from "./handlers"
import { livePassthroughHandlers } from "./live-passthrough"

export const worker = setupWorker(...livePassthroughHandlers(), ...handlers)
