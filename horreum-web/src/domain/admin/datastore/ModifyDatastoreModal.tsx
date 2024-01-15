import React, {useState} from "react"

import {
    Button, Form,
    FormGroup, FormSelect, FormSelectOption,
    Modal, TextInput
} from "@patternfly/react-core"
import {
    Datastore,
    DatastoreTypeEnum, ElasticsearchDatastoreConfig,
} from "../../../api";

type ConfirmDeleteModalProps = {
    isOpen: boolean
    dataStore: Datastore
    onClose(): void
    onDelete(): Promise<any>
    updateDatastore(datastore: Datastore): void
    persistDatastore: (datastore: Datastore) => Promise<void>
    description: string
    extra?: string
}

interface datastoreOption {
    value: DatastoreTypeEnum,
    label: string,
    disabled: boolean,
    urlDisabled: boolean,
    usernameDisable: boolean,
    tokenDisbaled: boolean
}

export default function ModifyDatastoreModal({isOpen, onClose, persistDatastore, dataStore, updateDatastore}: ConfirmDeleteModalProps) {

    const [enabledURL, setEnableUrl] = useState(false);
    const [enabledToken, setEnableToken] = useState(false);


    const handleOptionChange = (value: string, _event: React.FormEvent<HTMLSelectElement>) => {
        const option: datastoreOption | undefined = options.filter( optionvalue => optionvalue.value === value).pop()
        if ( option ){
            setEnableUrl(option.urlDisabled)
            setEnableToken(option.tokenDisbaled)
        }
    };

    const saveBackend = () => {
        persistDatastore(dataStore).then( () => onClose())
    }

    const options : datastoreOption[] = [
        { value:  DatastoreTypeEnum.Elasticsearch, label: 'Elasticsearch', disabled: false, urlDisabled: false, usernameDisable: false, tokenDisbaled: false },
    ];

    const actionButtons = [
        <Button variant="primary" onClick={saveBackend}>Save</Button>,
        <Button variant="link">Cancel</Button>
    ]

    return (
        <Modal variant="medium" title="Modify Datastore" actions={actionButtons} isOpen={isOpen} onClose={onClose}>

            {/*TODO: create dynamic form based from config - see change detection for example*/}
            <Form isHorizontal>
                <FormGroup label="Datastore Type" fieldId="horizontal-form-datastore-type">
                    <FormSelect
                        value={dataStore.type}
                        onChange={handleOptionChange}
                        id="horizontal-form-datastore-type"
                        name="horizontal-form-datastore-type"
                        aria-label="Backend Type"
                    >
                        {options.map((option, index) => (
                            <FormSelectOption isDisabled={option.disabled} key={index} value={option.value} label={option.label}/>
                        ))}
                    </FormSelect>
                </FormGroup>
                <FormGroup
                    label="name"
                    isRequired
                    fieldId="horizontal-form-name"
                    helperText="Please an name for the datastore"
                >
                    <TextInput
                        value={dataStore.name}
                        onChange={ value => updateDatastore({...dataStore, name: value})}
                        isRequired
                        type="text"
                        id="horizontal-form-name"
                        aria-describedby="horizontal-form-name-helper"
                        name="horizontal-form-name"
                    />
                </FormGroup>
                <FormGroup
                    label="URL"
                    fieldId="horizontal-form-name"
                    helperText="Please provide the full host URL to for the datastore service"
                >
                    <TextInput
                        value={"url" in dataStore.config ? dataStore.config.url : ""}
                        onChange={ value => {
                            const config :ElasticsearchDatastoreConfig = dataStore.config as ElasticsearchDatastoreConfig;
                            config.url = value
                            updateDatastore({...dataStore, config: config})
                            }}
                        isDisabled={enabledURL}
                        type="text"
                        id="horizontal-form-url"
                        aria-describedby="horizontal-form-name-helper"
                        name="horizontal-form-url"
                    />
                </FormGroup>
                <FormGroup
                    label="Api Key"
                    fieldId="horizontal-form-token"
                    helperText="Please provide an API token to authenticate against datastore"
                >
                    <TextInput
                        value={"apiKey" in dataStore.config ? dataStore.config.apiKey : ""}
                        onChange={ value => {
                            const config :ElasticsearchDatastoreConfig = dataStore.config as ElasticsearchDatastoreConfig;
                            config.apiKey = value
                            updateDatastore({...dataStore, config: config})
                        }}isDisabled={enabledToken}
                        type="text"
                        id="horizontal-form-api-key"
                        aria-describedby="horizontal-form-token-helper"
                        name="horizontal-form-token"
                    />
                </FormGroup>
            </Form>
        </Modal>
    )
}