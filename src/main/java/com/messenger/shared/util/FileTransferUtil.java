package com.messenger.shared.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FileTransferUtil {

    private static final int BUFFER_SIZE = 8192;

    private FileTransferUtil() {
        throw new UnsupportedOperationException("FileTransferUtil is a utility class and cannot be instantiated");
    }

    public static byte[] readFileChunk(String filePath, long offset, int chunkSize) throws IOException {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found: " + filePath);
        }
        long fileSize = file.length();
        if (offset >= fileSize) return new byte[0];
        int actualSize = (int) Math.min(chunkSize, fileSize - offset);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            byte[] buffer = new byte[actualSize];
            int bytesRead = raf.read(buffer);
            if (bytesRead == -1) return new byte[0];
            if (bytesRead < actualSize) {
                byte[] trimmed = new byte[bytesRead];
                System.arraycopy(buffer, 0, trimmed, 0, bytesRead);
                return trimmed;
            }
            return buffer;
        }
    }

    public static void writeFileChunk(String filePath, long offset, byte[] data) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(offset);
            raf.write(data);
        }
    }

    public static long getFileSize(String filePath) {
        File file = new File(filePath);
        return file.exists() ? file.length() : 0;
    }

    public static String getFileChecksum(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) digest.update(buffer, 0, bytesRead);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) return "";
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    public static boolean isImageExtension(String extension) {
        return "jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension)
                || "gif".equals(extension) || "bmp".equals(extension) || "webp".equals(extension);
    }

    public static boolean isVideoExtension(String extension) {
        return "mp4".equals(extension) || "avi".equals(extension) || "mkv".equals(extension)
                || "mov".equals(extension) || "wmv".equals(extension) || "flv".equals(extension);
    }

    public static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        else if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        else return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
}
