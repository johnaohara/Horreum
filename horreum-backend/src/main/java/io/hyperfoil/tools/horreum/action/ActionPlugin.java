package io.hyperfoil.tools.horreum.action;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

public interface ActionPlugin {
   String type();

   //TODO:remove this method from interface
   void validate(JsonNode config, JsonNode secrets);
   void validate();

   Uni<String> execute(JsonNode config, JsonNode secrets, Object payload);
}
