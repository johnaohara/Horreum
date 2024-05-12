package io.hyperfoil.tools.horreum.pipeline.steps.actions;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.action.ActionPlugin;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineTerminatingStep;

public abstract class ActionStep implements IPipelineTerminatingStep<JsonNode>, ActionPlugin {

    protected final JsonNode config, secrets;

    public ActionStep(JsonNode config, JsonNode secrets) {
        this.config = config;
        this.secrets = secrets;
    }

    public JsonNode getConfig() {
        return config;
    }

    public JsonNode getSecrets() {
        return secrets;
    }

    @Override
    public void run(JsonNode data) {

        try {
            validate();
        } catch (IllegalArgumentException e) {
            //TODO:: handle gracefully
            throw new RuntimeException("ActionStep validation failed: " + e.getMessage());
        }

        execute(config, secrets, data);
    }

    protected void requireProperties(JsonNode configuration, String... properties) {
        for (String property : properties) {
            if (!configuration.hasNonNull(property)) {
                throw missing(property);
            }
        }
    }

    private IllegalArgumentException missing(String property) {
        return new IllegalArgumentException("Configuration is missing property '" + property + "'");
    }

}
