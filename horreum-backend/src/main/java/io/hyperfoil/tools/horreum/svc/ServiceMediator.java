package io.hyperfoil.tools.horreum.svc;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.api.alerting.DataPoint;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.data.ActionDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.events.DatasetChanges;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@ApplicationScoped
public class ServiceMediator {
    private static final Logger log = Logger.getLogger(ServiceMediator.class);

    @Inject
    private TestServiceImpl testService;

    @Inject
    private AlertingServiceImpl alertingService;

    @Inject
    private RunServiceImpl runService;

    @Inject
    private ReportServiceImpl reportService;

    @Inject
    private ExperimentServiceImpl experimentService;

    @Inject
    private LogServiceImpl logService;

    @Inject
    private SubscriptionServiceImpl subscriptionService;

    @Inject
    private ActionServiceImpl actionService;

    @Inject
    private NotificationServiceImpl notificationService;

    @Inject
    private DatasetServiceImpl datasetService;

    @Inject
    private EventAggregator aggregator;

    @Inject
    Vertx vertx;
    @Inject
    private SchemaServiceImpl schemaService;

    @Inject
    @ConfigProperty(name = "horreum.test-mode", defaultValue = "false")
    private Boolean testMode;

    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    @Channel("dataset-event-out")
    Emitter<Dataset.EventNew> dataSetEmitter;

    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    @Channel("run-recalc-out")
    Emitter<Integer> runEmitter;

    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    @Channel("schema-sync-out")
    Emitter<Integer> schemaEmitter;

    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    @Channel("run-upload-out")
    Emitter<Integer> runUploadEmitter;

    private Map<AsyncEventChannels, Map<Integer, BlockingQueue<Object>>> events =  new ConcurrentHashMap<>();

    public ServiceMediator() {
    }

    void executeBlocking(Runnable runnable) {
        Util.executeBlocking(vertx, runnable);
    }

    boolean testMode() {
        return testMode;
    }
    @Transactional
    void newTest(Test test) {
        actionService.onNewTest(test);
    }

    @Transactional
    void deleteTest(int testId) {
        // runService will call mediator.propagatedDatasetDelete which needs
        // to be completed before we call the other services
        runService.onTestDeleted(testId);
        actionService.onTestDelete(testId);
        alertingService.onTestDeleted(testId);
        experimentService.onTestDeleted(testId);
        logService.onTestDelete(testId);
        reportService.onTestDelete(testId);
        subscriptionService.onTestDelete(testId);
    }

    @Transactional
    void newRun(Run run) {
        actionService.onNewRun(run);
        alertingService.removeExpected(run);
    }

    @Transactional
    void propagatedDatasetDelete(int datasetId) {
        //make sure to delete the entities that has a reference on dataset first
        alertingService.onDatasetDeleted(datasetId);
        datasetService.deleteDataset(datasetId);
    }

    @Transactional
    void updateLabels(Dataset.LabelsUpdatedEvent event) {
        alertingService.onLabelsUpdated(event);
    }

    void newDataset(Dataset.EventNew eventNew) {
        //Note: should we call onNewDataset which will enable a lock?
        datasetService.onNewDataset(eventNew);
    }

    @Transactional
    void newChange(Change.Event event) {
        actionService.onNewChange(event);
        aggregator.onNewChange(event);
    }

    @Incoming("dataset-event-in")
    @Blocking(ordered = false, value = "horreum.dataset.pool")
    @ActivateRequestContext
    public void processDatasetEvents(Dataset.EventNew newEvent) {
        datasetService.onNewDatasetNoLock(newEvent);
        validateDataset(newEvent.datasetId);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void queueDatasetEvents(Dataset.EventNew event) {
        dataSetEmitter.send(event);
    }
    @Incoming("run-recalc-in")
    @Blocking(ordered = false, value = "horreum.run.pool")
    @ActivateRequestContext
    public void processRunRecalculation(int runId) {
        runService.transform(runId, true);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void queueRunRecalculation(int runId) {
        runEmitter.send(runId);
    }

    @Incoming("schema-sync-in")
    @Blocking(ordered = false, value = "horreum.schema.pool")
    @ActivateRequestContext
    public void processSchemaSync(int schemaId) {
        runService.onNewOrUpdatedSchema(schemaId);
    }

    @Incoming("run-upload-in")
    @Blocking(ordered = false, value = "horreum.run.pool")
    @ActivateRequestContext
    public void processRunUpload(Integer runUpload) {
        log.infof("Run Upload: %d", runUpload);
//        runService.persistRun(runUpload);

    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void queueSchemaSync(int schemaId) {
        schemaEmitter.send(schemaId);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void queueRunUpload(String start, String stop, String test, String owner, Access access, String token, String schemaUri, String description, JsonNode metadata, JsonNode jsonNode, TestDAO testEntity) {
        RunUpload upload = new RunUpload(start, stop, test, owner, access, token, schemaUri, description, metadata, jsonNode, testEntity.id);
        runUploadEmitter.send(1);
    }


    void dataPointsProcessed(DataPoint.DatasetProcessedEvent event) {
        experimentService.onDatapointsCreated(event);
    }

    void missingValuesDataset(MissingValuesEvent event) {
        notificationService.onMissingValues(event);
    }
    void newDatasetChanges(DatasetChanges changes) {
        notificationService.onNewChanges(changes);
    }
    int transform(int runId, boolean isRecalculation) {
        return runService.transform(runId, isRecalculation);
    }
    void withRecalculationLock(Runnable run) {
        datasetService.withRecalculationLock(run);
    }
    void newExperimentResult(ExperimentService.ExperimentResult result) {
        actionService.onNewExperimentResult(result);
    }
    void validate(Action dto) {
        actionService.validate(dto);
    }

    void merge(ActionDAO dao) {
        actionService.merge(dao);
    }

    void exportTest(TestExport test) {
        alertingService.exportTest(test);
        actionService.exportTest(test);
        experimentService.exportTest(test);
        subscriptionService.exportSubscriptions(test);
    }

    @Transactional
    void importTestToAll(TestExport test) {
        if(test.variables != null)
            alertingService.importVariables(test);
        if(test.missingDataRules != null)
            alertingService.importMissingDataRules(test);
        if(test.actions != null)
            actionService.importTest(test);
        if(test.experiments != null && !test.experiments.isEmpty())
            experimentService.importTest(test);
        if(test.subscriptions != null)
            subscriptionService.importSubscriptions(test);
    }

    public void updateFingerprints(int testId) {
        datasetService.updateFingerprints(testId);
    }

    public void validateRun(Integer runId) {
        schemaService.validateRunData(runId, null);
    }
    public void validateDataset(Integer datasetId) {
        schemaService.validateDatasetData(datasetId, null);
    }
    public void validateSchema(int schemaId) {
        schemaService.revalidateAll(schemaId);
    }

    public <T> void publishEvent(AsyncEventChannels channel, int testId, T payload) {
        if (testMode ) {
            log.debugf("Publishing test %d on %s: %s", testId, channel, payload);
//        eventBus.publish(channel.name(), new MessageBus.Message(BigInteger.ZERO.longValue(), testId, 0, payload));
            events.putIfAbsent(channel, new HashMap<>());
            BlockingQueue<Object> queue = events.get(channel).computeIfAbsent(testId, k -> new LinkedBlockingQueue<>());
            queue.add(payload);
        } else {
            //no-op
        }
    }

    public <T> BlockingQueue<T> getEventQueue(AsyncEventChannels channel, Integer id) {
        if (testMode) {
            events.putIfAbsent(channel, new HashMap<>());
            BlockingQueue<?> queue = events.get(channel).computeIfAbsent(id, k -> new LinkedBlockingQueue<>());
            return (BlockingQueue<T>) queue;
        } else {
            return null;
        }
    }

    static class RunUpload {
        public String start;
        public String stop;
        public String test;
        public String owner;
        public Access access;
        public String token;
        public String schemaUri;
        public String description;
        public JsonNode metaData;
        public JsonNode payload;
        public Integer testId;

        public RunUpload() {
        }

        public RunUpload(String start, String stop, String test, String owner, Access access, String token, String schemaUri, String description, JsonNode metaData, JsonNode payload, Integer testId) {
            this.start = start;
            this.stop = stop;
            this.test = test;
            this.owner = owner;
            this.access = access;
            this.token = token;
            this.schemaUri = schemaUri;
            this.description = description;
            this.metaData = metaData;
            this.payload = payload;
            this.testId = testId;
        }
    }

}
