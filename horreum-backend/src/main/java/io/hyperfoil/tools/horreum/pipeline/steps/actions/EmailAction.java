package io.hyperfoil.tools.horreum.pipeline.steps.actions;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

public class EmailAction extends ActionStep {
    public static final String EMAIL_ACTION = "email-action";

    public EmailAction(JsonNode config, JsonNode secrets) {
        super(config, secrets);
    }

    @Override
    public String type() {
        return EMAIL_ACTION;
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
        Log.infof("Sending email to: %s; using formatter: %s, with payload: %s",
                config.get("user").toString(),
                config.get("formatter").toString(),
                payload
        );

        return null;
    }

}
