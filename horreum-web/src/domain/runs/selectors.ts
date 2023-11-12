import { State } from "../../store"


export const viewsSelector = (testID : number) => (state: State) => {
    if (!state.tests.byId) {
        return undefined
    }
    return state.tests.byId.get(testID)?.views
}

