package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class DataPoint {
    public Integer id;
    @NotNull
    public Instant timestamp;
    @NotNull
    public double value;
    @NotNull
    public Variable variable;

    @NotNull
    @JsonProperty("datasetId")
    public Integer datasetId;

    public DataPoint() {
    }

    public double value() {
        return this.value;
    }

    public String toString() {
        return this.id + "|" + this.datasetId + "@" + this.timestamp + ": " + this.value;
    }

    public static class DatasetProcessedEvent {
        public Dataset.Info dataset;
        public boolean notify;

        public DatasetProcessedEvent() {
        }

        public DatasetProcessedEvent(Dataset.Info dataset, boolean notify) {
            this.dataset = dataset;
            this.notify = notify;
        }

        @Override
        public String toString() {
            return "DatasetProcessedEvent{" +
                    "dataset=" + dataset +
                    ", notify=" + notify +
                    '}';
        }
    }

    public static class Event {
        public DataPoint dataPoint;
        public int testId;
        public boolean notify;

        public Event() {
        }

        public Event(DataPoint dataPoint, int testId, boolean notify) {
            this.dataPoint = dataPoint;
            this.testId = testId;
            this.notify = notify;
        }

        public String toString() {
            return "DataPoint.Event{dataPoint=" + this.dataPoint + ", notify=" + this.notify + '}';
        }
    }

    public static class EventNew {

        public int datasetId;
        public Instant timestamp;
        public int variableId;
        public double value;
        public boolean notify;

        public EventNew() {
        }

        public EventNew(int datasetId, Instant timestamp, int variableId, double value, boolean notify) {
            this.datasetId = datasetId;
            this.timestamp = timestamp;
            this.variableId = variableId;
            this.value = value;
            this.notify = notify;
        }

        @Override
        public String toString() {
            return "EventNew{" +
                    "datasetId=" + datasetId +
                    ", timestamp=" + timestamp +
                    ", variableId=" + variableId +
                    ", value=" + value +
                    ", notify=" + notify +
                    '}';
        }
    }
}
