import * as actionTypes from "./actionTypes"
import { Map } from "immutable"
import { Team } from "../../components/TeamSelect"
import { ThunkDispatch } from "redux-thunk"
import { RunExtended, RunSummary, Access } from "../../api"


export class RunsState {
    byId?: Map<number, RunExtended> = undefined
    byTest?: Map<number, Map<number, RunExtended>> = undefined
    currentPage: number[] = []
    currentTotal = 0
    selectedRoles?: Team = undefined
    suggestQuery: string[] = []
    suggestions: string[] = []
}

export interface UpdateTokenAction {
    type: typeof actionTypes.UPDATE_TOKEN
    id: number
    testid: number
    token: string | null
}

export interface UpdateAccessAction {
    type: typeof actionTypes.UPDATE_ACCESS
    id: number
    testid: number
    owner: string
    access: Access
}

export interface TrashAction {
    type: typeof actionTypes.TRASH
    id: number
    testid: number
    isTrashed: boolean
}

export interface UpdateDescriptionAction {
    type: typeof actionTypes.UPDATE_DESCRIPTION
    id: number
    testid: number
    description: string
}

type RunsAction =
    | UpdateTokenAction
    | UpdateAccessAction
    | TrashAction
    | UpdateDescriptionAction

export type RunsDispatch = ThunkDispatch<any, unknown, RunsAction >

//Takes events and updates the state accordingly
export const reducer = (state = new RunsState(), action: RunsAction) => {
    switch (action.type) {
        case actionTypes.UPDATE_TOKEN: {
            state = updateRun(state, action.id, action.testid, { token: action.token })
            break
        }
        case actionTypes.UPDATE_ACCESS: {
            state = updateRun(state, action.id, action.testid, { owner: action.owner, access: action.access })
            break
        }
        case actionTypes.TRASH: {
            state = updateRun(state, action.id, action.testid, { trashed: action.isTrashed })
            break
        }
        case actionTypes.UPDATE_DESCRIPTION: {
            state = updateRun(state, action.id, action.testid, { description: action.description })
            break
        }
        default:
    }
    return state
}

function updateRun(
    state: RunsState,
    id: number,
    testid: number,
    patch: Record<string, unknown> | ((current: RunExtended) => RunExtended)
) {
    const run = state.byId?.get(id)
    if (run) {
        const updated = typeof patch === "function" ? patch(run) : { ...run, ...patch }
        state.byId = (state.byId || Map<number, RunExtended>()).set(run.id, updated)
    }
    let testMap: Map<number, RunExtended> | undefined = state.byTest?.get(testid)
    if (testMap) {
        const current = testMap.get(id)
        if (current) {
            const updated = typeof patch === "function" ? patch(current) : { ...current, ...patch }
            testMap = testMap.set(id, updated)
        }
        state.byTest = state.byTest?.set(testid, testMap)
    }
    return state
}
