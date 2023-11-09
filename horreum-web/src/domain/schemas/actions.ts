import * as actionTypes from "./actionTypes"
import { DeleteAction, LoadedAction, UpdateTokenAction, UpdateAccessAction } from "./reducers"
import { Dispatch } from "redux"
import { ThunkDispatch } from "redux-thunk"
import {Schema, schemaApi, Access } from "../../api"
import {AlertContextType} from "../../context/appContext";

const loaded = (schema: Schema | Schema[]): LoadedAction => ({
    type: actionTypes.LOADED,
    schemas: Array.isArray(schema) ? schema : [schema],
})

export function getById(id: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<LoadedAction >) =>
        schemaApi.getSchema(id).then(
            response => dispatch(loaded(response)),
            error => {
                dispatch(loaded([]))
                return alerting.dispatchError(error, "GET_SCHEMA", "Failed to fetch schema")
            }
        )
}

export function add(payload: Schema, alerting: AlertContextType) {
    return (dispatch: Dispatch<LoadedAction >) =>
        schemaApi.add(payload).then(
            id => {
                dispatch(loaded({ ...payload, id }))
                return id
            },
            error => alerting.dispatchError(error, "SAVE_SCHEMA", "Failed to save schema")
        ) as Promise<number>
}

export function all(alerting: AlertContextType) {
    return (dispatch: Dispatch<LoadedAction >) =>
        schemaApi.list().then(
            response => dispatch(loaded(response.schemas)),
            error => {
                dispatch(loaded([]))
                return alerting.dispatchError(error, "LIST_SCHEMAS", "Failed to list schemas")
            }
        )
}

export function resetToken(id: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTokenAction >) =>
        schemaApi.resetToken(id).then(
            token =>
                dispatch({
                    type: actionTypes.UPDATE_TOKEN,
                    id: id,
                    token: token,
                }),
            error => alerting.dispatchError(error, "RESET_SCHEMA_TOKEN", "Failed to reset schema token")
        )
}

export function dropToken(id: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateTokenAction >) =>
        schemaApi.dropToken(id).then(
            () =>
                dispatch({
                    type: actionTypes.UPDATE_TOKEN,
                    id: id,
                    token: undefined,
                }),
            error => alerting.dispatchError(error, "DROP_SCHEMA_TOKEN", "Failed to drop schema token")
        )
}

export function updateAccess(id: number, owner: string, access: Access, alerting: AlertContextType) {
    return (dispatch: Dispatch<UpdateAccessAction >) =>
        schemaApi.updateAccess(id, access, owner).then(
            () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access }),
            error => alerting.dispatchError(error, "SCHEMA_UPDATE", "Failed to update schema access.")
        )
}

export function deleteSchema(id: number, alerting: AlertContextType) {
    return (dispatch: ThunkDispatch<any, unknown, DeleteAction>) =>
        schemaApi._delete(id).then(
            () => {
                dispatch({
                    type: actionTypes.DELETE,
                    id: id,
                })
            },
            error => alerting.dispatchError(error, "SCHEMA_DELETE", "Failed to delete schema " + id)
        )
}
