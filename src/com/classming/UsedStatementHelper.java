package com.classming;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UsedStatementHelper {
    public static Map<String, Map<String, Set<String>>> classMethodUsedStmt = new HashMap<>();

    public static void addClassMethodUsedStmt(String className, String signature, Set<String> usedStmt) {
        if (!classMethodUsedStmt.containsKey(className)) {
            classMethodUsedStmt.put(className, new HashMap<>());
        }
        Map<String, Set<String>> methods = classMethodUsedStmt.get(className);
        if (!methods.containsKey(signature)) {
            methods.put(signature, new HashSet<>());
        }
        Set<String> stmts = methods.get(signature);
        stmts.addAll(usedStmt);
    }

    public static boolean queryIfHasInstructionsAlready(String className, String signature, String statement) {
        if (classMethodUsedStmt.containsKey(className)) {
            return classMethodUsedStmt.get(className).get(signature).contains(statement);
        }
        return false;
    }
}
