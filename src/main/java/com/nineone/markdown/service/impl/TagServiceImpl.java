package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nineone.markdown.entity.ArticleTag;
import com.nineone.markdown.entity.Tag;
import com.nineone.markdown.mapper.ArticleTagMapper;
import com.nineone.markdown.mapper.TagMapper;
import com.nineone.markdown.service.TagService;
import com.nineone.markdown.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "tags", allEntries = true)
    public Long createTag(Tag tag) {
        // 参数验证
        Assert.notNull(tag, "标签不能为空");
        Assert.hasText(tag.getName(), "标签名称不能为空");

        // 名称归一化：去除前后空格并统一小写（与 resolveTagIds 保持一致）
        String normalizedName = tag.getName().trim().toLowerCase();
        Assert.isTrue(normalizedName.length() <= 50, "标签名称不能超过50个字符");
        tag.setName(normalizedName);

        // 检查标签名称是否已存在
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Tag::getName, tag.getName());
        Tag existingTag = tagMapper.selectOne(queryWrapper);
        if (existingTag != null) {
            throw new IllegalArgumentException("标签名称已存在");
        }

        // 保存标签
        tagMapper.insert(tag);
        return tag.getId();
    }

    @Override
    public Tag getTag(Long id) {
        Assert.notNull(id, "标签ID不能为空");
        return tagMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "tags", allEntries = true)
    public boolean updateTag(Tag tag) {
        Assert.notNull(tag, "标签不能为空");
        Assert.notNull(tag.getId(), "标签ID不能为空");
        
        // 检查标签是否存在
        Tag existingTag = tagMapper.selectById(tag.getId());
        if (existingTag == null) {
            return false;
        }
        
        // 如果名称有变化，先归一化再检查新名称是否已存在
        String newName = tag.getName();
        if (newName != null) {
            newName = newName.trim().toLowerCase();
            Assert.isTrue(newName.length() <= 50, "标签名称不能超过50个字符");
            tag.setName(newName);
        }
        if (!existingTag.getName().equals(tag.getName())) {
            LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Tag::getName, tag.getName());
            Tag duplicateTag = tagMapper.selectOne(queryWrapper);
            if (duplicateTag != null) {
                throw new IllegalArgumentException("标签名称已存在");
            }
        }
        
        // 更新标签
        int updateCount = tagMapper.updateById(tag);
        return updateCount > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "tags", allEntries = true)
    public boolean deleteTag(Long id) {
        Assert.notNull(id, "标签ID不能为空");
        
        // 检查标签是否存在
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            return false;
        }
        
        // 检查是否有文章使用该标签
        LambdaQueryWrapper<ArticleTag> articleTagQuery = new LambdaQueryWrapper<>();
        articleTagQuery.eq(ArticleTag::getTagId, id);
        Long articleTagCount = articleTagMapper.selectCount(articleTagQuery);
        if (articleTagCount > 0) {
            throw new IllegalStateException("该标签已被文章使用，无法删除");
        }
        
        // 删除标签
        int deleteCount = tagMapper.deleteById(id);
        return deleteCount > 0;
    }

    @Override
    @Cacheable(value = "tags", key = "'allTags'", unless = "#result == null || #result.isEmpty()")
    public List<TagVO> getAllTags() {
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Tag::getName);
        List<Tag> tags = tagMapper.selectList(queryWrapper);
        
        // 转换为VO
        return tags.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<TagVO> searchTags(String keyword) {
        Assert.hasText(keyword, "搜索关键词不能为空");
        
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(Tag::getName, keyword);
        queryWrapper.orderByAsc(Tag::getName);
        List<Tag> tags = tagMapper.selectList(queryWrapper);
        
        // 转换为VO
        return tags.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "tags", key = "'popular_' + #limit", unless = "#result == null || #result.isEmpty()")
    public List<TagVO> getPopularTags(Integer limit) {
        Assert.notNull(limit, "数量限制不能为空");
        Assert.isTrue(limit > 0, "数量限制必须大于0");

        // 使用 LEFT JOIN + GROUP BY 一次查询获取热门标签，避免 N+1 问题
        List<java.util.Map<String, Object>> rows = tagMapper.selectPopularTags(limit);

        return rows.stream().map(row -> TagVO.builder()
                .id((Long) row.get("id"))
                .name((String) row.get("name"))
                .createTime((java.time.LocalDateTime) row.get("create_time"))
                .build()).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "tags", key = "'names'", unless = "#result == null || #result.isEmpty()")
    public List<String> getTagNames() {
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Tag::getName);
        List<Tag> tags = tagMapper.selectList(queryWrapper);
        
        // 提取标签名称列表
        return tags.stream()
                .map(Tag::getName)
                .collect(Collectors.toList());
    }
    /**
     * 将Tag实体转换为TagVO
     */
    private TagVO convertToVO(Tag tag) {
        if (tag == null) {
            return null;
        }
        
        return TagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .createTime(tag.getCreateTime())
                .build();
    }
}