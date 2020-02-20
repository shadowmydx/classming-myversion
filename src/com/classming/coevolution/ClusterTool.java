package com.classming.coevolution;

import com.classming.MutateClass;
import com.classming.rf.State;
import com.classming.rf.Tool;
import com.classming.rf.cluster.Cluster;
import com.classming.rf.cluster.HACluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ClusterTool {

    private static final int MUTATE_SIZE = 50;
    public static List<State> SelectMutateByCluster(List<State> mutateAcceptHistory) {
        if (mutateAcceptHistory.size() > MUTATE_SIZE) {
            List<State> mutateList = new ArrayList<>();

            Cluster cluster = new HACluster();
            List<Double> distribution = cluster.cluster(mutateAcceptHistory);

            for (int i = 0; i < MUTATE_SIZE; i++) {
                State state = Tool.randomStateByDistribution(distribution, mutateAcceptHistory);
                mutateList.add(state);
            }
            return mutateList;
        }else {
            return mutateAcceptHistory;
        }
    }


    public static void getEvoClusterData(List<State> accept, List<State> reject) {
        List<State> stateList = new ArrayList<>();
        stateList.addAll(accept);
        stateList.addAll(reject);

        Cluster cluster = new HACluster();
        cluster.cluster(stateList);
    }

    public static void getClassmingClusterData(List<MutateClass> accept) {
        List<State> stateList = new ArrayList<>();
        for (MutateClass sClass: accept) {
            State state = new State();
            state.setTarget(sClass);
            stateList.add(state);
        }

        Cluster cluster = new HACluster();
        cluster.cluster(stateList);
    }

}
