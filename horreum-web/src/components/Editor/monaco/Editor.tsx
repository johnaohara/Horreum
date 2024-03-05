import {CodeEditor, Language} from "@patternfly/react-code-editor";
import * as monacoEditor from "monaco-editor";

type EditorProps = {
    value?: string
    language?: Language
    options: any
    onChange?(value: string | undefined): void
    height?: string
}

const onEditorDidMount = (editor: monacoEditor.editor.IStandaloneCodeEditor, monaco: typeof monacoEditor) => {
    editor.layout();
    editor.focus();
    monaco.editor.getModels()[0].updateOptions({ tabSize: 5 });
};

export default function Editor(props: EditorProps) {
    return (
        <CodeEditor
            code={props.value}
            language={props.language || Language.json}
            isDarkTheme={false}
            isLanguageLabelVisible
            // options={{
            //     //renderLineHighlight : 'none',
            //     ...props.options,
            //     automaticLayout: true,
            //     scrollBeyondLastLine: false,
            // }}
            height={props.height}
            onEditorDidMount={onEditorDidMount}
            // onMount={onMount}
            onChange={props.onChange}
        />
    )
}
