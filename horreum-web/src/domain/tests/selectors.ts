import { State } from "../../store"


export function all(state: State) {
    if (!state.tests.byId) {
        return undefined
    }
    const list = [...state.tests.byId.values()]
    list.sort((a, b) => a.id - b.id)
    state.tests.watches.forEach((watching, id) => {
        const test = list.find(t => t.id === id)
        if (test) {
            test.watching = watching
        }
    })
    return list
}

export const get = (id: number) => (state: State) => {
    if (!state.tests.byId) {
        return undefined
    }
    return state.tests.byId.get(id)
}

export function subscriptions(id: number) {
    return (state: State) => state.tests.watches?.get(id)
}
