import { Dispatch } from "redux"
import {
    UpdateAccessAction,
    UpdateTokenAction,
    TrashAction,
    UpdateDescriptionAction,
    UpdateSchemaAction,
    UpdateDatasetsAction,
} from "../runs/reducers"
import * as actionTypes from "./actionTypes"
import {runApi, Access} from "../../api"
import {AlertContextType} from "../../context/@types/appContextTypes";


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
