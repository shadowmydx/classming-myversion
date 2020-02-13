package com.classming.rf.cluster;

import com.classming.MutateClass;
import com.classming.record.Recover;
import com.classming.rf.Action;
import com.classming.rf.State;
import com.classming.rf.Tool;

import java.io.IOException;
import java.util.List;

public class RollBackAction implements Action {
    @Override
    public State proceedAction(MutateClass target, List<State> total) {
        Cluster cluster = new HACluster();
        List<Double> distribution = cluster.cluster(total);
        State state = Tool.randomStateByDistribution(distribution, total);
        try {
            Recover.recoverFromPath(state.getTarget());
        } catch (IOException e) {
            System.out.println("should not recover failed.");
        }

        return state;
    }
}
