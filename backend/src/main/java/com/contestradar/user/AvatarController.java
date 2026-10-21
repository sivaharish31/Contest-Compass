package com.contestradar.user;

import com.contestradar.auth.AuthUtil;
import com.contestradar.auth.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class AvatarController {

    private static final Logger log = LoggerFactory.getLogger(AvatarController.class);
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    private final UserRepository userRepository;
    private final Path uploadDir;

    public AvatarController(UserRepository userRepository,
                           @Value("${contestradar.upload.dir:uploads/avatars}") String uploadPath) {
        this.userRepository = userRepository;
        this.uploadDir = Path.of(uploadPath);
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", e.getMessage());
        }
    }

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = AuthUtil.requireUserId();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum 2MB."));
        }

        // Validate content type by reading actual bytes, not trusting client header
        String detectedType = detectImageType(file);
        if (detectedType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid image. Only JPEG, PNG, and WebP are accepted."));
        }

        String ext = switch (detectedType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        String filename = "avatar-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path target = uploadDir.resolve(filename);
        Path temp = uploadDir.resolve(filename + ".tmp");

        // Write to a temporary file first so the old avatar is not lost if the write fails.
        try {
            Files.copy(file.getInputStream(), temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Failed to save avatar: {}", e.getMessage());
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to save image"));
        }

        // Only delete the old avatar after the new one is safely in place.
        String oldPath = user.getAvatarPath();
        user.setAvatarPath(filename);
        userRepository.save(user);

        if (oldPath != null) {
            try { Files.deleteIfExists(uploadDir.resolve(oldPath)); } catch (IOException ignored) {}
        }

        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<?> deleteAvatar() {
        Long userId = AuthUtil.requireUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (user.getAvatarPath() != null) {
            try { Files.deleteIfExists(uploadDir.resolve(user.getAvatarPath())); } catch (IOException ignored) {}
            user.setAvatarPath(null);
            userRepository.save(user);
        }

        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> getAvatar(@PathVariable Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getAvatarPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path resolved = uploadDir.resolve(user.getAvatarPath()).normalize().toAbsolutePath();
        Path normalizedBase = uploadDir.normalize().toAbsolutePath();
        if (!resolved.startsWith(normalizedBase)) {
            log.warn("Avatar path traversal attempt blocked for user {}", userId);
            return ResponseEntity.notFound().build();
        }

        if (!Files.exists(resolved)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = "image/jpeg";
        String name = resolved.getFileName().toString();
        if (name.endsWith(".png")) contentType = "image/png";
        else if (name.endsWith(".webp")) contentType = "image/webp";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Cache-Control", "public, max-age=3600")
                .body(new FileSystemResource(resolved));
    }

    private String detectImageType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) return null;

            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return "image/jpeg";
            }
            // PNG: 89 50 4E 47
            if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return "image/png";
            }
            // WebP: RIFF....WEBP
            if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "image/webp";
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}
