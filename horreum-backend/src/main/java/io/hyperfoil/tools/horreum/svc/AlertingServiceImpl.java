package io.hyperfoil.tools.horreum.svc;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import io.hyperfoil.tools.horreum.api.AlertingService;
import io.hyperfoil.tools.horreum.api.ChangeDetectionModelConfig;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRule;
import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRuleResult;
import io.hyperfoil.tools.horreum.entity.alerting.RunExpectation;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionModel;
import io.hyperfoil.tools.horreum.changedetection.RelativeDifferenceChangeDetectionModel;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.jpa.TypedParameterValue;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.AliasToBeanResultTransformer;
import org.hibernate.transform.Transformers;
import org.hibernate.type.IntegerType;
import org.hibernate.type.TextType;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.array.IntArrayType;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.grafana.Dashboard;
import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.hyperfoil.tools.horreum.grafana.Target;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

@ApplicationScoped
@Startup
public class AlertingServiceImpl implements AlertingService {
   private static final Logger log = Logger.getLogger(AlertingServiceImpl.class);

   //@formatter:off
   private static final String LOOKUP_VARIABLES =
         "SELECT var.id as variableId, var.name, var.\"group\", var.calculation, jsonb_array_length(var.labels) AS numLabels, (CASE " +
            "WHEN jsonb_array_length(var.labels) = 1 THEN jsonb_agg(lv.value)->0 " +
            "ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::::jsonb) " +
         "END) AS value FROM variable var " +
         "LEFT JOIN label ON json_contains(var.labels, label.name) LEFT JOIN label_values lv ON label.id = lv.label_id " +
         "WHERE var.testid = ?1 AND lv.dataset_id = ?2 " +
         "GROUP BY var.id, var.name, var.\"group\", var.calculation";

   private static final String LOOKUP_RULE_LABEL_VALUES =
         "SELECT mdr.id AS rule_id, mdr.condition, " +
         "(CASE " +
            "WHEN mdr.labels IS NULL OR jsonb_array_length(mdr.labels) = 0 THEN NULL " +
            "WHEN jsonb_array_length(mdr.labels) = 1 THEN jsonb_agg(lv.value)->0 " +
            "ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::::jsonb) " +
         "END) as value FROM missingdata_rule mdr " +
         "LEFT JOIN label ON json_contains(mdr.labels, label.name) " +
         "LEFT JOIN label_values lv ON label.id = lv.label_id AND lv.dataset_id = ?1 " +
         "WHERE mdr.test_id = ?2 " +
         "GROUP BY rule_id, mdr.condition";

   private static final String LOOKUP_LABEL_VALUE_FOR_RULE =
         "SELECT (CASE " +
            "WHEN mdr.labels IS NULL OR jsonb_array_length(mdr.labels) = 0 THEN NULL " +
            "WHEN jsonb_array_length(mdr.labels) = 1 THEN jsonb_agg(lv.value)->0 " +
            "ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::::jsonb) " +
         "END) as value FROM missingdata_rule mdr " +
         "LEFT JOIN label ON json_contains(mdr.labels, label.name) " +
         "LEFT JOIN label_values lv ON label.id = lv.label_id AND lv.dataset_id = ?1 " +
         "WHERE mdr.id = ?2 " +
         "GROUP BY mdr.labels";

   private static final String LOOKUP_RECENT =
         "SELECT DISTINCT ON(mdr.id) mdr.id, mdr.test_id, mdr.name, mdr.maxstaleness, rr.timestamp " +
         "FROM missingdata_rule mdr " +
         "LEFT JOIN missingdata_ruleresult rr ON mdr.id = rr.rule_id " +
         "WHERE last_notification IS NULL OR EXTRACT(EPOCH FROM last_notification) * 1000 < EXTRACT(EPOCH FROM current_timestamp) * 1000 - mdr.maxstaleness " +
         "ORDER BY mdr.id, timestamp DESC";

   private static final String FIND_LAST_DATAPOINTS =
         "SELECT DISTINCT ON(variable_id) variable_id AS variable, EXTRACT(EPOCH FROM timestamp) * 1000 AS timestamp " +
         "FROM datapoint dp LEFT JOIN fingerprint fp ON fp.dataset_id = dp.dataset_id " +
         "WHERE ((fp.fingerprint IS NULL AND ?1 IS NULL) OR json_equals(fp.fingerprint, (?1)::::jsonb)) AND variable_id = ANY(?2) " +
         "ORDER BY variable_id, timestamp DESC;";
   //@formatter:on
   private static final Instant LONG_TIME_AGO = Instant.ofEpochSecond(0);

   private static final Map<String, ChangeDetectionModel> MODELS =
         Map.of(RelativeDifferenceChangeDetectionModel.NAME, new RelativeDifferenceChangeDetectionModel());

   @Inject
   TestServiceImpl testService;

   @Inject
   EntityManager em;

   @Inject
   EventBus eventBus;

   @Inject
   SecurityIdentity identity;

   @Inject @RestClient
   GrafanaClient grafana;

   @ConfigProperty(name = "horreum.grafana.url")
   Optional<String> grafanaBaseUrl;

   @ConfigProperty(name = "horreum.grafana.update.datasource")
   Optional<Boolean> updateGrafanaDatasource;

   @ConfigProperty(name = "horreum.test")
   Optional<Boolean> isTest;

   @ConfigProperty(name = "horreum.internal.url")
   String internalUrl;

   @Inject
   TransactionManager tm;

   @Inject
   Vertx vertx;

   @Inject
   NotificationServiceImpl notificationService;

   long grafanaDatasourceTimerId;

   // entries can be removed from timer thread while normally this is updated from one of blocking threads
   private final ConcurrentMap<Integer, Recalculation> recalcProgress = new ConcurrentHashMap<>();

   static {
      System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @ConsumeEvent(value = DataSet.EVENT_LABELS_UPDATED, blocking = true)
   public void onLabelsUpdated(DataSet.LabelsUpdatedEvent event) {
      boolean sendNotifications;
      DataSet dataset = DataSet.findById(event.datasetId);
      if (dataset == null) {
         // The run is not committed yet?
         vertx.setTimer(1000, timerId -> Util.executeBlocking(vertx,
               CachedSecurityIdentity.ANONYMOUS, () -> onLabelsUpdated(event)
         ));
         return;
      }
      try {
         sendNotifications = (Boolean) em.createNativeQuery("SELECT notificationsenabled FROM test WHERE id = ?")
               .setParameter(1, dataset.testid).getSingleResult();
      } catch (NoResultException e) {
         sendNotifications = true;
      }
      recalculateDatapointsForDataset(dataset, sendNotifications, false, null);
      recalculateMissingDataRules(dataset);
   }

   private void recalculateMissingDataRules(DataSet dataset) {
      MissingDataRuleResult.deleteForDataset(dataset.id);
      @SuppressWarnings("unchecked") List<Object[]> ruleValues = em.createNativeQuery(LOOKUP_RULE_LABEL_VALUES)
            .setParameter(1, dataset.id).setParameter(2, dataset.testid)
            .unwrap(NativeQuery.class)
            .addScalar("rule_id", IntegerType.INSTANCE)
            .addScalar("condition", TextType.INSTANCE)
            .addScalar("value", JsonNodeBinaryType.INSTANCE)
            .getResultList();
      Util.evaluateMany(ruleValues, row -> (String) row[1], row -> (JsonNode) row[2],
            (row, result) -> {
               int ruleId = (int) row[0];
               if (result.isBoolean()) {
                  if (result.asBoolean()) {
                     createMissingDataRuleResult(dataset, ruleId);
                  }
               } else {
                  log.errorf("Result for missing data rule %d, dataset %d is not a boolean: %s", ruleId, dataset.id, result);
                  logMissingDataMessage(dataset, DatasetLog.ERROR,
                        "Result for missing data rule %d, dataset %d is not a boolean: %s", ruleId, dataset.id, result);
               }
            },
            // Absence of condition means that this dataset is taken into account. This happens e.g. when value == NULL
            row -> createMissingDataRuleResult(dataset, (int) row[0]),
            (row, exception, code) -> {
               Integer ruleId = (Integer) row[0];
               log.errorf(exception, "Exception evaluating missing data rule %d, dataset %d, code: %s", ruleId, dataset.id, code);
               logMissingDataMessage(dataset, DatasetLog.ERROR, "Exception evaluating missing data rule %d, dataset %d: '%s' Code: <pre>%s</pre>", ruleId, dataset.id, exception.getMessage(), code);
            }, output -> {
               log.debugf("Output while evaluating missing data rules for dataset %d: '%s'", dataset.id, output);
               logMissingDataMessage(dataset, DatasetLog.DEBUG, "Output while evaluating missing data rules for dataset %d: '%s'", dataset.id, output);
            });
   }

   private void createMissingDataRuleResult(DataSet dataset, int ruleId) {
      new MissingDataRuleResult(ruleId, dataset.id, dataset.start).persist();
   }

   @PostConstruct
   void init() {
      if (grafanaBaseUrl.isPresent() && updateGrafanaDatasource.orElse(true)) {
         setupGrafanaDatasource(0);
      }
   }

   @PreDestroy
   void destroy() {
      synchronized (this) {
         // The timer is not cancelled automatically during live reload:
         // https://github.com/quarkusio/quarkus/issues/25254
         vertx.cancelTimer(grafanaDatasourceTimerId);
      }
   }

   private void setupGrafanaDatasource(@SuppressWarnings("unused") long timerId) {
      vertx.executeBlocking(promise -> {
         setupGrafanaDatasource();
         promise.complete();
      }, false, null);
   }

   private void setupGrafanaDatasource() {
      String url = internalUrl + "/api/grafana";
      try {
         boolean create = true;
         for (GrafanaClient.Datasource ds : grafana.listDatasources()) {
            if (ds.name.equals("Horreum")) {
               if (!url.equals(ds.url) && ds.id != null) {
                  log.infof("Deleting Grafana datasource %d: has URL %s, expected %s", ds.id, ds.url, url);
                  grafana.deleteDatasource(ds.id);
               } else {
                  create = false;
               }
            }
         }
         if (create) {
            log.info("Creating new Horreum datasource in Grafana");
            GrafanaClient.Datasource newDatasource = new GrafanaClient.Datasource();
            newDatasource.url = url;
            grafana.addDatasource(newDatasource);
         }
         scheduleNextSetup(10000);
      } catch (ProcessingException | WebApplicationException e) {
         log.warn("Cannot set up Horreum datasource in Grafana , retrying in 5 seconds.", e);
         scheduleNextSetup(5000);
      }
   }

   private void scheduleNextSetup(int delay) {
      synchronized (this) {
         vertx.cancelTimer(grafanaDatasourceTimerId);
         grafanaDatasourceTimerId = vertx.setTimer(delay, this::setupGrafanaDatasource);
      }
   }

   private void recalculateDatapointsForDataset(DataSet dataset, boolean notify, boolean debug, Recalculation recalculation) {
      log.infof("Analyzing dataset %d (%d/%d)", dataset.id, dataset.run.id, dataset.ordinal);
      try {
         Test test = Test.findById(dataset.testid);
         if (test == null) {
            log.errorf("Cannot load test ID %d", dataset.testid);
            return;
         }
         if (!testFingerprint(dataset, test.fingerprintFilter)) {
            return;
         }

         emitDatapoints(dataset, notify, debug, recalculation);
      } catch (Throwable t) {
         log.error("Failed to create new datapoints", t);
      }
   }

   private boolean testFingerprint(DataSet dataset, String filter) {
      if (filter == null || filter.isBlank()) {
         return true;
      }
      @SuppressWarnings("unchecked") Optional<String> result =
            em.createNativeQuery("SELECT fp.fingerprint::::text FROM fingerprint fp WHERE dataset_id = ?1")
                  .setParameter(1, dataset.id).getResultStream().findFirst();
      JsonNode fingerprint;
      if (result.isPresent()) {
         fingerprint = Util.toJsonNode(result.get());
         if (fingerprint != null && fingerprint.size() == 1) {
            fingerprint = fingerprint.elements().next();
         }
      } else {
         fingerprint = JsonNodeFactory.instance.nullNode();
      }
      boolean testResult = Util.evaluateTest(filter, fingerprint,
            value -> {
               logCalculationMessage(dataset, DatasetLog.ERROR, "Evaluation of fingerprint failed: '%s' is not a boolean", value);
               return false;
            },
            (code, e) -> logCalculationMessage(dataset, DatasetLog.ERROR, "Evaluation of fingerprint filter failed: '%s' Code:<pre>%s</pre>", e.getMessage(), code),
            output -> logCalculationMessage(dataset, DatasetLog.DEBUG, "Output while evaluating fingerprint filter: <pre>%s</pre>", output));
      if (!testResult) {
         logCalculationMessage(dataset, DatasetLog.DEBUG, "Fingerprint %s was filtered out.", fingerprint);
      }
      return testResult;
   }

   public static class VariableData {
      public int variableId;
      public String name;
      public String group;
      public String calculation;
      public int numLabels;
      public JsonNode value;

      public String fullName() {
         return (group == null || group.isEmpty()) ? name : group + "/" + name;
      }
   }

   private void emitDatapoints(DataSet dataset, boolean notify, boolean debug, Recalculation recalculation) {
      Set<String> missingValueVariables = new HashSet<>();
      @SuppressWarnings("unchecked")
      List<VariableData> values = em.createNativeQuery(LOOKUP_VARIABLES)
            .setParameter(1, dataset.testid)
            .setParameter(2, dataset.id)
            .unwrap(NativeQuery.class)
            .addScalar("variableId", IntegerType.INSTANCE)
            .addScalar("name", TextType.INSTANCE)
            .addScalar("group", TextType.INSTANCE)
            .addScalar("calculation", TextType.INSTANCE)
            .addScalar("numLabels", IntegerType.INSTANCE)
            .addScalar("value", JsonNodeBinaryType.INSTANCE)
            .setResultTransformer(new AliasToBeanResultTransformer(VariableData.class))
            .getResultList();
      if (debug) {
         for (VariableData data : values) {
            logCalculationMessage(dataset, DatasetLog.DEBUG, "Fetched value for variable %s: <pre>%s</pre>", data.fullName(), data.value);
         }
      }
      Util.evaluateMany(values, data -> data.calculation, data -> data.value,
            (data, result) -> {
               Double value = Util.toDoubleOrNull(result,
                     error -> logCalculationMessage(dataset, DatasetLog.ERROR, "Evaluation of variable %s failed: %s", data.fullName(), error),
                     info -> logCalculationMessage(dataset, DatasetLog.INFO, "Evaluation of variable %s: %s", data.fullName(), info));
               if (value != null) {
                  createDataPoint(dataset, data.variableId, value, notify);
               } else {
                  if (recalculation != null) {
                     recalculation.datasetsWithoutValue.put(dataset.id, new DatasetInfo(dataset.id, dataset.run.id, dataset.ordinal));
                  }
                  missingValueVariables.add(data.fullName());
               }
            },
            data -> {
               if (data.numLabels > 1) {
                  logCalculationMessage(dataset, DatasetLog.WARN, "Variable %s has more than one label (%s) but no calculation function.", data.fullName(), data.value.fieldNames());
               }
               if (data.value == null || data.value.isNull()) {
                  logCalculationMessage(dataset, DatasetLog.INFO, "Null value for variable %s - datapoint is not created", data.fullName());
                  if (recalculation != null) {
                     recalculation.datasetsWithoutValue.put(dataset.id, new DatasetInfo(dataset.id, dataset.run.id, dataset.ordinal));
                  }
                  missingValueVariables.add(data.fullName());
                  return;
               }

               Double value = null;
               if (data.value.isNumber()) {
                  value = data.value.asDouble();
               } else if (data.value.isTextual()) {
                  try {
                     value = Double.parseDouble(data.value.asText());
                  } catch (NumberFormatException e) {
                     // ignore
                  }
               }
               if (value == null) {
                  logCalculationMessage(dataset, DatasetLog.ERROR, "Cannot turn %s into a floating-point value for variable %s", data.value, data.fullName());
                  if (recalculation != null) {
                     recalculation.errors++;
                  }
                  missingValueVariables.add(data.fullName());
               } else {
                  createDataPoint(dataset, data.variableId, value, notify);
               }
            },
            (data, exception, code) -> logCalculationMessage(dataset, DatasetLog.ERROR, "Evaluation of variable %s failed: '%s' Code:<pre>%s</pre>", data.fullName(), exception.getMessage(), code),
            output -> logCalculationMessage(dataset, DatasetLog.DEBUG, "Output while calculating variable: <pre>%s</pre>", output)
      );
      if (!missingValueVariables.isEmpty()) {
         Util.publishLater(tm, eventBus, DataSet.EVENT_MISSING_VALUES, new MissingValuesEvent(dataset.run.id, dataset.id, dataset.ordinal, dataset.testid, missingValueVariables, notify));
      }
   }

   private void createDataPoint(DataSet dataset, int variableId, double value, boolean notify) {
      DataPoint dataPoint = new DataPoint();
      dataPoint.variable = em.getReference(Variable.class, variableId);
      dataPoint.dataset = dataset;
      dataPoint.timestamp = dataset.start;
      dataPoint.value = value;
      dataPoint.persist();
      Util.publishLater(tm, eventBus, DataPoint.EVENT_NEW, new DataPoint.Event(dataPoint, notify));
   }

   private void logCalculationMessage(DataSet dataSet, int level, String format, Object... args) {
      logCalculationMessage(dataSet.testid, dataSet.id, level, format, args);
   }

   private void logCalculationMessage(int testId, int datasetId, int level, String format, Object... args) {
      new DatasetLog(em.getReference(Test.class, testId), em.getReference(DataSet.class, datasetId),
            level, "variables", String.format(format, args)).persist();
   }

   private void logMissingDataMessage(DataSet dataSet, int level, String format, Object... args) {
      logMissingDataMessage(dataSet.testid, dataSet.id, level, format, args);
   }

   private void logMissingDataMessage(int testId, int datasetId, int level, String format, Object... args) {
      new DatasetLog(em.getReference(Test.class, testId), em.getReference(DataSet.class, datasetId),
            level, "missingdata", String.format(format, args)).persist();
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @ConsumeEvent(value = Run.EVENT_TRASHED, blocking = true)
   public void onRunTrashed(Integer runId) {
      log.infof("Trashing datapoints for run %d", runId);
      // Hibernate would generate DELETE FROM change CROSS JOIN ... and that's not a valid PostgreSQL
      em.createNativeQuery("DELETE FROM change USING dataset WHERE change.dataset_id = dataset.id AND dataset.runid = ?")
            .setParameter(1, runId).executeUpdate();
      em.createNativeQuery("DELETE FROM datapoint USING dataset WHERE datapoint.dataset_id = dataset.id AND dataset.runid = ?")
            .setParameter(1, runId).executeUpdate();
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @ConsumeEvent(value = DataPoint.EVENT_NEW, blocking = true, ordered = true)
   public void onNewDataPoint(DataPoint.Event event) {
      DataPoint dataPoint = event.dataPoint;
      // The variable referenced by datapoint is a fake
      Variable variable = Variable.findById(dataPoint.variable.id);
      log.debugf("Processing new datapoint for run %d, variable %d (%s), value %f", dataPoint.dataset.id,
            dataPoint.variable.id, variable != null ? variable.name : "<unknown>", dataPoint.value);
      Change lastChange = em.createQuery("SELECT c FROM Change c LEFT JOIN Fingerprint fp ON c.dataset.id = fp.dataset.id " +
            "WHERE c.variable = ?1 AND TRUE = function('json_equals', fp.fingerprint, (SELECT fp2.fingerprint FROM Fingerprint fp2 WHERE dataset.id = ?2)) " +
            "ORDER by c.timestamp DESC", Change.class)
            .setParameter(1, variable).setParameter(2, event.dataPoint.dataset.id).setMaxResults(1)
            .getResultStream().findFirst().orElse(null);
      Instant changeTimestamp = LONG_TIME_AGO;
      if (lastChange != null) {
         if (lastChange.timestamp.compareTo(dataPoint.timestamp) > 0) {
            // We won't revision changes until next variable recalculation
            log.debugf("Ignoring datapoint %d from %s as there is a newer change %d from %s.",
                  dataPoint.id, dataPoint.timestamp, lastChange.id, lastChange.timestamp);
            return;
         }
         log.debugf("Filtering datapoints newer than %s (change %d)", lastChange.timestamp, lastChange.id);
         changeTimestamp = lastChange.timestamp;
      }
      List<DataPoint> dataPoints = em.createQuery("SELECT dp FROM DataPoint dp LEFT JOIN Fingerprint fp ON dp.dataset.id = fp.dataset.id " +
            "WHERE dp.variable = ?1 AND dp.timestamp BETWEEN ?2 AND ?3 AND " +
            "TRUE = function('json_equals', fp.fingerprint, (SELECT fp2.fingerprint FROM Fingerprint fp2 WHERE dataset.id = ?4)) " +
            "ORDER BY dp.timestamp DESC", DataPoint.class)
            .setParameter(1, variable).setParameter(2, changeTimestamp)
            .setParameter(3, dataPoint.timestamp).setParameter(4, dataPoint.dataset.id).getResultList();
      // Last datapoint is already in the list
      if (dataPoints.isEmpty()) {
         log.error("The published datapoint should be already in the list");
         return;
      }
      DataPoint lastDatapoint = dataPoints.get(0);
      if (!lastDatapoint.id.equals(dataPoint.id)) {
         log.warnf("Ignoring datapoint %d from %s - it is not the last datapoint (%d from %s)",
               dataPoint.id, dataPoint.timestamp, lastDatapoint.id, lastDatapoint.timestamp);
         return;
      }

      for (ChangeDetection detection : ChangeDetection.<ChangeDetection>find("variable", variable).list()) {
         ChangeDetectionModel model = MODELS.get(detection.model);
         if (model == null) {
            log.errorf("Cannot find change detection model %s", detection.model);
            continue;
         }
         model.analyze(dataPoints, detection.config, change -> {
            Query datasetQuery = em.createNativeQuery("SELECT id, runid as \"runId\", ordinal FROM dataset WHERE id = ?1");
            SqlServiceImpl.setResultTransformer(datasetQuery, Transformers.aliasToBean(DatasetInfo.class));
            DatasetInfo datasetInfo = (DatasetInfo) datasetQuery.setParameter(1, change.dataset.id).getSingleResult();
            em.persist(change);
            Util.publishLater(tm, eventBus, Change.EVENT_NEW, new Change.Event(change, datasetInfo, event.notify));
         });
      }
   }

   @Override
   @WithRoles
   @PermitAll
   public List<Variable> variables(Integer testId) {
      if (testId != null) {
         return Variable.list("testid", testId);
      } else {
         return Variable.listAll();
      }
   }

   @Override
   @WithRoles
   @RolesAllowed("tester")
   @Transactional
   public void variables(Integer testId, List<Variable> variables) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing query param 'test'");
      }
      try {
         List<Variable> currentVariables = Variable.list("testid", testId);
         updateCollection(currentVariables, variables, v -> v.id, item -> {
            if (item.id != null && item.id <= 0) {
               item.id = null;
            }
            if (item.changeDetection != null) {
               ensureDefaults(item.changeDetection);
               item.changeDetection.forEach(rd -> rd.variable = item);
            }
            item.testId = testId;
            item.persist(); // insert
         }, (current, matching) -> {
            current.name = matching.name;
            current.group = matching.group;
            current.labels = matching.labels;
            current.calculation = matching.calculation;
            if (matching.changeDetection != null) {
               ensureDefaults(matching.changeDetection);
            }
            updateCollection(current.changeDetection, matching.changeDetection, rd -> rd.id, item -> {
               if (item.id != null && item.id <= 0) {
                  item.id = null;
               }
               item.variable = current;
               item.persist();
               current.changeDetection.add(item);
            }, (crd, mrd) -> {
               crd.model = mrd.model;
               crd.config = mrd.config;
            }, PanacheEntityBase::delete);
            current.persist();
         }, current -> {
            DataPoint.delete("variable_id", current.id);
            Change.delete("variable_id", current.id);
            current.delete();
         });

         if (grafanaBaseUrl.isPresent()) {
            try {
               for (var dashboard : grafana.searchDashboard("", "testId=" + testId)) {
                  grafana.deleteDashboard(dashboard.uid);
               }
            } catch (ProcessingException | WebApplicationException e) {
               log.warnf(e, "Failed to delete dasboards for test %d", testId);
            }
         }

         em.flush();
      } catch (PersistenceException e) {
         throw new WebApplicationException(e, Response.serverError().build());
      }
   }

   private void ensureDefaults(Set<ChangeDetection> rds) {
      rds.forEach(rd -> {
         ChangeDetectionModel model = MODELS.get(rd.model);
         if (model == null) {
            throw ServiceException.badRequest("Unknown model " + rd.model);
         }
         if (!(rd.config instanceof ObjectNode)) {
            throw ServiceException.badRequest("Invalid config for model " + rd.model + " - not an object: " + rd.config);
         }
         for (var entry : model.config().defaults.entrySet()) {
            JsonNode property = rd.config.get(entry.getKey());
            if (property == null || property.isNull()) {
               ((ObjectNode) rd.config).set(entry.getKey(), entry.getValue());
            }
         }
      });
   }

   private <T> void updateCollection(Collection<T> currentList, Collection<T> newList, Function<T, Object> idSelector, Consumer<T> create, BiConsumer<T, T> update, Consumer<T> delete) {
      for (Iterator<T> iterator = currentList.iterator(); iterator.hasNext(); ) {
         T current = iterator.next();
         T matching = newList.stream().filter(v -> idSelector.apply(current).equals(idSelector.apply(v))).findFirst().orElse(null);
         if (matching == null) {
            delete.accept(current);
            iterator.remove();
         } else {
            update.accept(current, matching);
         }
      }
      for (T item : newList) {
         if (currentList.stream().noneMatch(v -> idSelector.apply(v).equals(idSelector.apply(item)))) {
            create.accept(item);
         }
      }
   }

   private GrafanaClient.GetDashboardResponse findDashboard(int testId, String fingerprint) {
      try {
         List<GrafanaClient.DashboardSummary> list = grafana.searchDashboard("", testId + ":" + fingerprint);
         if (list.isEmpty()) {
            return null;
         } else {
            return grafana.getDashboard(list.get(0).uid);
         }
      } catch (ProcessingException | WebApplicationException e) {
         log.debugf(e, "Error looking up dashboard for test %d, fingerprint %s", testId, fingerprint);
         return null;
      }
   }

   private DashboardInfo createDashboard(int testId, String fingerprint, List<Variable> variables) {
      DashboardInfo info = new DashboardInfo();
      info.testId = testId;
      Dashboard dashboard = new Dashboard();
      dashboard.title = Test.<Test>findByIdOptional(testId).map(t -> t.name).orElse("Test " + testId)
            + (fingerprint.isEmpty() ? "" : ", " + fingerprint);
      dashboard.tags.add(testId + ";" + fingerprint);
      dashboard.tags.add("testId=" + testId);
      int i = 0;
      Map<String, List<Variable>> byGroup = groupedVariables(variables);
      for (Variable variable : variables) {
         dashboard.annotations.list.add(new Dashboard.Annotation(variable.name, variable.id + ";" + fingerprint));
      }
      for (Map.Entry<String, List<Variable>> entry : byGroup.entrySet()) {
         entry.getValue().sort(Comparator.comparing(v -> v.name));
         Dashboard.Panel panel = new Dashboard.Panel(entry.getKey(), new Dashboard.GridPos(12 * (i % 2), 9 * (i / 2), 12, 9));
         info.panels.add(new PanelInfo(entry.getKey(), entry.getValue()));
         for (Variable variable : entry.getValue()) {
            panel.targets.add(new Target(variable.id + ";" + fingerprint, "timeseries", "T" + i));
         }
         dashboard.panels.add(panel);
         ++i;
      }
      try {
         GrafanaClient.DashboardSummary response = grafana.createOrUpdateDashboard(new GrafanaClient.PostDashboardRequest(dashboard, true));
         info.uid = response.uid;
         info.url = grafanaBaseUrl.get() + response.url;
         return info;
      } catch (WebApplicationException e) {
         log.errorf(e, "Failed to create/update dashboard %s", dashboard.uid);
         try {
            tm.setRollbackOnly();
         } catch (SystemException systemException) {
            throw ServiceException.serverError("Failure in transaction");
         }
         return null;
      }
   }

   private Map<String, List<Variable>> groupedVariables(List<Variable> variables) {
      Map<String, List<Variable>> byGroup = new TreeMap<>();
      for (Variable variable : variables) {
         byGroup.computeIfAbsent(variable.group == null || variable.group.isEmpty() ? variable.name : variable.group, g -> new ArrayList<>()).add(variable);
      }
      return byGroup;
   }

   @Override
   @WithRoles
   @PermitAll
   @Transactional
   public DashboardInfo dashboard(Integer testId, String fingerprint) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing param 'test'");
      }
      if (fingerprint == null) {
         fingerprint = "";
      }
      GrafanaClient.GetDashboardResponse response = findDashboard(testId, fingerprint);
      List<Variable> variables = Variable.list("testid", testId);
      DashboardInfo dashboard;
      if (response == null) {
         dashboard = createDashboard(testId, fingerprint, variables);
         if (dashboard == null) {
            throw new ServiceException(Response.Status.SERVICE_UNAVAILABLE, "Cannot update Grafana dashboard.");
         }
      } else {
         dashboard = new DashboardInfo();
         dashboard.testId = testId;
         dashboard.uid = response.dashboard.uid;
         dashboard.url = response.meta.url;
         for (var entry : groupedVariables(variables).entrySet()) {
            dashboard.panels.add(new PanelInfo(entry.getKey(), entry.getValue()));
         }
      }
      return dashboard;
   }

   @Override
   @WithRoles
   @PermitAll
   public List<Change> changes(Integer varId) {
      Variable v = Variable.findById(varId);
      if (v == null) {
         throw ServiceException.notFound("Variable " + varId + " not found");
      }
      // TODO: Avoid sending variable in each datapoint
      return Change.list("variable", v);
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public void updateChange(Integer id, Change change) {
      try {
         if (id != change.id) {
            throw ServiceException.badRequest("Path ID and entity don't match");
         }
         em.merge(change);
      } catch (PersistenceException e) {
         throw new WebApplicationException(e, Response.serverError().build());
      }
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public void deleteChange(Integer id) {
      if (!Change.deleteById(id)) {
         throw ServiceException.notFound("Change not found");
      }
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   public void recalculateDatapoints(Integer testId, boolean notify,
                                     boolean debug, Long from, Long to) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing param 'test'");
      }

      // We cannot use resteasy propagation because when the request completes the request data
      // are terminated anyway (it's not reference counter) - therefore we need to manually copy the identity
      // to the new context in a different thread.
      // CDI needs to be propagated - without that the interceptors wouldn't run.
      // Without thread context propagation we would get an exception in Run.findById, though the interceptors would be invoked correctly.
      Util.executeBlocking(vertx, new CachedSecurityIdentity(identity), () -> {
         Recalculation recalculation = new Recalculation();
         if (recalcProgress.putIfAbsent(testId, recalculation) != null) {
            log.infof("Already started recalculation on test %d, ignoring.", testId);
            return;
         }
         try {
            recalculation.datasets = getDatasetsForRecalculation(testId, from, to);
            int numRuns = recalculation.datasets.size();
            log.infof("Starting recalculation of test %d, %d runs", testId, numRuns);
            int completed = 0;
            recalcProgress.put(testId, recalculation);
            for (int datasetId : recalculation.datasets) {
               // Since the evaluation might take few moments and we're dealing potentially with thousands
               // of runs we'll process each run in a separate transaction
               recalulateForDataset(datasetId, notify, debug, recalculation);
               recalculation.progress = 100 * ++completed / numRuns;
            }

         } catch (Throwable t) {
            log.error("Recalculation failed", t);
            throw t;
         } finally {
            recalculation.done = true;
            vertx.setTimer(30_000, timerId -> recalcProgress.remove(testId, recalculation));
         }
      });
   }

   @WithRoles
   @Transactional
   List<Integer> getDatasetsForRecalculation(Integer testId, Long from, Long to) {
      Query query = em.createNativeQuery("SELECT id FROM dataset WHERE testid = ?1 AND (EXTRACT(EPOCH FROM start) * 1000 BETWEEN ?2 AND ?3) ORDER BY start")
            .setParameter(1, testId)
            .setParameter(2, from == null ? Long.MIN_VALUE : from)
            .setParameter(3, to == null ? Long.MAX_VALUE : to);
      @SuppressWarnings("unchecked")
      List<Integer> ids = query.getResultList();
      DataPoint.delete("dataset_id in ?1", ids);
      Change.delete("dataset_id in ?1 AND confirmed = false", ids);
      if (ids.size() > 0) {
         // Due to RLS policies we cannot add a record to a dataset we don't own
         logCalculationMessage(testId, ids.get(0), DatasetLog.INFO, "Starting recalculation of %d runs.", ids.size());
      }
      return ids;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional(Transactional.TxType.REQUIRES_NEW)
   void recalulateForDataset(Integer datasetId, boolean notify, boolean debug, Recalculation recalculation) {
      DataSet dataset = DataSet.findById(datasetId);
      recalculateDatapointsForDataset(dataset, notify, debug, recalculation);
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   public RecalculationStatus getRecalculationStatus(Integer testId) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing param 'test'");
      }
      Recalculation recalculation = recalcProgress.get(testId);
      RecalculationStatus status = new RecalculationStatus();
      status.percentage = recalculation == null ? 100 : recalculation.progress;
      status.done = recalculation == null || recalculation.done;
      if (recalculation != null) {
         status.totalRuns = recalculation.datasets.size();
         status.errors = recalculation.errors;
         status.datasetsWithoutValue = recalculation.datasetsWithoutValue.values();
      }
      return status;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @Scheduled(every = "{horreum.alerting.missing.dataset.check}")
   public void checkMissingDataset() {
      @SuppressWarnings("unchecked")
      List<Object[]> results = em.createNativeQuery(LOOKUP_RECENT).getResultList();
      for (Object[] row : results) {
         int ruleId = (int) row[0];
         int testId = (int) row[1];
         String ruleName = (String) row[2];
         long maxStaleness = ((BigInteger) row[3]).longValue();
         Timestamp ts = (Timestamp) row[4];
         Instant timestamp = ts == null ? null : ts.toInstant();
         if (timestamp == null || timestamp.isBefore(Instant.now().minusMillis(maxStaleness))) {
            if (ruleName == null) {
               ruleName = "rule #" + ruleId;
            }
            notificationService.notifyMissingDataset(testId, ruleName, maxStaleness, timestamp);
            int numUpdated = em.createNativeQuery("UPDATE missingdata_rule SET last_notification = ?1 WHERE id = ?2")
                  .setParameter(1, Instant.now()).setParameter(2, ruleId).executeUpdate();
            if (numUpdated != 1) {
               log.errorf("Missing data rules update for rule %d (test %d) didn't work: updated: %d", ruleId, testId, numUpdated);
            }
         }
      }
   }

   @Override
   @WithRoles
   @PermitAll
   public List<DatapointLastTimestamp> findLastDatapoints(LastDatapointsParams params) {
      Query query = em.createNativeQuery(FIND_LAST_DATAPOINTS)
            .setParameter(1, String.valueOf(Util.parseFingerprint(params.fingerprint)))
            .setParameter(2, new TypedParameterValue(IntArrayType.INSTANCE, params.variables));
      SqlServiceImpl.setResultTransformer(query, Transformers.aliasToBean(DatapointLastTimestamp.class));
      //noinspection unchecked
      return query.getResultList();
   }

   @Override
   @RolesAllowed(Roles.UPLOADER)
   @WithRoles
   @Transactional
   public void expectRun(String testNameOrId, Long timeoutSeconds, String expectedBy, String backlink) {
      if (timeoutSeconds == null) {
         throw ServiceException.badRequest("No timeout set.");
      } else if (timeoutSeconds <= 0) {
         throw ServiceException.badRequest("Timeout must be positive (unit: seconds)");
      }
      Test test = testService.getByNameOrId(testNameOrId);
      if (test == null) {
         throw ServiceException.notFound("Test " + testNameOrId + " does not exist.");
      }
      RunExpectation runExpectation = new RunExpectation();
      runExpectation.testId = test.id;
      runExpectation.expectedBefore = Instant.now().plusSeconds(timeoutSeconds);
      runExpectation.expectedBy = expectedBy != null ? expectedBy : identity.getPrincipal().getName();
      runExpectation.backlink = backlink;
      runExpectation.persist();
   }

   @PermitAll
   @WithRoles(extras = Roles.HORREUM_ALERTING)
   @Override
   public List<RunExpectation> expectations() {
      if (!isTest.orElse(false)) {
         throw ServiceException.notFound("Not available without test mode.");
      }
      return RunExpectation.listAll();
   }

   @PermitAll
   @Override
   public List<ChangeDetectionModelConfig> models() {
      return MODELS.values().stream().map(ChangeDetectionModel::config).collect(Collectors.toList());
   }

   @PermitAll
   @Override
   public List<ChangeDetection> defaultChangeDetectionConfigs() {
      ChangeDetection lastDatapoint = new ChangeDetection();
      lastDatapoint.model = RelativeDifferenceChangeDetectionModel.NAME;
      lastDatapoint.config = JsonNodeFactory.instance.objectNode()
            .put("window", 1).put("filter", "mean").put("threshold", 0.2).put("minPrevious", 5);
      ChangeDetection floatingWindow = new ChangeDetection();
      floatingWindow.model = RelativeDifferenceChangeDetectionModel.NAME;
      floatingWindow.config = JsonNodeFactory.instance.objectNode()
            .put("window", 5).put("filter", "mean").put("threshold", 0.1).put("minPrevious", 5);
      return Arrays.asList(lastDatapoint, floatingWindow);
   }

   @WithRoles
   @Override
   public List<MissingDataRule> missingDataRules(int testId) {
      if (testId <= 0) {
         throw ServiceException.badRequest("Invalid test ID: " + testId);
      }
      return MissingDataRule.list("test.id", testId);
   }

   @WithRoles
   @Transactional
   @Override
   public int updateMissingDataRule(int testId, MissingDataRule rule) {
      if (testId <= 0) {
         throw ServiceException.badRequest("Invalid test ID: " + testId);
      }
      if (rule.id != null && rule.id <= 0) {
         rule.id = null;
      }
      // drop any info about last notification
      rule.lastNotification = null;
      if (rule.maxStaleness <= 0) {
         throw ServiceException.badRequest("Invalid max staleness in rule " + rule.name + ": " + rule.maxStaleness);
      }

      if (rule.id == null) {
         rule.test = em.getReference(Test.class, testId);
         rule.persistAndFlush();
      } else {
         MissingDataRule existing = MissingDataRule.findById(rule.id);
         if (existing == null) {
            throw ServiceException.badRequest("Rule does not exist.");
         } else if (existing.test.id != testId) {
            throw ServiceException.badRequest("Rule belongs to a different test");
         }
         rule.test = existing.test;
         em.merge(rule);
         em.flush();
      }
      Util.executeBlocking(vertx, new CachedSecurityIdentity(identity), () -> {
         @SuppressWarnings("unchecked") List<Object[]> idsAndTimestamps =
               em.createNativeQuery("SELECT id, start FROM dataset WHERE testid = ?1").setParameter(1, testId).getResultList();
         for (Object[] row : idsAndTimestamps) {
            recalculateMissingDataRule((int) row[0], ((Timestamp) row[1]).toInstant(), rule);
         }
      });
      return rule.id;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional(Transactional.TxType.REQUIRES_NEW)
   void recalculateMissingDataRule(int datasetId, Instant timestamp, MissingDataRule rule) {
      JsonNode value = (JsonNode) em.createNativeQuery(LOOKUP_LABEL_VALUE_FOR_RULE)
            .setParameter(1, datasetId).setParameter(2, rule.id)
            .unwrap(NativeQuery.class)
            .addScalar("value", JsonNodeBinaryType.INSTANCE)
            .getSingleResult();
      boolean match = true;
      if (rule.condition != null && !rule.condition.isBlank()) {
         String ruleName = rule.name == null ? "#" + rule.id : rule.name;
         match = Util.evaluateTest(rule.condition, value, notBoolean -> {
            log.errorf("Missing data rule %s result is not a boolean: %s", ruleName, notBoolean);
            logMissingDataMessage(rule.testId(), datasetId, DatasetLog.ERROR,
                  "Missing data rule %s result is not a boolean: %s", ruleName, notBoolean);
            return true;
         }, (code, exception) -> {
            log.errorf(exception, "Error evaluating missing data rule %s: %s", ruleName, code);
            logMissingDataMessage(rule.testId(), datasetId, DatasetLog.ERROR,
                  "Error evaluating missing data rule %s: '%s' Code:<pre>%s</pre>", ruleName, exception.getMessage(), code);
         }, output -> {
            log.debugf("Output while evaluating missing data rule %s: '%s'", ruleName, output);
            logMissingDataMessage(rule.testId(), datasetId, DatasetLog.DEBUG,
                  "Output while evaluating missing data rule %s: '%s'", ruleName, output);
         });
      }
      if (match) {
         new MissingDataRuleResult(rule.id, datasetId, timestamp).persist();
      }
   }

   @WithRoles
   @Transactional
   @Override
   public void deleteMissingDataRule(int id) {
      MissingDataRule.deleteById(id);
   }

   @ConsumeEvent(value = Run.EVENT_NEW, blocking = true)
   @WithRoles(extras = Roles.HORREUM_ALERTING)
   @Transactional
   public void removeExpected(Run run) {
      // delete at most one expectation
      Query query = em.createNativeQuery("DELETE FROM run_expectation WHERE id = (SELECT id FROM run_expectation WHERE testid = (SELECT testid FROM run WHERE id = ?1) LIMIT 1)");
      query.setParameter(1, run.id);
      int updated = query.executeUpdate();
      if (updated > 0) {
         log.infof("Removed %d run expectations as run %d was added.", updated, run.id);
      }
   }

   @Transactional
   @WithRoles(extras = Roles.HORREUM_ALERTING)
   @Scheduled(every = "{horreum.alerting.expected.run.check}")
   public void checkExpectedRuns() {
      for (RunExpectation expectation : RunExpectation.<RunExpectation>find("expectedbefore < ?1", Instant.now()).list()) {
         boolean sendNotifications = (Boolean) em.createNativeQuery("SELECT notificationsenabled FROM test WHERE id = ?")
               .setParameter(1, expectation.testId).getSingleResult();
         if (sendNotifications) {
            // We will perform this only if this transaction succeeds, to allow no-op retries
            Util.doAfterCommit(tm, () -> notificationService.notifyExpectedRun(expectation.testId,
                  expectation.expectedBefore.toEpochMilli(), expectation.expectedBy, expectation.backlink));
         } else {
            log.debugf("Skipping expected run notification on test %d since it is disabled.", expectation.testId);
         }
         expectation.delete();
      }
   }

   // Note: this class must be public - otherwise when this is used as a parameter to
   // a method in AlertingServiceImpl the interceptors would not be invoked.
   public static class Recalculation {
      List<Integer> datasets = Collections.emptyList();
      int progress;
      boolean done;
      public int errors;
      Map<Integer, DatasetInfo> datasetsWithoutValue = new HashMap<>();
   }
}
