import { State } from "../../store"


export function subscriptions(id: number) {
    return (state: State) => state.tests.watches?.get(id)
}
