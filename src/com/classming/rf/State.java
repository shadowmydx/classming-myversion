package com.classming.rf;

import com.classming.MutateClass;

import java.util.*;

public class State {

    private Map<String, Integer> mappingToIndex = new HashMap<>();
    private List<String> actions = new ArrayList<>();
    private List<Double> scores = new ArrayList<>();
    private static double exploreRate = 0.1;
    public static final String GOTO = "goto";
    public static final String BACKTRACK = "backtrack";
    public static final String LOOK_UP = "lookup";
    public static final String RETURN = "return";

    State() {
        mappingToIndex.put(GOTO, 0);
        actions.add(GOTO);
        mappingToIndex.put(BACKTRACK, 1);
        actions.add(BACKTRACK);
        mappingToIndex.put(LOOK_UP, 2);
        actions.add(LOOK_UP);
        mappingToIndex.put(RETURN, 3);
        actions.add(RETURN);

        for (int i = 0; i < 4; i ++) {
            scores.add(0.0);
        }
    }

    public MutateClass getTarget() {
        return target;
    }

    public void setTarget(MutateClass target) {
        this.target = target;
    }

    private MutateClass target;

    // estimate the Q(s, a) = Q(s, a) + alpha * (R - Q(s, a))
    public void updateScore(String action, double reward) {
        int scoreIndex = mappingToIndex.get(action);
        double previous = scores.get(scoreIndex);
        previous += RfFramework.ALPHA * (reward - previous);
        scores.set(scoreIndex, previous);
    }


    public String selectAction() {
        Random random = new Random();
        if (random.nextDouble() < exploreRate) {
            return actions.get(random.nextInt(actions.size()));
        }
        double maxScore = Collections.max(scores);
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < scores.size(); i ++) {
            if (maxScore == scores.get(i)) {
                candidates.add(i);
            }
        }
        int resultIndex = candidates.get(random.nextInt(candidates.size()));
        return actions.get(resultIndex);
    }
}
