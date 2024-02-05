package io.hyperfoil.tools.horreum.changedetection.eDivisive;

import java.util.Objects;
import java.util.Optional;

import static java.lang.Math.abs;

public class EDivisiveStructs {

    public static class ChangePoint {
        public int index;
        public ComparativeStats stats;
    }

    public static class Metric {

        public Integer direction;
        public Float scale;
        public String unit;

        public Metric(Integer direction, Float scale) {
            this(direction, scale, "");

        }
        public Metric(Integer direction, Float scale, String unit) {
            this.direction = direction;
            this.scale = scale;
            this.unit = unit;
        }

    }

    /*
        A change point.
    */
    public static class EDivisiveChangePoint {
        public int index;
        public float qhat;
        public Optional<Float> probability;


        /*

                Create an E-Divisive change point, representing a change point found by E-Divisive algorithm.

                :param index: Index of the change point.
                :param qhat: The Q-Hat metric for the change point.
                :param probability: The probability that the change point is valid, based on a permutation test.
        */
        public EDivisiveChangePoint(int index, float qhat, Optional<Float> probability) {
            this.index = index;
            this.qhat = qhat;
            this.probability = probability;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EDivisiveChangePoint that = (EDivisiveChangePoint) o;
            return index == that.index && Float.compare(qhat, that.qhat) == 0 && Objects.equals(probability, that.probability);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, qhat, probability);
        }
    }

    /*
        Keeps statistics of two series of data and the probability both series
        have the same distribution.
    */
    private static class ComparativeStats {

        public float mean_1;
        public float mean_2;
        public float std_1;
        public float std_2;
        public float pvalue;

        public float forward_rel_change() {
            // Relative change from left to right
            return this.mean_2 / this.mean_1 - 1.0f;
        }

        public float backward_rel_change() {
            // Relative change from right to left
            return this.mean_1 / this.mean_2 - 1.0f;
        }

        public float change_magnitude() {
            // Maximum of absolutes of rel_change and rel_change_reversed
            return Math.max(abs(forward_rel_change()), abs(backward_rel_change()));
        }
    }
}
