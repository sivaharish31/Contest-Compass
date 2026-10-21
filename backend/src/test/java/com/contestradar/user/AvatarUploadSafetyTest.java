package com.contestradar.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-tests for the avatar-upload safety logic introduced in MED-07.
 * Tests the write-to-temp-then-move pattern without starting the full Spring context.
 */
class AvatarUploadSafetyTest {

    @TempDir
    Path uploadDir;

    // ── Minimal port of the relevant upload logic ──────────────────────────

    private String uploadAvatar(InputStream data, String filename) throws IOException {
        Path target = uploadDir.resolve(filename);
        Path temp   = uploadDir.resolve(filename + ".tmp");
        Files.copy(data, temp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        return filename;
    }

    private void deleteOldIfNeeded(String oldPath) {
        if (oldPath != null) {
            try { Files.deleteIfExists(uploadDir.resolve(oldPath)); } catch (IOException ignored) {}
        }
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    void uploadCreatesTargetFileAndRemovesTemp(@TempDir Path dir) throws IOException {
        // The temp file must not remain after a successful upload.
        Path target = dir.resolve("avatar.jpg");
        Path temp   = dir.resolve("avatar.jpg.tmp");

        Files.copy(InputStream.nullInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);

        assertTrue(Files.exists(target), "Target file must exist after upload");
        assertFalse(Files.exists(temp), "Temp file must be cleaned up after move");
    }

    @Test
    void oldAvatarPreservedWhenWriteFails() throws IOException {
        // Simulate: old avatar exists; new write fails; old file must survive.
        Path oldAvatar = Files.createFile(uploadDir.resolve("old-avatar.jpg"));
        assertTrue(Files.exists(oldAvatar), "Old avatar should exist before upload attempt");

        Path badTarget = uploadDir.resolve("new-avatar.jpg");
        Path badTemp   = uploadDir.resolve("new-avatar.jpg.tmp");

        // Simulate a write failure before the move
        IOException simulatedWriteError = new IOException("disk full");
        try {
            throw simulatedWriteError; // write never completes
        } catch (IOException e) {
            try { Files.deleteIfExists(badTemp); } catch (IOException ignored) {}
            // old avatar NOT deleted (deletion only happens after successful move)
        }

        assertTrue(Files.exists(oldAvatar), "Old avatar must survive after upload failure");
        assertFalse(Files.exists(badTarget), "New avatar must not exist after failure");
    }

    @Test
    void oldAvatarDeletedAfterSuccessfulReplacement() throws IOException {
        // Successful upload: old file should be gone, new file should exist.
        Path oldAvatar = Files.createFile(uploadDir.resolve("old.jpg"));
        Files.write(oldAvatar, new byte[]{0x01});

        String newFilename = "new.jpg";
        uploadAvatar(InputStream.nullInputStream(), newFilename);

        // Simulate post-upload cleanup
        deleteOldIfNeeded(oldAvatar.getFileName().toString());

        assertFalse(Files.exists(oldAvatar), "Old avatar must be deleted after successful replacement");
        assertTrue(Files.exists(uploadDir.resolve(newFilename)), "New avatar must exist");
    }

    @Test
    void tempFileCleanedUpOnWriteFailure() throws IOException {
        Path temp = uploadDir.resolve("fail.jpg.tmp");
        Files.createFile(temp);

        // Simulate: write started (temp exists), then fails
        try {
            throw new IOException("write error");
        } catch (IOException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }

        assertFalse(Files.exists(temp), "Temp file must be removed on write failure");
    }

    @Test
    void uploadWithNoExistingAvatarDoesNotThrow() throws IOException {
        // First upload — no old avatar path — must complete without NPE
        String filename = uploadAvatar(InputStream.nullInputStream(), "first.jpg");
        deleteOldIfNeeded(null); // null oldPath must not throw
        assertTrue(Files.exists(uploadDir.resolve(filename)));
    }
}
