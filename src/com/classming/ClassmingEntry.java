package com.classming;

import com.classming.record.Recover;
import soot.jimple.Stmt;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ClassmingEntry {

    public static void process(String className, int iterationCount, String[] args) throws IOException {
        MutateClass mutateClass = new MutateClass();
        Main.initial(args);
        mutateClass.initialize(className, args);
        List<MutateClass> mutateHistory = new ArrayList<>();
        mutateHistory.add(mutateClass);
        for (int i = 0; i < iterationCount; i ++) {
            MutateClass newOne = mutateClass.iteration();
            if (newOne != null) {
                MethodCounter current = newOne.getCurrentMethod();
                List<Stmt> currentLiveCode = newOne.getMethodLiveCode(current.getSignature());
                List<Stmt> originalCode = newOne.getMethodOriginalStmtList(current.getSignature());
                double covScore = calculateCovScore(currentLiveCode, originalCode);
                System.out.println(covScore);
                mutateHistory.add(newOne);
                mutateClass = newOne;
            } else {
                mutateClass = Recover.recoverFromPath(mutateHistory.get(mutateHistory.size() - 1));
                System.out.println(mutateClass.getBackPath());
            }
        }

        Recover.recoverFromPath(mutateHistory.get(mutateHistory.size() - 1));
    }

    private static double calculateCovScore(List<Stmt> currentLiveCode, List<Stmt> originalCode) {
        return currentLiveCode.size() / (double)originalCode.size();
    }


    public static void main(String[] args) throws IOException {
        process("com.classming.Hello", 10, args);
    }



}
