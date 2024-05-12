package io.hyperfoil.tools.horreum.pipeline.steps.actions;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

public class SlackAction extends ActionStep {

    public static final String SLACK_ACTION = "slack-action";

    public SlackAction(JsonNode config, JsonNode secrets) {
        super(config, secrets);
    }

    @Override
    public String type() {
        return SLACK_ACTION;
    }

    @Override
    public void validate(JsonNode config, JsonNode secrets) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void validate() {
        requireProperties(config, "formatter", "channel");
        requireProperties(secrets, "token");
    }

    @Override
    public Uni<String> execute(JsonNode config, JsonNode secrets, Object payload) {
        Log.infof("Sending slack message to: %s; using formatter: %s, with payload: %s",
                config.get("channel").toString(),
                config.get("formatter").toString(),
                payload
        );

        return null;
    }

}
