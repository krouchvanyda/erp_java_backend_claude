package com.company.erp.features.chats.service;

import com.company.erp.core.config.AppProperties;
import com.company.erp.core.exceptions.BadRequestException;
import com.company.erp.features.chats.dto.ChatAttachmentDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stores chat message attachments (image / voice / file) on disk and returns a
 * server-relative public URL. Mirrors {@code EmployeeAvatarService} but is not
 * tied to any entity — the resulting URL is carried in the chat message's
 * {@code attachmentUrl} so every participant loads the SAME hosted file
 * (instead of the sender's local device path, which was invisible to peers).
 */
@Service
public class ChatAttachmentService {

    private final Path dir;
    private final String publicBaseUrl;
    private final long maxBytes;
    private final Set<String> allowedTypes;

    public ChatAttachmentService(AppProperties props) {
        AppProperties.Uploads.ChatAttachment cfg = props.uploads().chatAttachment();
        this.dir           = Paths.get(cfg.dir()).toAbsolutePath().normalize();
        this.publicBaseUrl = trimTrailingSlash(cfg.publicBaseUrl());
        this.maxBytes      = cfg.maxFileSize();
        this.allowedTypes  = Arrays.stream(cfg.allowedContentTypes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public ChatAttachmentDto store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attachment file is required");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Attachment exceeds max size of " + maxBytes + " bytes");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new BadRequestException("Unsupported content type: " + contentType
                    + " (allowed: " + allowedTypes + ")");
        }

        String filename = UUID.randomUUID() + "." + extensionFor(contentType);
        Path target = dir.resolve(filename);
        try {
            Files.createDirectories(dir);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store attachment: " + ex.getMessage(), ex);
        }

        return new ChatAttachmentDto(
                publicBaseUrl + "/" + filename,
                contentType,
                file.getSize());
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            case "image/gif"  -> "gif";
            case "image/heic" -> "heic";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4", "audio/aac" -> "m4a";
            case "audio/ogg"  -> "ogg";
            case "audio/wav"  -> "wav";
            case "application/pdf" -> "pdf";
            default           -> "bin";
        };
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
