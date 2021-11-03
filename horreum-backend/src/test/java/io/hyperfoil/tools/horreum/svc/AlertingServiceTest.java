package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SchemaService;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.vertx.core.eventbus.EventBus;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class AlertingServiceTest extends BaseServiceTest {

   @Inject
   EventBus eventBus;

   @org.junit.jupiter.api.Test
   public void testNotifications(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));

      Schema schema = new Schema();
      schema.owner = test.owner;
      schema.name = "Foo Schema";
      schema.uri = "urn:foo:bar:1.0";
      jsonRequest().body(schema).post("/api/schema").then().statusCode(200);

      SchemaService.ExtractorUpdate extractor = new SchemaService.ExtractorUpdate();
      extractor.accessor = "value";
      extractor.jsonpath = ".value";
      extractor.schema = schema.uri;
      jsonRequest().body(extractor).post("/api/schema/extractor").then().statusCode(204);

      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.set("$schema", JsonNodeFactory.instance.textNode(schema.uri));
      runJson.set("value", JsonNodeFactory.instance.numberNode(42));

      ArrayNode variables = JsonNodeFactory.instance.arrayNode();
      ObjectNode variable = JsonNodeFactory.instance.objectNode();
      variable.set("testid", JsonNodeFactory.instance.numberNode(test.id));
      variable.set("name", JsonNodeFactory.instance.textNode("Value"));
      variable.set("accessors", JsonNodeFactory.instance.textNode("value"));
      variables.add(variable);
      jsonRequest().body(variables.toString()).post("/api/alerting/variables?test=" + test.id).then().statusCode(204);

      BlockingQueue<DataPoint.Event> dpe = new LinkedBlockingDeque<>();
      eventBus.consumer(DataPoint.EVENT_NEW, msg -> {
         if (msg.body() instanceof DataPoint.Event) {
            dpe.add((DataPoint.Event) msg.body());
         }
      });

      uploadRun(System.currentTimeMillis(), System.currentTimeMillis(), runJson.toString(), test.name);

      DataPoint.Event event1 = dpe.poll(10, TimeUnit.SECONDS);
      assertNotNull(event1);
      assertEquals(42d, event1.dataPoint.value);
      assertTrue(event1.notify);

      RestAssured.given().auth().oauth2(TESTER_TOKEN)
            .post("/api/test/" + test.id + "/notifications?enabled=false")
            .then().statusCode(204);

      runJson.set("value", JsonNodeFactory.instance.numberNode(0));
      uploadRun(System.currentTimeMillis(), System.currentTimeMillis(), runJson.toString(), test.name);

      DataPoint.Event event2 = dpe.poll(10, TimeUnit.SECONDS);
      assertNotNull(event2);
      assertEquals(0, event2.dataPoint.value);
      assertFalse(event2.notify);
   }
}
