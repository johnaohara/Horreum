import * as actionTypes from "./actionTypes"
import { Map } from "immutable"
import { ThunkDispatch } from "redux-thunk"
import {
    Test,
    Transformer,
    View
} from "../../api"
import { AnyAction } from "redux"


export interface TestStorage extends Test {
    datasets?: number // dataset count in AllTests
    runs?: number // run count in AllTests
    watching?: string[]
    views?: View[]
}

export class TestsState {
    byId?: Map<number, TestStorage> = undefined
    loading = false
    allFolders: string[] = []
    // we need to store watches independently as the information
    // can arrive before the actual test list
    watches: Map<number, string[] | undefined> = Map<number, string[] | undefined>()
}

export interface UpdateTestWatchAction {
    type: typeof actionTypes.UPDATE_TEST_WATCH
    byId: Map<number, string[] | undefined>
}



export interface UpdateTransformersAction {
    type: typeof actionTypes.UPDATE_TRANSFORMERS
    testId: number
    transformers: Transformer[]
}

export interface UpdateChangeDetectionAction extends AnyAction {
    type: typeof actionTypes.UPDATE_CHANGE_DETECTION
    testId: number
    timelineLabels?: string[]
    timelineFunction?: string
    fingerprintLabels: string[]
    fingerprintFilter?: string
}

export interface UpdateRunsAndDatasetsAction {
    type: typeof actionTypes.UPDATE_RUNS_AND_DATASETS
    testId: number
    runs: number
    datasets: number
}

export type TestAction =
    | UpdateTestWatchAction
    | UpdateTransformersAction
    | UpdateChangeDetectionAction
    | UpdateRunsAndDatasetsAction

export type TestDispatch = ThunkDispatch<any, unknown, TestAction >

export const reducer = (state = new TestsState(), action: TestAction) => {
    switch (action.type) {
        case actionTypes.UPDATE_TEST_WATCH:
            {
                state.watches = state.watches.merge(action.byId)
            }
            break
        case actionTypes.UPDATE_TRANSFORMERS: {
            const test = state.byId?.get(action.testId)
            if (test) {
                state.byId = state.byId?.set(action.testId, { ...test, transformers: action.transformers })
            }
            break
        }
        case actionTypes.UPDATE_CHANGE_DETECTION: {
            const test = state.byId?.get(action.testId)
            if (test) {
                state.byId = state.byId?.set(action.testId, {
                    ...test,
                    fingerprintLabels: action.fingerprintLabels,
                    fingerprintFilter: action.fingerprintFilter,
                })
            }
            break
        }
        case actionTypes.UPDATE_RUNS_AND_DATASETS: {
            const test = state.byId?.get(action.testId)
            if (test) {
                state.byId = state.byId?.set(action.testId, {
                    ...test,
                    runs: action.runs,
                    datasets: action.datasets,
                })
            }
            break
        }
        default:
    }
    return state
}
