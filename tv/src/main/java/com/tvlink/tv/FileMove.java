package com.tvlink.tv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

final class FileMove {
    static void move(File source, File target) throws IOException {
        if (!source.isFile() || !source.getCanonicalFile().equals(
                new File(source.getParentFile().getCanonicalFile(), source.getName()))) {
            throw new IOException("Source file is unavailable");
        }
        if (!target.createNewFile()) throw new IOException("A file with this name already exists");

        boolean reserved = true;
        boolean moved = false;
        File temporary = null;
        try {
            if (source.renameTo(target)) {
                moved = true;
                return;
            }

            if (!target.delete()) throw new IOException("Could not prepare the destination");
            reserved = false;
            long size = source.length();
            long modified = source.lastModified();
            temporary = File.createTempFile(".move-", ".part", target.getParentFile());
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Move cancelled");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (temporary.length() != size || source.length() != size || source.lastModified() != modified) {
                throw new IOException("Source file changed during the move");
            }
            temporary.setLastModified(modified);
            if (!target.createNewFile()) throw new IOException("A file with this name already exists");
            reserved = true;
            if (!temporary.renameTo(target)) throw new IOException("Could not save the file in this folder");
            if (!source.delete()) throw new IOException("Could not remove the original file");
            moved = true;
        } finally {
            if (temporary != null) temporary.delete();
            if (reserved && !moved) target.delete();
        }
    }
}
