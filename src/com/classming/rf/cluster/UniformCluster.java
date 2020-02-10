package com.classming.rf.cluster;

import com.classming.rf.State;

import java.util.LinkedList;
import java.util.List;

public class UniformCluster implements Cluster {

    @Override
    public List<Double> cluster(List<State> states) {
        List<Double> distribution = new LinkedList<>();
        for (int i = 0, size = states.size(); i < size; i++)
            distribution.add(1.0 / size);
        return distribution;
    }
}
