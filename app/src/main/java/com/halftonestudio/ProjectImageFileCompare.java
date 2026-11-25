package com.halftonestudio;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Constant-memory byte equality for ZIP photo and original, avoiding a second preview decode. */
final class ProjectImageFileCompare {
    private ProjectImageFileCompare() {}
    static boolean equal(File a, File b) throws IOException {
        if (a == b) return true;
        if (a == null || b == null || !a.isFile() || !b.isFile()) return false;
        if (a.length() != b.length()) return false;
        try (BufferedInputStream left = new BufferedInputStream(new FileInputStream(a));
             BufferedInputStream right = new BufferedInputStream(new FileInputStream(b))) {
            byte[] l = new byte[32768], r = new byte[32768];
            for (;;) {
                if (Thread.currentThread().isInterrupted())
                    throw new IOException("Project open canceled");
                int n = left.read(l);
                if (n < 0) return right.read() < 0;
                int offset = 0;
                while (offset < n) {
                    int amount = right.read(r, offset, n - offset);
                    if (amount < 0) return false;
                    if (amount == 0) continue;
                    offset += amount;
                }
                for (int i=0; i<n; i++) if (l[i] != r[i]) return false;
            }
        }
    }
}
