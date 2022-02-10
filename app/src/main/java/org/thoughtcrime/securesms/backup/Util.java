package org.thoughtcrime.securesms.backup;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class Util {
    public final static int MAX_BYTES_PER_FILE = 1024 * 1024 * 1024;

    public static void renameMulti(List<File> fromFiles, List<File> toFiles) {
        if (fromFiles.size() != toFiles.size()) {
            throw (new RuntimeException("fromFiles.size() doesn't match toFiles.size()"));
        }
        for (int i = 0; i < fromFiles.size(); ++i) {
            File from = fromFiles.get(i);
            File to = toFiles.get(i);
            from.renameTo(to);
        }
    }

    public static List<File> generateBackupFilenames(File backupDirectory, int n) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
        List<File> files = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            String fileName  = String.format("signal-%s.backup.part%03d", timestamp, n);
            File file = new File(backupDirectory, fileName);
            files.add(file);
        }
        return files;
    }
}
