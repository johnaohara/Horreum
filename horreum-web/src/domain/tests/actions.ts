import * as actionTypes from "./actionTypes"
import {
    LoadingAction,
    LoadedSummaryAction,
    LoadedTestAction,
    UpdateAccessAction,
    DeleteAction,
    UpdateTestWatchAction,
    UpdateViewAction,
    DeleteViewAction,
    UpdateActionAction,
    UpdateTokensAction,
    RevokeTokenAction,
    UpdateChangeDetectionAction,
    UpdateFoldersAction,
    UpdateFolderAction,
    UpdateTransformersAction,
    UpdateRunsAndDatasetsAction,
    LoadedViewsAction,
} from "./reducers"
import {
    uiApi,
    testApi,
    subscriptionsApi,
    Action,
    Test,
    Transformer,
    View,
    Watch,
    alertingApi,
    actionApi, Access
} from "../../api"
import { Dispatch } from "redux"
import { Map } from "immutable"
import {
    constraintValidationFormatter,
} from "../../alerts"
import {AlertContextType} from "../../context/@types/appContextTypes";

function loading(isLoading: boolean): LoadingAction {
    return { type: actionTypes.LOADING, isLoading }
}

export function fetchSummary(alertingContext: AlertContextType, roles?: string, folder?: string) {
    return (dispatch: Dispatch<LoadingAction | LoadedSummaryAction >) => {
        dispatch(loading(true))
        return testApi.summary(folder, roles).then(
            listing =>
                dispatch({
                    type: actionTypes.LOADED_SUMMARY,
                    tests: listing.tests?.map(t => ({ ...t, notificationsEnabled: false })) || [],
                }),
            error => {
                dispatch(loading(false))
                return alertingContext.dispatchError(error, "FETCH_TEST_SUMMARY", "Failed to fetch test summary.")
            }
        )
    }
}

export function fetchTest(id: number, alerting: AlertContextType) : Promise<Test> {
    return testApi.get(id).then(
        test => test,
        error => alerting.dispatchError(
                error,
                "FETCH_TEST",
                "Failed to fetch test; the test may not exist or you don't have sufficient permissions to access it."
        ))
}

export function sendTest(test: Test, alerting: AlertContextType) {
    return (dispatch: Dispatch<LoadedTestAction >) => {
        return testApi.add(test).then(
            response => {
                dispatch({ type: actionTypes.LOADED_TEST, test: response })
                return response
            },
            error =>
                alerting.dispatchError(
                    error,
                    "UPDATE_TEST",
                    "Failed to create/update test " + test.name,
                    constraintValidationFormatter("the saved test")
                )
        )
    }
}

export function fetchViews(testId: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<LoadingAction | LoadedViewsAction >) => {
        dispatch(loading(true))
        return uiApi.getViews(testId).then(
            views => dispatch({ type: actionTypes.LOADED_VIEWS, testId, views }),
            error => {
                dispatch(loading(false))
                return alerting.dispatchError(
                    error,
                    "FETCH_VIEWS",
                    "Failed to fetch test views; the views may not exist or you don't have sufficient permissions to access them."
                )
            }
        )
    }
}

export function updateView(alerting: AlertContextType, testId: number, view: View) {
    return (dispatch: Dispatch<UpdateViewAction >): Promise<number> => {
        for (const c of view.components) {
            if (c.labels.length === 0) {
                alerting.dispatchError(
                    undefined,
                    "VIEW_UPDATE",
                    "Column " + c.headerName + " is invalid; must set at least one label."
                )
                return Promise.reject()
            }
        }
        view.testId = testId
        return uiApi.updateView(view).then(
            viewId => {
                const id: number = ensureInteger(viewId)
                dispatch({
                    type: actionTypes.UPDATE_VIEW,
                    testId,
                    view: {
                        ...view,
                        id,
                    },
                })
                return id
            },
            error => alerting.dispatchError( error, "VIEW_UPDATE", "View update failed.")
        )
    }
}

export function deleteView(alerting: AlertContextType, testId: number, viewId: number) {
    return (dispatch: Dispatch<DeleteViewAction >) => {
        return uiApi.deleteView(testId, viewId).then(
            _ => {
                dispatch({
                    type: actionTypes.DELETE_VIEW,
                    testId,
                    viewId,
                })
                return viewId
            },
            error => alerting.dispatchError(error, "VIEW_DELETE", "View update failed.")
        )
    }
}

export function updateFolder(testId: number, prevFolder: string, newFolder: string, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateFolderAction >) =>
        testApi.updateFolder(testId, newFolder).then(
            _ =>
                dispatch({
                    type: actionTypes.UPDATE_FOLDER,
                    testId,
                    prevFolder,
                    newFolder,
                }),
            error => alerting.dispatchError(error, "TEST_FOLDER_UPDATE", "Cannot update test folder")
        )
}

export function updateActions(testId: number, actions: Action[], alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateActionAction >) => {
        const promises: any[] = []
        actions.forEach(action => {
            promises.push(
                (action.testId = testId),
                actionApi.update(action).then(
                    response => {
                        dispatch({
                            type: actionTypes.UPDATE_ACTION,
                            testId,
                            action,
                        })
                        return response
                    },
                    error =>
                        alerting.dispatchError(
                            error,
                            "UPDATE_ACTION",
                            `Failed to update action ${action.id} (${JSON.stringify(action.config)}`
                        )
                )
            )
        })
        return Promise.all(promises)
    }
}

export function addToken(testId: number, value: string, description: string, permissions: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTokensAction >) =>
        testApi.addToken(testId, { id: -1, value, description, permissions }).then(
            () =>
                testApi.tokens(testId).then(
                    tokens =>
                        dispatch({
                            type: actionTypes.UPDATE_TOKENS,
                            testId,
                            tokens,
                        }),
                    error =>
                        alerting.dispatchError(error, "FETCH_TOKENS", "Failed to fetch token list for test " + testId)
                ),
            error => alerting.dispatchError(error, "ADD_TOKEN", "Failed to add token for test " + testId)
        )
}

export function revokeToken(testId: number, tokenId: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<RevokeTokenAction >) =>
        testApi.dropToken(testId, tokenId).then(
            () =>
                dispatch({
                    type: actionTypes.REVOKE_TOKEN,
                    testId,
                    tokenId,
                }),
            error => alerting.dispatchError(error, "REVOKE_TOKEN", "Failed to revoke token")
        )
}

export function updateAccess(id: number, owner: string, access: Access, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateAccessAction >) =>
        testApi.updateAccess(id, access, owner).then(
            () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access }),
            error =>
                alerting.dispatchError(
                    error,
                    "UPDATE_ACCESS",
                    "Test access update failed",
                    constraintValidationFormatter("the saved test")
                )
        )
}

export function deleteTest(id: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<DeleteAction >) =>
        testApi._delete(id).then(
            () => dispatch({ type: actionTypes.DELETE, id }),
            error => alerting.dispatchError(error, "DELETE_TEST", "Failed to delete test " + id)
        )
}

export function allSubscriptions(alerting:AlertContextType, folder?: string) {
    return (dispatch: Dispatch<UpdateTestWatchAction >) =>
        subscriptionsApi.all(folder).then(
            response =>
                dispatch({
                    type: actionTypes.UPDATE_TEST_WATCH,
                    byId: Map(Object.entries(response).map(([key, value]) => [parseInt(key), [...value]])),
                }),
            error => alerting.dispatchError(error, "GET_ALL_SUBSCRIPTIONS", "Failed to fetch test subscriptions")
        )
}

function watchToList(watch: Watch) {
    return [...watch.users, ...watch.teams, ...watch.optout.map((u: string) => `!${u}`)]
}

export function getSubscription(testId: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTestWatchAction >) =>
        subscriptionsApi.get(testId).then(
            watch => {
                dispatch({
                    type: actionTypes.UPDATE_TEST_WATCH,
                    byId: Map([[testId, watchToList(watch)]]),
                })
                return watch
            },
            error => alerting.dispatchError(error, "SUBSCRIPTION_LOOKUP", "Subscription lookup failed")
        ) as Promise<Watch>
}

export function updateSubscription(watch: Watch, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTestWatchAction >) =>
        subscriptionsApi.update(watch.testId, watch).then(
            () =>
                dispatch({
                    type: actionTypes.UPDATE_TEST_WATCH,
                    byId: Map([[watch.testId, watchToList(watch)]]),
                }),
            error => alerting.dispatchError(error, "SUBSCRIPTION_UPDATE", "Failed to update subscription")
        )
}

export function addUserOrTeam(id: number, userOrTeam: string, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTestWatchAction >) => {
        dispatch({
            type: actionTypes.UPDATE_TEST_WATCH,
            byId: Map([[id, undefined]]),
        })
        return subscriptionsApi.addUserOrTeam(id, userOrTeam).then(
            response =>
                dispatch({
                    type: actionTypes.UPDATE_TEST_WATCH,
                    byId: Map([[id, response as string[]]]),
                }),
            error => alerting.dispatchError(error, "ADD_SUBSCRIPTION", "Failed to add test subscriptions")
        )
    }
}

export function removeUserOrTeam(id: number, userOrTeam: string, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTestWatchAction >) => {
        dispatch({
            type: actionTypes.UPDATE_TEST_WATCH,
            byId: Map([[id, undefined]]),
        })
        return subscriptionsApi.removeUserOrTeam(id, userOrTeam).then(
            response =>
                dispatch({
                    type: actionTypes.UPDATE_TEST_WATCH,
                    byId: Map([[id, response as string[]]]),
                }),
            error => alerting.dispatchError(error, "REMOVE_SUBSCRIPTION", "Failed to remove test subscriptions")
        )
    }
}

export function fetchFolders(alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateFoldersAction >) => {
        return testApi.folders().then(
            response =>
                dispatch({
                    type: actionTypes.UPDATE_FOLDERS,
                    folders: response,
                }),
            error => alerting.dispatchError(error, "UPDATE_FOLDERS", "Failed to retrieve a list of existing folders")
        )
    }
}

export function updateTransformers(testId: number, transformers: Transformer[], alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTransformersAction >) => {
        return testApi.updateTransformers(
            testId,
            transformers.map(t => t.id)
        ).then(
            () => dispatch({ type: actionTypes.UPDATE_TRANSFORMERS, testId, transformers }),
            error =>
                alerting.dispatchError(
                    error,
                    "UPDATE_TRANSFORMERS",
                    "Failed to update transformers for test " + testId
                )
        )
    }
}

export function updateChangeDetection(
    alerting: AlertContextType,
    testId: number,
    timelineLabels: string[] | undefined,
    timelineFunction: string | undefined,
    fingerprintLabels: string[],
    fingerprintFilter: string | undefined
) {
    return (dispatch: Dispatch<UpdateChangeDetectionAction >) => {
        const update = {
            timelineLabels,
            timelineFunction,
            fingerprintLabels,
            fingerprintFilter,
        }
        return alertingApi.updateChangeDetection(testId, update).then(
            () =>
                dispatch({
                    type: actionTypes.UPDATE_CHANGE_DETECTION,
                    testId,
                    ...update,
                }),
            error =>
                alerting.dispatchError(error, "UPDATE_FINGERPRINT", "Failed to update fingerprint for test " + testId)
        )
    }
}

export function updateRunsAndDatasetsAction(
    testId: number,
    runs: number,
    datasets: number
): UpdateRunsAndDatasetsAction {
    return { type: actionTypes.UPDATE_RUNS_AND_DATASETS, testId, runs, datasets }
}

function ensureInteger(id: any): number {
    if (typeof id === "string") {
        return parseInt(id)
    } else if (typeof id === "number") {
        return id
    } else {
        throw "Cannot convert " + id + " to integer"
    }
}
