package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.ArticleVersion;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.ArticleVersionMapper;
import com.nineone.markdown.service.ArticleVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文章版本历史服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleVersionServiceImpl implements ArticleVersionService {

    private final ArticleVersionMapper articleVersionMapper;
    private final ArticleMapper articleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveVersion(Long articleId, String title, String content, String summary, 
                           String changeNote, Long operatorId, String operatorName) {
        // 获取当前最大版本号
        Integer maxVersion = articleVersionMapper.getMaxVersion(articleId);
        int newVersion = maxVersion + 1;

        ArticleVersion version = ArticleVersion.builder()
                .articleId(articleId)
                .version(newVersion)
                .title(title)
                .content(content)
                .summary(summary)
                .changeNote(changeNote)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .build();

        articleVersionMapper.insert(version);
        log.info("文章版本快照保存成功, 文章ID: {}, 版本号: {}, 修改备注: {}", articleId, newVersion, changeNote);
    }

    @Override
    public List<ArticleVersion> getVersionsByArticleId(Long articleId) {
        LambdaQueryWrapper<ArticleVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleVersion::getArticleId, articleId)
                   .orderByDesc(ArticleVersion::getVersion);
        return articleVersionMapper.selectList(queryWrapper);
    }

    @Override
    public ArticleVersion getVersionById(Long versionId) {
        ArticleVersion version = articleVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException("版本不存在");
        }
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackToVersion(Long articleId, Long versionId, Long operatorId, String operatorName) {
        // 获取目标版本
        ArticleVersion targetVersion = articleVersionMapper.selectById(versionId);
        if (targetVersion == null) {
            throw new BizException("目标版本不存在");
        }
        if (!targetVersion.getArticleId().equals(articleId)) {
            throw new BizException("版本不属于该文章");
        }

        // 获取当前文章（忽略数据权限拦截器，因为权限已由调用方检查）
        Article article = articleMapper.selectByIdIgnorePermission(articleId);
        if (article == null) {
            throw new BizException("文章不存在");
        }

        // 先保存当前版本快照（作为回滚前的备份）
        saveVersion(articleId, article.getTitle(), article.getContent(), article.getSummary(),
                "回滚前自动备份（回滚到版本" + targetVersion.getVersion() + "）", operatorId, operatorName);

        // 回滚文章内容到目标版本
        article.setTitle(targetVersion.getTitle());
        article.setContent(targetVersion.getContent());
        article.setSummary(targetVersion.getSummary());
        articleMapper.updateById(article);

        log.info("文章回滚成功, 文章ID: {}, 回滚到版本: {}, 操作者: {}", articleId, targetVersion.getVersion(), operatorName);
    }

    @Override
    public String diffVersions(Long versionId1, Long versionId2) {
        ArticleVersion v1 = articleVersionMapper.selectById(versionId1);
        ArticleVersion v2 = articleVersionMapper.selectById(versionId2);

        if (v1 == null || v2 == null) {
            throw new BizException("版本不存在");
        }

        // 简单的文本差异比较（基于行的比较）
        StringBuilder diff = new StringBuilder();
        diff.append("=== 版本差异对比 ===\n");
        diff.append("版本").append(v1.getVersion()).append(" vs 版本").append(v2.getVersion()).append("\n\n");

        String[] lines1 = v1.getContent().split("\n");
        String[] lines2 = v2.getContent().split("\n");

        int maxLines = Math.max(lines1.length, lines2.length);
        int changes = 0;

        for (int i = 0; i < maxLines; i++) {
            String line1 = i < lines1.length ? lines1[i] : "";
            String line2 = i < lines2.length ? lines2[i] : "";

            if (!line1.equals(line2)) {
                changes++;
                if (changes <= 50) { // 最多显示50处差异
                    if (!line1.isEmpty()) {
                        diff.append("- ").append(line1).append("\n");
                    }
                    if (!line2.isEmpty()) {
                        diff.append("+ ").append(line2).append("\n");
                    }
                }
            }
        }

        if (changes == 0) {
            diff.append("两个版本内容完全相同\n");
        } else {
            diff.append("\n共 ").append(changes).append(" 处差异");
            if (changes > 50) {
                diff.append("（仅显示前50处）");
            }
            diff.append("\n");
        }

        return diff.toString();
    }
}
