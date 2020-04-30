package com.classming.cluster;

public class ClusterTest {

    public static void main(String[] args) {
        Cluster cluster = new DBScanCluster();
        System.out.println(cluster.getClusterInfo(null));
    }

}
