package com.classming;

import soot.*;
import soot.jimple.Stmt;

import javax.naming.Name;
import java.io.IOException;
import java.util.*;


/**
 * This object refers to L(n). L(n+1) = L(n).deepcopy() + LBCMutation. deepcopy() activates the effects of mutation for L(n) by calling util method again.
 * L(n)'s effect is executed in deepcopy(). Key point: now we are in L(n) to generate L(n+1) while deep copy is to get effect of L(n)
 */
public class MutateClass {



    public SootClass getSootClass() {
        return sootClass;
    }

    public void initialize(String className, String[] args) throws IOException {
        this.activeArgs = args;
        this.className = className;
        this.sootClass = Main.loadTargetClass(className);
        initializeSootClass(null);
    }

    public void initializeSootClass(List<MethodCounter> previousMutationCounter) throws IOException {
        Main.outputClassFile(this.sootClass); // active inject class.
        for (SootMethod method : this.sootClass.getMethods()) {
            this.methodLiveBody.put(method.getSignature(), method.retrieveActiveBody());
        }
        this.mainLiveStmt = Main.getExecutedLiveInstructions(className, Main.MAIN_SIGN, activeArgs);
        this.liveMethod = Main.getLiveMethod(this.mainLiveStmt, this.sootClass.getMethods());
        int counter = 0;
        for (SootMethod method : this.liveMethod) {
//            methodOriginalQuery.put(method.getSignature(), Main.getAllStatementsSet(method)); // for tp selection: all stmts
            methodOriginalStmtList.addAll(Main.getAllStatementsList(method));
            methodMap.put(method.getSignature(), method);
            Set<String> usedStmt = Main.getExecutedLiveInstructions(className, method.getSignature(), activeArgs);
            methodLiveQuery.put(method.getSignature(), usedStmt);
            UsedStatementHelper.addClassMethodUsedStmt(className, method.getSignature(), usedStmt);
            methodLiveCode.put(method.getSignature(), Main.getActiveInstructions(usedStmt, className, method.getSignature(), activeArgs));
            int callCount = previousMutationCounter == null ? 1 : previousMutationCounter.get(counter ++).getCount();
            mutationCounter.add(new MethodCounter(method.getSignature(), callCount));
        }
    }

    public List<Stmt> getMethodLiveCode(String signature) {
        return this.methodLiveCode.get(signature);
    }

    public void reload() throws IOException {
        this.initialize(className, activeArgs);
    }

    public MutateClass deepCopy() throws IOException {
        MutateClass result = new MutateClass();
        result.setActiveArgs(activeArgs);
        result.setClassName(className);
        result.setSootClass(sootClass);
        result.initializeSootClass(mutationCounter);
        return result;
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
            index = mutationCounter.size() - 1;
        }
        MethodCounter method = this.mutationCounter.get(index);
        method.setCount(method.getCount() + 1);
        this.sortByPotential();
//        return method;
        return this.mutationCounter.get(2);
    }

    public int selectHookingPoint(String signature, int candidates) {
        int resultIndex = 0;
        List<Stmt> targetLiveCode = this.methodLiveCode.get(signature);
//        Body methodBody = this.methodLiveBody.get(signature);
//        UnitPatchingChain units = methodBody.getUnits();
        Random rand = new Random();
        int[] candidatesIndex = new int[candidates];
        for (int i = 0; i < candidatesIndex.length; i ++) {
            candidatesIndex[i] = rand.nextInt(targetLiveCode.size());
        }
        int maxSize = -1;
        for (int i = 0; i < candidatesIndex.length; i ++) {
            int defUseConflict = computeDefUseSizeByIndex(candidatesIndex[i], targetLiveCode);
            if (maxSize < defUseConflict) {
                maxSize = defUseConflict;
                resultIndex = candidatesIndex[i];
            }
        }
        return resultIndex;
    }

    public Stmt selectTargetPoints(String signature) {
        Random random = new Random();
        while (true) {
            int tpIndex = random.nextInt(this.methodOriginalStmtList.size() - 1);
            double rand = random.nextDouble();
            Stmt nextStmt = this.methodOriginalStmtList.get(tpIndex);
            if (!UsedStatementHelper.queryIfHasInstructionsAlready(className, signature, nextStmt.toString())) {
                return nextStmt;
            }
            Set<String> previousLive = this.methodLiveQuery.get(signature);
            if (!previousLive.contains(nextStmt.toString())) {
                if (rand <= 0.8) {
                    return nextStmt;
                }
            }
            rand = random.nextDouble();
            if (rand <= 0.2) {
                return nextStmt;
            }
        }

    }

    public int computeDefUseSizeByIndex(int split, List<Stmt> targetStmt) {
        Set<String> defInThisMethod = new HashSet<>();
        Set<String> beforeSet = new HashSet<>();
        Set<String> afterSet = new HashSet<>();

        for (Stmt stmt: targetStmt) {
            List<ValueBox> defVar = stmt.getDefBoxes();
            for (ValueBox box: defVar) {
                defInThisMethod.add(box.getValue().toString());
            }
        }
        liveCodeSetHelper(0, split + 1, defInThisMethod, beforeSet, targetStmt);
        liveCodeSetHelper(split + 1, targetStmt.size(), defInThisMethod, afterSet, targetStmt);
        Set<String> resultSet = new HashSet<>(beforeSet);
        resultSet.retainAll(afterSet);
        return resultSet.size();
    }

    private void liveCodeSetHelper(int start, int end, Set<String> dictionary, Set<String> target, List<Stmt> targetStmt) {
        for (int i = start; i < end; i ++) {
            Stmt stmt = targetStmt.get(i);
            List<ValueBox> defVar = stmt.getUseAndDefBoxes();
            for (ValueBox box: defVar) {
                if (dictionary.contains(box.getValue().toString())) {
                    target.add(box.getValue().toString());
                }
            }
        }
    }


    private static double epsilon = 0.05;

    public String[] getActiveArgs() {
        return activeArgs;
    }

    public void setActiveArgs(String[] activeArgs) {
        this.activeArgs = activeArgs;
    }

    private String[] activeArgs;

    public void setSootClass(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    private SootClass sootClass;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    private String className;
    private Set<String> mainLiveStmt;
    private List<MethodCounter> mutationCounter = new ArrayList<>();
//    private Map<String, Set<String>> methodOriginalQuery = new HashMap<>();
    private List<Stmt> methodOriginalStmtList = new ArrayList<>();
    private Map<String, Set<String>> methodLiveQuery = new HashMap<>();
    private Map<String, List<Stmt>> methodLiveCode = new HashMap<>();
    private Map<String, Body> methodLiveBody = new HashMap<>();
    private Map<String, SootMethod> methodMap = new HashMap<>();
    private List<SootMethod> liveMethod;

    public static void main(String[] args) throws IOException {
        MutateClass mutateClass = new MutateClass();
        Main.initial(args);
        mutateClass.initialize("com.classming.Hello", args);
        mutateClass.sortByPotential();
        MethodCounter counter = mutateClass.getMethodToMutate();
        List<Stmt> liveCode = mutateClass.getMethodLiveCode(counter.getSignature());
        String test = "<com.classming.Hello: void main(java.lang.String[])>";
        System.out.println(mutateClass.selectHookingPoint(test, 2));
        mutateClass.selectTargetPoints(test);
//        for (int i = 0; i < 100; i ++) {
//            mutateClass.getMethodToMutate();
//        }
        System.out.println("hello");
    }
}
