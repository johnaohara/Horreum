import { AnyAction, Dispatch } from "redux"
import { useDispatch } from "react-redux"

import { Alert as PatternflyAlert, AlertActionCloseButton, AlertVariant } from "@patternfly/react-core"

import { State } from "./store"
import {AppContext, AppContextType} from "./context/appContext";
import React, {useContext} from "react";

export const ADD_ALERT = "alert/ADD"
export const CLEAR_ALERT = "alert/CLEAR"

export interface Alert {
    title: string
    type: string
    variant?: AlertVariant
    content: Element | string | undefined
}

export interface ConstraintValidationException {
    error: string
    violations: ConstraintViolation[]
}

export interface ConstraintViolation {
    class: string
    path: string
    message: string
}

export interface AddAlertAction extends AnyAction {
    type: typeof ADD_ALERT
    alert: Alert
}

interface ClearAlertAction {
    type: typeof CLEAR_ALERT
    alert?: Alert
}

type AlertActions = AddAlertAction | ClearAlertAction

export const reducer = (state: Alert[] = [], action: AlertActions) => {
    switch (action.type) {
        case ADD_ALERT:
            return [...state.filter(a => a.type !== action.alert.type), action.alert]
        case CLEAR_ALERT:
            if (action.alert) {
                const alert: Alert = action.alert
                return state.filter(a => a.type !== alert.type && a.title !== alert.title)
            } else {
                return []
            }
        default:
    }
    return state
}

export const constraintValidationFormatter = (object: any) => (e: any) => {
    if (e && e.error && e.error === "jakarta.validation.ConstraintViolationException") {
        return (
            <>
                <span>Some constraints on {object} have failed:</span>
                <br />
                <ul>
                    {(e as ConstraintValidationException).violations.map((v, i) => (
                        <li key={i}>
                            <code>
                                {v.class}/{v.path}
                            </code>
                            : {v.message}
                        </li>
                    ))}
                </ul>
            </>
        )
    } else {
        return false
    }
}

export const alertAction = (
    type: string,
    title: string,
    e: any,
    ...errorFormatter: ((error: any) => any)[]
): AddAlertAction => {
    let formatted = undefined
    for (const f of errorFormatter) {
        formatted = f.call(null, e)
        if (formatted) break
    }
    if (!formatted) {
        formatted = defaultFormatError(e)
    }
    return {
        type: ADD_ALERT,
        alert: {
            type,
            title,
            content: formatted,
        },
    }
}

// this method will always reject so we can type it with 'any', reason is 'any' anyway
export function dispatchError(
    dispatch: Dispatch<AddAlertAction>,
    error: any,
    type: string,
    title: string,
    ...errorFormatter: ((error: any) => any)[]
): Promise<any> {
    dispatch(alertAction(type, title, error, ...errorFormatter))
    return Promise.reject(error)
}

export function defaultFormatError(e: any) {
    console.log(e)
    if (!e) {
        return ""
    }
    if (typeof e === "string") {
        try {
            e = JSON.parse(e)
        } catch {
            /* noop */
        }
    }
    if (typeof e !== "object") {
        return String(e)
    } else if (e instanceof Error) {
        return e.toString()
    } else {
        return <pre>{JSON.stringify(e, null, 2)}</pre>
    }
}

const alertsSelector = (state: State) => state.alerts

function Alerts() {
    const { alerting } = useContext(AppContext) as AppContextType;
    // const alerts = useSelector(alertsSelector)
    const dispatch = useDispatch()
    if (alerting.alerts.length === 0) {
        return <></>
    }
    return (
        <div style={{ position: "absolute", zIndex: 1000, width: "100%" }}>
            {alerting.alerts.map((alert, i) => (
                <PatternflyAlert
                    key={i}
                    variant={alert.variant || "warning"}
                    title={alert.title || "Title is missing"}
                    actionClose={
                        <AlertActionCloseButton
                            onClose={() => { alerting.clearAlert (alert)}}
                        />
                    }
                >
                    {alert.content}
                </PatternflyAlert>
            ))}
        </div>
    )
}

export default Alerts
