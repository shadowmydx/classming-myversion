package com.classming;

import com.classming.record.Recover;
import com.classming.rf.Tool;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JLookupSwitchStmt;
import soot.jimple.internal.JReturnStmt;
import soot.options.Options;

import javax.naming.Name;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;


/**
 * This object refers to L(n). L(n+1) = L(n).deepcopy() + LBCMutation. deepcopy() activates the effects of mutation for L(n) by calling util method again.
 * L(n)'s effect is executed in deepcopy(). Key point: now we are in L(n) to generate L(n+1) while deep copy is to get effect of L(n)
 */
public class MutateClass {

    private static int gotoVarCount = 1;
    private static int loopLimit = 5;
    private static boolean noBegin = false;
    private static boolean shouldRandom = false;
    private static String jvmOptions = "";
//    private static boolean wantReload = false;  // aim at avoid the bug on 1st line in initializeSootClass

//    public static void setWantReload(boolean wantReload) {
//        MutateClass.wantReload = wantReload;
//    }

    public static void setJvmOptions(String jvmOptions) {
        MutateClass.jvmOptions = jvmOptions;
    }

    public static void switchSelectStrategy() {
        shouldRandom = !shouldRandom;
    }


    public List<String> getClassPureInstructionFlow() {
        return classPureInstructionFlow;
    }

    public void setClassPureInstructionFlow(List<String> classPureInstructionFlow) {
        this.classPureInstructionFlow = classPureInstructionFlow;
    }

    private List<String> classPureInstructionFlow;

    private List<Double> methodDistribution = new ArrayList<>();

    public SootClass getSootClass() {
        return sootClass;
    }

    public void initialize(String className, String[] args, List<MethodCounter> previousMutationCounter, String jvmOptions) throws IOException {
        this.activeArgs = args;
        this.jvmOptions = jvmOptions;
        this.className = className;
        this.sootClass = Main.loadTargetClass(className);
        if(Main.forceResolveFailed){
            System.out.println("****************** ForceResolve Failed!! ******************");
            System.out.println("***************** Recover Initial Class!! *****************");
            previousMutationCounter = null;  // recover original class file
            Main.forceResolveFailed = false;
        }
        initializeSootClass(previousMutationCounter);
    }

    public List<String> getLiveMethodSignature() {
        List<String> result = new ArrayList<>();
        for (SootMethod method: this.liveMethod) {
            result.add(method.getSignature());
        }
        return result;
    }

    public void initializeSootClass(List<MethodCounter> previousMutationCounter) throws IOException {
//        if(!wantReload)
        Main.outputClassFile(this.sootClass); // active inject class.
        for (SootMethod method : this.sootClass.getMethods()) {
            this.methodLiveBody.put(method.getSignature(), method.retrieveActiveBody());
        }
        this.classPureInstructionFlow = Main.getPureMainInstructionsFlow(className, activeArgs, jvmOptions);
        Debug.debug(this, classPureInstructionFlow);
        this.mainLiveStmt = Main.getExecutedLiveInstructions(className, Main.MAIN_SIGN, activeArgs, jvmOptions);
        Set<String> classPureInstructionFlowSet= new HashSet<>();
        for(String s: classPureInstructionFlow){
            String[] elements = s.split("[*]+");
            String currentStmt = elements[3].trim();
            classPureInstructionFlowSet.add(currentStmt);
        }
        this.liveMethod = Main.getLiveMethod(classPureInstructionFlowSet, this.sootClass.getMethods());
        int counter = 0;
        for (SootMethod method : this.liveMethod) {
//            methodOriginalQuery.put(method.getSignature(), Main.getAllStatementsSet(method)); // for tp selection: all stmts

            methodOriginalStmtList.put(method.getSignature(), Main.getAllStatementsList(method));

            methodMap.put(method.getSignature(), method);
            Set<String> usedStmt = Main.getExecutedLiveInstructions(className, method.getSignature(), activeArgs, jvmOptions); // usedStmt is stdout string
            List<Stmt> liveStmt = Main.getActiveInstructions(usedStmt, this.sootClass, method.getSignature(), activeArgs);
            methodLiveQuery.put(method.getSignature(), changeListToSet(liveStmt));
            UsedStatementHelper.addClassMethodUsedStmt(className, method.getSignature(), usedStmt);
            methodLiveCode.put(method.getSignature(), liveStmt);
            int callCount = previousMutationCounter == null ? 1 : previousMutationCounter.get(counter++).getCount();
            mutationCounter.add(new MethodCounter(method.getSignature(), callCount));
        }

        transformStmtToString(methodOriginalStmtList, methodOriginalStmtStringList);
        transformStmtToStringAdvanced(methodLiveCode, methodLiveCodeString);
//        transformStmtToString(methodLiveCode, methodLiveCodeString); // potential bug here because doesn't map to stdout
    }

    private Set<String> changeListToSet(List target) {
        if (target == null) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (Object object: target) {
            result.add(object.toString());
        }
        return result;
    }

    public List<Stmt> getMethodLiveCode(String signature) {
        return this.methodLiveCode.get(signature);
    }

    public void reload() throws IOException {
        G.reset();
        Main.initial(activeArgs);
        SootClass newClass = Main.loadTargetClass(this.getClassName());
        this.setSootClass(newClass);
        List<MethodCounter> previousMutationCounter = this.mutationCounter;
        if(Main.forceResolveFailed){
            System.out.println("****************** ForceResolve Failed!! ******************");
            System.out.println("***************** Recover Initial Class!! *****************");
            previousMutationCounter = null;  // recover original class file
            Main.forceResolveFailed = false;
        }
//        setWantReload(true);
        this.initializeSootClass(previousMutationCounter);
//        setWantReload(false);
    }

    public MethodCounter getMethodByDistribution() {
        if (this.methodDistribution.size() == 0) {
            double totalInstructions = 0.0;
            for (String method: this.methodOriginalStmtList.keySet()) {
                totalInstructions += this.methodOriginalStmtList.get(method).size();
            }
            for (String method: this.methodOriginalStmtList.keySet()) {
                this.methodDistribution.add(this.methodOriginalStmtList.get(method).size() / totalInstructions);
            }
        }
        List<String> signatures = new ArrayList<>(this.methodOriginalStmtList.keySet());
        String currentMethod = (String)Tool.randomSelectionByDistribution(this.methodDistribution, signatures);
        MethodCounter counter = new MethodCounter(currentMethod, 0);
        counter.setSignature(currentMethod);
        return counter;
    }

    public MutateClass evoGotoIteration(boolean shouldSet) throws IOException {
        if (shouldSet) {
            MethodCounter current = this.getMethodByDistribution();
            this.setCurrentMethod(current);
        }
        this.saveCurrentClass(); // save current class
        try {
            this.gotoMutation(this.getCurrentMethod().getSignature()); // change current topology
            return this.deepCopy(this.getCurrentMethod().getSignature()); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public MutateClass evoSwitchIteration(boolean shouldSet) throws IOException {
        if (shouldSet) {
            MethodCounter current = this.getMethodByDistribution();
            this.setCurrentMethod(current);
        }
        this.saveCurrentClass(); // save current class
        try {
            this.lookUpSwitchMutation(this.getCurrentMethod().getSignature()); // change current topology
            return this.deepCopy(this.getCurrentMethod().getSignature()); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public MutateClass evoReturnIteration(boolean shouldSet) throws IOException {
        if (shouldSet) {
            MethodCounter current = this.getMethodByDistribution();
            this.setCurrentMethod(current);
        }
        this.saveCurrentClass(); // save current class
        try {
            this.returnMutation(this.getCurrentMethod().getSignature()); // change current topology
            return this.deepCopy(this.getCurrentMethod().getSignature()); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public MutateClass iteration() throws IOException {
        MethodCounter current = this.getMethodToMutate();
        this.setCurrentMethod(current);
        this.saveCurrentClass(); // save current class
        try {
            this.gotoMutation(current.getSignature()); // change current topology
            return this.deepCopy(current.getSignature()); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
//            UnitPatchingChain units = this.methodLiveBody.get(current.getSignature()).getUnits();
//            Iterator iter = units.snapshotIterator();
//            System.err.println("===============================================================");
//            while (iter.hasNext()) {
//                String stmt = iter.next().toString();
//                System.err.println(stmt);
//            }
//            System.err.println("===============================================================");
            return null;
        }
    }


    public MutateClass returnIteration() throws IOException {
        MethodCounter current = this.getMethodToMutate();
        this.setCurrentMethod(current);
        this.saveCurrentClass(); // save current class
        try {
            this.returnMutation(current.getSignature()); // change current topology
            return this.deepCopy(current.getSignature()); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
//            UnitPatchingChain units = this.methodLiveBody.get(current.getSignature()).getUnits();
//            Iterator iter = units.snapshotIterator();
//            System.err.println("===============================================================");
//            while (iter.hasNext()) {
//                String stmt = iter.next().toString();
//                System.err.println(stmt);
//            }
//            System.err.println("===============================================================");
            return null;
        }
    }


    public MutateClass lookUpSwitchIteration() throws IOException {
        MethodCounter current = this.getMethodToMutate();
        this.setCurrentMethod(current);
        this.saveCurrentClass(); // save current class
        try {
            this.lookUpSwitchMutation(current.getSignature()); // change current topology
            return this.deepCopy(current.getSignature()); // applied change to new class
        } catch (Exception e) {
            e.printStackTrace();
//            UnitPatchingChain units = this.methodLiveBody.get(current.getSignature()).getUnits();
//            Iterator iter = units.snapshotIterator();
//            System.err.println("===============================================================");
//            while (iter.hasNext()) {
//                String stmt = iter.next().toString();
//                System.err.println(stmt);
//            }
//            System.err.println("===============================================================");
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
        result.setJvmOptions(jvmOptions);
        result.setClassName(className);
        result.setSootClass(sootClass);
//        result.setBackPath(this.getBackPath());
        result.setCurrentMethod(this.getCurrentMethod());  // current method can be only changed in iteration()

//        File source = new File(this.backPath);
//        String destName = SourceLocator.v().getFileNameFor(result.sootClass, Options.output_format_class);
//        destName = destName.replace("sootOutput"+File.separator, Main.getGenerated());
//        File dest = new File(destName);
//        Recover.copy(source, dest);

//        setWantReload(true);
        result.initializeSootClass(mutationCounter);
//        setWantReload(false);
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
                return methodLiveCode.get(o2.getSignature()).size() / o2.getCount() - methodLiveCode.get(o1.getSignature()).size() / o1.getCount();
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
        if (shouldRandom) {
            return rand.nextInt(targetLiveCode.size());
        }
        int[] candidatesIndex = new int[candidates];
        for (int i = 0; i < candidatesIndex.length; i++) {
            candidatesIndex[i] = rand.nextInt(targetLiveCode.size());
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
            if (shouldRandom) {
                return nextStmt;
            }
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
        System.out.println("one round start in goto.================================================================================");
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
        Value rightValue = IntConstant.v(loopLimit);
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, rightValue);
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1));
        ConditionExpr cond = Jimple.v().newGeExpr(newVar, IntConstant.v(0));
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub);
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, nop);

//        Iterator<Unit> iter = units.snapshotIterator();
//        Stmt firstStmt = (Stmt)iter.next();
        units.insertBefore(assign, getTargetUnit(units, liveCode.get(0)));
        units.insertBeforeNoRedirect(nop, getTargetUnit(units, targetPoint));
        Stmt printStmt = (Stmt)units.getSuccOf(getTargetUnit(units, liveCode.get(hookingPoint)));
        units.insertAfter(ifGoto, printStmt);
        units.insertAfter(substmt, printStmt);
    }
    
    public void returnMutation(String signature) throws IOException {
        System.out.println("one round start in return.================================================================================");
        List<Stmt> liveCode = methodLiveCode.get(signature);
        int hookingPoint = this.selectHookingPoint(signature, 2);
        Body body = this.methodLiveBody.get(signature);
        UnitPatchingChain units = body.getUnits();
        Stmt printStmt = (Stmt)units.getSuccOf(getTargetUnit(units, liveCode.get(hookingPoint)));

        Stmt targetReturnStmt = getReturnStmt(signature);
        Stmt nop = Jimple.v().newNopStmt();
        // create new variable and stmts
        Local newVar = Jimple.v().newLocal("_M" + (gotoVarCount++), IntType.v());
        body.getLocals().add(newVar);
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, IntConstant.v(1)); // _M = 1
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1)); // _M-1
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub); // _M = _M-1
        ConditionExpr cond = Jimple.v().newLeExpr(newVar, IntConstant.v(0)); // if _M <= 0
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, nop); // if _M <= 0 goto nop
        // insert stmts
        units.insertBefore(assign, getTargetUnit(units, liveCode.get(0)));
        units.insertAfter(ifGoto, printStmt);
        units.insertAfter(substmt, printStmt);

        // check if has return in live code.
        for(Unit unit: units){
            if (unit.toString().equals(targetReturnStmt.toString())){
                units.insertBeforeNoRedirect(nop, unit);
                return;
            }
        }
        // create return stmt
        units.insertAfter(targetReturnStmt, units.getLast());
        units.insertBeforeNoRedirect(nop, targetReturnStmt);
    }

    // Return the target return statement
    public Stmt getReturnStmt(String signature){
        List<String> originStmtString = methodOriginalStmtStringList.get(signature);
        List<Stmt> originStmt = methodOriginalStmtList.get(signature);
        List<Stmt> foundReturnStmt = new ArrayList();
        // find all return stmt in this method
        for (int i = 0; i < originStmtString.size(); i++){
            String stmtString = originStmtString.get(i);
            if(stmtString.contains("return")){
                // the return stmt can be "if i1 != 0 goto return 0"
                if(stmtString.contains("goto")){
                    JIfStmt ifReturnStmt = (JIfStmt)originStmt.get(i);
                    Stmt returnStmt = (Stmt)ifReturnStmt.getTargetBox().getUnit();
                    foundReturnStmt.add(returnStmt);
                }else{
                    Stmt returnStmt = originStmt.get(i);
                    foundReturnStmt.add(returnStmt);
                }
            }
        }
        // create return stmt if it has multiple return stmts
        if(foundReturnStmt.size() == 1){
            return foundReturnStmt.get(0);
        }else {
            if(signature.contains("void")){
                return Jimple.v().newReturnVoidStmt();
            }else{
                return Jimple.v().newReturnStmt(NullConstant.v());
            }
        }
    }

    public void lookUpSwitchMutation(String signature) throws IOException {
        System.out.println("one round start in lookup.================================================================================");
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
        int gotoVarCountCopy = loopLimit;  // value of _M
        for (int i = 0; i < caseNum; i++){
            lookUpValues.add(IntConstant.v(--gotoVarCountCopy));
            Stmt tempTargetPoint = selectTargetPoints(signature);
            int selectTimes = 0;
            while(selectedTargetPoints.contains(tempTargetPoint)){  // make sure target is different
                if (selectTimes>=5){
                    break;
                }
                tempTargetPoint = selectTargetPoints(signature);
                selectTimes++;
            }
            selectedTargetPoints.add(tempTargetPoint);
            Stmt nop = Jimple.v().newNopStmt();
            units.insertBeforeNoRedirect(nop, getTargetUnit(units, tempTargetPoint));
            labels.add(nop);
        }
        Stmt defaultTargetPoint = selectTargetPoints(signature);
        int selectTimes = 0;
        while(selectedTargetPoints.contains(defaultTargetPoint)){  // make sure target is different
            if (selectTimes>=5){
                break;
            }
            defaultTargetPoint = selectTargetPoints(signature);
            selectTimes++;
        }
        Stmt defaultNop = Jimple.v().newNopStmt();
        units.insertBeforeNoRedirect(defaultNop, getTargetUnit(units, defaultTargetPoint));
        JLookupSwitchStmt switchStmt = new JLookupSwitchStmt(newVar, lookUpValues, labels, defaultNop);

        Stmt skipSwitch = Jimple.v().newNopStmt();
        Value rightValue = IntConstant.v(loopLimit);
        AssignStmt assign = Jimple.v().newAssignStmt(newVar, rightValue);
        SubExpr sub = Jimple.v().newSubExpr(newVar, IntConstant.v(1));
        ConditionExpr cond = Jimple.v().newLeExpr(newVar, IntConstant.v(0));  // <= then skip switch
        AssignStmt substmt = Jimple.v().newAssignStmt(newVar, sub);
        IfStmt ifGoto = Jimple.v().newIfStmt(cond, skipSwitch);

        units.insertBefore(assign, getTargetUnit(units, getTargetUnit(units, liveCode.get(0))));
        Stmt printStmt = (Stmt)units.getSuccOf(getTargetUnit(units, liveCode.get(hookingPoint)));
        units.insertAfter(skipSwitch, printStmt);
        units.insertAfter(switchStmt, printStmt);
        units.insertAfter(ifGoto, printStmt);
        units.insertAfter(substmt, printStmt);
    }

    private Unit getTargetUnit(UnitPatchingChain units, Unit target){
        Iterator<Unit> iter = units.snapshotIterator();
        Map<String, String> mapping = new HashMap<>();
        while (iter.hasNext()) {
            Stmt current = (Stmt)iter.next();
            if (current.toString().equals(target.toString())) { // because soot will rename variable
                return current;
            }
        }
        System.err.println("Can not find insert point: " + target.toString());
        return target;
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

    public static void transformStmtToStringAdvanced(Map<String, List<Stmt>> from, Map<String, List<String>> to) {
        for (String key: from.keySet()) {
            List<Stmt> current = from.get(key);
            List<String> currentString = new ArrayList<>();
            for (Stmt stmt: current) {
                currentString.add(UsedStatementHelper.getMappingStdoutStmtString(key, stmt.toString()));
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

    public List<MethodCounter> getMutationCounter() {
        return mutationCounter;
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
        mutateClass.initialize("com.classming.Hello", args, null,"");
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
