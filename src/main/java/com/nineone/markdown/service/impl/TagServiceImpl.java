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
    public Long createTag(Tag tag) {
        // 参数验证
        Assert.notNull(tag, "标签不能为空");
        Assert.hasText(tag.getName(), "标签名称不能为空");
        
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
    public boolean updateTag(Tag tag) {
        Assert.notNull(tag, "标签不能为空");
        Assert.notNull(tag.getId(), "标签ID不能为空");
        
        // 检查标签是否存在
        Tag existingTag = tagMapper.selectById(tag.getId());
        if (existingTag == null) {
            return false;
        }
        
        // 如果名称有变化，检查新名称是否已存在
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
    public List<TagVO> getPopularTags(Integer limit) {
        Assert.notNull(limit, "数量限制不能为空");
        Assert.isTrue(limit > 0, "数量限制必须大于0");
        
        // 获取所有标签
        List<TagVO> allTags = getAllTags();
        
        // 为每个标签统计使用次数
        List<TagWithUsage> tagWithUsages = new java.util.ArrayList<>();
        for (TagVO tagVO : allTags) {
            LambdaQueryWrapper<ArticleTag> articleTagQuery = new LambdaQueryWrapper<>();
            articleTagQuery.eq(ArticleTag::getTagId, tagVO.getId());
            Long useCount = articleTagMapper.selectCount(articleTagQuery);
            
            Tag tag = new Tag();
            tag.setId(tagVO.getId());
            tag.setName(tagVO.getName());
            tag.setCreateTime(tagVO.getCreateTime());
            
            tagWithUsages.add(new TagWithUsage(tag, useCount != null ? useCount : 0L));
        }
        
        // 按使用次数降序排序
        tagWithUsages.sort((a, b) -> Long.compare(b.useCount, a.useCount));
        
        // 获取前limit个标签
        List<Tag> popularTags = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(limit, tagWithUsages.size()); i++) {
            popularTags.add(tagWithUsages.get(i).tag);
        }
        
        // 转换为VO
        List<TagVO> result = new java.util.ArrayList<>();
        for (Tag tag : popularTags) {
            result.add(convertToVO(tag));
        }
        return result;
    }

    @Override
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
     * 内部类：标签使用统计
     */
    private static class TagWithUsage {
        Tag tag;
        Long useCount;
        
        TagWithUsage(Tag tag, Long useCount) {
            this.tag = tag;
            this.useCount = useCount;
        }
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