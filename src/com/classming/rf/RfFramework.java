package com.classming.rf;

import com.classming.ClassmingEntry;
import com.classming.Main;
import com.classming.MethodCounter;
import com.classming.MutateClass;
import com.classming.record.Recover;

import java.io.IOException;
import java.util.*;

public class RfFramework {
    public static final double ALPHA = .2;
    private GotoAction gotoAction = new GotoAction();
    private LookupAction lookupAction = new LookupAction();
    private ReturnAction returnAction = new ReturnAction();
    private BacktrackAction backtrackAction = new BacktrackAction();
    private static final int DEAD_END = -1;
    private Map<String, Action> actionContainer = new HashMap<>();
    {
        actionContainer.put(State.RETURN, returnAction);
        actionContainer.put(State.LOOK_UP, lookupAction);
        actionContainer.put(State.BACKTRACK, backtrackAction);
        actionContainer.put(State.GOTO, gotoAction);
    }

    // estimate the Q(s, a) = Q(s, a) + alpha * (R - Q(s, a))
    public void process(String className, int iterationCount, String[] args) throws IOException {
        MutateClass mutateClass = new MutateClass();
        Main.initial(args);
        mutateClass.initialize(className, args);
        List<State> mutateAcceptHistory = new ArrayList<>();
        List<MutateClass> mutateRejectHistory = new ArrayList<>();
        Random random = new Random();
        State currentState = new State();
        currentState.setTarget(mutateClass);
        mutateAcceptHistory.add(currentState);
        for (int i = 0; i < iterationCount; i ++) {
//            MutateClass newOne = mutateClass.iteration(); // sootclass has changed here for all objects.
            String actionString = currentState.selectAction();
            Action action = actionContainer.get(actionString);
            MutateClass newOne = action.proceedAction(currentState.getTarget(), mutateAcceptHistory); // sootclass has changed here for all objects
            if (newOne != null) {
                MutateClass previousClass = mutateAcceptHistory.get(mutateAcceptHistory.size() - 1).getTarget();
                MethodCounter current = newOne.getCurrentMethod();
                List<String> originalCode = previousClass.getMethodOriginalStmtListString(current.getSignature());
                double covScore = ClassmingEntry.calculateCovScore(newOne);
                double rand = random.nextDouble();
                System.out.println(covScore);
                double fitnessScore = ClassmingEntry.fitness(ClassmingEntry.calculateCovScore(mutateClass), covScore, originalCode.size());
                if(rand < fitnessScore) {
                    currentState.updateScore(actionString, fitnessScore);
                    State nextState = new State();
                    nextState.setTarget(newOne);
                    mutateAcceptHistory.add(nextState);
                    currentState = nextState;
                } else {
                    currentState.updateScore(actionString, DEAD_END);
                    mutateRejectHistory.add(newOne);
                }

            } else {
                mutateClass = Recover.recoverFromPath(mutateAcceptHistory.get(mutateAcceptHistory.size() - 1).getTarget());
                currentState.setTarget(mutateClass);
                currentState.updateScore(actionString, DEAD_END);
            }
        }

        Recover.recoverFromPath(mutateAcceptHistory.get(0).getTarget());
    }

}
