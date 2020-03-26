package com.classming;

import com.classming.Vector.LevenshteinDistance;
import com.classming.Vector.MathTool;
import com.classming.coevolution.ClusterTool;
import com.classming.coevolution.EvolutionFramework;
import com.classming.coevolution.Fitness;
import com.classming.record.Recover;
import com.classming.rf.State;
import soot.G;

import java.io.*;
import java.nio.file.Files;
import java.util.*;


public class ClassmingEntryResumable {
    public static String cpSeparator = ":";  // classpath separator
    public static int leftIterationNumReaded = Integer.MAX_VALUE;

    public static MutateClass randomMutation(MutateClass target) throws IOException {
        Random random = new Random();
        int randomAction = random.nextInt(3);
        switch (randomAction) {
            case 0:
                return target.iteration();
            case 1:
                return target.lookUpSwitchIteration();
            case 2:
                return target.returnIteration();
        }
        return null;
    }

    public static void process(String className, int iterationCount, String[] args, String classPath, String dependencies, String jvmOptions) throws IOException {
        List<MethodCounter> mc = readMutationCounter(classPath);
        if(leftIterationNumReaded <= 0)
            return;
        int startIteration = Math.max(iterationCount-leftIterationNumReaded, 0);
        // redirect the ouput to the log file
        PrintStream newStream=new PrintStream("./"+className+startIteration+".log");
        System.setOut(newStream);
        System.setErr(newStream);

        if(leftIterationNumReaded < iterationCount)
            iterationCount = leftIterationNumReaded;
        if(classPath!=null && !classPath.equals("")){
            Main.setGenerated(classPath);
        }
        if(dependencies!=null && !dependencies.equals("")){
            Main.setDependencies(dependencies);
        }
        MutateClass mutateClass = new MutateClass();
        Main.initial(args);
        mutateClass.initialize(className, args, mc, jvmOptions);
        List<MutateClass> mutateAcceptHistory = new ArrayList<>();
        List<MutateClass> mutateRejectHistory = new ArrayList<>();
        List<Double> averageDistance = new ArrayList<>();
        Random random = new Random();
        mutateAcceptHistory.add(mutateClass);
        mutateClass.saveCurrentClass();
        for (int i = 0; i < iterationCount; i ++) {
            System.out.println("Current size is : " + (mutateAcceptHistory.size() + mutateRejectHistory.size()) + ", iteration is :" + i + ", average distance is " + MathTool.mean(averageDistance));
            MutateClass newOne = randomMutation(mutateClass); // sootclass has changed here for all objects.
            if (newOne != null) {
                newOne.saveCurrentClass();
                MutateClass previousClass = mutateAcceptHistory.get(mutateAcceptHistory.size() - 1);
                MethodCounter current = newOne.getCurrentMethod();
                List<String> currentLiveCode = newOne.getMethodLiveCodeString(current.getSignature());
                List<String> originalCode = previousClass.getMethodOriginalStmtListString(current.getSignature());
                int distance = LevenshteinDistance.computeLevenshteinDistance(currentLiveCode, previousClass.getMethodLiveCodeString(current.getSignature()));
                double covScore = calculateCovScore(newOne);
                double rand = random.nextDouble();
                double fitnessScore = fitness(calculateCovScore(mutateClass), covScore, originalCode.size());
                if(rand < fitnessScore) {
                    System.out.println(covScore);
                    System.out.println("Distance is " + distance + " signature is " + current.getSignature());
                    averageDistance.add(distance / 1.0);
                    showListElement(currentLiveCode);
                    showListElement(previousClass.getMethodLiveCodeString(current.getSignature()));
                    mutateAcceptHistory.add(newOne);
                    dumpSingleMutateClass(newOne, "./AcceptHistory/");
                    mutateClass = newOne;
                    dumpMutationCounter(newOne, classPath, iterationCount-i);
                } else {
                    newOne.saveCurrentClass(); // backup reject
                    mutateRejectHistory.add(newOne);
                    dumpSingleMutateClass(newOne, "./RejectHistory/");
                    mutateClass = Recover.recoverFromPath(mutateAcceptHistory.get(mutateAcceptHistory.size() - 1));
                    dumpMutationCounter(mutateAcceptHistory.get(mutateAcceptHistory.size() - 1), classPath, iterationCount-i);
                }

            } else {
                mutateClass = Recover.recoverFromPath(mutateAcceptHistory.get(mutateAcceptHistory.size() - 1));
                dumpMutationCounter(mutateAcceptHistory.get(mutateAcceptHistory.size() - 1), classPath, iterationCount-i);
//                System.out.println(mutateClass.getBackPath());
            }
        }

        ClusterTool.getClassmingClusterData(mutateAcceptHistory);

        Recover.recoverFromPath(mutateAcceptHistory.get(0));
//        dumpAcceptHistory(mutateAcceptHistory);
//        dumpRejectHistory(mutateRejectHistory);
        try{
            System.out.println("Accept size is " + mutateAcceptHistory.size());
            System.out.println("Average distance is " + MathTool.mean(averageDistance));
            System.out.println("var is " + MathTool.standardDeviation(averageDistance));
            System.out.println("max is " + Collections.max(averageDistance));
            calculateAverageDistance(mutateAcceptHistory);
        }catch (Exception e){
            e.printStackTrace();
        }
        G.reset();
    }

    public static void dumpMutationCounter(MutateClass m, String classPath, int leftIterationNum){
        try {
            File file = new File(classPath + "MutationCounter.log");
            if (!file.exists())
                file.createNewFile();
            FileWriter fw = new FileWriter(file.getPath(), false);
            for(MethodCounter mc : m.getMutationCounter()){
                fw.write(mc.getSignature()+","+mc.getCount()+"\n");
            }
            fw.write(m.getBackPath());  // for emergency
            fw.write("Iteration Left:"+leftIterationNum);
            fw.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static List<MethodCounter> readMutationCounter(String classPath){
        List<MethodCounter> mc = new ArrayList<>();
        try {
            File file = new File(classPath + "MutationCounter.log");
            if(!file.exists())
                return null;
            FileReader fr = new FileReader(file.getPath());
            BufferedReader br = new BufferedReader(fr);
            String line = null;
            while((line = br.readLine())!=null){
                if(line.contains(",")){
                    String[] temp = line.split("[,]");
                    String sig = temp[0];
                    int count = Integer.parseInt(temp[1]);
                    mc.add(new MethodCounter(sig, count));
                }else if(line.contains(":")){
                    leftIterationNumReaded = Integer.parseInt(line.split("[:]")[1]);
                    System.out.println("Iteration left: "+ leftIterationNumReaded);
                }
            }
            fr.close();
            br.close();
        }catch (Exception e){
            e.printStackTrace();
        }
        return mc;
    }

    public static void calculateAverageDistance(List<MutateClass> accepted) {
        List<State> states = new ArrayList<>();
        List<Double> score = new ArrayList<>();
        for (MutateClass sClass: accepted) {
            State state = new State();
            state.setTarget(sClass);
            states.add(state);
        }
        for (State state: states) {
            state.setCoFitnessScore(Fitness.fitness(state, states));
        }
        states.sort(new Comparator<State>() {
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
        for (State state: states) {
            System.out.print(state.getCoFitnessScore() + " ");
            score.add(state.getCoFitnessScore());
        }
        System.out.println();
        System.out.println("Total average: " + MathTool.mean(score));
        score = score.subList(0, EvolutionFramework.POPULATION_LIMIT);
        System.out.println();
        System.out.println("Best average: " + MathTool.mean(score));
    }

    public static double fitness(double previousCov, double currentCov, int total) {
        double result = Math.exp(0.08 * total * (previousCov - currentCov));
        return 1.0 < result ? 1.0 : result;
    }

    public static double calculateCovScore(MutateClass mutateClass) {
        MethodCounter current = mutateClass.getCurrentMethod();
        List<String> currentLiveCode = mutateClass.getMethodLiveCodeString(current.getSignature());
        List<String> originalCode = mutateClass.getMethodOriginalStmtListString(current.getSignature());
        return currentLiveCode.size() / (double)originalCode.size();
    }

    public static void showListElement(List<String> target) {
        StringBuilder builder = new StringBuilder();
        for (String element: target) {
            builder.append(element + " ");
        }
        System.out.println(builder);
    }

    // targetDirectory should be "./AcceptHistory/" or "./RejectHistory/"
    public static void dumpSingleMutateClass(MutateClass mc, String targetDirectory){
        String backPath = mc.getBackPath();
        File source = new File(backPath);
        File dest = new File(backPath.replace("./tmp/", targetDirectory));
        try {
            Files.copy(source.toPath(), dest.toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException {
        cpSeparator = File.pathSeparator;
        long startTime = System.currentTimeMillis();

//        process("com.classming.Hello", 1000, args, null, "", "");
        process("avrora.Main", 3000,
                new String[]{"-action=cfg","sootOutput/avrora-cvs-20091224/example.asm"},
                "./sootOutput/avrora-cvs-20091224/",null, "");
//        process("org.apache.batik.apps.rasterizer.Main", 400,null,
//                "./sootOutput/batik-all/",null, "");
        process("org.eclipse.core.runtime.adaptor.EclipseStarter", 18000,
                new String[]{"-debug"}, "./sootOutput/eclipse/", null, "");
        process("org.sunflow.Benchmark", 2000,
                new String[]{"-bench","2","256"},
                "./sootOutput/sunflow-0.07.2/",
                "dependencies/janino-2.5.15.jar", "");
        process("org.apache.fop.cli.Main", 3000,
                new String[]{"-xml","sootOutput/fop/name.xml","-xsl","sootOutput/fop/name2fo.xsl","-pdf","sootOutput/fop/name.pdf"},
                "./sootOutput/fop/",
                "dependencies/xmlgraphics-commons-1.3.1.jar" + cpSeparator +
                        "dependencies/commons-logging.jar" + cpSeparator +
                        "dependencies/avalon-framework-4.2.0.jar" + cpSeparator +
                        "dependencies/batik-all.jar" + cpSeparator +
                        "dependencies/commons-io-1.3.1.jar", "");
        process("org.python.util.jython", 6000,
                new String[]{"sootOutput/jython/hello.py"},
                "./sootOutput/jython/",
                "dependencies/guava-r07.jar" + cpSeparator +
                        "dependencies/constantine.jar" + cpSeparator +
                        "dependencies/jnr-posix.jar" + cpSeparator +
                        "dependencies/jaffl.jar" + cpSeparator +
                        "dependencies/jline-0.9.95-SNAPSHOT.jar" + cpSeparator +
                        "dependencies/antlr-3.1.3.jar" + cpSeparator +
                        "dependencies/asm-3.1.jar", "");
//        process("net.sourceforge.pmd.PMD", 2000,
//                new String[]{"sootOutput/pmd-4.2.5/Hello.java","text","unusedcode"},
//                "./sootOutput/pmd-4.2.5/",
//                "dependencies/jaxen-1.1.1.jar;" +
//                        "dependencies/asm-3.1.jar", "");  // pmd no accept


        long endTime = System.currentTimeMillis();
        System.out.println("Program used time: "+(endTime-startTime)+" ms");
    }
}
