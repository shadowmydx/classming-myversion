package com.classming;

import com.classming.record.Recover;
import soot.jimple.Stmt;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClassmingEntry {

    public static void process(String className, int iterationCount, String[] args) throws IOException {
        MutateClass mutateClass = new MutateClass();
        Main.initial(args);
        mutateClass.initialize(className, args);
        List<MutateClass> mutateAcceptHistory = new ArrayList<>();
        List<MutateClass> mutateRejectHistory = new ArrayList<>();
        Random random = new Random();
        mutateAcceptHistory.add(mutateClass);
        for (int i = 0; i < iterationCount; i ++) {
            MutateClass newOne = mutateClass.iteration(); // sootclass has changed here for all objects.
            if (newOne != null) {
                MutateClass previousClass = mutateAcceptHistory.get(mutateAcceptHistory.size() - 1);
                MethodCounter current = newOne.getCurrentMethod();
                List<String> currentLiveCode = newOne.getMethodLiveCodeString(current.getSignature());
                List<String> originalCode = previousClass.getMethodOriginalStmtListString(current.getSignature());
                double covScore = calculateCovScore(newOne);
                double rand = random.nextDouble();
                System.out.println(covScore);
                double fitnessScore = fitness(calculateCovScore(mutateClass), covScore, originalCode.size());
                if(rand < fitnessScore) {
                    mutateAcceptHistory.add(newOne);
                    mutateClass = newOne;
                } else {
                    mutateRejectHistory.add(newOne);
                    mutateClass = Recover.recoverFromPath(mutateAcceptHistory.get(mutateAcceptHistory.size() - 1));
                }

            } else {
                mutateClass = Recover.recoverFromPath(mutateAcceptHistory.get(mutateAcceptHistory.size() - 1));
                System.out.println(mutateClass.getBackPath());
            }
        }

        Recover.recoverFromPath(mutateAcceptHistory.get(0));
    }

    private static double fitness(double previousCov, double currentCov, int total) {
        double result = Math.exp(0.08 * total * (previousCov - currentCov));
        return 1.0 < result ? 1.0 : result;
    }

    private static double calculateCovScore(MutateClass mutateClass) {
        MethodCounter current = mutateClass.getCurrentMethod();
        List<String> currentLiveCode = mutateClass.getMethodLiveCodeString(current.getSignature());
        List<String> originalCode = mutateClass.getMethodOriginalStmtListString(current.getSignature());
        return currentLiveCode.size() / (double)originalCode.size();
    }


    public static void main(String[] args) throws IOException {
        process("com.classming.Hello", 3, args);
    }



}
