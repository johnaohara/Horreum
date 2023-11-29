import Keycloak, {KeycloakConfig, KeycloakInitOptions} from "keycloak-js"

import { configApi, userApi} from "./api"
import { noop } from "./utils"
import {AlertContextType} from "./context/@types/appContextTypes";
import {AuthState} from "./auth";

export const initKeycloak = (alerting: AlertContextType , authState: AuthState, updateAuthState: (authState: AuthState) => AuthState) : Promise<boolean> => {
    let updatedState = authState;
    return configApi.keycloak()
        .then((response: any) : Keycloak => new Keycloak(response as KeycloakConfig))
        .then((keycloak: Keycloak) : boolean => {
            let initPromise: Promise<boolean> | undefined = undefined
            if (!keycloak.authenticated) {
                // Typecast required due to https://github.com/keycloak/keycloak/pull/5858
                initPromise = keycloak.init({
                    onLoad: "check-sso",
                    silentCheckSsoRedirectUri: window.location.origin + "/silent-check-sso.html",
                    promiseType: "native",
                } as KeycloakInitOptions)
                initPromise?.then(authenticated => {
                    updatedState = updateAuthState({...updatedState, authenticated: authenticated,  roles: keycloak?.realmAccess?.roles || [] })
                    if (authenticated) {
                        keycloak
                            .loadUserProfile()
                            .then(profile => updateAuthState({...authState,  userProfile: profile }))
                            .catch(error =>
                                alerting.dispatchError(error, "PROFILE_FETCH_FAILURE", "Failed to fetch user profile")
                            )
                        userApi.defaultTeam().then(
                            response =>  updatedState = updateAuthState({...updatedState,  defaultTeam: response || undefined  }),
                            error =>
                                alerting.dispatchError(error, "DEFAULT_ROLE_FETCH_FAILURE", "Cannot retrieve default role")
                        )
                        keycloak.onTokenExpired = () =>
                            keycloak.updateToken(30).catch(e => console.log("Expired token update failed: " + e))
                    } else {
                        updatedState = updateAuthState({...updatedState,  userProfile: {}  })
                    }
                })
            }
            updatedState = updateAuthState({...updatedState,  keycloak: keycloak  }) //TODO:: validate keyclaok client state
            return true;
        })
}
