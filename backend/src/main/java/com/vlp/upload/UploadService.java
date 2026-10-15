package com.vlp.upload;

import com.vlp.common.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地上传。文件名强制 UUID，杜绝路径穿越与原名覆盖；
 * 扩展名白名单 + 体积上限；禁止 svg（可内嵌脚本，图片公网可读）。
 */
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
        return store(file, "videos", Set.of(allowedVideo.split(",")), maxVideoMb * 1024L * 1024L, true);
    }

    public String storeImage(MultipartFile file) {
        return store(file, "images", Set.of(allowedImage.split(",")), 10 * 1024L * 1024L, false);
    }

    private String store(MultipartFile file, String sub, Set<String> allowed, long maxBytes, boolean video) {
        if (file == null || file.isEmpty()) throw new BusinessException("文件不能为空");
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        // 拒绝路径片段与双扩展名伪装
        if (original.contains("..") || original.contains("/") || original.contains("\\")) {
            throw new BusinessException("非法文件名");
        }
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        ext = ext.trim();
        if (ext.isEmpty() || !allowed.contains(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext);
        }
        if ("svg".equals(ext)) {
            throw new BusinessException("出于安全考虑不支持 SVG 上传");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("文件过大");
        }
        // 轻量魔数抽检（防改后缀）
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (!looksPlausible(head, ext, video)) {
                throw new BusinessException("文件内容与扩展名不符");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dest = root.resolve(sub).resolve(name).normalize();
        if (!dest.startsWith(root.resolve(sub))) {
            throw new BusinessException("非法路径");
        }
        try {
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }
        return sub + "/" + name;
    }

    private static boolean looksPlausible(byte[] head, String ext, boolean video) {
        if (head == null || head.length < 4) return false;
        if (video) {
            // mp4/mov often ftyp at offset 4; webm EBML 1A45DFA3
            if ("webm".equals(ext)) {
                return head[0] == 0x1A && head[1] == (byte) 0x45;
            }
            // allow mp4/mov/m4v if has ftyp or looks like ISO BMFF
            String ascii = new String(head, 0, Math.min(head.length, 12));
            return ascii.contains("ftyp") || head[0] == 0x00;
        }
        // images
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
        }
        if ("png".equals(ext)) {
            return head[0] == (byte) 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
        }
        if ("gif".equals(ext)) {
            return head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
        }
        if ("webp".equals(ext)) {
            return head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F';
        }
        return true;
    }
}
