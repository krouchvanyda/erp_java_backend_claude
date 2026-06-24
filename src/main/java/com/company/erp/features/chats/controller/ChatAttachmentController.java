package com.company.erp.features.chats.controller;

import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.features.chats.dto.ChatAttachmentDto;
import com.company.erp.features.chats.service.ChatAttachmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload endpoint for chat attachments. The client uploads the picked image /
 * voice clip / file here FIRST, then sends a message whose {@code attachmentUrl}
 * is the hosted URL returned below — so every participant loads the same file.
 */
@RestController
@RequestMapping("/api/v1/chats")
public class ChatAttachmentController {

    private final ChatAttachmentService attachments;

    public ChatAttachmentController(ChatAttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping(path = "/attachments", consumes = "multipart/form-data")
    public ChatAttachmentDto upload(@RequestParam("file") MultipartFile file) {
        AuthenticatedUser.require(); // any authenticated user may upload
        return attachments.store(file);
    }
}
