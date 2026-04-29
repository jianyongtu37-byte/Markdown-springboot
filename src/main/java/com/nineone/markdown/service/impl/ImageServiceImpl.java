package com.nineone.markdown.service.impl;

import com.nineone.markdown.entity.Image;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.exception.PermissionDeniedException;
import com.nineone.markdown.mapper.ImageMapper;
import com.nineone.markdown.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 图片上传与管理服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageServiceImpl implements ImageService {

    private final ImageMapper imageMapper;

    /**
     * 图片上传存储根目录
     */
    @Value("${app.upload.path:uploads/images}")
    private String uploadPath;

    /**
     * 应用访问地址
     */
    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    /**
     * 允许的图片MIME类型
     */
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    /**
     * 最大文件大小（5MB）
     */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 初始化上传路径：将相对路径转为绝对路径
     */
    @PostConstruct
    public void initUploadPath() {
        Path path = Paths.get(uploadPath);
        if (!path.isAbsolute()) {
            // 如果是相对路径，基于用户目录（项目根目录）转为绝对路径
            String userDir = System.getProperty("user.dir");
            uploadPath = Paths.get(userDir, uploadPath).toString();
            log.info("图片上传路径已转换为绝对路径: {}", uploadPath);
        }
        // 确保根目录存在
        try {
            Files.createDirectories(Paths.get(uploadPath));
        } catch (IOException e) {
            log.error("创建图片上传根目录失败: {}", uploadPath, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Image uploadImage(MultipartFile file, Long userId, Long articleId) {
        // 参数校验
        if (file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }

        // 检查文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException("图片文件大小不能超过5MB，当前文件大小：" + (file.getSize() / (1024 * 1024)) + "MB");
        }

        // 检查MIME类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BizException("不支持的文件类型，仅支持JPEG、PNG、GIF、WebP、BMP格式");
        }

        // 获取文件扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 生成唯一文件名
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueFileName = UUID.randomUUID().toString() + extension;

        // 构建存储路径：uploads/images/2024/01/01/uuid.jpg
        String relativePath = dateStr + "/" + uniqueFileName;
        String fullPath = uploadPath + "/" + relativePath;

        try {
            // 创建目录
            Path directoryPath = Paths.get(uploadPath, dateStr);
            Files.createDirectories(directoryPath);

            // 保存文件
            Path filePath = Paths.get(fullPath);
            file.transferTo(filePath.toFile());

            // 获取图片尺寸并生成缩略图
            int width = 0;
            int height = 0;
            String thumbnailRelativePath = null;
            try {
                BufferedImage bufferedImage = ImageIO.read(filePath.toFile());
                if (bufferedImage != null) {
                    width = bufferedImage.getWidth();
                    height = bufferedImage.getHeight();

                    // 生成缩略图（最大宽度300px，保持宽高比）
                    thumbnailRelativePath = generateThumbnail(bufferedImage, dateStr, uniqueFileName, extension);
                }
            } catch (IOException e) {
                log.warn("无法读取图片尺寸或生成缩略图: {}", originalFilename, e);
            }

            // 保存图片记录到数据库
            Image image = Image.builder()
                    .userId(userId)
                    .articleId(articleId)
                    .originalName(originalFilename)
                    .storagePath(relativePath)
                    .thumbnailPath(thumbnailRelativePath)
                    .fileSize(file.getSize())
                    .width(width)
                    .height(height)
                    .mimeType(contentType)
                    .storageType("local")
                    .build();

            imageMapper.insert(image);
            log.info("图片上传成功, ID: {}, 路径: {}, 用户: {}", image.getId(), relativePath, userId);

            return image;

        } catch (IOException e) {
            log.error("图片上传失败: {}", originalFilename, e);
            throw new BizException("图片上传失败: " + e.getMessage());
        }
    }

    @Override
    public List<Image> getUserImages(Long userId) {
        return imageMapper.findByUserId(userId);
    }

    @Override
    public List<Image> getArticleImages(Long articleId) {
        return imageMapper.findByArticleId(articleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImage(Long imageId, Long userId) {
        Image image = imageMapper.selectById(imageId);
        if (image == null) {
            throw new BizException("图片不存在");
        }

        // 权限检查：只有上传者可以删除
        if (!image.getUserId().equals(userId)) {
            throw new PermissionDeniedException("您没有权限删除此图片");
        }

        // 删除物理文件
        try {
            Path filePath = Paths.get(uploadPath, image.getStoragePath());
            File file = filePath.toFile();
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.info("图片文件已删除: {}", image.getStoragePath());
                } else {
                    log.warn("图片文件删除失败: {}", image.getStoragePath());
                }
            }

            // 删除缩略图（如果有）
            if (image.getThumbnailPath() != null) {
                Path thumbPath = Paths.get(uploadPath, image.getThumbnailPath());
                File thumbFile = thumbPath.toFile();
                if (thumbFile.exists()) {
                    thumbFile.delete();
                }
            }
        } catch (Exception e) {
            log.error("删除图片文件失败: {}", image.getStoragePath(), e);
        }

        // 删除数据库记录
        imageMapper.deleteById(imageId);
        log.info("图片记录已删除, ID: {}", imageId);
    }

    @Override
    public String getImageUrl(Long imageId) {
        Image image = imageMapper.selectById(imageId);
        if (image == null) {
            return null;
        }
        return appUrl + "/api/images/" + image.getId() + "/file";
    }

    @Override
    public String getThumbnailUrl(Long imageId) {
        Image image = imageMapper.selectById(imageId);
        if (image == null || image.getThumbnailPath() == null) {
            return getImageUrl(imageId); // 没有缩略图则返回原图
        }
        return appUrl + "/api/images/" + image.getId() + "/thumbnail";
    }

    @Override
    public Image getImageById(Long imageId) {
        return imageMapper.selectById(imageId);
    }

    /**
     * 生成缩略图
     * @param originalImage 原始图片
     * @param dateStr 日期路径
     * @param fileName 文件名
     * @param extension 扩展名
     * @return 缩略图相对路径，如果生成失败返回null
     */
    private String generateThumbnail(BufferedImage originalImage, String dateStr, String fileName, String extension) {
        // 缩略图最大宽度
        final int THUMB_MAX_WIDTH = 300;
        // 缩略图最大高度
        final int THUMB_MAX_HEIGHT = 300;

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 如果图片本身小于缩略图尺寸，直接使用原图作为缩略图
        if (originalWidth <= THUMB_MAX_WIDTH && originalHeight <= THUMB_MAX_HEIGHT) {
            return dateStr + "/" + fileName;
        }

        // 计算缩放比例，保持宽高比
        double scale = Math.min(
                (double) THUMB_MAX_WIDTH / originalWidth,
                (double) THUMB_MAX_HEIGHT / originalHeight
        );

        int thumbWidth = (int) (originalWidth * scale);
        int thumbHeight = (int) (originalHeight * scale);

        // 生成缩略图文件名
        String thumbFileName = "thumb_" + fileName;
        String thumbRelativePath = dateStr + "/" + thumbFileName;
        String thumbFullPath = uploadPath + "/" + thumbRelativePath;

        try {
            // 创建缩略图
            BufferedImage thumbImage = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = thumbImage.createGraphics();

            // 设置高质量缩放
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 绘制缩略图
            g2d.drawImage(originalImage, 0, 0, thumbWidth, thumbHeight, null);
            g2d.dispose();

            // 保存缩略图
            Path thumbPath = Paths.get(thumbFullPath);
            String formatName = getFormatName(extension);
            ImageIO.write(thumbImage, formatName, thumbPath.toFile());

            log.info("缩略图生成成功: {}, 尺寸: {}x{}", thumbRelativePath, thumbWidth, thumbHeight);
            return thumbRelativePath;

        } catch (IOException e) {
            log.warn("缩略图生成失败: {}", thumbRelativePath, e);
            return null;
        }
    }

    /**
     * 根据文件扩展名获取图片格式名称
     */
    private String getFormatName(String extension) {
        if (extension == null || extension.isBlank()) {
            return "jpeg";
        }
        String ext = extension.toLowerCase().replace(".", "");
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "jpeg";
            case "png":
                return "png";
            case "gif":
                return "gif";
            case "bmp":
                return "bmp";
            case "webp":
                return "png"; // WebP格式回退到PNG
            default:
                return "jpeg";
        }
    }
}
