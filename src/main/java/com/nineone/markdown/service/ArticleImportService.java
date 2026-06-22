package com.nineone.markdown.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ArticleImportService {

    List<Long> importFromFiles(MultipartFile[] files, Long userId, Long categoryId);

    Long importFromUrl(String url, Long userId, Long categoryId);
}
