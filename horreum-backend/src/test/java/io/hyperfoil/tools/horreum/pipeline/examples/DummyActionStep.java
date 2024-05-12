package io.hyperfoil.tools.horreum.pipeline.examples;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.pipeline.steps.actions.ActionStep;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

public class DummyActionStep extends ActionStep {

    public static final String TYPE_DUMMY_ACTION = "dummy-action";

    public DummyActionStep(JsonNode config, JsonNode secrets) {
        super(config, secrets);
    }

    @Override
    public String type() {
        return TYPE_DUMMY_ACTION;
    }

    @Override
    public void validate(JsonNode config, JsonNode secrets) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void validate() {
        requireProperties(this.config, "user");
        requireProperties(this.config, "formatter");
    }

    @Override
    public Uni<String> execute(JsonNode config, JsonNode secrets, Object payload) {
        Log.infof("Sending action to: %s; using formatter: %s, with payload: %s",
                config.get("user").toString(),
                config.get("formatter").toString(),
                payload
        );

        return null;
    }
}
