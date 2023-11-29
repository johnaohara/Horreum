import { Button } from "@patternfly/react-core"

import { UserData } from "./api"
import Keycloak, {KeycloakProfile} from "keycloak-js";
import {AppContextType, AuthContextType} from "./context/@types/appContextTypes";
import {useContext} from "react";
import {AppContext} from "./context/appContext";

export class AuthState {
    keycloak?: Keycloak
    authenticated = false
    roles: string[] = []
    teams: string[] = []
    defaultTeam?: string = undefined
    userProfile?: KeycloakProfile
    initPromise?: Promise<boolean> = undefined
}

export function resetUserSession() {
    const {auth} = useContext(AppContext) as AppContextType;
    auth.state.userProfile = undefined
    auth.state.initPromise = undefined
    auth.state.authenticated = false
    auth.state.roles = []
    auth.state.teams = []
    auth.state.defaultTeam = undefined
    //todo: reset user session
}

export const keycloakSelector = ()  => {
    return useContext(AppContext)?.auth.state.keycloak
}

export const tokenSelector = () => {
    const {auth} = useContext(AppContext) as AppContextType;
    return auth.state.keycloak && auth.state.keycloak.token
}

export const teamToName = (team?: string) => {
    return team ? team.charAt(0).toUpperCase() + team.slice(1, -5) : undefined
}

export const userProfileSelector = () => {
    return useContext(AppContext)?.auth.state.userProfile
}

export const isAuthenticatedSelector = () => {
    return useContext(AppContext)?.auth.state.authenticated
}

export const isAdminSelector = () => {
    return useContext(AppContext)?.auth.state.roles.includes("admin")
}

export const teamsSelector = (): string[] => {
    return useContext(AppContext)?.auth.state.teams || []
}

function rolesSelector(auth: AuthContextType) {
    return auth.state.roles
}

function isTester(owner: string, roles: string[]) {
    return roles.includes(owner.slice(0, -4) + "tester")
}

export function useTester(owner?: string) {
    const {auth} = useContext(AppContext) as AppContextType;
    const roles = rolesSelector(auth)
    return roles.includes("tester") && (!owner || isTester(owner, roles))
}

export function managedTeamsSelector() {
    const {auth} = useContext(AppContext) as AppContextType;
    return auth.state.roles.filter(role => role.endsWith("-manager")).map(role => role.slice(0, -7) + "team")
}

export function useManagedTeams(): string[] {
    return managedTeamsSelector()
}

export const defaultTeamSelector = () => {
    const {auth} = useContext(AppContext) as AppContextType;
    if (auth.state.defaultTeam !== undefined) {
        return auth.state.defaultTeam
    }
    const teamRoles = teamsSelector()
    return teamRoles.length > 0 ? teamRoles[0] : undefined
}

export const TryLoginAgain = () => {
    const keycloak = keycloakSelector()
    return keycloak ? (
        <>
            Try{" "}
            <Button variant="link" onClick={() => keycloak.login()}>
                log in again
            </Button>
        </>
    ) : null
}

export const LoginLogout = () => {
    const keycloak = keycloakSelector()
    const authenticated = isAuthenticatedSelector()
    if (!keycloak) {
        return <Button isDisabled>Cannot log in</Button>
    }
    if (authenticated) {
        return (
            <Button
                onClick={() => {
                    keycloak?.logout({ redirectUri: window.location.origin })
                    resetUserSession()
                }}
            >
                Log out
            </Button>
        )
    } else {
        return <Button onClick={() => keycloak?.login()}>Log in</Button>
    }
}


export function userName(user: UserData) {
    let str = ""
    if (user.firstName) {
        str += user.firstName + " "
    }
    if (user.lastName) {
        str += user.lastName + " "
    }
    if (user.firstName || user.lastName) {
        return str + " [" + user.username + "]"
    } else {
        return user.username
    }
}
