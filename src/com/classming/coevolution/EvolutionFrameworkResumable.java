package com.classming.coevolution;

import com.classming.ClassmingEntry;
import com.classming.Main;
import com.classming.MethodCounter;
import com.classming.MutateClass;
import com.classming.Vector.LevenshteinDistance;
import com.classming.Vector.MathTool;
import com.classming.record.Recover;
import com.classming.rf.*;
import soot.G;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class EvolutionFrameworkResumable {
    private static final int DEAD_END = -1;
    public static final int POPULATION_LIMIT = 20;
    public static String cpSeparator = ":";  // classpath separator

    private static Action gotoAction = new GotoAction();
    private static Action lookupAction = new LookupAction();
    private static Action returnAction = new ReturnAction();

    EvolutionFrameworkResumable(){
        if(System.getProperties().getProperty("os.name").startsWith("Windows")){
            cpSeparator = ";";
        }
    }

    public static Map<String, Action> getActionContainer() {
        return actionContainer;
    }

    public static void setActionContainer(Map<String, Action> actionContainer) {
        EvolutionFrameworkResumable.actionContainer = actionContainer;
    }

    private static Map<String, Action> actionContainer = new HashMap<>();
    static {
        actionContainer.put(State.RETURN, returnAction);
        actionContainer.put(State.LOOK_UP, lookupAction);
        actionContainer.put(State.GOTO, gotoAction);
    }

    public void process(String className, int iterationLimit, String[] args, String classPath, String dependencies) throws IOException {
        int iterationLeft = readLeftIterationNum(classPath+"currentPopulation/", iterationLimit);
        if(iterationLeft <= 0)
            return;

        // redirect the ouput to the log file
        PrintStream newStream=new PrintStream("./"+className+(iterationLimit-iterationLeft)+".log");
        System.setOut(newStream);
        System.setErr(newStream);

        iterationLimit = iterationLeft;

        if(classPath != null && !classPath.equals("")){
            Main.setGenerated(classPath);
        }else{
            classPath = "";
        }
        if(dependencies != null && !dependencies.equals("")){
            Main.setDependencies(dependencies);
        }
        MutateClass.switchSelectStrategy();
        Main.initial(args);
        List<State> mutateRejectHistory = new ArrayList<>(); // once accpeted but get out
        List<Double> averageDistance = new ArrayList<>();
        Random random = new Random();

        List<State> mutateAcceptHistory = readCurrentPopulation(classPath+"currentPopulation/", classPath, className, args);
        if(mutateAcceptHistory.size() == 0){
            MutateClass mutateClass = new MutateClass();
            mutateClass.initialize(className, args, null);
            mutateClass.saveCurrentClass(); // in case 1st backtrack no backup
            State startState = new State();
            startState.setTarget(mutateClass);
            mutateAcceptHistory.add(startState);
        }
        int iterationCount = 0;
        while (iterationCount < iterationLimit) {
            int currentSize = mutateAcceptHistory.size();
            for (int j = 0; j < currentSize; j ++) {
                State current = mutateAcceptHistory.get(j);
                current.setTarget(Recover.recoverFromPath(current.getTarget()));
                String nextActionString = current.selectActionAndMutatedMethod();
                Action nextAction = EvolutionFrameworkResumable.getActionContainer().get(nextActionString);
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
                saveCurrentPopulation(mutateAcceptHistory, classPath+"currentPopulation/", iterationLimit-iterationCount);
                dumpAcceptPopulation(mutateAcceptHistory, className);
            }else{
                saveCurrentPopulation(mutateAcceptHistory, classPath+"currentPopulation/",  iterationLimit-iterationCount);
            }
        }

        ClusterTool.getEvoClusterData(mutateAcceptHistory, mutateRejectHistory);

        List<Double> totalScore = new ArrayList<>();
        try{
            System.out.println("Average distance is " + MathTool.mean(averageDistance));
            System.out.println("var is " + MathTool.standardDeviation(averageDistance));
            System.out.println("max is " + Collections.max(averageDistance));
        }catch (Exception e){
            e.printStackTrace();
        }
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

    public static List<State> readCurrentPopulation(String populationPath, String classPath, String className, String[] args) {
        List<State> list = new ArrayList<>();
        File file = new File(populationPath);
        String dstFilePath = classPath+className.replaceAll("[.]","/")+".class";
        File dstFile = new File(dstFilePath);
        if(!file.exists() || file.listFiles().length == 0){
            return list;
        }
        for(File f: file.listFiles()){
            if(!f.getName().endsWith(".state"))
                continue;
            if(dstFile.exists()){
                dstFile.delete();
            }
            System.out.println("Loading individual: "+f.getName());
            G.reset();  // important!!
            Main.initial(args);
            MutateClass mc = new MutateClass();
            State s = new State();
            Map<String, List<Double>> methodScores = new HashMap<>();
            try{
                FileReader fr = new FileReader(f.getPath());
                BufferedReader br = new BufferedReader(fr);
                String line = null;
                String backPath = br.readLine();
                mc.setBackPath(backPath);  // backpath
                File mcFile = new File(backPath);
                Files.copy(mcFile.toPath(), dstFile.toPath());
                mc.initialize(className, null, null);
                s.setTarget(mc);
                while((line = br.readLine())!=null){
                    String[] content = line.split("[;]");
                    List<Double> listDouble = new ArrayList<>();
                    for(int i = 1; i < content.length; i++){
                        listDouble.add(Double.parseDouble(content[i]));
                    }
                    methodScores.put(content[0], listDouble);
                }
                br.close();
                s.setMethodScores(methodScores);
                list.add(s);
            }catch (Exception e){
                e.printStackTrace();
            }

        }
        return list;
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

    public static int readLeftIterationNum(String populationPath, int iterationLimit){
        int leftIterationNum = iterationLimit;
        File infoFile = new File(populationPath + "population.info");
        if(!infoFile.exists())
            return iterationLimit;
        try {
            FileReader fr = new FileReader(infoFile.getPath());
            BufferedReader br = new BufferedReader(fr);
            String line = br.readLine();
            leftIterationNum = Integer.parseInt(line.split("[:]")[1]);
            System.out.println("Iteration left: "+ leftIterationNum);
        }catch (Exception e){
            e.printStackTrace();
        }
        return leftIterationNum;

    }

    public static void saveCurrentPopulation(List<State> stateList, String populationPath, int leftIterationNum){
        File file = new File(populationPath);
        if(!file.exists()){
            file.mkdir();
        }else{
            for(File f: file.listFiles()){
                f.delete();
            }
        }
        for (State s: stateList){
            saveState(s, populationPath);
        }
        String infoName = "population.info";
        File f = new File(populationPath + infoName);
        try {
            FileWriter fw = new FileWriter(f.getPath(), false);
            fw.write("Iteration Left:"+leftIterationNum);
            fw.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void saveState(State s, String directory){
//        ClassmingEntry.dumpSingleMutateClass(s.getTarget(), directory);
        String logName = s.getTarget().getBackPath().replace("./tmp/","").replace(".class",".state");
        File file = new File(directory+logName);
        try {
            FileWriter fw = new FileWriter(file.getPath(), false);
            fw.write(s.getTarget().getBackPath()+"\n");
            String line = "";
            for (String key : s.getMethodScores().keySet()) {
                line = key+";";
                List<Double> list = s.getMethodScores().get(key);
                for (double num:list){
                    line += num + ";";
                }
                fw.write(line + "\n");
            }
            fw.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        EvolutionFrameworkResumable fwk = new EvolutionFrameworkResumable();
//        fwk.process("com.classming.Hello", 1000, args, null, "");
        fwk.process("avrora.Main", 3000,
                new String[]{"-action=cfg","sootOutput/avrora-cvs-20091224/example.asm"},
                "./sootOutput/avrora-cvs-20091224/",null);
//        fwk.process("org.apache.batik.apps.rasterizer.Main", 400,null,
//                "./sootOutput/batik-all/",null);
        fwk.process("org.eclipse.core.runtime.adaptor.EclipseStarter", 18000,
                new String[]{"-debug"}, "./sootOutput/eclipse/", null);
        fwk.process("org.sunflow.Benchmark", 2000,
                new String[]{"-bench","2","256"},
                "./sootOutput/sunflow-0.07.2/",
                "dependencies/janino-2.5.15.jar");
        fwk.process("org.apache.fop.cli.Main", 3000,
                new String[]{"-xml","sootOutput/fop/name.xml","-xsl","sootOutput/fop/name2fo.xsl","-pdf","sootOutput/fop/name.pdf"},
                "./sootOutput/fop/",
                "dependencies/xmlgraphics-commons-1.3.1.jar" + cpSeparator +
                        "dependencies/commons-logging.jar" + cpSeparator +
                        "dependencies/avalon-framework-4.2.0.jar" + cpSeparator +
                        "dependencies/batik-all.jar" + cpSeparator +
                        "dependencies/commons-io-1.3.1.jar");
        fwk.process("org.python.util.jython", 6000,
                new String[]{"sootOutput/jython/hello.py"},
                "./sootOutput/jython/",
                "dependencies/guava-r07.jar" + cpSeparator +
                        "dependencies/constantine.jar" + cpSeparator +
                        "dependencies/jnr-posix.jar" + cpSeparator +
                        "dependencies/jaffl.jar" + cpSeparator +
                        "dependencies/jline-0.9.95-SNAPSHOT.jar" + cpSeparator +
                        "dependencies/antlr-3.1.3.jar" + cpSeparator +
                        "dependencies/asm-3.1.jar");
//        fwk.process("net.sourceforge.pmd.PMD", 2000,
//                new String[]{"sootOutput/pmd-4.2.5/Hello.java","text","unusedcode"},
//                "./sootOutput/pmd-4.2.5/",
//                "dependencies/jaxen-1.1.1.jar;" +
//                        "dependencies/asm-3.1.jar");  // pmd no accept

    }
}
