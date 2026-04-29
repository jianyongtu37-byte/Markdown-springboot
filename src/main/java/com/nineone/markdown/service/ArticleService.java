package com.nineone.markdown.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nineone.markdown.common.PageResult;
import com.nineone.markdown.dto.ArticleSaveDTO;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ArticleTimestamp;
import com.nineone.markdown.vo.ArticleDetailVO;
import com.nineone.markdown.vo.ArticleVO;

import java.util.List;
import java.util.Map;

/**
 * 文章服务接口
 */
public interface ArticleService extends IService<Article> {

    /**
     * 创建文章
     * @param article 文章实体
     * @param tagNames 标签名称列表（即写即存，不存在则自动创建）
     * @return 创建的文章ID
     */
    Long createArticle(Article article, List<String> tagNames);

    /**
     * 更新文章
     * @param article 文章实体
     * @param tagNames 标签名称列表（即写即存，不存在则自动创建）
     * @return 是否更新成功
     */
    boolean updateArticle(Article article, List<String> tagNames);

    /**
     * 根据ID获取文章详情（包含标签）
     * @param id 文章ID
     * @return 文章详情VO
     */
    ArticleVO getArticleDetail(Long id);

    /**
     * 分页查询文章列表
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param categoryId 分类ID（可选）
     * @param tagId 标签ID（可选）
     * @param keyword 关键词（可选）
     * @param status 文章状态（可选：0-草稿，1-仅自己可见，2-公开可见）
     * @param isPublic 兼容字段，已弃用，使用status字段替代（0-私有或草稿，1-公开）
     * @return 分页结果
     */
    PageResult<ArticleVO> getArticleList(Integer pageNum, Integer pageSize, Long categoryId, Long tagId, 
                                         String keyword, Integer status, Integer isPublic);

    /**
     * 删除文章（同时删除关联的标签关系）
     * @param id 文章ID
     * @return 是否删除成功
     */
    boolean deleteArticle(Long id);

    /**
     * 增加文章阅读量（带防刷逻辑）
     * @param id 文章ID
     * @return 是否增加成功
     */
    boolean increaseViewCount(Long id);

    /**
     * 更新文章AI摘要状态
     * @param articleId 文章ID
     * @param aiStatus AI状态
     * @param summary 摘要内容（可选）
     * @return 是否更新成功
     */
    boolean updateAiStatus(Long articleId, Integer aiStatus, String summary);

    /**
     * 获取当前用户的文章列表（包括草稿和私密文章）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param categoryId 分类ID（可选）
     * @param tagId 标签ID（可选）
     * @param keyword 关键词（可选）
     * @param status 文章状态（可选：0-草稿，1-已发布）
     * @param isPublic 公开状态（可选：0-私密，1-公开）
     * @return 分页结果
     */
    PageResult<ArticleVO> getMyArticles(Integer pageNum, Integer pageSize, Long categoryId, Long tagId, 
                                        String keyword, Integer status, Integer isPublic);

    /**
     * 获取指定用户的公开文章列表
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param categoryId 分类ID（可选）
     * @param tagId 标签ID（可选）
     * @param keyword 关键词（可选）
     * @return 分页结果
     */
    PageResult<ArticleVO> getUserArticles(Long userId, Integer pageNum, Integer pageSize, 
                                          Long categoryId, Long tagId, String keyword);

    /**
     * 获取当前用户的文章统计信息
     * @return 文章统计信息
     */
    Map<String, Object> getMyArticleStats();

    /**
     * 批量更新文章状态
     * @param articleIds 文章ID列表
     * @param status 文章状态（0-草稿，1-已发布）
     * @param isPublic 公开状态（0-私密，1-公开）
     * @return 是否更新成功
     */
    boolean batchUpdateStatus(List<Long> articleIds, Integer status, Integer isPublic);

    /**
     * 获取文章详情（包含视频信息和时间戳目录）
     * @param id 文章ID
     * @return 文章详情VO
     */
    ArticleDetailVO getDetail(Long id);

    /**
     * 保存/更新文章
     * @param dto 保存请求DTO
     * @param userId 用户ID
     */
    void save(ArticleSaveDTO dto, Long userId);

    /**
     * 获取文章的时间戳目录
     * @param id 文章ID
     * @return 时间戳列表
     */
    List<ArticleTimestamp> getTimestamps(Long id);

    /**
     * 更新文章的导出权限设置
     * @param articleId 文章ID
     * @param allowExport 是否允许导出（1-允许，0-禁止）
     */
    void updateAllowExport(Long articleId, Integer allowExport);
}
