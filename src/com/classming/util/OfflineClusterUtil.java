package com.classming.util;

import com.classming.cluster.ClusterResult;
import com.classming.cluster.DBScanCluster;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OfflineClusterUtil {

    private static final ExecutorService service = Executors.newCachedThreadPool();

    public static void dumpInstructions(String path, List<String> instructions) {
        service.submit(() -> {
            File file = new File(path);
            try (PrintWriter out = new PrintWriter(file)) {
                for (String instruction : instructions) {
                    out.println(instruction);
                }
                out.flush();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        });
    }

    public static Set<String> loadInstructionHistory(String path) {
        File dir = new File(path);
        return new HashSet<>(Arrays.asList(Objects.requireNonNull(dir.list())));
    }

    public static String convertBack2Log(String path) {
        String[] strings = path.split("/");
        return "./tmpRecord/" + strings[strings.length - 1] + ".log";
    }

    public static List<List<String>> getPureInstructions(String path) {
        // path should be ./tmpRecord
        List<List<String>> lists = new ArrayList<>();
        File dir = new File(path);
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (!file.getName().endsWith(".log"))
                continue;
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                List<String> list = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    list.add(line);
                }
                lists.add(list);
            } catch (Exception e) {
                System.err.println("load instructions failed!");
                System.exit(-1);
            }
        }
        return lists;
    }

    public static ClusterResult getClusterInfo() {
        return new DBScanCluster().getClusterInfo(null);
    }

}
