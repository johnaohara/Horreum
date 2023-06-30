import {useEffect, useState} from "react"
import { useDispatch, useSelector } from "react-redux"

import {
    Dropdown,
    DropdownItem,
    DropdownSeparator,
    DropdownToggle,
    TreeView,
    TreeViewDataItem
} from "@patternfly/react-core"
import { FolderIcon, FolderOpenIcon } from "@patternfly/react-icons"

import { teamsSelector } from "../auth"
import { noop } from "../utils"
import { fetchFolders } from "../domain/tests/actions"
import { TestDispatch } from "../domain/tests/reducers"
import { allFolders } from "../domain/tests/selectors"
import * as React from "react";

type FoldersDropDownProps = {
    folder: string
    onChange(folder: string): void
}



export default function FoldersDropDown(props: FoldersDropDownProps) {
    const folders = useSelector(allFolders())
    const teams = useSelector(teamsSelector)
    const dispatch = useDispatch<TestDispatch>()
    useEffect(() => {
        dispatch(fetchFolders()).catch(noop)
    }, [teams])
    const [isOpen, setIsOpen] = useState(false);

    const onToggle = (isOpen: boolean) => {
        setIsOpen(isOpen);
    };

 // const onFocus = () => {
 //     const element = document.getElementById('toggle-basic');
 //     element.focus();
 // };

    const onSelect = () => {
        setIsOpen(false);
        // onFocus();
    };
    const dropdownItems: any[] = []
    dropdownItems.push(<DropdownItem key="action" component="button" onClick={() => props.onChange('')}>
        Horreum
    </DropdownItem>)

    for (const folder of folders) {
        if (!folder) continue
        dropdownItems.push(<DropdownItem key="action" component="button" onClick={() => props.onChange(folder)}>
            {folder}
        </DropdownItem>)
    }
    return (
        <Dropdown
            style={{marginRight: "16px"}}
            onSelect={onSelect}
            toggle={
                <DropdownToggle id="toggle-basic" onToggle={onToggle}>
                    Folder
                </DropdownToggle>
            }
            isOpen={isOpen}
            dropdownItems={dropdownItems}
        />
    )
}
