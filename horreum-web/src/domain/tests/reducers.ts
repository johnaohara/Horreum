import * as actionTypes from "./actionTypes"
import { Map } from "immutable"
import { ThunkDispatch } from "redux-thunk"
import {
    Access,
    Action,
    Run,
    Test,
    TestToken,
    Transformer,
    View
} from "../../api"
import { AnyAction } from "redux"

export type CompareFunction = (runs: Run[]) => string

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

export interface LoadingAction {
    type: typeof actionTypes.LOADING
    isLoading: boolean
}

export interface LoadedSummaryAction {
    type: typeof actionTypes.LOADED_SUMMARY
    tests: Test[]
}

export interface LoadedTestAction {
    type: typeof actionTypes.LOADED_TEST
    test: Test
}

export interface DeleteAction {
    type: typeof actionTypes.DELETE
    id: number
}

export interface UpdateAccessAction {
    type: typeof actionTypes.UPDATE_ACCESS
    id: number
    owner: string
    access: Access
}

export interface UpdateTestWatchAction {
    type: typeof actionTypes.UPDATE_TEST_WATCH
    byId: Map<number, string[] | undefined>
}

export interface GetViewsAction {
    type: typeof actionTypes.GET_VIEWS
    testId: number
}

export interface LoadedViewsAction {
    type: typeof actionTypes.LOADED_VIEWS
    testId: number
    views: View[]
}

export interface UpdateViewAction {
    type: typeof actionTypes.UPDATE_VIEW
    testId: number
    view: View
}

export interface DeleteViewAction {
    type: typeof actionTypes.DELETE_VIEW
    testId: number
    viewId: number
}

export interface UpdateTokensAction {
    type: typeof actionTypes.UPDATE_TOKENS
    testId: number
    tokens: TestToken[]
}

export interface RevokeTokenAction {
    type: typeof actionTypes.REVOKE_TOKEN
    testId: number
    tokenId: number
}

export interface UpdateFoldersAction {
    type: typeof actionTypes.UPDATE_FOLDERS
    folders: string[]
}

export interface UpdateFolderAction {
    type: typeof actionTypes.UPDATE_FOLDER
    testId: number
    prevFolder: string
    newFolder: string
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
    | DeleteAction
    | UpdateAccessAction
    | UpdateTestWatchAction
    | UpdateTokensAction
    | RevokeTokenAction
    | UpdateTransformersAction
    | UpdateChangeDetectionAction
    | UpdateRunsAndDatasetsAction

export type TestDispatch = ThunkDispatch<any, unknown, TestAction >

export const reducer = (state = new TestsState(), action: TestAction) => {
    switch (action.type) {
        case actionTypes.UPDATE_ACCESS:
            {
                const test = state.byId?.get(action.id)
                if (test) {
                    state.byId = state.byId?.set(action.id, { ...test, owner: action.owner, access: action.access })
                }
            }
            break
        case actionTypes.DELETE:
            {
                state.byId = state.byId?.delete(action.id)
            }
            break
        case actionTypes.UPDATE_TEST_WATCH:
            {
                state.watches = state.watches.merge(action.byId)
            }
            break
        case actionTypes.UPDATE_TOKENS:
            {
                const test = state.byId?.get(action.testId)
                if (test) {
                    state.byId = state.byId?.set(action.testId, { ...test, tokens: action.tokens })
                }
            }
            break
        case actionTypes.REVOKE_TOKEN:
            {
                const test = state.byId?.get(action.testId)
                if (test) {
                    state.byId = state.byId?.set(action.testId, {
                        ...test,
                        tokens: test.tokens?.filter(t => t.id !== action.tokenId),
                    })
                }
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
