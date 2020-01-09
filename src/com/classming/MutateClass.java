package com.classming;

import soot.*;
import soot.jimple.*;

import javax.naming.Name;
import java.io.IOException;
import java.util.*;


/**
 * This object refers to L(n). L(n+1) = L(n).deepcopy() + LBCMutation. deepcopy() activates the effects of mutation for L(n) by calling util method again.
 * L(n)'s effect is executed in deepcopy(). Key point: now we are in L(n) to generate L(n+1) while deep copy is to get effect of L(n)
 */
public class MutateClass {

    private static int gotoVarCount = 1;


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
            methodOriginalStmtList.put(method.getSignature(), Main.getAllStatementsList(method));
            methodMap.put(method.getSignature(), method);
            Set<String> usedStmt = Main.getExecutedLiveInstructions(className, method.getSignature(), activeArgs);
            methodLiveQuery.put(method.getSignature(), usedStmt);
            UsedStatementHelper.addClassMethodUsedStmt(className, method.getSignature(), usedStmt);
            methodLiveCode.put(method.getSignature(), Main.getActiveInstructions(usedStmt, className, method.getSignature(), activeArgs));
            int callCount = previousMutationCounter == null ? 1 : previousMutationCounter.get(counter++).getCount();
            mutationCounter.add(new MethodCounter(method.getSignature(), callCount));
        }
        transformStmtToString(methodOriginalStmtList, methodOriginalStmtStringList);
        transformStmtToString(methodLiveCode, methodLiveCodeString);
    }

    public List<Stmt> getMethodLiveCode(String signature) {
        return this.methodLiveCode.get(signature);
    }

    public void reload() throws IOException {
        G.reset();
        Main.initial(activeArgs);
        SootClass newClass = Main.loadTargetClass(this.getClassName());
        this.setSootClass(newClass);
        this.initializeSootClass(this.mutationCounter);
    }

    public MutateClass iteration() throws IOException {
        MethodCounter current = this.getMethodToMutate();
        this.setCurrentMethod(current);
        try {
            this.saveCurrentClass(); // save current class
            this.gotoMutation(current.getSignature()); // change current topology
            return this.deepCopy(current.getSignature()); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
            UnitPatchingChain units = this.methodLiveBody.get(current.getSignature()).getUnits();
            Iterator iter = units.snapshotIterator();
            System.err.println("===============================================================");
            while (iter.hasNext()) {
                String stmt = iter.next().toString();
//            if (!stmt.contains("**** Executed Line")) {
                System.err.println(stmt);
//            }
            }
            System.err.println("===============================================================");
            return null;
        }
    }

    public void saveCurrentClass() throws IOException {
        String path = Main.temporaryOutput(sootClass, "./tmp", System.currentTimeMillis() + ".");
        this.setBackPath(path);
    }

    public MutateClass deepCopy(String signature) throws IOException {

        MutateClass result = new MutateClass();
        result.setActiveArgs(activeArgs);
        result.setClassName(className);
        result.setSootClass(sootClass);
//        result.setBackPath(this.getBackPath());
        result.setCurrentMethod(this.getCurrentMethod());  // current method can be only changed in iteration()
        result.initializeSootClass(mutationCounter);
        if (result.mainLiveStmt.size() == 0|| result.getMethodLiveCode(result.getCurrentMethod().getSignature()).size() == 0) {
            Main.temporaryOutput(result.getSootClass(), "./nolivecode/", System.currentTimeMillis() + ".");
            return null; // no live code.
        }
        return result;


    }

    public void sortByPotential() {
        this.mutationCounter.sort(new Comparator<MethodCounter>() {
            @Override
            public int compare(MethodCounter o1, MethodCounter o2) {
                return methodLiveCode.get(o1.getSignature()).size() / o1.getCount() - methodLiveCode.get(o2.getSignature()).size() / o2.getCount();
            }
        });
    }

    public static double realLog(double value, double base) {
        return Math.log(value) / Math.log(base);
    }

    public MethodCounter getMethodToMutate() {
        double rand = Math.random();
        int index = (int) (realLog(Math.pow(1 - rand, mutationCounter.size()), epsilon));
        if (index >= mutationCounter.size()) {
            index = mutationCounter.size() - 1;
        }
        MethodCounter method = this.mutationCounter.get(index);
        method.setCount(method.getCount() + 1);
        this.sortByPotential();
//        return method;
        return method;
    }

    /*
     **  insert before
     */
    public int selectHookingPoint(String signature, int candidates) {
        int resultIndex = 0;
        List<Stmt> targetLiveCode = this.methodLiveCode.get(signature);
//        Body methodBody = this.methodLiveBody.get(signature);
//        UnitPatchingChain units = methodBody.getUnits();
        Random rand = new Random();
        int[] candidatesIndex = new int[candidates];
        for (int i = 0; i < candidatesIndex.length; i++) {
            candidatesIndex[i] = rand.nextInt(targetLiveCode.size() - 1) + 1;
        }
        int maxSize = -1;
        for (int i = 0; i < candidatesIndex.length; i++) {
            int defUseConflict = computeDefUseSizeByIndex(candidatesIndex[i], targetLiveCode);
            if (maxSize < defUseConflict) {
                maxSize = defUseConflict;
                resultIndex = candidatesIndex[i];
            }
        }
        return resultIndex;
    }

    /*
     ** insert before
     */
    public Stmt selectTargetPoints(String signature) {
        Random random = new Random();
        while (true) {
            int tpIndex = random.nextInt(this.methodOriginalStmtList.get(signature).size() - 1) + 1;
            double rand = random.nextDouble();
            Stmt nextStmt = this.methodOriginalStmtList.get(signature).get(tpIndex);
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

        for (Stmt stmt : targetStmt) {
            List<ValueBox> defVar = stmt.getDefBoxes();
            for (ValueBox box : defVar) {
                defInThisMethod.add(box.getValue().toString());
            }
        }
        liveCodeSetHelper(0, split + 1, defInThisMethod, beforeSet, targetStmt);
        liveCodeSetHelper(split + 1, targetStmt.size(), defInThisMethod, afterSet, targetStmt);
        Set<String> resultSet = new HashSet<>(beforeSet);
        resultSet.retainAll(afterSet);
        return resultSet.size();
    }

    public void gotoMutation(String signature) throws IOException {
        List<Stmt> liveCode = methodLiveCode.get(signature);
        int hookingPoint = this.selectHookingPoint(signature, 2);
        Stmt targetPoint = selectTargetPoints(signature);
//        System.out.println(targetPoint);
//        System.out.println(liveCode.get(hookingPoint));
        Stmt nop = Jimple.v().newNopStmt();
        Body body = this.methodLiveBody.get(signature);
        UnitPatchingChain units = body.getUnits();
        Local newVar = Jimple.v().newLocal("_M" + (gotoVarCount++), IntType.v());
        body.getLocals().add(newVar);
        Value rightValue = IntConstant.v(100);
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, rightValue);
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1));
        ConditionExpr cond = Jimple.v().newGeExpr(newVar, IntConstant.v(0));
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub);
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, nop);

        units.insertAfter(assign, liveCode.get(0));
        units.insertBefore(nop, targetPoint);
        units.insertBefore(substmt, liveCode.get(hookingPoint));
        units.insertBefore(ifGoto, liveCode.get(hookingPoint));

        System.out.println("one round.================================================================================");


    }
    
    public void returnMutation(String signature) throws IOException {
        List<Stmt> liveCode = methodLiveCode.get(signature);
        int hookingPoint = this.selectHookingPoint(signature, 2);
        Body body = this.methodLiveBody.get(signature);
        UnitPatchingChain units = body.getUnits();
        ReturnStmt returnStmt = Jimple.v().newReturnStmt(NullConstant.v());
        units.insertBefore(returnStmt, liveCode.get(hookingPoint));
    }

    public void lookUpSwitchMutation(String signature) throws IOException {
        List<Stmt> liveCode = methodLiveCode.get(signature);
        int hookingPoint = this.selectHookingPoint(signature, 2);
//        Stmt targetPoint = selectTargetPoints(signature);
//        System.out.println(targetPoint);
//        System.out.println(liveCode.get(hookingPoint));

        Body body = this.methodLiveBody.get(signature);
        UnitPatchingChain units = body.getUnits();
        Local newVar = Jimple.v().newLocal("_M" + (gotoVarCount++), IntType.v());
        body.getLocals().add(newVar);

        //  Setting the switch statement
        Random rand = new Random();
        int caseNum = rand.nextInt(3) + 1;  // 1~3 cases
        List<IntConstant> lookUpValues = new ArrayList<>();
        List<Stmt> labels = new ArrayList<>();  // nops
        List<Stmt> selectedTargetPoints = new ArrayList<>();  // targets for lookUp values
        int gotoVarCountCopy = gotoVarCount - 1;  // value of _M
        for (int i = 0; i < caseNum; i++){
            lookUpValues.add(IntConstant.v(--gotoVarCountCopy));
            Stmt tempTargetPoint = selectTargetPoints(signature);
            while(selectedTargetPoints.contains(tempTargetPoint)){  // make sure target is different
                tempTargetPoint = selectTargetPoints(signature);
            }
            selectedTargetPoints.add(tempTargetPoint);
            Stmt nop = Jimple.v().newNopStmt();
            units.insertBefore(nop, tempTargetPoint);
            labels.add(nop);
        }
        Stmt defaultTargetPoint = selectTargetPoints(signature);
        while(selectedTargetPoints.contains(defaultTargetPoint)){  // make sure target is different
            defaultTargetPoint = selectTargetPoints(signature);
        }
        Stmt defaultNop = Jimple.v().newNopStmt();
        units.insertBefore(defaultNop, defaultTargetPoint);
        JLookupSwitchStmt switchStmt = new JLookupSwitchStmt(newVar, lookUpValues, labels, defaultNop);


        Stmt skipSwitch = Jimple.v().newNopStmt();
        Value rightValue = IntConstant.v(100);
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, rightValue);
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1));
        ConditionExpr cond = Jimple.v().newLeExpr(newVar, IntConstant.v(0));  // <= then skip switch
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub);
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, skipSwitch);

        units.insertAfter(assign, liveCode.get(0));
        units.insertBefore(substmt, liveCode.get(hookingPoint));
        units.insertBefore(ifGoto, liveCode.get(hookingPoint));
        units.insertBefore(switchStmt, liveCode.get(hookingPoint));
        units.insertBefore(skipSwitch, liveCode.get(hookingPoint));

        System.out.println("one round.================================================================================");
    }

    private void liveCodeSetHelper(int start, int end, Set<String> dictionary, Set<String> target, List<Stmt> targetStmt) {
        for (int i = start; i < end; i++) {
            Stmt stmt = targetStmt.get(i);
            List<ValueBox> defVar = stmt.getUseAndDefBoxes();
            for (ValueBox box : defVar) {
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

    public static void transformStmtToString(Map<String, List<Stmt>> from, Map<String, List<String>> to) {
        for (String key: from.keySet()) {
            List<Stmt> current = from.get(key);
            List<String> currentString = new ArrayList<>();
            for (Stmt stmt: current) {
                currentString.add(stmt.toString());
            }
            to.put(key, currentString);
        }
    }

    public void setClassName(String className) {
        this.className = className;
    }

    private String className;
    private Set<String> mainLiveStmt;
    private List<MethodCounter> mutationCounter = new ArrayList<>();
    //    private Map<String, Set<String>> methodOriginalQuery = new HashMap<>();
    private Map<String, List<Stmt>> methodOriginalStmtList = new HashMap<>();
    private Map<String, List<String>> methodOriginalStmtStringList = new HashMap<>();
    private Map<String, Set<String>> methodLiveQuery = new HashMap<>();
    private Map<String, List<Stmt>> methodLiveCode = new HashMap<>();
    private Map<String, List<String>> methodLiveCodeString = new HashMap<>();
    private Map<String, Body> methodLiveBody = new HashMap<>();
    private Map<String, SootMethod> methodMap = new HashMap<>();
    private List<SootMethod> liveMethod;
    private String backPath; // record current mutatant path
    private double covScore;


    public double getCovScore() {
        return covScore;
    }

    public void setCovScore(double covScore) {
        this.covScore = covScore;
    }





    public String getBackPath() {
        return backPath;
    }

    public void setBackPath(String backPath) {
        this.backPath = backPath;
    }


    public List<Stmt> getMethodOriginalStmtList(String signature) {
        return methodOriginalStmtList.get(signature);
    }

    public List<String> getMethodOriginalStmtListString(String signature) {
        return methodOriginalStmtStringList.get(signature);
    }

    public MethodCounter getCurrentMethod() {
        return currentMethod;
    }

    public void setCurrentMethod(MethodCounter currentMethod) {
        this.currentMethod = currentMethod;
    }

    private MethodCounter currentMethod;


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
        for (int i = 0; i < 100; i++) {
            MutateClass newOne = mutateClass.iteration();
        }
//        newOne.saveCurrentClass();
//        for (int i = 0; i < 100; i ++) {
//            mutateClass.getMethodToMutate();
//        }
        System.out.println("hello");
    }

    public List<String> getMethodLiveCodeString(String signature) {
        return this.methodLiveCodeString.get(signature);
    }
}
