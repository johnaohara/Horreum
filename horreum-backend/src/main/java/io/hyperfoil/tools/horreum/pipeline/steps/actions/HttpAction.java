package io.hyperfoil.tools.horreum.pipeline.steps.actions;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.entity.data.AllowedSiteDAO;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

public class HttpAction extends ActionStep {

    public static final String SLACK_ACTION = "http-action";

    public HttpAction(JsonNode config, JsonNode secrets) {
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
        requireProperties(config, "url");

        String url = config.path("url").asText();
//        if (AllowedSiteDAO.find("?1 LIKE CONCAT(prefix, '%')", url).count() == 0) {
            throw ServiceException.badRequest("The requested URL (" + url + ") is not on the list of allowed URL prefixes; " +
                    "visit /api/hook/prefixes to see this list. Only the administrator is allowed to add prefixes.");
//        }
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
