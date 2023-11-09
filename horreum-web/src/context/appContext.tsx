import * as React from 'react';
import {Alert} from "../alerts";
import {createBrowserHistory} from "history";
import {useState} from "react";
import {contextAlertAction} from "./alertActions";
import {AlertVariant} from "@patternfly/react-core";
import {userApi} from "../api";

export const AppContext = React.createContext<AppContextType | null>(null);
export const history = createBrowserHistory()

export type AlertContextType = {

    alerts: Alert[];

    addAlert: (alert: Alert) => void;

    clearAlert: (alert: Alert) => void;

    dispatchError: (error: any,
                    type: string,
                    title: string,
                    ...errorFormatter: ((error: any) => any)[]) => void;

    dispatchInfo: (type: string,
                   title: string,
                   message: string,
                   timeout: number) => void;

}

export type AuthContextType = {
    updateDefaultTeam: (team: string,
                        onSuccess: () => void,
                        onFailure: (error: any) => void) => Promise<any>;
}


export type AppContextType = {
    alerting: AlertContextType;
    auth: AuthContextType;
};



const ContextProvider: React.FC<React.ReactNode> = ({ children }) => {
    const [alerts, setAlerts] = useState<Alert[]>([]);
    const newAlert = (newAlert: Alert) => {
        setAlerts([...alerts, newAlert]);
    };

    const clearAlert = (newAlert: Alert) => {
        setAlerts(alerts.filter(a => a.type !== newAlert.type && a.title !== newAlert.title))
    };

    //todo - refactor into alertActions with newAlert callback
    const contextDispatchError = (
        error: any,
        type: string,
        title: string,
        ...errorFormatter: ((error: any) => any)[]
    ): Promise<any> => {
        newAlert(contextAlertAction(type, title, error, ...errorFormatter));
        return Promise.reject(error)
    }

    const contextDispatchInfo = (type: string,
                                 title: string,
                                 message: string,
                                 timeout: number
    ): Promise<any> => {
        const infoAlert: Alert =  {
            type,
            title,
            content: message,
            variant: AlertVariant.info,
        }
        newAlert(infoAlert);
        window.setTimeout(() => clearAlert(infoAlert), timeout)
        return Promise.resolve();
    }

    const alerting : AlertContextType = {
        alerts: alerts,
        addAlert: newAlert,
        clearAlert: clearAlert,
        dispatchError: contextDispatchError,
        dispatchInfo: contextDispatchInfo
    };

    const updateDefaultTeam = (team: string, onSuccess: () => void, onFailure: (error: any) => void): Promise<any> => {
            return userApi.setDefaultTeam(team).then(
                _ => onSuccess(),
                error => onFailure(error)
            )
    }


    const auth : AuthContextType = {
        updateDefaultTeam: updateDefaultTeam
    };

    const context : AppContextType = {
        alerting: alerting,
        auth: auth
    };

    return <AppContext.Provider value={ context }>{children}</AppContext.Provider>;
};

export default ContextProvider;