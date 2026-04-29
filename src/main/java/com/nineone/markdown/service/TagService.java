package com.nineone.markdown.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nineone.markdown.entity.Tag;
import com.nineone.markdown.vo.TagVO;

import java.util.List;

/**
 * 标签服务接口
 */
public interface TagService extends IService<Tag> {

    /**
     * 创建标签
     * @param tag 标签实体
     * @return 创建的标签ID
     */
    Long createTag(Tag tag);

    /**
     * 根据ID获取标签详情
     * @param id 标签ID
     * @return 标签实体
     */
    Tag getTag(Long id);

    /**
     * 更新标签
     * @param tag 标签实体
     * @return 是否更新成功
     */
    boolean updateTag(Tag tag);

    /**
     * 删除标签
     * @param id 标签ID
     * @return 是否删除成功
     */
    boolean deleteTag(Long id);

    /**
     * 获取所有标签列表
     * @return 标签列表
     */
    List<TagVO> getAllTags();

    /**
     * 根据名称搜索标签
     * @param keyword 关键词
     * @return 匹配的标签列表
     */
    List<TagVO> searchTags(String keyword);

    /**
     * 获取热门标签（按使用频率排序）
     * @param limit 返回数量限制
     * @return 热门标签列表
     */
    List<TagVO> getPopularTags(Integer limit);

    /**
     * 获取所有标签名称列表
     * @return 标签名称列表
     */
    List<String> getTagNames();
}