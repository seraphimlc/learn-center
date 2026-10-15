package com.vlp.upload;

import com.vlp.common.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class UploadService {
    @Value("${vlp.upload.dir}")
    private String uploadDir;

    @Value("${vlp.upload.allowed-video}")
    private String allowedVideo;

    @Value("${vlp.upload.allowed-image}")
    private String allowedImage;

    @Value("${vlp.upload.max-video-mb:500}")
    private int maxVideoMb;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("videos"));
        Files.createDirectories(root.resolve("images"));
        log.info("Upload root: {}", root);
    }

    public Path getRoot() { return root; }

    public String storeVideo(MultipartFile file) {
        return store(file, "videos", Set.of(allowedVideo.split(",")), maxVideoMb * 1024L * 1024L);
    }

    public String storeImage(MultipartFile file) {
        return store(file, "images", Set.of(allowedImage.split(",")), 10 * 1024L * 1024L);
    }

    private String store(MultipartFile file, String sub, Set<String> allowed, long maxBytes) {
        if (file == null || file.isEmpty()) throw new BusinessException("文件不能为空");
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext);
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("文件过大");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dest = root.resolve(sub).resolve(name);
        try {
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }
        return sub + "/" + name;
    }
}
