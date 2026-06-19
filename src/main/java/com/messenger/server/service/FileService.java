package com.messenger.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages file uploads to server disk storage.
 */
public final class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private static final String UPLOAD_DIR = "uploads";
    private static final String CHUNK_DIR = "uploads/chunks";
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100 MB

    private static final Map<String, FileChunkState> chunkStates = new ConcurrentHashMap<>();

    private static class FileChunkState {
        final String fileId;
        final String fileName;
        final int totalChunks;
        int receivedChunks;
        final Path chunkDir;

        FileChunkState(String fileId, String fileName, int totalChunks) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.totalChunks = totalChunks;
            this.chunkDir = Paths.get(CHUNK_DIR, fileId);
            try { Files.createDirectories(chunkDir); } catch (IOException ignored) {}
        }
    }

    private FileService() {
        throw new UnsupportedOperationException("Utility class");
    }

    static {
        try { Files.createDirectories(Paths.get(UPLOAD_DIR)); } catch (IOException ignored) {}
        try { Files.createDirectories(Paths.get(CHUNK_DIR)); } catch (IOException ignored) {}
    }

    /**
     * Save a complete file to disk. Returns the file path, or null on failure.
     */
    public static String saveFile(String fileName, byte[] data) {
        if (data.length > MAX_FILE_SIZE) {
            logger.error("File too large: {} bytes", data.length);
            return null;
        }
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            String uniqueName = System.currentTimeMillis() + "_" + sanitize(fileName);
            Path path = Paths.get(UPLOAD_DIR, uniqueName);
            Files.write(path, data);
            logger.info("Saved file: {} ({} bytes)", path, data.length);
            return path.toString().replace("\\", "/");
        } catch (IOException e) {
            logger.error("Failed to save file {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    /**
     * Save a chunk of a file. Returns true if this was the last chunk (file is complete).
     */
    public static boolean saveChunk(String fileId, String fileName, int chunkIndex,
                                     byte[] chunkData, int totalChunks) {
        FileChunkState state = chunkStates.computeIfAbsent(fileId,
                k -> new FileChunkState(fileId, fileName, totalChunks));

        try {
            Path chunkPath = state.chunkDir.resolve(String.format("chunk_%06d", chunkIndex));
            Files.write(chunkPath, chunkData);
            state.receivedChunks++;
            logger.debug("Chunk {}/{} for file {}", state.receivedChunks, totalChunks, fileId);

            if (state.receivedChunks >= totalChunks) {
                assembleChunks(state);
                chunkStates.remove(fileId);
                return true;
            }
        } catch (IOException e) {
            logger.error("Failed to save chunk {} for file {}: {}", chunkIndex, fileId, e.getMessage());
        }
        return false;
    }

    private static void assembleChunks(FileChunkState state) throws IOException {
        String uniqueName = System.currentTimeMillis() + "_" + sanitize(state.fileName);
        Path finalPath = Paths.get(UPLOAD_DIR, uniqueName);
        try {
            for (int i = 0; i < state.totalChunks; i++) {
                Path chunkPath = state.chunkDir.resolve(String.format("chunk_%06d", i));
                byte[] data = Files.readAllBytes(chunkPath);
                if (i == 0) {
                    Files.write(finalPath, data);
                } else {
                    Files.write(finalPath, data, StandardOpenOption.APPEND);
                }
            }
            logger.info("Assembled file: {} ({} chunks)", finalPath, state.totalChunks);
        } finally {
            deleteDirectory(state.chunkDir);
        }
    }

    /**
     * Get the URL/path for a file stored on the server.
     */
    public static String getFileUrl(String filePath) {
        Path abs = Paths.get(filePath).toAbsolutePath().normalize();
        return "file:///" + abs.toString().replace("\\", "/");
    }

    /**
     * Read file bytes from disk. Returns null if not found.
     */
    public static byte[] getFileBytes(String filePath) {
        try {
            return Files.readAllBytes(Paths.get(filePath));
        } catch (IOException e) {
            logger.error("Failed to read file {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Delete a file from disk.
     */
    public static boolean deleteFile(String filePath) {
        try {
            return Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            logger.error("Failed to delete file {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private static void deleteDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                          .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
        } catch (IOException ignored) {}
    }
}
