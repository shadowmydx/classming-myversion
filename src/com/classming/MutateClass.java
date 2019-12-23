package com.classming;

import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.io.IOException;
import java.util.*;

public class MutateClass {

    public class MethodCounter implements Comparable {

        public MethodCounter(String signature, int count) {
            this.signature = signature;
            this.count = count;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        private String signature;
        private Integer count;

        @Override
        public int compareTo(Object o) {
            return this.getCount().compareTo(((MethodCounter)o).getCount());
        }
    }

    public SootClass getSootClass() {
        return sootClass;
    }

    public void initialize(String className, String[] args) throws IOException {
        this.activeArgs = args;
        this.className = className;
        this.sootClass = Main.loadTargetClass(className);
        Main.outputClassFile(this.sootClass); // active inject class.
        this.mainLiveStmt = Main.getExecutedLiveInstructions(className, Main.MAIN_SIGN, args);
        this.liveMethod = Main.getLiveMethod(this.mainLiveStmt, this.sootClass.getMethods());
        for (SootMethod method : this.liveMethod) {
            methodMap.put(method.getSignature(), method);
            Set<String> usedStmt = Main.getExecutedLiveInstructions(className, method.getSignature(), args);
            methodLiveCode.put(method.getSignature(), Main.getActiveInstructions(usedStmt, className, method.getSignature(), args));
            mutationCounter.add(new MethodCounter(method.getSignature(), 1));
        }
    }

    public void reload() throws IOException {
        this.initialize(className, activeArgs);
    }

    public void sortByPotential() {
        this.mutationCounter.sort(new Comparator<MethodCounter>() {
            @Override
            public int compare(MethodCounter o1, MethodCounter o2) {
                return methodLiveCode.get(o1.getSignature()).size()/o1.getCount() - methodLiveCode.get(o2.getSignature()).size()/o2.getCount();
            }
        });
    }

    public static double realLog(double value, double base) {
        return Math.log(value) / Math.log(base);
    }

    public MethodCounter getMethodToMutate() {
        double rand = Math.random();
        int index = (int)(realLog(Math.pow(1 - rand, mutationCounter.size()), epsilon));
        if (index >= mutationCounter.size()) {
            System.out.println("SHOULD NOT!");
            return null;
        }
        MethodCounter method = this.mutationCounter.get(index);
        method.setCount(method.getCount() + 1);
        this.sortByPotential();
        return method;
    }

    private static double epsilon = 0.05;

    private String[] activeArgs;
    private SootClass sootClass;
    private String className;
    private Set<String> mainLiveStmt;
    private List<MethodCounter> mutationCounter = new ArrayList<>();
    private Map<String, List<Stmt>> methodLiveCode = new HashMap<>();
    private Map<String, SootMethod> methodMap = new HashMap<>();
    private List<SootMethod> liveMethod;

    public static void main(String[] args) throws IOException {
        MutateClass mutateClass = new MutateClass();
        Main.initial(args);
        mutateClass.initialize("com.classming.Hello", args);
        mutateClass.sortByPotential();
        for (int i = 0; i < 100; i ++) {
            mutateClass.getMethodToMutate();
        }
        System.out.println("hello");
    }
}
