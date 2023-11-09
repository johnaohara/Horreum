import { Alert as PatternflyAlert, AlertActionCloseButton, AlertVariant } from "@patternfly/react-core"

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


function Alerts() {
    const { alerting } = useContext(AppContext) as AppContextType;
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
