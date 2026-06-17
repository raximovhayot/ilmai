import { setupServer } from "msw/node"

import { handlers } from "./handlers"
import { livePassthroughHandlers } from "./live-passthrough"

export const server = setupServer(...livePassthroughHandlers(), ...handlers)
