import {useMemo, useEffect, useState, useContext} from "react"
import { useSelector } from "react-redux"
import { useDispatch } from "react-redux"
import { Card, CardHeader, CardFooter, CardBody, PageSection, Pagination } from "@patternfly/react-core"
import { NavLink } from "react-router-dom"

import * as actions from "./actions"
import { useTester, teamsSelector, teamToName } from "../../auth"
import { noop } from "../../utils"
import Table from "../../components/Table"
 
import ActionMenu, { useShareLink, useChangeAccess, useDelete } from "../../components/ActionMenu"
import ButtonLink from "../../components/ButtonLink"
import { CellProps, Column } from "react-table"
import { SchemaDispatch } from "./reducers"
import {Access, SortDirection, SchemaQueryResult, Schema, schemaApi} from "../../api"
import SchemaImportButton from "./SchemaImportButton"
import AccessIconOnly from "../../components/AccessIconOnly"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type C = CellProps<Schema>

export default function AllSchema() {
    document.title = "Schemas | Horreum"
    const { alerting } = useContext(AppContext) as AppContextType;
    const dispatch = useDispatch<SchemaDispatch>()

    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [direction] = useState<SortDirection>('Ascending')
    const pagination = useMemo(() => ({ page, perPage, direction }), [page, perPage, direction])
    const [schemas, setSchemas] = useState<SchemaQueryResult>()
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        setLoading(true)
        schemaApi.list(
             'Ascending',
             pagination.perPage,
             pagination.page - 1
             )
            .then(setSchemas)
            .catch(error => alerting.dispatchError(error,"FETCH_SCHEMA", "Failed to fetch schemas"))
            .finally(() => setLoading(false))
    }, [pagination,  dispatch])

    const columns: Column<Schema>[] = useMemo(
        () => [
            {
                Header: "Name",
                accessor: "name",
                disableSortBy: false,
                Cell: (arg: C) => {
                    return <NavLink to={"/schema/" + arg.row.original.id}>{arg.cell.value}</NavLink>
                },
            },
            {
                Header: "URI",
                accessor: "uri",
            },
            {
                Header: "Description",
                accessor: "description",
            },
            {
                Header: "Owner",
                id: "owner",
                accessor: (row: Schema) => ({
                    owner: row.owner,
                    access: row.access,
                }),
                Cell: (arg: C) => (
                    <>
                        {teamToName(arg.cell.value.owner)}
                        <span style={{ marginLeft: '8px' }}>
                        <AccessIconOnly access={arg.cell.value.access} />
                        </span>
                    </>
                ),
            },
            {
                Header: "Actions",
                accessor: "id",
                Cell: arg => {
                    const shareLink = useShareLink({
                        token: arg.row.original.token || undefined,
                        tokenToLink: (id, token) => "/schema/" + id + "?token=" + token,
                        onTokenReset: id => dispatch(actions.resetToken(id,  alerting)).catch(noop),
                        onTokenDrop: id => dispatch(actions.dropToken(id, alerting)).catch(noop),
                    })
                    const changeAccess = useChangeAccess({
                        onAccessUpdate: (id, owner, access) =>
                            dispatch(actions.updateAccess(id, owner, access, alerting)).catch(noop),
                    })
                    const del = useDelete({
                        onDelete: id => dispatch(actions.deleteSchema(id, alerting)).catch(noop),
                    })
                    return (
                        <ActionMenu
                            id={arg.cell.value}
                            owner={arg.row.original.owner}
                            access={arg.row.original.access as Access}
                            description={"schema " + arg.row.original.name + " (" + arg.row.original.uri + ")"}
                            items={[shareLink, changeAccess, del]}
                        />
                    )
                },
            },
        ],
        [dispatch]
    )
    const [reloadCounter, setReloadCounter] = useState(0)
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        dispatch(actions.all(alerting)).catch(noop)
    }, [dispatch, teams, reloadCounter])
    const isTester = useTester()
    return (
        <PageSection>
            <Card>
                {isTester && (
                    <CardHeader>
                        <ButtonLink to="/schema/_new">New Schema</ButtonLink>
                        <SchemaImportButton
                            schemas={schemas?.schemas || []}
                            onImported={() => setReloadCounter(reloadCounter + 1)}
                        />
                    </CardHeader>
                )}
                <CardBody style={{ overflowX: "auto" }}>
                    <Table columns={columns}
                    data={schemas?.schemas || []}
                    sortBy={[{ id: "name", desc: false }]}
                    isLoading={loading}
                    />
                </CardBody>
                <CardFooter style={{ textAlign: "right" }}>
                    <Pagination
                        itemCount={schemas?.count || 0}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </CardFooter>
            </Card>
        </PageSection>
    )
}
