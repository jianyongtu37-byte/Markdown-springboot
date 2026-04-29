package com.nineone.markdown.service;

import com.nineone.markdown.entity.Image;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 图片上传与管理服务接口
 */
public interface ImageService {

    /**
     * 上传图片
     * @param file 图片文件
     * @param userId 用户ID
     * @param articleId 关联文章ID（可选）
     * @return 图片实体
     */
    Image uploadImage(MultipartFile file, Long userId, Long articleId);

    /**
     * 获取用户上传的图片列表
     * @param userId 用户ID
     * @return 图片列表
     */
    List<Image> getUserImages(Long userId);

    /**
     * 获取文章的关联图片列表
     * @param articleId 文章ID
     * @return 图片列表
     */
    List<Image> getArticleImages(Long articleId);

    /**
     * 删除图片
     * @param imageId 图片ID
     * @param userId 用户ID
     */
    void deleteImage(Long imageId, Long userId);

    /**
     * 获取图片的访问URL
     * @param imageId 图片ID
     * @return 访问URL
     */
    String getImageUrl(Long imageId);

    /**
     * 获取图片的缩略图URL
     * @param imageId 图片ID
     * @return 缩略图URL
     */
    String getThumbnailUrl(Long imageId);

    /**
     * 根据ID获取图片实体
     * @param imageId 图片ID
     * @return 图片实体
     */
    Image getImageById(Long imageId);
}
