import {Action, actionApi} from "../../api"
import * as actionTypes from "./actionTypes"
import { LoadedAction, DeleteAction } from "./reducers"
import { Dispatch } from "redux"
import { AlertContextType } from "../../context/appContext";

const loaded = (action: Action | Action[]): LoadedAction => {
    return {
        type: actionTypes.LOADED,
        actions: Array.isArray(action) ? action : [action],
    }
}
const removed = (id: number): DeleteAction => ({
    type: actionTypes.DELETE,
    id,
})


export function addAction(action: Action, alerting: AlertContextType ) {
    return (dispatch: Dispatch<LoadedAction >) =>
        actionApi.add(action).then(
            response => dispatch(loaded(response)),
            error => alerting.dispatchError(error, "ADD_ACTION", "Failed to add action")
        )
}

export function allActions(alerting: AlertContextType) {
    return (dispatch: Dispatch<LoadedAction >) =>
        actionApi.list().then(
            response => dispatch(loaded(response)),
            error => alerting.dispatchError(error, "GET_ACTIONS", "Failed to get actions")
        )
}

export function removeAction(id: number, alerting: AlertContextType) {
    return (dispatch: Dispatch<DeleteAction >) =>
        actionApi._delete(id).then(
            _ => dispatch(removed(id)),
            error => alerting.dispatchError(error, "REMOVE_ACTION", "Failed to remove action")
        )
}
