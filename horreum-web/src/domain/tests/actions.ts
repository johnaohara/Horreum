import * as actionTypes from "./actionTypes"
import {
    UpdateTestWatchAction,
    UpdateChangeDetectionAction,
    UpdateTransformersAction,
    UpdateRunsAndDatasetsAction,
} from "./reducers"
import {
    testApi,
    subscriptionsApi,
    Action,
    Transformer,
    Watch,
    alertingApi,
    TestListing, updateAction
} from "../../api"
import {Dispatch} from "redux"
import {Map} from "immutable"
import {AlertContextType} from "../../context/@types/appContextTypes";

export function fetchTestsSummariesByFolder(alertingContext: AlertContextType, roles?: string, folder?: string): Promise<TestListing> {
    return testApi.summary(folder, roles).then(
        listing => listing,
        error => {
            return alertingContext.dispatchError(error, "FETCH_TEST_SUMMARY", "Failed to fetch test summary.")
        }
    )
}


export function updateActions(testId: number, actions: Action[], alerting: AlertContextType) {
        const promises: any[] = []
        actions.forEach(action => {
            promises.push(
                (action.testId = testId),
                updateAction(action, alerting)
            )
        })
        return Promise.all(promises)
}


function watchToList(watch: Watch) {
    return [...watch.users, ...watch.teams, ...watch.optout.map((u: string) => `!${u}`)]
}

export function getSubscription(testId: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTestWatchAction>) =>
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
    return (dispatch: Dispatch<UpdateTestWatchAction>) =>
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
    return (dispatch: Dispatch<UpdateTestWatchAction>) => {
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
    return (dispatch: Dispatch<UpdateTestWatchAction>) => {
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


export function updateTransformers(testId: number, transformers: Transformer[], alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTransformersAction>) => {
        return testApi.updateTransformers(
            testId,
            transformers.map(t => t.id)
        ).then(
            () => dispatch({type: actionTypes.UPDATE_TRANSFORMERS, testId, transformers}),
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
    return (dispatch: Dispatch<UpdateChangeDetectionAction>) => {
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
    return {type: actionTypes.UPDATE_RUNS_AND_DATASETS, testId, runs, datasets}
}
