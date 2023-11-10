import {useContext, useEffect, useState} from "react"
import { useParams } from "react-router"
import { useSelector } from "react-redux"

import * as actions from "./actions"
import { formatDateTime, noop } from "../../utils"
import { teamsSelector, useTester } from "../../auth"

import { Bullseye, Button, Card, CardHeader, CardBody, PageSection, Spinner } from "@patternfly/react-core"
import { TableComposable, Thead, Tr, Th, Tbody, Td } from "@patternfly/react-table"
import FragmentTabs, { FragmentTab } from "../../components/FragmentTabs"
import OwnerAccess from "../../components/OwnerAccess"
import { NavLink } from "react-router-dom"
import { Description } from "./components"
import DatasetData from "./DatasetData"
import MetaData from "./MetaData"
import RunData from "./RunData"
import TransformationLogModal from "../tests/TransformationLogModal"
import {Access, runApi, RunExtended} from "../../api"
import {AppContext} from "../../context/appContext";
import { AppContextType} from "../../context/@types/appContextTypes";

export default function Run() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { id: stringId } = useParams<any>()
    const id = parseInt(stringId)
    document.title = `Run ${id} | Horreum`

    const [run, setRun] = useState<RunExtended | undefined>(undefined)
    const [loading, setLoading] = useState(false)
    const [recalculating, setRecalculating] = useState(false)
    const [transformationLogOpen, setTransformationLogOpen] = useState(false)
    const [updateCounter, setUpdateCounter] = useState(0)

    const teams = useSelector(teamsSelector)
    const isTester = useTester(run?.owner)


    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get("token")
        setLoading(true)
        runApi.getRunSummary(id, token === null ? undefined : token).then(
            response =>
                    setRun({
                        data: "",
                        schemas: [],
                        metadata: response.hasMetadata ? "" : undefined,
                        ...response,
                    }),
            error => alerting.dispatchError(error,"FETCH_RUN_SUMMARY", "Failed to fetch data for run " + id)
        ).finally(() => setLoading(false))
    }, [id, teams, updateCounter])

    const updateAccess = (id: number, testid: number, owner: string, access: Access) : Promise<void> => {
        return runApi.updateAccess(id, access, owner).then(
            () => noop(),
            error => alerting.dispatchError(error, "UPDATE_RUN_ACCESS", "Failed to update run access")
        ) //TODO:: refresh view
    }

    return (
        <PageSection>
            {loading && (
                <Bullseye>
                    <Spinner />
                </Bullseye>
            )}
            {run && (
                <>
                    <Card>
                        <CardHeader>
                            <TableComposable variant="compact">
                                <Thead>
                                    <Tr>
                                        <Th>Id</Th>
                                        <Th>Test</Th>
                                        <Th>Owner</Th>
                                        <Th>Start</Th>
                                        <Th>Stop</Th>
                                        <Th>Description</Th>
                                        <Th>Actions</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    <Tr>
                                        <Td>{run.id}</Td>
                                        <Td>
                                            <NavLink to={`/test/${run.testid}`}>{run.testname || run.testid}</NavLink>
                                        </Td>
                                        <Td>
                                            <OwnerAccess
                                                owner={run.owner}
                                                access={run.access as Access}
                                                readOnly={!isTester}
                                                onUpdate={(owner, access) => updateAccess(run.id, run.testid, owner, access)
                                                }
                                            />
                                        </Td>
                                        <Td>{formatDateTime(run.start)}</Td>
                                        <Td>{formatDateTime(run.stop)}</Td>
                                        <Td>{Description(run.description || "")}</Td>
                                        <Td>
                                            {isTester && (
                                                <>
                                                    <Button
                                                        isDisabled={recalculating}
                                                        onClick={() => {
                                                            setRecalculating(true)
                                                            actions.recalculateDatasets(run.id, run.testid, alerting)
                                                                .then(recalcDataSets => {
                                                                    setRun({...run,
                                                                    datasets: recalcDataSets})
                                                                })
                                                                .finally(() => setRecalculating(false))
                                                        }}
                                                    >
                                                        Re-transform datasets {recalculating && <Spinner size="md" />}
                                                    </Button>
                                                    <Button
                                                        variant="secondary"
                                                        onClick={() => setTransformationLogOpen(true)}
                                                    >
                                                        Transformation log
                                                    </Button>
                                                    <TransformationLogModal
                                                        testId={run.testid}
                                                        runId={run.id}
                                                        title="Transformation log"
                                                        emptyMessage="There are no messages"
                                                        isOpen={transformationLogOpen}
                                                        onClose={() => setTransformationLogOpen(false)}
                                                    />
                                                </>
                                            )}
                                        </Td>
                                    </Tr>
                                </Tbody>
                            </TableComposable>
                        </CardHeader>
                        <CardBody>
                            <FragmentTabs>
                                {[
                                    ...(run.datasets || []).map((id, i) => (
                                        <FragmentTab title={`Dataset #${i + 1}`} key={id} fragment={`dataset${i}`}>
                                            <DatasetData testId={run.testid} runId={run.id} datasetId={id} />
                                        </FragmentTab>
                                    )),
                                    <FragmentTab title="Original run data" fragment="run" key="original">
                                        <RunData run={run} updateCounter={updateCounter} onUpdate={() => setUpdateCounter(updateCounter + 1)} />
                                    </FragmentTab>,
                                    <FragmentTab
                                        title="Metadata"
                                        fragment="metadata"
                                        key="metadata"
                                        isHidden={!run.metadata}
                                    >
                                        <MetaData run={run} />
                                    </FragmentTab>,
                                ]}
                            </FragmentTabs>
                        </CardBody>
                    </Card>
                </>
            )}
        </PageSection>
    )
}
