import {useContext, useEffect, useState} from "react"
import { useSelector, useDispatch } from "react-redux"

import { Select, SelectOption } from "@patternfly/react-core"

import { teamsSelector } from "../auth"
import { TestDispatch } from "../domain/tests/reducers"
import { fetchFolders } from "../domain/tests/actions"
import { UPDATE_FOLDERS } from "../domain/tests/actionTypes"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";

type FolderSelectProps = {
    folder: string
    onChange(folder: string): void
    canCreate: boolean
    readOnly: boolean
}

export default function FolderSelect({folder, onChange, canCreate, readOnly}: FolderSelectProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [open, setOpen] = useState(false)
    const [folders, setFolders] = useState<string[]>([])
    const dispatch = useDispatch<TestDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        fetchFolders(alerting).then(setFolders)
    }, [teams])
    return (
        <Select
            readOnly={readOnly}
            isOpen={open}
            isCreatable={canCreate}
            variant={canCreate ? "typeahead" : "single"}
            onToggle={setOpen}
            selections={folder}
            menuAppendTo="parent"
            onSelect={(_, item) => {
                onChange(item as string)
                setOpen(false)
            }}
            onCreateOption={newFolder => {
                dispatch({ type: UPDATE_FOLDERS, folders: [...folders, newFolder].sort() })
                onChange(newFolder)
            }}
            placeholderText="Horreum"
        >
            {folders.map((folder, i) => (
                <SelectOption key={i} value={folder || ""}>
                    {folder || "Horreum"}
                </SelectOption>
            ))}
        </Select>
    )
}
