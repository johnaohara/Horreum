package io.hyperfoil.tools.horreum.action;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LoggingAction implements ActionPlugin {
   private static final Logger log = Logger.getLogger(LoggingAction.class);

   public static final String TYPE = "LOGGING";

   @PostConstruct()
   public void postConstruct(){

   }

   @Override
   public String type() {
      return TYPE;
   }

   @Override
   public void validate(JsonNode config, JsonNode secrets) {

   }

   @Override
   public Uni<String> execute(JsonNode config, JsonNode secrets, Object payload) {
      log.info("Logging action: " + payload);
        return Uni.createFrom().item("Logging action: " + payload);
   }
}
