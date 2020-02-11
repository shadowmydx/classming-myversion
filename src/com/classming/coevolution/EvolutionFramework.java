package com.classming.coevolution;

import com.classming.ClassmingEntry;
import com.classming.Main;
import com.classming.MethodCounter;
import com.classming.MutateClass;
import com.classming.Vector.LevenshteinDistance;
import com.classming.record.Recover;
import com.classming.rf.*;

import java.io.IOException;
import java.util.*;

public class EvolutionFramework {
    private static final int DEAD_END = -1;
    private static final int POPULATION_LIMIT = 100;

    private static Action gotoAction = new GotoAction();
    private static Action lookupAction = new LookupAction();
    private static Action returnAction = new ReturnAction();


    public static Map<String, Action> getActionContainer() {
        return actionContainer;
    }

    public static void setActionContainer(Map<String, Action> actionContainer) {
        EvolutionFramework.actionContainer = actionContainer;
    }

    private static Map<String, Action> actionContainer = new HashMap<>();
    static {
        actionContainer.put(State.RETURN, returnAction);
        actionContainer.put(State.LOOK_UP, lookupAction);
        actionContainer.put(State.GOTO, gotoAction);
    }

    public void process(String className, int iterationLimit, String[] args, String classPath, String dependencies) throws IOException {
        if(classPath != null && !classPath.equals("")){
            Main.setGenerated(classPath);
        }
        if(dependencies != null && !dependencies.equals("")){
            Main.setDependencies(dependencies);
        }
        MutateClass mutateClass = new MutateClass();
        Main.initial(args);
        mutateClass.initialize(className, args);
        List<State> mutateAcceptHistory = new ArrayList<>();
        List<State> mutateRejectHistory = new ArrayList<>(); // once accpeted but get out
        List<Double> averageDistance = new ArrayList<>();
        Random random = new Random();
        State startState = new State();
        startState.setTarget(mutateClass);
        mutateClass.saveCurrentClass(); // in case 1st backtrack no backup
        mutateAcceptHistory.add(startState);
        int iterationCount = 0;
        while (iterationCount < iterationLimit) {
            int currentSize = mutateAcceptHistory.size();
            for (int j = 0; j < currentSize; j ++) {
                State current = mutateAcceptHistory.get(j);
                current.setTarget(Recover.recoverFromPath(current.getTarget()));
                String nextActionString = current.selectActionWithoutBacktrack();
                Action nextAction = EvolutionFramework.getActionContainer().get(nextActionString);
                State nextState = nextAction.proceedAction(current.getTarget(), mutateAcceptHistory);
                iterationCount ++;
                MutateClass newOne = nextState.getTarget();
                if (newOne != null) {
                    int totalSize = mutateAcceptHistory.size() + mutateRejectHistory.size();
                    System.out.println("Current size is : " + totalSize + ", iteration is :" + iterationCount);
                    MethodCounter currentCounter = newOne.getCurrentMethod();
                    int distance = LevenshteinDistance.computeLevenshteinDistance(current.getTarget().getClassPureInstructionFlow(), newOne.getClassPureInstructionFlow());
                    System.out.println("Distance is " + distance + " signature is " + currentCounter.getSignature());
                    ClassmingEntry.showListElement(newOne.getMethodLiveCodeString(currentCounter.getSignature()));
                    ClassmingEntry.showListElement(current.getTarget().getMethodLiveCodeString(currentCounter.getSignature()));
                    nextState.getTarget().saveCurrentClass();
                    mutateAcceptHistory.add(nextState);
                    current.updateScore(nextActionString, distance / 1.0);
                } else {
                    current.updateScore(nextActionString, DEAD_END);
                }
            }
            for (State state: mutateAcceptHistory) {
                state.setCoFitnessScore(Fitness.fitness(state, mutateAcceptHistory));
            }
            if (mutateAcceptHistory.size() > POPULATION_LIMIT) {
                mutateAcceptHistory.sort(new Comparator<State>() {
                    @Override
                    public int compare(State o1, State o2) {
                        double scoreOne = o1.getCoFitnessScore();
                        double scoreTwo = o2.getCoFitnessScore();
                        if (Math.abs(scoreOne - scoreTwo) < .00001) {
                            return 0;
                        }
                        return (o2.getCoFitnessScore() - o1.getCoFitnessScore()) > 0 ? 1 : -1;
                    }
                });
                for (int j = POPULATION_LIMIT; j < mutateAcceptHistory.size(); j ++) {
                    mutateRejectHistory.add(mutateAcceptHistory.get(j));
                }
                mutateAcceptHistory = mutateAcceptHistory.subList(0, POPULATION_LIMIT);
            }
        }
        for (State state: mutateAcceptHistory) {
            System.out.print(state.getCoFitnessScore() + " ");
        }
    }

    public static void main(String[] args) throws IOException {
        EvolutionFramework fwk = new EvolutionFramework();
        fwk.process("com.classming.Hello", 500, args, null, "");
    }

}
