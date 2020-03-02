package com.classming.rf.cluster;

import com.classming.Main;
import com.classming.MutateClass;
import com.classming.Vector.LevenshteinDistance;
import soot.options.Options;

import java.io.*;
import java.util.*;

public class ClassesCluster {

    public static void main(String[] args) throws Exception {
        int size = clusterAllIn("tmp");
        System.out.println("total " + size + " clusters");
    }

    public static int clusterAllIn(String directory) {
        init(directory);
        List<MutateClass> mutateClassList = loadClasses(directory);
        Map<MutateClass, Integer> clusterIdMap = Cluster.getClusterIdMap(mutateClassList);
        Map<Integer, Integer> clusterCntMap = getClusterCntMap(clusterIdMap);
        System.out.println(clusterCntMap);
        return clusterCntMap.size();
    }

    public static Map<Integer, Integer> getClusterCntMap(Map<MutateClass, Integer> map) {
        Map<Integer, Integer> clusterCntMap = new HashMap<>();
        for (MutateClass mClass : map.keySet()) {
            int clusterId = map.get(mClass);
            int cnt = clusterCntMap.getOrDefault(clusterId, 0);
            clusterCntMap.put(clusterId, cnt + 1);
        }
        return clusterCntMap;
    }

    private static void init(String directory) {
        Main.initial(null);
        String sootClassPath = directory + File.pathSeparator + System.getProperty("java.class.path");
        Options.v().set_soot_classpath(sootClassPath);
    }

    private static List<MutateClass> loadClasses(String directory) {
        File file = new File(directory);
        if (!file.exists()) {
            System.err.println("no directory " + directory + " found");
            System.exit(-1);
        }
        List<MutateClass> list = new ArrayList<>();
        String[] names = Objects.requireNonNull(file.list());
        for (int i = 0, size = names.length; i < size; i++) {
            String name = names[i];
            if (!name.endsWith(".class"))
                continue;
            String srcPath = directory + "/" + name;
            String dstPath = getOutputDir(directory, name);
            createIfNotExist(dstPath);
            System.out.printf("load class %04.2f%%\n", (i + 1.0) / size * 100);
            copy(srcPath, dstPath);
            MutateClass mClass = new MutateClass();
            try {
                mClass.initialize(getExecuteName(name), null);
                list.add(mClass);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    private static void copy(String from, String to) {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0)
                out.write(buf, 0, len);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getOutputDir(String directory, String fileName) {
        String[] slice = fileName.split("\\.");
        StringBuilder s = new StringBuilder(directory);
        int size = slice.length;
        for (int i = 1; i < size - 2; i++)
            s.append('/').append(slice[i]);
        s.append('/').append(slice[size - 2]).append(".class");
        return s.toString();
    }

    private static String getExecuteName(String fileName) {
        int from = fileName.indexOf('.');
        int to = fileName.lastIndexOf('.');
        return fileName.substring(from + 1, to);
    }

    private static boolean createIfNotExist(String dir) {
        File file = new File(dir);
        return file.exists() || file.mkdirs();
    }

    private static class Cluster {

        private static final int DBScan_minPts = 3;
        private static final double DBScan_radius = 5;

        public static Map<MutateClass, Integer> getClusterIdMap(List<MutateClass> mutateClassList) {
            List<Point> points = new LinkedList<>();
            List<Point> cores = new ArrayList<>();
            mutateClassList.forEach(mClass -> points.add(new Point(mClass)));

            //find cores
            for (Point point : points) {
                int cnt = 0;
                for (Point other : points) {
                    if (point != other && point.distanceTo(other) < DBScan_radius)
                        cnt++;
                }
                if (cnt >= DBScan_minPts)
                    cores.add(point);
            }

            //set groups
            int id = 0;
            for (Point p : cores) {
                if (p.visited)
                    continue;
                p.clusterId = ++id;
                densityConnected(p, id, points, cores);
            }

            //return map
            Map<MutateClass, Integer> clusterIdMap = new HashMap<>();
            for (Point p : points)
                clusterIdMap.put(p.mClass, p.clusterId);
            return clusterIdMap;
        }

        private static void densityConnected(Point center, int id, List<Point> points, List<Point> cores) {
            center.visited = true;
            for (Point point : points) {
                if (point.visited)
                    continue;
                if (point.distanceTo(center) < DBScan_radius) {
                    point.clusterId = id;
                    point.visited = true;
                    if (cores.contains(point))
                        densityConnected(point, id, points, cores);
                }
            }
        }

    }

    private static class Point {
        public MutateClass mClass;
        public boolean visited;
        public int clusterId;

        public Point(MutateClass mClass) {
            this.mClass = mClass;
            this.visited = false;
            this.clusterId = 0;
        }

        public int distanceTo(Point other) {
            List<String> aMethodLiveCode = this.mClass.getClassPureInstructionFlow();
            List<String> bMethodLiveCode = other.mClass.getClassPureInstructionFlow();
            return LevenshteinDistance.computeLevenshteinDistance(aMethodLiveCode, bMethodLiveCode);
        }
    }

}
