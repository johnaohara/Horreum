package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.groovy.util.Maps;
import org.hibernate.query.NativeQuery;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.action.ExperimentResultToMarkdown;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.api.alerting.Watch;
import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.api.data.ActionLog;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.ExperimentProfile;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.FingerprintValue;
import io.hyperfoil.tools.horreum.api.data.Fingerprints;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.TestExport;
import io.hyperfoil.tools.horreum.api.data.Transformer;
import io.hyperfoil.tools.horreum.api.data.View;
import io.hyperfoil.tools.horreum.api.data.ViewComponent;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.ExperimentProfileDAO;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDetectionDAO;
import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRuleDAO;
import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import io.hyperfoil.tools.horreum.entity.alerting.WatchDAO;
import io.hyperfoil.tools.horreum.entity.data.ActionDAO;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.VariableMapper;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
class TestServiceTest extends BaseServiceTest {

    @org.junit.jupiter.api.Test
    void testListTests() {
        int count = 10;
        // all with owner TESTER_ROLES[0];
        createTests(count, "test-");

        TestService.TestQueryResult testsResult = listTests(null, null, null, null, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(count, testsResult.tests.size());

        testsResult = listTests(null, null, 5, 0, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(5, testsResult.tests.size());

        // get all my tests
        testsResult = listTests(null, Roles.MY_ROLES, null, null, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(10, testsResult.tests.size());

        // get my tests for admin user
        testsResult = listTests(getAdminToken(), Roles.MY_ROLES, null, null, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(0, testsResult.tests.size());

        // get all tests for admin user
        testsResult = listTests(getAdminToken(), Roles.ALL_ROLES, null, null, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(10, testsResult.tests.size());
    }

    @org.junit.jupiter.api.Test
    public void testCreateDelete(TestInfo info) throws InterruptedException {

        Test test = createTest(createExampleTest(getTestName(info)));
        try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            assertNotNull(TestDAO.findById(test.id));
        }

        BlockingQueue<Dataset.EventNew> dsQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        int runId = uploadRun("{ \"foo\" : \"bar\" }", test.name);
        assertNotNull(dsQueue.poll(10, TimeUnit.SECONDS));

        jsonRequest().get("/api/test/summary?roles=__my").then().statusCode(200);

        BlockingQueue<Integer> events = serviceMediator.getEventQueue(AsyncEventChannels.RUN_TRASHED, test.id);
        deleteTest(test);
        assertNotNull(events.poll(10, TimeUnit.SECONDS));

        em.clear();
        try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            assertNull(TestDAO.findById(test.id));
            // There's no constraint between runs and tests; therefore the run is not deleted
            RunDAO run = RunDAO.findById(runId);
            assertNotNull(run);
            assertTrue(run.trashed);

            assertEquals(0, DatasetDAO.count("testid", test.id));
        }
    }

    @org.junit.jupiter.api.Test
    public void testRecalculate(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);

        BlockingQueue<Dataset.EventNew> newDatasetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW,
                test.id);
        final int NUM_DATASETS = 5;
        for (int i = 0; i < NUM_DATASETS; ++i) {
            uploadRun(runWithValue(i, schema), test.name);
            Dataset.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(event);
            assertFalse(event.isRecalculation);
        }
        List<DatasetDAO> datasets = DatasetDAO.list("testid", test.id);
        assertEquals(NUM_DATASETS, datasets.size());
        int maxId = datasets.stream().mapToInt(ds -> ds.id).max().orElse(0);

        jsonRequest().post("/api/test/" + test.id + "/recalculate").then().statusCode(204);
        TestUtil.eventually(() -> {
            TestService.RecalculationStatus status = jsonRequest().get("/api/test/" + test.id + "/recalculate")
                    .then().statusCode(200).extract().body().as(TestService.RecalculationStatus.class);
            assertEquals(NUM_DATASETS, status.totalRuns);
            return status.finished == status.totalRuns;
        });
        for (int i = 0; i < NUM_DATASETS; ++i) {
            Dataset.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(event);
            assertTrue(event.datasetId > maxId);
            assertTrue(event.isRecalculation);
        }
        datasets = DatasetDAO.list("testid", test.id);
        assertEquals(NUM_DATASETS, datasets.size());
        datasets.forEach(ds -> {
            assertTrue(ds.id > maxId);
            assertEquals(0, ds.ordinal);
        });
        assertEquals(NUM_DATASETS, datasets.stream().map(ds -> ds.run.id).collect(Collectors.toSet()).size());
    }

    @org.junit.jupiter.api.Test
    public void testAddTestAction(TestInfo info) {
        Test test = createTest(createExampleTest(getTestName(info)));

        // look for the TEST_NEW action log for test just created
        List<ActionLog> actionLog = jsonRequest().auth().oauth2(getTesterToken()).queryParam("level", PersistentLogDAO.DEBUG)
                .get("/api/log/action/" + test.id).then().statusCode(200).extract().body().jsonPath().getList(".");
        assertFalse(actionLog.isEmpty());

        addTestHttpAction(test, AsyncEventChannels.RUN_NEW, "https://attacker.com").then().statusCode(400);

        addAllowedSite("https://example.com");

        Action action = addTestHttpAction(test, AsyncEventChannels.RUN_NEW, "https://example.com/foo/bar").then()
                .statusCode(200).extract().body().as(Action.class);
        assertNotNull(action.id);
        assertTrue(action.active);
        action.active = false;
        action.testId = test.id;
        jsonRequest().body(action).post("/api/action/update").then().statusCode(204);

        deleteTest(test);
    }

    @org.junit.jupiter.api.Test
    public void testUpdateView(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);

        BlockingQueue<Dataset.EventNew> newDatasetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW,
                test.id);
        uploadRun(runWithValue(42, schema), test.name);
        Dataset.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event);

        ViewComponent vc = new ViewComponent();
        vc.headerName = "Foobar";
        vc.labels = JsonNodeFactory.instance.arrayNode().add("value");
        List<View> views = getViews(test.id);
        View defaultView = views.stream().filter(v -> "Default".equals(v.name)).findFirst().orElseThrow();
        defaultView.components.add(vc);
        defaultView.testId = test.id;
        updateView(defaultView);

        TestUtil.eventually(() -> {
            em.clear();
            @SuppressWarnings("unchecked")
            List<JsonNode> list = em.createNativeQuery(
                    "SELECT value FROM dataset_view WHERE dataset_id = ?1 AND view_id = ?2")
                    .setParameter(1, event.datasetId).setParameter(2, defaultView.id)
                    .unwrap(NativeQuery.class).addScalar("value", JsonBinaryType.INSTANCE)
                    .getResultList();
            return !list.isEmpty() && !list.get(0).isEmpty();
        });
    }

    private void updateView(View view) {
        View newView = jsonRequest().body(view).post("/api/ui/view")
                .then().statusCode(200).extract().body().as(View.class);
        if (view.id != null) {
            assertEquals(view.id, newView.id);
        }
    }

    @org.junit.jupiter.api.Test
    public void testLabelValues(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);

        BlockingQueue<Dataset.LabelsUpdatedEvent> newDatasetQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        uploadRun(runWithValue(42, schema), test.name);
        uploadRun(JsonNodeFactory.instance.objectNode(), test.name);
        assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));
        assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));

        List<ExportedLabelValues> values = jsonRequest().get("/api/test/" + test.id + "/labelValues").then().statusCode(200)
                .extract().body().as(new TypeRef<>() {
                });
        assertNotNull(values);
        assertFalse(values.isEmpty());
        assertEquals(2, values.size());
        assertTrue(values.get(1).values.containsKey("value"));
    }

    @org.junit.jupiter.api.Test
    public void testFilterLabelValues(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));

        String name = info.getTestClass().map(Class::getName).orElse("<unknown>") + "." + info.getDisplayName();
        Schema schema = createSchema(name, uriForTest(info, "1.0"));

        addLabel(schema, "filter-1", null, true, false, new Extractor("filter", "$.filter1", false));
        addLabel(schema, "filter-2", null, true, false, new Extractor("filter", "$.filter2", false));

        BlockingQueue<Dataset.LabelsUpdatedEvent> newDatasetQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        ObjectNode run;

        run = runWithValue(42, schema);
        run.put("filter1", "foo");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(43, schema);
        run.put("filter1", "foo");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(44, schema);
        run.put("filter1", "biz");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(45, schema);
        run.put("filter1", "foo");
        run.put("filter2", "baz");
        uploadRun(run, test.name);

        for (int i = 0; i < 4; i++) {
            assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));
        }

        List<ObjectNode> values = jsonRequest().get("/api/test/" + test.id + "/filteringLabelValues").then().statusCode(200)
                .extract().body().as(new TypeRef<>() {
                });
        assertNotNull(values);
        assertFalse(values.isEmpty());
        assertEquals(3, values.size());
        assertNotNull(values.stream()
                .filter(node -> node.get("filter-1").asText().equals("foo") && node.get("filter-2").asText().equals("bar"))
                .findAny().orElse(null));
        assertNotNull(values.stream()
                .filter(node -> node.get("filter-1").asText().equals("biz") && node.get("filter-2").asText().equals("bar"))
                .findAny().orElse(null));
        assertNotNull(values.stream()
                .filter(node -> node.get("filter-1").asText().equals("foo") && node.get("filter-2").asText().equals("baz"))
                .findAny().orElse(null));

    }

    @org.junit.jupiter.api.Test
    public void testImportFromFile() throws JsonProcessingException {
        Path p = new File(getClass().getClassLoader().getResource(".").getPath()).toPath();
        p = p.getParent().getParent().getParent().resolve("infra-legacy/example-data/");

        Test t = new ObjectMapper().readValue(
                readFile(p.resolve("quarkus_quickstart_test_empty.json").toFile()), Test.class);
        assertEquals("perf-team", t.owner);
        t.owner = "foo-team";
        Test t2 = createTest(t);
        assertEquals(t.description, t2.description);
        assertNotEquals(t.id, t2.id);
    }

    @org.junit.jupiter.api.Test
    public void testImportExportWithWipe() throws InterruptedException {
        testImportExport(true);
    }

    @org.junit.jupiter.api.Test
    public void testImportExportWithoutWipe() throws InterruptedException {
        testImportExport(false);
    }

    private void testImportExport(boolean wipe) throws InterruptedException {
        Schema schema = createSchema("Example", "urn:example:1.0");
        Extractor barExtractor = new Extractor();
        barExtractor.name = "bar";
        barExtractor.jsonpath = "$.bar";
        addLabel(schema, "value", "", barExtractor);
        Transformer transformer = createTransformer("Foobar", schema, null, new Extractor("foo", "$.foo", false));

        Test test = createTest(createExampleTest("to-be-exported"));
        addTransformer(test, transformer);
        View view = new View();
        view.name = "Another";
        ViewComponent vc = new ViewComponent();
        vc.labels = JsonNodeFactory.instance.arrayNode().add("foo");
        vc.headerName = "Some foo";
        view.components = Collections.singletonList(vc);
        view.testId = test.id;
        updateView(view);

        addTestHttpAction(test, AsyncEventChannels.RUN_NEW, "http://example.com");
        addTestGithubIssueCommentAction(test, AsyncEventChannels.EXPERIMENT_RESULT_NEW,
                ExperimentResultToMarkdown.NAME, "hyperfoil", "horreum", "123", "super-secret-github-token");

        addChangeDetectionVariable(test, schema.id);
        addMissingDataRule(test, "Let me know", JsonNodeFactory.instance.arrayNode().add("foo"), null,
                (int) TimeUnit.DAYS.toMillis(1));

        addExperimentProfile(test, "Some profile", VariableDAO.<VariableDAO> listAll().get(0));
        addSubscription(test);

        HashMap<String, List<JsonNode>> db = dumpDatabaseContents();

        Response response = jsonRequest().get("/api/test/" + test.id + "/export").then()
                .statusCode(200).extract().response();

        String export = response.asString();

        TestExport testExport = response.as(TestExport.class);
        assertEquals(testExport.id, test.id);
        assertEquals(testExport.variables.size(), 1);

        if (wipe) {
            BlockingQueue<Test> events = serviceMediator.getEventQueue(AsyncEventChannels.TEST_DELETED, test.id);
            deleteTest(test);
            Test deleted = events.poll(10, TimeUnit.SECONDS);
            assertNotNull(deleted);
            assertEquals(test.id, deleted.id);

            TestUtil.eventually(() -> {
                em.clear();
                try (var h = roleManager.withRoles(Collections.singleton(Roles.HORREUM_SYSTEM))) {
                    assertEquals(0, TestDAO.count("id = ?1", test.id));
                    assertEquals(0, ActionDAO.count("testId = ?1", test.id));
                    assertEquals(0, VariableDAO.count("testId = ?1", test.id));
                    assertEquals(0, ChangeDetectionDAO.count());
                    assertEquals(0, MissingDataRuleDAO.count("test.id = ?1", test.id));
                    assertEquals(0, ExperimentProfileDAO.count("test.id = ?1", test.id));
                    assertEquals(0, WatchDAO.count("test.id = ?1", test.id));
                }
            });
        }

        //wipeing and inserting with the same ids just results in too much foobar
        if (!wipe) {
            jsonRequest().body(testExport).post("/api/test/import").then().statusCode(204);
            //if we wipe, we actually import a new test and there is no use validating the db
            validateDatabaseContents(db);
            //clean up after us
            deleteTest(test);
        }
    }

    @org.junit.jupiter.api.Test
    public void testImportWithTransformers() throws InterruptedException {
        Path p = new File(getClass().getClassLoader().getResource(".").getPath()).toPath();
        p = p.getParent().getParent().getParent().resolve("infra-legacy/example-data/");

        String s = readFile(p.resolve("quarkus_sb_schema.json").toFile());
        jsonRequest().body(s).post("/api/schema/import").then().statusCode(204);

        String t = readFile(p.resolve("quarkus_sb_test.json").toFile());
        jsonRequest().body(t).post("/api/test/import").then().statusCode(204);
        TestDAO test = TestDAO.<TestDAO> find("name", "quarkus-spring-boot-comparison").firstResult();
        assertEquals(1, test.transformers.size());

        List<SchemaService.SchemaDescriptor> descriptors = jsonRequest().get("/api/schema/descriptors")
                .then().statusCode(200).extract().body().as(new TypeRef<>() {
                });
        assertEquals("quarkus-sb-compare", descriptors.get(0).name);

        List<ExperimentProfileDAO> experiments = ExperimentProfileDAO.list("test.id", test.id);
        assertEquals(1, experiments.size());
        assertNotNull(experiments.get(0).comparisons.get(0).variable);
    }

    @org.junit.jupiter.api.Test
    public void testListFingerprints() throws JsonProcessingException {
        List<JsonNode> fps = new ArrayList<>();
        fps.add(mapper.readTree("""
                {
                   "Mode" : "library",
                   "TestName" : "reads10",
                   "ConfigName" : "dist"
                }
                """));

        List<Fingerprints> values = Fingerprints.parse(fps);
        assertEquals(1, values.size());
        assertEquals(3, values.get(0).values.size());
        assertEquals("dist", values.get(0).values.get(2).value);

        fps.add(mapper.readTree("""
                {
                   "tag": "main",
                   "params":  {
                       "storeFirst": false,
                       "numberOfRules": 200,
                       "rulesProviderId": "RulesWithJoinsProvides",
                       "useCanonicalMode": true
                    },
                    "testName": "BaseFromContainer"
                }
                """));
        values = Fingerprints.parse(fps);
        assertEquals(2, values.size());
        assertEquals(3, values.get(0).values.size());
        assertEquals(3, values.get(1).values.size());
        assertEquals(4, values.get(1).values.get(1).children.size());

        //We need the cast on children due to Type Erasure on recursive elements
        assertEquals("storeFirst", ((FingerprintValue<Boolean>) values.get(1).values.get(1).children.get(0)).name);
        assertEquals(false, ((FingerprintValue<Boolean>) values.get(1).values.get(1).children.get(0)).value);
        assertEquals("numberOfRules", ((FingerprintValue<Double>) values.get(1).values.get(1).children.get(1)).name);
        assertEquals(200d, ((FingerprintValue<Double>) values.get(1).values.get(1).children.get(1)).value);
        assertEquals("rulesProviderId", ((FingerprintValue<String>) values.get(1).values.get(1).children.get(2)).name);
        assertEquals("RulesWithJoinsProvides", ((FingerprintValue<String>) values.get(1).values.get(1).children.get(2)).value);
    }

    private String labelValuesSetup(Test t, boolean load) {
        Schema fooSchema = createSchema("foo", "urn:foo");
        Extractor fooExtractor = new Extractor();
        fooExtractor.name = "foo";
        fooExtractor.jsonpath = "$.foo";
        Extractor barExtractor = new Extractor();
        barExtractor.name = "bar";
        barExtractor.jsonpath = "$.bar";
        addLabel(fooSchema, "labelFoo", "", fooExtractor);
        addLabel(fooSchema, "labelBar", "", barExtractor);

        if (load) {
            return uploadRun("{ \"foo\": \"uno\", \"bar\": \"dox\"}", t.name, fooSchema.uri);
        } else {
            return "-1";
        }
    }

    @org.junit.jupiter.api.Test
    public void labelValuesIncludeExcluded() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?include=labelFoo&exclude=labelFoo")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertFalse(objectNode.has("labelFoo"), objectNode.toString());
        assertTrue(objectNode.has("labelBar"), objectNode.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesWithTimestampAfterFilter() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        long stop = System.currentTimeMillis();
        long start = System.currentTimeMillis();
        long delta = 5000; // 5 seconds
        uploadRun(start, stop, "{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo",
                jakarta.ws.rs.core.Response.Status.OK.getStatusCode());
        uploadRun(start + delta, stop, "{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo",
                jakarta.ws.rs.core.Response.Status.OK.getStatusCode());
        uploadRun(start + delta, stop, "{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo",
                jakarta.ws.rs.core.Response.Status.OK.getStatusCode());
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                // keep only those runs that started after (start+delta-1)
                .queryParam("after", Long.toString(start + delta - 1))
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(2, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        JsonNode second = arrayResponse.get(1);
        assertTrue(first.has("values"), second.toString());
        JsonNode secondValues = second.get("values");
        assertTrue(secondValues.has("labelBar"), secondValues.toString());
        assertEquals(JsonNodeType.STRING, secondValues.get("labelBar").getNodeType());

        List<String> labelBarValues = List.of(values.get("labelBar").asText(), secondValues.get("labelBar").asText());
        assertTrue(labelBarValues.contains("dos"), labelBarValues.toString());
        assertTrue(labelBarValues.contains("tres"), labelBarValues.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesWithISOAfterFilter() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        long stop = System.currentTimeMillis();
        uploadRun(Util.toInstant("2024-10-06T20:20:32.183Z").toEpochMilli(), stop, "{ \"foo\": 1, \"bar\": \"uno\"}", t.name,
                "urn:foo", jakarta.ws.rs.core.Response.Status.OK.getStatusCode());
        uploadRun(Util.toInstant("2024-10-06T20:20:32.183Z").toEpochMilli(), stop, "{ \"foo\": 2, \"bar\": \"dos\"}", t.name,
                "urn:foo", jakarta.ws.rs.core.Response.Status.OK.getStatusCode());
        uploadRun(Util.toInstant("2024-10-09T20:20:32.183Z").toEpochMilli(), stop, "{ \"foo\": 3, \"bar\": \"tres\"}", t.name,
                "urn:foo", jakarta.ws.rs.core.Response.Status.OK.getStatusCode());
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                // keep only those runs that started after (start+delta-1)
                .queryParam("after", "2024-10-07T20:20:32.183Z")
                .queryParam("filter", Maps.of("labelBar", Arrays.asList("none", "tres")))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        assertEquals("tres", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithJsonpath() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", "$.labelFoo ? (@ < 2)")
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        assertEquals("uno", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithInvalidJsonpath() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo");
        jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", "$..name")
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(400);
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithObject() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", "uno", "labelFoo", 1))
                .queryParam("multiFilter", false)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        assertEquals("uno", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithObjectNoMatch() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                // no runs match both conditions
                .queryParam("filter", Maps.of("labelBar", "uno", "labelFoo", "3"))
                .queryParam("multiFilter", false)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(0, arrayResponse.size(), "unexpected number of responses " + response);
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelectMultipleValues() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", Arrays.asList("uno", "tres")))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(2, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        JsonNode second = arrayResponse.get(1);
        assertTrue(first.has("values"), second.toString());
        JsonNode secondValues = second.get("values");
        assertTrue(secondValues.has("labelBar"), secondValues.toString());
        assertEquals(JsonNodeType.STRING, secondValues.get("labelBar").getNodeType());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelectStrings() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", Arrays.asList("uno", 30)))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        assertEquals("uno", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelectNumber() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": 10}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": 20}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", Arrays.asList(10, 30)))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.NUMBER, values.get("labelBar").getNodeType());
        assertEquals("10", values.get("labelBar").toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelectBoolean() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": true}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": 20}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", Arrays.asList(true, 30)))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.BOOLEAN, values.get("labelBar").getNodeType());
        assertEquals("true", values.get("labelBar").toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesIncludeTwoParams() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?include=labelFoo&include=labelBar")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertTrue(objectNode.has("labelFoo"), objectNode.toString());
        assertTrue(objectNode.has("labelBar"), objectNode.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesIncludeTwoSeparated() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?include=labelFoo,labelBar")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertTrue(objectNode.has("labelFoo"), objectNode.toString());
        assertTrue(objectNode.has("labelBar"), objectNode.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesInclude() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?include=labelFoo")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertTrue(objectNode.has("labelFoo"));
        assertFalse(objectNode.has("labelBar"));
    }

    @org.junit.jupiter.api.Test
    public void labelValuesExclude() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?exclude=labelFoo")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertFalse(objectNode.has("labelFoo"), objectNode.toPrettyString());
        assertTrue(objectNode.has("labelBar"), objectNode.toPrettyString());

    }

    @org.junit.jupiter.api.Test
    public void testLabelValues() throws JsonProcessingException {
        List<Object[]> toParse = new ArrayList<>();
        toParse.add(
                new Object[] { "job", mapper.readTree("\"quarkus-release-startup\""), 10, 10, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Max RSS", mapper.readTree("[]"), 10, 10, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "build-id", mapper.readTree("null"), 10, 10, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 1 CPU", mapper.readTree("null"), 10, 10, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 2 CPU", mapper.readTree("null"), 10, 10, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 4 CPU", mapper.readTree("null"), 10, 10, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 8 CPU", mapper.readTree("null"), 10, 10, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 32 CPU", mapper.readTree("null"), 10, 10, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Quarkus - Kafka_tags", mapper.readTree("\"quarkus-release-startup\""), 10, 10,
                Instant.now(), Instant.now() });
        List<ExportedLabelValues> values = LabelValuesService.parse(toParse);
        assertEquals(1, values.size());
        assertEquals(9, values.get(0).values.size());
        assertEquals("quarkus-release-startup", values.get(0).values.get("job").asText());
        assertEquals("null", values.get(0).values.get("Throughput 32 CPU").asText());

        toParse.add(
                new Object[] { "job", mapper.readTree("\"quarkus-release-startup\""), 10, 11, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Max RSS", mapper.readTree("[]"), 10, 11, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "build-id", mapper.readTree("null"), 10, 11, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 1 CPU", mapper.readTree("17570.30"), 10, 11, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 2 CPU", mapper.readTree("43105.62"), 10, 11, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 4 CPU", mapper.readTree("84895.13"), 10, 11, Instant.now(), Instant.now() });
        toParse.add(new Object[] { "Throughput 8 CPU", mapper.readTree("141086.29"), 10, 11, Instant.now(), Instant.now() });
        values = LabelValuesService.parse(toParse);
        assertEquals(2, values.size());
        assertEquals(9, values.get(0).values.size());
        assertEquals(7, values.get(1).values.size());
        assertEquals(84895.13d, values.get(1).values.get("Throughput 4 CPU").asDouble());
    }

    @org.junit.jupiter.api.Test
    public void testPagination() {
        int count = 50;
        createTests(count, "acme");
        try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            assertEquals(count, TestDAO.count());
        }
        int limit = 20;
        TestService.TestListing listing = listTestSummary("__my", "", limit, 1, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_00", listing.tests.get(0).name);
        assertEquals("acme_19", listing.tests.get(19).name);
        listing = listTestSummary(null, "*", limit, 1, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_00", listing.tests.get(0).name);
        assertEquals("acme_19", listing.tests.get(19).name);
        listing = listTestSummary(null, "*", limit, 1, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_00", listing.tests.get(0).name);
        assertEquals("acme_19", listing.tests.get(19).name);
        listing = listTestSummary("__all", "*", limit, 1, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_00", listing.tests.get(0).name);
        assertEquals("acme_19", listing.tests.get(19).name);

        listing = listTestSummary("__my", "*", limit, 2, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_20", listing.tests.get(0).name);
        assertEquals("acme_39", listing.tests.get(19).name);

        listing = listTestSummary("__my", "*", limit, 3, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(10, listing.tests.size());
        assertEquals("acme_40", listing.tests.get(0).name);
        assertEquals("acme_49", listing.tests.get(9).name);

        listing = listTestSummary("__my", "foo", limit, 1, SortDirection.Ascending);
        assertEquals(0, listing.count);
        assertEquals(0, listing.tests.size());
    }

    @org.junit.jupiter.api.Test
    public void testImportTestWithChangeDetectionVariableWithExperimentProfile() {
        String schema = resourceToString("data/acme_sb_schema.json");
        jsonRequest().body(schema).post("/api/schema/import").then().statusCode(204);
        String test = resourceToString("data/acme_new_variable_test.json");
        jsonRequest().body(test).post("/api/test/import").then().statusCode(204);
        VariableDAO maxRSS = VariableDAO.<VariableDAO> find("name", "Max RSS").firstResult();
        assertNotNull(maxRSS);
        assertEquals("Max RSS", maxRSS.name);
        VariableDAO dao = new VariableDAO();
        ArrayNode labels = JsonNodeFactory.instance.arrayNode();
        labels.add("Quarkus - JVM - maxRss");
        dao.labels = labels;
        dao.changeDetection = new HashSet<>();
        Variable mappedVariable = VariableMapper.from(dao);
        assertNotNull(mappedVariable);
        assertEquals(labels.size(), mappedVariable.labels.size());
        TestDAO testDAO = TestDAO.<TestDAO> find("name", "new-variable").firstResult();
        Response response = jsonRequest().get("/api/test/" + testDAO.id + "/export").then()
                .statusCode(200).extract().response();
        TestExport testExport = response.as(TestExport.class);
        ExperimentProfile ep = new ExperimentProfile();
        testExport.experiments.add(ep);
        ep.testId = testExport.id;
        ep.name = "acme Quarkus experiment";
        ArrayNode labelsJSON = JSON_NODE_FACTORY.arrayNode();
        ep.selectorLabels = labelsJSON;
        labelsJSON.add("Quarkus - JVM - maxRss");
        ep.selectorFilter = "value => {return true;}";
        ep.baselineLabels = JSON_NODE_FACTORY.arrayNode();
        ep.baselineFilter = "value => {return true;}";
        ep.comparisons = new ArrayList<>();
        ep.extraLabels = JSON_NODE_FACTORY.arrayNode();
        jsonRequest().body(testExport).post("/api/test/import").then().statusCode(204);
        ExperimentProfileDAO epDAO = ExperimentProfileDAO.<ExperimentProfileDAO> find("name", "acme Quarkus experiment")
                .firstResult();
        assertNotNull(epDAO);
    }

    private void addSubscription(Test test) {
        Watch watch = new Watch();
        watch.testId = test.id;
        watch.users = Arrays.asList("john", "bill");
        watch.teams = Collections.singletonList("dev-team");
        watch.optout = Collections.singletonList("ignore-me");

        jsonRequest().body(watch).post("/api/subscriptions/" + test.id);
    }

    // utility to get list of schemas
    private TestService.TestQueryResult listTests(String token, String roles, Integer limit, Integer page, String sort,
            SortDirection direction) {
        StringBuilder query = new StringBuilder("/api/test/");
        if (roles != null || limit != null || page != null || sort != null || direction != null) {
            query.append("?");

            if (roles != null) {
                query.append("roles=").append(roles).append("&");
            }

            if (limit != null) {
                query.append("limit=").append(limit).append("&");
            }

            if (page != null) {
                query.append("page=").append(page).append("&");
            }

            if (sort != null) {
                query.append("sort=").append(sort).append("&");
            }

            if (direction != null) {
                query.append("direction=").append(direction);
            }
        }
        return jsonRequest()
                .auth()
                .oauth2(token == null ? getTesterToken() : token)
                .get(query.toString())
                .then()
                .statusCode(200)
                .extract()
                .as(TestService.TestQueryResult.class);
    }
}
