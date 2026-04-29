package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.entity.Image;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.service.ImageService;
import com.nineone.markdown.vo.ImageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图片上传与管理控制器
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @Value("${app.upload.path:uploads/images}")
    private String uploadPath;

    /**
     * 初始化上传路径：将相对路径转为绝对路径
     */
    @PostConstruct
    public void initUploadPath() {
        Path path = Paths.get(uploadPath);
        if (!path.isAbsolute()) {
            String userDir = System.getProperty("user.dir");
            uploadPath = Paths.get(userDir, uploadPath).toString();
        }
    }

    /**
     * 获取当前登录用户的ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationException("用户未认证", "UNAUTHENTICATED");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getId();
        }
        throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
    }

    /**
     * 上传图片
     */
    @PostMapping("/upload")
    public Result<ImageVO> uploadImage(@RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false) Long articleId) {
        Long userId = getCurrentUserId();
        Image image = imageService.uploadImage(file, userId, articleId);

        ImageVO vo = ImageVO.builder()
                .id(image.getId())
                .originalName(image.getOriginalName())
                .url(imageService.getImageUrl(image.getId()))
                .thumbnailUrl(imageService.getThumbnailUrl(image.getId()))
                .fileSize(image.getFileSize())
                .width(image.getWidth())
                .height(image.getHeight())
                .mimeType(image.getMimeType())
                .createTime(image.getCreateTime())
                .build();

        return Result.success("图片上传成功", vo);
    }

    /**
     * 获取我的图片列表
     */
    @GetMapping("/my")
    public Result<List<ImageVO>> getMyImages() {
        Long userId = getCurrentUserId();
        List<Image> images = imageService.getUserImages(userId);

        List<ImageVO> vos = images.stream()
                .map(img -> ImageVO.builder()
                        .id(img.getId())
                        .originalName(img.getOriginalName())
                        .url(imageService.getImageUrl(img.getId()))
                        .thumbnailUrl(imageService.getThumbnailUrl(img.getId()))
                        .fileSize(img.getFileSize())
                        .width(img.getWidth())
                        .height(img.getHeight())
                        .mimeType(img.getMimeType())
                        .createTime(img.getCreateTime())
                        .build())
                .collect(Collectors.toList());

        return Result.success(vos);
    }

    /**
     * 获取文章的关联图片列表
     */
    @GetMapping("/article/{articleId}")
    public Result<List<ImageVO>> getArticleImages(@PathVariable Long articleId) {
        List<Image> images = imageService.getArticleImages(articleId);

        List<ImageVO> vos = images.stream()
                .map(img -> ImageVO.builder()
                        .id(img.getId())
                        .originalName(img.getOriginalName())
                        .url(imageService.getImageUrl(img.getId()))
                        .thumbnailUrl(imageService.getThumbnailUrl(img.getId()))
                        .fileSize(img.getFileSize())
                        .width(img.getWidth())
                        .height(img.getHeight())
                        .mimeType(img.getMimeType())
                        .createTime(img.getCreateTime())
                        .build())
                .collect(Collectors.toList());

        return Result.success(vos);
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/{imageId}")
    public Result<Void> deleteImage(@PathVariable Long imageId) {
        Long userId = getCurrentUserId();
        imageService.deleteImage(imageId, userId);
        return Result.success("图片已删除", null);
    }

    /**
     * 获取图片文件（公开访问）
     */
    @GetMapping("/{imageId}/file")
    public ResponseEntity<Resource> getImageFile(@PathVariable Long imageId) {
        Image image = imageService.getImageById(imageId);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path filePath = Paths.get(uploadPath, image.getStoragePath());
            File file = filePath.toFile();
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String contentType = image.getMimeType() != null ? image.getMimeType() : "application/octet-stream";

            // 对文件名进行 URL 编码，解决中文文件名导致的 Tomcat Header 异常
            // 使用 RFC 5987 规范的 filename* 格式，兼容各种浏览器
            String encodedFileName = URLEncoder.encode(image.getOriginalName(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=utf-8''" + encodedFileName)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
