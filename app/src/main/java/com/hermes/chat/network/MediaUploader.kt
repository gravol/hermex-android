package com.hermes.chat.network

import com.hermes.chat.model.MessageAttachment

/**
 * Uploads a media attachment and returns its accessible URL.
 */
interface MediaUploader {
    suspend fun upload(attachment: MessageAttachment): Result<String>
}

/**
 * Placeholder uploader that returns a localhost URL.
 * Replace with a real implementation that POSTs to a server endpoint.
 */
class PlaceholderMediaUploader : MediaUploader {
    override suspend fun upload(attachment: MessageAttachment): Result<String> {
        return Result.success("http://localhost/uploads/${attachment.id}/${attachment.displayName}")
    }
}
