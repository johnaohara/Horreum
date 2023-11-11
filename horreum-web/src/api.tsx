import {
    ActionApi,
    AlertingApi,
    BannerApi,
    ChangesApi,
    ConfigApi,
    DatasetApi,
    ExperimentApi,
    LogApi,
    NotificationsApi,
    ReportApi,
    RunApi,
    SchemaApi,
    SqlApi,
    SubscriptionsApi,
    TestApi,
    UiApi,
    UserApi,

} from "./generated/apis"
import {
    Action, AllowedSite,
    Configuration,
    Middleware,
    Schema,
    Test,
    TestListing,
} from "./generated"
import store from "./store"
import {ADD_ALERT} from "./alerts"
import {TryLoginAgain} from "./auth"
import {AlertContextType} from "./context/@types/appContextTypes";
export * from "./generated/models"

const authMiddleware: Middleware = {
    pre: ctx => {
        const keycloak = store.getState().auth.keycloak
        if (keycloak != null && keycloak.authenticated) {
            return keycloak.updateToken(30).then(
                () => {
                    if (keycloak != null && keycloak.token != null) {
                        return {
                            url: ctx.url,
                            init: {
                                ...ctx.init,
                                headers: {...ctx.init.headers, Authorization: "Bearer " + keycloak.token},
                            },
                        }
                    }
                },
                e => {
                    store.dispatch({
                        type: ADD_ALERT,
                        alert: {
                            type: "TOKEN_UPDATE_FAILED",
                            title: "Token update failed",
                            content: <TryLoginAgain/>,
                        },
                    })
                    return Promise.reject(e)
                }
            )
        } else {
            return Promise.resolve()
        }
    },
    post: ctx => {
        if (ctx.response.ok) {
            return Promise.resolve(ctx.response)
        } else if (ctx.response.status === 401 || ctx.response.status === 403) {
            store.dispatch({
                type: ADD_ALERT,
                alert: {
                    type: "REQUEST_FORBIDDEN",
                    title: "Request failed due to insufficient permissions",
                    content: <TryLoginAgain/>,
                },
            })

            const contentType = ctx.response.headers.get("content-type")
            if (contentType === "application/json") {
                return ctx.response.json().then((body: any) => Promise.reject(body))
            } else {
                return ctx.response.text().then((text: any) => Promise.reject(text))
            }
        } else {
            // We won't reject it because that would skip other middleware
            return Promise.resolve(ctx.response)
        }
    },
}

const serialize = (input: any): any => {
    if (input === null || input === undefined) {
        return input
    } else if (Array.isArray(input)) {
        return input.map(v => serialize(v))
    } else if (typeof input === "function") {
        return input.toString()
    } else if (typeof input === "object") {
        const rtrn: { [key: string]: any } = {}
        Object.keys(input).forEach(key => {
            rtrn[key] = serialize(input[key])
        })
        return rtrn
    } else {
        return input
    }
}

const serializationMiddleware: Middleware = {
    pre: ctx => {
        return Promise.resolve({url: ctx.url, init: {...ctx.init, body: serialize(ctx.init.body)}})
    },
    // we won't deserialize functions eagerly
}

const noResponseMiddleware: Middleware = {
    post: ctx => {
        if (ctx.response.status === 204) {
            const rsp = ctx.response.clone()
            rsp.json = () => Promise.resolve(undefined)
            return Promise.resolve(rsp)
        } else if (ctx.response.status >= 400) {
            return ctx.response.text().then(err => {
                if (err) {
                    return Promise.reject(err)
                } else {
                    return Promise.reject(ctx.response.status + " " + ctx.response.statusText)
                }
            })
        }
        return Promise.resolve(ctx.response)
    },
}


const configuration = new Configuration({
    basePath: window.location.origin,
    middleware: [authMiddleware, serializationMiddleware, noResponseMiddleware],
});

const actionApi = new ActionApi(configuration)
export const alertingApi = new AlertingApi(configuration)
export const bannerApi = new BannerApi(configuration)
export const changesApi = new ChangesApi(configuration)
export const datasetApi = new DatasetApi(configuration)
export const experimentApi = new ExperimentApi(configuration)
export const logApi = new LogApi(configuration)
export const notificationsApi = new NotificationsApi(configuration)
export const reportApi = new ReportApi(configuration)
export const runApi = new RunApi(configuration)
export const schemaApi = new SchemaApi(configuration)
export const sqlApi = new SqlApi(configuration)
export const subscriptionsApi = new SubscriptionsApi(configuration)
export const testApi = new TestApi(configuration)
export const uiApi = new UiApi(configuration)
export const userApi = new UserApi(configuration)
export const configApi = new ConfigApi(configuration)


//Actions
export function addAction(action: Action, alerting: AlertContextType): Promise<Action> {
    return executeApiCall(actionApi.add(action), alerting, "ADD_ACTION", "Failed to add action");
}
export function addSite(prefix: string, alerting: AlertContextType): Promise<AllowedSite> {
    return executeApiCall(actionApi.addSite(prefix), alerting, "ADD_ALLOWED_SITE", "Failed to add allowed site");
}
export function deleteSite(id: number, alerting: AlertContextType): Promise<void> {
    return executeApiCall(actionApi.deleteSite(id), alerting, "REMOVE_ALLOWED_SITE", "Failed to remove allowed site");

}
export function getTestActions(testId: number, alerting: AlertContextType): Promise<Action[]> {
    return executeApiCall(actionApi.getTestActions(testId), alerting, "GET_TEST_ACTIONS", "Failed to get test actions");
}

export function getAllowedSites(alerting: AlertContextType): Promise<AllowedSite[]> {
    return executeApiCall(actionApi.allowedSites(), alerting, "GET_ALLOWED_SITES", "Failed to get allowed sites");
}

export function allActions(alerting: AlertContextType): Promise<Action[]> {
    return executeApiCall(actionApi.list(), alerting, "GET_ACTIONS", "Failed to get actions");
}

export function removeAction(id: number, alerting: AlertContextType): Promise<void> {
    return executeApiCall(actionApi._delete(id), alerting, "REMOVE_ACTION", "Failed to remove action");
}

export function updateAction(action: Action, alerting: AlertContextType): Promise<Action> {
    return executeApiCall(actionApi.update(action), alerting, "UPDATE_ACTION", "Failed to update action");
}

//Schemas
export function getSchema(schemaId: number, alerting: AlertContextType): Promise<Schema> {
    return executeApiCall(schemaApi.getSchema(schemaId), alerting, "GET_SCHEMA", "Failed to fetch schema");
}

//Tests
export function fetchTestsSummariesByFolder(alertingContext: AlertContextType, roles?: string, folder?: string): Promise<TestListing> {
    return executeApiCall(testApi.summary(folder, roles), alertingContext, "FETCH_TEST_SUMMARY", "Failed to fetch test summary.");
}

export function fetchTest(id: number, alerting: AlertContextType): Promise<Test> {
    return executeApiCall(testApi.get(id), alerting, "FETCH_TEST", "Failed to fetch test; the test may not exist or you don't have sufficient permissions to access it.");
}

export function revokeTestToken(testId: number, tokenId: number, alerting: AlertContextType) {
    return executeApiCall(testApi.dropToken(testId, tokenId), alerting, "REVOKE_TOKEN", "Failed to revoke token");
}

function executeApiCall<T>(apiCall: Promise<T>, alerting: AlertContextType, errorKey: string, errorMessage: string): Promise<T> {
    return apiCall.then(
        response => response,
        error => alerting.dispatchError(error, errorKey, errorMessage)
    )
}



