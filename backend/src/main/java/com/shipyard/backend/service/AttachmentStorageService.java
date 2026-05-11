package com.shipyard.backend.service;

import com.shipyard.backend.api.ApiDtos;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachmentStorageService {

    private final Path root = Paths.get("build", "attachments");
    private final Path stagingRoot = Paths.get("build", "attachment-staging");

    public StoredAttachments store(
        String recordId,
        List<ApiDtos.AttachmentPayload> photoFiles,
        ApiDtos.AttachmentPayload audioFile
    ) {
        try {
            Path recordDir = root.resolve(recordId);
            Files.createDirectories(recordDir);

            List<String> storedPhotos = new ArrayList<>();
            for (ApiDtos.AttachmentPayload payload : photoFiles) {
                storedPhotos.add(writeAttachment(recordDir, payload));
            }

            String storedAudio = null;
            if (audioFile != null) {
                storedAudio = writeAttachment(recordDir, audioFile);
            }
            return new StoredAttachments(storedPhotos, storedAudio);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "附件落盘失败", exception);
        }
    }

    private String writeAttachment(Path recordDir, ApiDtos.AttachmentPayload payload) throws IOException {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.base64Data());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "附件编码不合法", exception);
        }

        String sanitizedName = sanitizeFileName(payload.fileName());
        Path output = recordDir.resolve(sanitizedName);
        Files.write(output, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return output.getFileName().toString();
    }

    private String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public long appendChunk(
        String recordId,
        String fileId,
        long offset,
        byte[] bytes,
        long totalBytes
    ) {
        try {
            Path tempFile = stagingFile(recordId, fileId);
            Files.createDirectories(tempFile.getParent());
            long currentSize = Files.exists(tempFile) ? Files.size(tempFile) : 0L;
            if (offset > currentSize) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "分片偏移不连续，服务器当前偏移为 " + currentSize
                );
            }

            int startIndex = 0;
            if (offset < currentSize) {
                long overlap = currentSize - offset;
                if (overlap >= bytes.length) {
                    return currentSize;
                }
                startIndex = (int) overlap;
                offset = currentSize;
            }

            try (RandomAccessFile file = new RandomAccessFile(tempFile.toFile(), "rw")) {
                file.seek(offset);
                file.write(bytes, startIndex, bytes.length - startIndex);
            }

            long uploadedBytes = Files.size(tempFile);
            if (uploadedBytes > totalBytes) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传内容超过文件声明大小");
            }
            return uploadedBytes;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入分片失败", exception);
        }
    }

    public void finalizeChunkedFile(
        String recordId,
        String fileId,
        String storedFileName
    ) {
        try {
            Path tempFile = stagingFile(recordId, fileId);
            Path finalFile = root.resolve(recordId).resolve(storedFileName);
            if (!Files.exists(tempFile) && Files.exists(finalFile)) {
                return;
            }
            if (!Files.exists(tempFile)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "待合并文件不存在");
            }
            Path recordDir = root.resolve(recordId);
            Files.createDirectories(recordDir);
            Files.move(
                tempFile,
                finalFile,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "完成附件落盘失败", exception);
        }
    }

    public void deleteRecordArtifacts(String recordId) {
        deleteDirectory(root.resolve(recordId));
        deleteDirectory(stagingRoot.resolve(recordId));
    }

    public String buildStoredFileName(String fileId, String originalFileName) {
        return sanitizeFileName(fileId + "_" + originalFileName);
    }

    private Path stagingFile(String recordId, String fileId) {
        return stagingRoot.resolve(recordId).resolve(sanitizeFileName(fileId) + ".part");
    }

    private void deleteDirectory(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left)).forEach(target -> {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "清理附件目录失败", ioException);
            }
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "清理附件目录失败", exception);
        }
    }

    public record StoredAttachments(
        List<String> photoFileNames,
        String audioFileName
    ) {}
}
