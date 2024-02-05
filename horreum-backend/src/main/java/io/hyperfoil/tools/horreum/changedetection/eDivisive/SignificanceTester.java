package io.hyperfoil.tools.horreum.changedetection.eDivisive;

public abstract class SignificanceTester {
    abstract boolean is_significant(EDivisiveStructs.EDivisiveChangePoint candidate, Float[] series, Integer windows);
}
