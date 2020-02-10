package com.classming.rf.cluster;

import com.classming.rf.State;

import java.util.List;

public interface Cluster {
    List<Double> cluster(List<State> states);
}
