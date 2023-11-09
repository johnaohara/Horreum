import { createBrowserHistory } from "history"
import { createStore, combineReducers, compose, applyMiddleware, StoreEnhancer } from "redux"
import { createReduxHistoryContext } from "redux-first-history"
import thunk from "redux-thunk"

const { routerMiddleware, routerReducer } = createReduxHistoryContext({
    history: createBrowserHistory(),
})

import { RunsState, reducer as runReducer } from "./domain/runs/reducers"
import { TestsState, reducer as testReducer } from "./domain/tests/reducers"
import { ActionsState, reducer as actionReducer } from "./domain/actions/reducers"
import { SchemasState, reducer as schemaReducer } from "./domain/schemas/reducers"
import { AuthState, reducer as authReducer } from "./auth"


export interface State {
    auth: AuthState
    actions: ActionsState
    runs: RunsState
    schemas: SchemasState
    tests: TestsState
}

const appReducers = combineReducers({
    router: routerReducer,
    runs: runReducer,
    tests: testReducer,
    actions: actionReducer,
    schemas: schemaReducer,
    auth: authReducer,
})
const enhancer = compose(applyMiddleware(thunk), applyMiddleware(routerMiddleware))
const store = createStore(appReducers, enhancer)
export default store
