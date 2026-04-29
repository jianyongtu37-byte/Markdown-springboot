package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.entity.Tag;
import com.nineone.markdown.service.TagService;
import com.nineone.markdown.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 标签控制器
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Validated
public class TagController {

    private final TagService tagService;

    /**
     * 创建标签
     */
    @PostMapping
    public Result<Long> createTag(@Valid @RequestBody Tag tag) {
        Long tagId = tagService.createTag(tag);
        return Result.success("标签创建成功", tagId);
    }

    /**
     * 获取标签详情
     */
    @GetMapping("/{id}")
    public Result<Tag> getTag(@PathVariable Long id) {
        Tag tag = tagService.getTag(id);
        if (tag == null) {
            return Result.<Tag>notFound("标签不存在");
        }
        return Result.success(tag);
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public Result<Void> updateTag(@PathVariable Long id, @Valid @RequestBody Tag tag) {
        tag.setId(id);
        boolean success = tagService.updateTag(tag);
        if (!success) {
            return Result.<Void>builder().code(404).message("标签不存在，更新失败").build();
        }
        return Result.<Void>builder().code(200).message("标签更新成功").build();
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        boolean success = tagService.deleteTag(id);
        if (!success) {
            return Result.<Void>builder().code(404).message("标签不存在，删除失败").build();
        }
        return Result.<Void>builder().code(200).message("标签删除成功").build();
    }

    /**
     * 获取所有标签列表
     */
    @GetMapping
    public Result<List<TagVO>> getAllTags() {
        List<TagVO> tags = tagService.getAllTags();
        return Result.success(tags);
    }

    /**
     * 搜索标签
     */
    @GetMapping("/search")
    public Result<List<TagVO>> searchTags(@RequestParam String keyword) {
        List<TagVO> tags = tagService.searchTags(keyword);
        return Result.success(tags);
    }

    /**
     * 获取所有标签名称列表
     */
    @GetMapping("/names")
    public Result<List<String>> getTagNames() {
        List<String> tagNames = tagService.getTagNames();
        return Result.success(tagNames);
    }

    /**
     * 获取热门标签
     */
    @GetMapping("/popular")
    public Result<List<TagVO>> getPopularTags(@RequestParam(defaultValue = "10") Integer limit) {
        List<TagVO> tags = tagService.getPopularTags(limit);
        return Result.success(tags);
    }
}