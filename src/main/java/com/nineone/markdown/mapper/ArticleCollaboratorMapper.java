package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.ArticleCollaborator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleCollaboratorMapper extends BaseMapper<ArticleCollaborator> {

    @Select("SELECT COUNT(*) FROM article_collaborator WHERE article_id = #{articleId} AND user_id = #{userId} AND permission = 'EDIT'")
    int hasEditPermission(@Param("articleId") Long articleId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM article_collaborator WHERE article_id = #{articleId} AND user_id = #{userId}")
    int isCollaborator(@Param("articleId") Long articleId, @Param("userId") Long userId);
}
