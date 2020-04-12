package com.classming.coevolution;

import com.classming.ClassmingEntry;
import com.classming.Main;
import com.classming.MethodCounter;
import com.classming.MutateClass;
import com.classming.Vector.LevenshteinDistance;
import com.classming.Vector.MathTool;
import com.classming.record.Recover;
import com.classming.rf.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class EvolutionFramework {
    private static final int DEAD_END = -1;
    public static final int POPULATION_LIMIT = 20;

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

    public void process(String className, int iterationLimit, String[] args, String classPath, String dependencies, String jvmOptions) throws IOException {
//        // redirect the ouput to the log file
//        PrintStream newStream=new PrintStream("./"+className+".log");
//        System.setOut(newStream);
//        System.setErr(newStream);

        if(classPath != null && !classPath.equals("")){
            Main.setGenerated(classPath);
        }
        if(dependencies != null && !dependencies.equals("")){
            Main.setDependencies(dependencies);
        }
        MutateClass.switchSelectStrategy();
        MutateClass mutateClass = new MutateClass();
        Main.initial(args);
        mutateClass.initialize(className, args, null, jvmOptions);
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
                String nextActionString = current.selectActionAndMutatedMethod();
                Action nextAction = EvolutionFramework.getActionContainer().get(nextActionString);
                State nextState = nextAction.proceedAction(current.getTarget(), mutateAcceptHistory);
                iterationCount ++;
                MutateClass newOne = nextState.getTarget();
                if (newOne != null) {
                    int totalSize = mutateAcceptHistory.size() + mutateRejectHistory.size();
                    System.out.println("Current size is : " + totalSize + ", iteration is :" + iterationCount + ", average distance is " + MathTool.mean(averageDistance));
                    MethodCounter currentCounter = newOne.getCurrentMethod();
                    int distance = LevenshteinDistance.computeLevenshteinDistance(current.getTarget().getClassPureInstructionFlow(), newOne.getClassPureInstructionFlow());
                    averageDistance.add(distance / 1.0);
                    System.out.println("Distance is " + distance + " signature is " + currentCounter.getSignature());
                    ClassmingEntry.showListElement(newOne.getMethodLiveCodeString(currentCounter.getSignature()));
                    ClassmingEntry.showListElement(current.getTarget().getMethodLiveCodeString(currentCounter.getSignature()));
                    nextState.getTarget().saveCurrentClass();
                    mutateAcceptHistory.add(nextState);
                    current.updateMethodScore(nextActionString, distance / 1.0);
                } else {
                    current.updateMethodScore(nextActionString, DEAD_END);
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
                    ClassmingEntry.dumpSingleMutateClass(mutateAcceptHistory.get(j).getTarget(), "./RejectHistory/");
                }
                mutateAcceptHistory = mutateAcceptHistory.subList(0, POPULATION_LIMIT);
                dumpAcceptPopulation(mutateAcceptHistory, className);
            }
        }

        ClusterTool.getEvoClusterData(mutateAcceptHistory, mutateRejectHistory);

        List<Double> totalScore = new ArrayList<>();
        System.out.println("Average distance is " + MathTool.mean(averageDistance));
        System.out.println("var is " + MathTool.standardDeviation(averageDistance));
        System.out.println("max is " + Collections.max(averageDistance));
        for (State state: mutateAcceptHistory) {
            System.out.print(state.getCoFitnessScore() + " ");
            totalScore.add(state.getCoFitnessScore());
        }
        System.out.println();
        System.out.println("Basic pattern average: " + MathTool.mean(totalScore));
        mutateRejectHistory.addAll(mutateAcceptHistory);
        for (State state: mutateRejectHistory) {
            state.setCoFitnessScore(Fitness.fitness(state, mutateRejectHistory));
            totalScore.add(state.getCoFitnessScore());
        }
        System.out.println("Total average:" + MathTool.mean(totalScore));
        System.out.println();
        System.out.println(MathTool.mean(totalScore));
    }

    // synchronize the population to AcceptHistory directory
    public static void dumpAcceptPopulation(List<State> stateList, String className){
        File acc = new File("./AcceptHistory/");
        for(File f: acc.listFiles()){
            if(f.getName().contains(className)){
                f.delete();
            }
        }
        for(State s: stateList){
            ClassmingEntry.dumpSingleMutateClass(s.getTarget(), "./AcceptHistory/");
        }
    }

    public static void main(String[] args) throws IOException {
        EvolutionFramework fwk = new EvolutionFramework();
        Main.setJunitCommand("D:\\NEW-RESEARCH\\junit-4.12.jar", "D:\\NEW-RESEARCH\\hamcrest-core-1.3.jar", "com.classming.HelloTest", "");
        fwk.process("com.classming.Hello", 1000, args, null, "", "");
//        fwk.process("avrora.Main", 783,
//                new String[]{"-action=cfg","sootOutput/avrora-cvs-20091224/example.asm"},
//                "./sootOutput/avrora-cvs-20091224/",null);
//        fwk.process("org.apache.batik.apps.rasterizer.Main", 400,null,
//                "./sootOutput/batik-all/",null);
//        fwk.process("org.eclipse.core.runtime.adaptor.EclipseStarter", 2000,
//                new String[]{"-debug"}, "./sootOutput/eclipse/", null);
//        fwk.process("org.apache.fop.cli.Main", 2000,
//                new String[]{"-xml","sootOutput/fop/name.xml","-xsl","sootOutput/fop/name2fo.xsl","-pdf","sootOutput/fop/name.pdf"},
//                "./sootOutput/fop/",
//                "dependencies/xmlgraphics-commons-1.3.1.jar;" +
//                        "dependencies/commons-logging.jar;" +
//                        "dependencies/avalon-framework-4.2.0.jar;" +
//                        "dependencies/batik-all.jar;" +
//                        "dependencies/commons-io-1.3.1.jar");
//        fwk.process("org.python.util.jython", 2000,
//                new String[]{"sootOutput/jython/hello.py"},
//                "./sootOutput/jython/",
//                "dependencies/guava-r07.jar;" +
//                        "dependencies/constantine.jar;" +
//                        "dependencies/jnr-posix.jar;" +
//                        "dependencies/jaffl.jar;" +
//                        "dependencies/jline-0.9.95-SNAPSHOT.jar;" +
//                        "dependencies/antlr-3.1.3.jar;" +
//                        "dependencies/asm-3.1.jar");
//        fwk.process("net.sourceforge.pmd.PMD", 2000,
//                new String[]{"sootOutput/pmd-4.2.5/Hello.java","text","unusedcode"},
//                "./sootOutput/pmd-4.2.5/",
//                "dependencies/jaxen-1.1.1.jar;" +
//                        "dependencies/asm-3.1.jar");  // pmd no accept
//        fwk.process("org.sunflow.Benchmark", 51,
//                new String[]{"-bench","2","256"},
//                "./sootOutput/sunflow-0.07.2/",
//                "dependencies/janino-2.5.15.jar");
    }

}
