package com.classming.record;

import com.classming.Main;
import com.classming.MutateClass;
import fj.data.IO;
import soot.SootClass;
import soot.SourceLocator;
import soot.UnitPatchingChain;
import soot.jimple.Stmt;
import soot.options.Options;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Recover {
    public static List<Stmt> insertRecord = new ArrayList<>();

    public static void recoverInsert(UnitPatchingChain units) {
        for (Stmt stmt: insertRecord) {
            units.remove(stmt);
        }
        clear();
    }

    public static MutateClass recoverFromPath(MutateClass sClass) throws IOException {
        String fileName = SourceLocator.v().getFileNameFor(sClass.getSootClass(), Options.output_format_class);
        String backupPath = sClass.getBackPath();
        File oldFile = new File(fileName);
        File newFile = new File(backupPath);
        copy(newFile, oldFile);
        sClass.reload();
        return sClass;
    }

    public static void clear() {
        insertRecord.clear();
    }
    private static void copy(File src, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(src);
            os = new FileOutputStream(dest);

            // buffer size 1K
            byte[] buf = new byte[1024];

            int bytesRead;
            while ((bytesRead = is.read(buf)) > 0) {
                os.write(buf, 0, bytesRead);
            }
        } finally {
            is.close();
            os.close();
        }
    }

    public static void main(String[] args) throws IOException {
        File file1 = new File("./tmp/1577288147953.com.classming.Hello");
        File file2 = new File("D:\\NEW-RESEARCH\\classming\\sootOutput\\com\\classming\\Hello.class");
        copy(file1, file2);
    }
}
