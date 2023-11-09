import { Dispatch } from "redux"
import {
    LoadedAction,
    LoadingAction,
    TestIdAction,
    UpdateAccessAction,
    UpdateTokenAction,
    TrashAction,
    UpdateDescriptionAction,
    UpdateSchemaAction,
    UpdateDatasetsAction,
} from "../runs/reducers"
import * as actionTypes from "./actionTypes"
import {runApi, RunExtended, RunSummary, SortDirection, Access } from "../../api"
import { PaginationInfo } from "../../utils"
import {AlertContextType} from "../../context/appContext";

const loaded = (run: RunExtended | undefined, total?: number): LoadedAction => ({
    type: actionTypes.LOADED,
    run,
    total,
})

const testId = (id: number, runs: RunSummary[], total: number): TestIdAction => ({
    type: actionTypes.TESTID,
    id,
    runs,
    total,
})

export function get(alerting: AlertContextType, id: number, token?: string) {
    return (dispatch: Dispatch<LoadedAction>) =>
        runApi.getRun(id, token).then(
            response => dispatch(loaded(response)),
            error => {
                alerting.dispatchError(error,"FETCH_RUN", "Failed to fetch data for run " + id)
                dispatch(loaded(undefined, 0))
                return Promise.reject(error)
            }
        )
}

export function getSummary(alerting: AlertContextType, id: number, token?: string) {
    return (dispatch: Dispatch<LoadedAction>) =>
        runApi.getRunSummary(id, token).then(
            response =>
                dispatch(
                    loaded({
                        data: "",
                        schemas: [],
                        metadata: response.hasMetadata ? "" : undefined,
                        ...response,
                    })
                ),
            error => {
                alerting.dispatchError(error,"FETCH_RUN_SUMMARY", "Failed to fetch data for run " + id)
                dispatch(loaded(undefined, 0))
                return Promise.reject(error)
            }
        )
}

export function byTest(alerting: AlertContextType, id: number, pagination: PaginationInfo, trashed: boolean) {
    return (dispatch: Dispatch<LoadingAction | TestIdAction >) => {
        dispatch({ type: actionTypes.LOADING })
        return runApi.listTestRuns(
            id,
            pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending,
            pagination.perPage,
            pagination.page,
            pagination.sort,
            trashed
        ).then(
            response => dispatch(testId(id, response.runs, response.total)),
            error => {
                alerting.dispatchError(error,"FETCH_RUNS", "Failed to fetch runs for test " + id)
                dispatch(testId(id, [], 0))
                return Promise.reject(error)
            }
        )
    }
}


export function resetToken(alerting: AlertContextType, id: number, testid: number) {
    return (dispatch: Dispatch<UpdateTokenAction >) => {
        return runApi.resetToken(id).then(
            token => {
                dispatch({
                    type: actionTypes.UPDATE_TOKEN,
                    id,
                    testid,
                    token,
                })
            },
            error => alerting.dispatchError(error, "RESET_RUN_TOKEN", "Failed to reset token for run " + id)
        )
    }
}

export const dropToken = (alerting: AlertContextType, id: number, testid: number) => (dispatch: Dispatch<UpdateTokenAction >) => {
    return runApi.dropToken(id).then(
        _ => {
            dispatch({
                type: actionTypes.UPDATE_TOKEN,
                id,
                testid,
                token: null,
            })
        },
        error => alerting.dispatchError(error, "DROP_RUN_TOKEN", "Failed to drop run token")
    )
}

export function updateAccess(alerting: AlertContextType, id: number, testid: number, owner: string, access: Access) {
    return (dispatch: Dispatch<UpdateAccessAction >) => {
        return runApi.updateAccess(id, access, owner).then(
            _ => {
                dispatch({
                    type: actionTypes.UPDATE_ACCESS,
                    id,
                    testid,
                    owner,
                    access,
                })
            },
            error => alerting.dispatchError(error, "UPDATE_RUN_ACCESS", "Failed to update run access")
        )
    }
}

export function trash(alerting: AlertContextType, id: number, testid: number, isTrashed = true) {
    return (dispatch: Dispatch<TrashAction >) =>
        runApi.trash(id, isTrashed).then(
            _ => {
                dispatch({
                    type: actionTypes.TRASH,
                    id,
                    testid,
                    isTrashed,
                })
            },
            error => alerting.dispatchError(error, "RUN_TRASH", "Failed to restore run ID " + id)
        )
}

export function updateDescription(id: number, testid: number, description: string, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateDescriptionAction >) =>
        runApi.updateDescription(id, description).then(
            _ => {
                dispatch({
                    type: actionTypes.UPDATE_DESCRIPTION,
                    id,
                    testid,
                    description,
                })
            },
            error => alerting.dispatchError(error, "RUN_UPDATE", "Failed to update description for run ID " + id)
        )
}

export function updateSchema(alerting: AlertContextType, id: number, testid: number, path: string | undefined, schemaUri: string) {
    return (dispatch: Dispatch<UpdateSchemaAction >) =>
        runApi.updateSchema(id, schemaUri, path).then(
            schemas =>
                dispatch({
                    type: actionTypes.UPDATE_SCHEMA,
                    id,
                    testid,
                    path,
                    schema: schemaUri,
                    schemas,
                }),
            error => alerting.dispatchError(error, "SCHEME_UPDATE_FAILED", "Failed to update run schema")
        )
}

export function recalculateDatasets(id: number, testid: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateDatasetsAction >) =>
        runApi.recalculateDatasets(id).then(
            datasets =>
                dispatch({
                    type: actionTypes.UPDATE_DATASETS,
                    id,
                    testid,
                    datasets,
                }),
            error => alerting.dispatchError(error, "RECALCULATE_DATASETS", "Failed to recalculate datasets")
        )
}
