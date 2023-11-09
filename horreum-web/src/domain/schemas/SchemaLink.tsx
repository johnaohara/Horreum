import { useDispatch } from "react-redux"

import { schemaApi } from "../../api"
import IndirectLink from "../../components/IndirectLink"
import {useContext} from "react";
import {AppContext, AppContextType} from "../../context/appContext";

type SchemaLinkProps = {
    uri: string
}

export default function SchemaLink({ uri }: SchemaLinkProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const dispatch = useDispatch()
    return (
        <IndirectLink
            style={{ padding: 0, fontWeight: "var(--pf-global--link--FontWeight)" }}
            onNavigate={() =>
                schemaApi.idByUri(uri).then(
                    id => `/schema/${id}`,
                    error => alerting.dispatchError(error, "FIND_SCHEMA", "Cannot find schema with URI " + uri)
                ).then()
            }
        >
            {uri}
        </IndirectLink>
    )
}
