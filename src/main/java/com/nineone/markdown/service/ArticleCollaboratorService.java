package com.nineone.markdown.service;

import com.nineone.common.result.PageResult;
import com.nineone.markdown.entity.ArticleCollaborator;
import com.nineone.markdown.vo.ArticleVO;

import java.util.List;

public interface ArticleCollaboratorService {

    void addCollaborator(Long articleId, Long ownerUserId, Long collaboratorUserId, String permission);

    void removeCollaborator(Long articleId, Long ownerUserId, Long collaboratorUserId);

    List<ArticleCollaborator> getCollaborators(Long articleId);

    PageResult<ArticleVO> getSharedArticles(Long userId, Integer pageNum, Integer pageSize);
}
