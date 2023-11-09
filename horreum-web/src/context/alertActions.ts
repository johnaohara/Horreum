import {Alert, defaultFormatError} from "../alerts";

export const contextAlertAction = (
    type: string,
    title: string,
    e: any,
    ...errorFormatter: ((error: any) => any)[]
): Alert => {
    let formatted = undefined
    for (const f of errorFormatter) {
        formatted = f.call(null, e)
        if (formatted) break
    }
    if (!formatted) {
        formatted = defaultFormatError(e)
    }
    const newAlert: Alert = {
        type,
        title,
        content: formatted,
    }
    return newAlert;
}

