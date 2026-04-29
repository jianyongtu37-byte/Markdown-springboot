package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.layout.font.FontProvider;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.BackupRecord;
import com.nineone.markdown.exception.PermissionDeniedException;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.BackupRecordMapper;
import com.nineone.markdown.service.ExportService;
import com.vladsch.flexmark.docx.converter.DocxRenderer;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.core.io.ClassPathResource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 数据导出服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final ArticleMapper articleMapper;
    private final BackupRecordMapper backupRecordMapper;

    @Value("${app.export.dir:exports}")
    private String exportDir;

    /**
     * Flexmark 解析器和 HTML 渲染器（线程安全，可复用）
     */
    private static final Parser FLEXMARK_PARSER = Parser.builder().build();
    private static final HtmlRenderer FLEXMARK_HTML_RENDERER = HtmlRenderer.builder().build();

    // ==================== PDF 导出 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String exportArticleToPdf(Long articleId, Long userId) {
        // 1. 查询文章并校验权限
        Article article = validateArticlePermission(articleId, userId);

        // 2. 创建备份记录
        BackupRecord record = createBackupRecord(userId, "MANUAL", "PDF", "PROCESSING");

        try {
            // 3. 生成PDF文件
            String filePath = generatePdf(article, userId);
            record.setFilePath(filePath);

            // 4. 获取文件大小
            File file = new File(filePath);
            record.setFileSize(file.length());
            record.setArticleCount(1);
            record.setStatus("SUCCESS");

            log.info("文章PDF导出成功, articleId={}, filePath={}", articleId, filePath);
            return filePath;
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            log.error("文章PDF导出失败, articleId={}", articleId, e);
            throw new RuntimeException("PDF导出失败: " + e.getMessage(), e);
        } finally {
            backupRecordMapper.updateById(record);
        }
    }

    /**
     * 生成PDF文件（使用 iText html2pdf 保留完整格式）
     */
    private String generatePdf(Article article, Long userId) throws Exception {
        // 确保导出目录存在
        String userDir = exportDir + "/pdf/" + userId;
        Files.createDirectories(Paths.get(userDir));

        // 生成文件名：标题_时间戳.pdf
        String safeTitle = sanitizeFileName(article.getTitle());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = safeTitle + "_" + timestamp + ".pdf";
        String filePath = userDir + "/" + fileName;

        // Markdown 转 HTML（使用 Flexmark）
        Node document = FLEXMARK_PARSER.parse(article.getContent());
        String htmlContent = FLEXMARK_HTML_RENDERER.render(document);

        // 构建完整的 HTML 文档（包含基础样式），直接传递给 html2pdf
        String fullHtml = buildStyledHtml(article.getTitle(), htmlContent);

        // 使用 iText html2pdf 直接将 HTML 渲染为 PDF
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            ConverterProperties converterProperties = new ConverterProperties();
            converterProperties.setBaseUri("");
            converterProperties.setCharset("UTF-8"); // 明确设置字符集

            // ========== 【核心修复：配置字体提供者】 ==========
            FontProvider fontProvider = new DefaultFontProvider(false, false, false);
            // 1. 自动加载运行环境的系统字体（在 Windows 开发环境下能直接生效）
            fontProvider.addSystemFonts();

            // 2. 加载项目内置的中文字体（解决 Linux 服务器部署无字体的问题，兼容 JAR 包运行）
            try {
                ClassPathResource fontResource = new ClassPathResource("fonts/simhei.ttf");
                if (fontResource.exists()) {
                    try (InputStream is = fontResource.getInputStream()) {
                        byte[] fontBytes = new byte[is.available()];
                        is.read(fontBytes);
                        FontProgram fontProgram = FontProgramFactory.createFont(fontBytes);
                        fontProvider.addFont(fontProgram);
                        log.info("成功加载内置中文字体：fonts/simhei.ttf");
                    }
                }
            } catch (Exception e) {
                log.warn("无法加载内置中文字体，PDF中文可能无法正常显示", e);
            }

            converterProperties.setFontProvider(fontProvider);
            // =================================================

            HtmlConverter.convertToPdf(fullHtml, fos, converterProperties);
        }

        log.info("PDF文件已生成（html2pdf）: {}", filePath);
        return filePath;
    }

    /**
     * 构建带样式的完整 HTML 文档，用于 PDF 渲染
     * 使用 GitHub 风格的 Markdown CSS，使 PDF 呈现接近前端页面的精美效果
     */
    private String buildStyledHtml(String title, String bodyHtml) {
        // GitHub 风格 Markdown CSS（针对 PDF 打印优化）
        String customCss = "body {\n" +
                "  font-family: 'SimHei', 'Microsoft YaHei', 'SimSun', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans SC', Helvetica, Arial, sans-serif;\n" +
                "  line-height: 1.6;\n" +
                "  color: #24292f;\n" +
                "  margin: 40px auto;\n" +
                "  max-width: 800px;\n" +
                "  padding: 0 20px;\n" +
                "  font-size: 14px;\n" +
                "}\n" +
                "h1, h2, h3, h4, h5, h6 {\n" +
                "  margin-top: 24px;\n" +
                "  margin-bottom: 16px;\n" +
                "  font-weight: 600;\n" +
                "  line-height: 1.25;\n" +
                "}\n" +
                "h1 { font-size: 2em; padding-bottom: .3em; border-bottom: 1px solid #d0d7de; }\n" +
                "h2 { font-size: 1.5em; padding-bottom: .3em; border-bottom: 1px solid #d0d7de; }\n" +
                "h3 { font-size: 1.25em; }\n" +
                "h4 { font-size: 1em; }\n" +
                "p, blockquote, ul, ol, dl, table, pre, details {\n" +
                "  margin-top: 0;\n" +
                "  margin-bottom: 16px;\n" +
                "}\n" +
                "blockquote {\n" +
                "  padding: 0 1em;\n" +
                "  color: #57606a;\n" +
                "  border-left: .25em solid #d0d7de;\n" +
                "}\n" +
                "ul, ol { padding-left: 2em; }\n" +
                "li { margin: 4px 0; }\n" +
                "table {\n" +
                "  border-spacing: 0;\n" +
                "  border-collapse: collapse;\n" +
                "  width: 100%;\n" +
                "  margin-bottom: 16px;\n" +
                "}\n" +
                "table th, table td {\n" +
                "  padding: 6px 13px;\n" +
                "  border: 1px solid #d0d7de;\n" +
                "}\n" +
                "table th {\n" +
                "  font-weight: 600;\n" +
                "  background-color: #f6f8fa;\n" +
                "}\n" +
                "table tr:nth-child(2n) { background-color: #f6f8fa; }\n" +
                "code, tt {\n" +
                "  padding: .2em .4em;\n" +
                "  margin: 0;\n" +
                "  font-size: 85%;\n" +
                "  background-color: rgba(175,184,193,0.2);\n" +
                "  border-radius: 6px;\n" +
                "  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, 'Liberation Mono', monospace;\n" +
                "}\n" +
                "pre {\n" +
                "  padding: 16px;\n" +
                "  overflow: auto;\n" +
                "  font-size: 85%;\n" +
                "  line-height: 1.45;\n" +
                "  background-color: #f6f8fa;\n" +
                "  border-radius: 6px;\n" +
                "  border: 1px solid #d0d7de;\n" +
                "}\n" +
                "pre code {\n" +
                "  padding: 0;\n" +
                "  background-color: transparent;\n" +
                "  border-radius: 0;\n" +
                "}\n" +
                "img {\n" +
                "  max-width: 100%;\n" +
                "  box-sizing: content-box;\n" +
                "  background-color: #fff;\n" +
                "}\n" +
                "hr {\n" +
                "  border: none;\n" +
                "  border-top: 1px solid #d0d7de;\n" +
                "  margin: 24px 0;\n" +
                "}\n" +
                "a { color: #0969da; text-decoration: none; }\n" +
                "a:hover { text-decoration: underline; }\n" +
                "strong { font-weight: 600; }\n" +
                "del { color: #57606a; }\n" +
                "input[type='checkbox'] { margin-right: 6px; }\n";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<style>\n" + customCss + "</style>\n" +
                "</head>\n" +
                "<body class=\"markdown-body\">\n" +
                "  <h1>" + escapeHtml(title) + "</h1>\n" +
                "  " + bodyHtml + "\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * 转义 HTML 特殊字符
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append('&').append("amp;");
                    break;
                case '<':
                    sb.append('&').append("lt;");
                    break;
                case '>':
                    sb.append('&').append("gt;");
                    break;
                case '"':
                    sb.append('&').append("quot;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    // ==================== Word 导出 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String exportArticleToWord(Long articleId, Long userId) {
        // 1. 查询文章并校验权限
        Article article = validateArticlePermission(articleId, userId);

        // 2. 创建备份记录
        BackupRecord record = createBackupRecord(userId, "MANUAL", "WORD", "PROCESSING");

        try {
            // 3. 生成Word文件
            String filePath = generateWord(article, userId);
            record.setFilePath(filePath);

            // 4. 获取文件大小
            File file = new File(filePath);
            record.setFileSize(file.length());
            record.setArticleCount(1);
            record.setStatus("SUCCESS");

            log.info("文章Word导出成功, articleId={}, filePath={}", articleId, filePath);
            return filePath;
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            log.error("文章Word导出失败, articleId={}", articleId, e);
            throw new RuntimeException("Word导出失败: " + e.getMessage(), e);
        } finally {
            backupRecordMapper.updateById(record);
        }
    }

    /**
     * 生成Word (.docx) 文件（使用 Flexmark DocxRenderer + docx4j）
     * <p>
     * 优先加载自定义 Word 模板（markdown-template.docx），
     * 该模板预定义了 Heading 1-4、Normal、Code、Quote 等样式，
     * 使导出的 Word 文档更加精美。
     * 如果模板加载失败，则降级使用默认模板。
     */
    private String generateWord(Article article, Long userId) throws Exception {
        // 确保导出目录存在
        String userDir = exportDir + "/word/" + userId;
        Files.createDirectories(Paths.get(userDir));

        // 生成文件名
        String safeTitle = sanitizeFileName(article.getTitle());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = safeTitle + "_" + timestamp + ".docx";
        String filePath = userDir + "/" + fileName;

        // 使用 Flexmark 解析 Markdown
        Parser flexmarkParser = Parser.builder().build();
        Node flexmarkDocument = flexmarkParser.parse(article.getContent());

        // 配置 Docx 渲染选项
        MutableDataSet options = new MutableDataSet();

        // 【关键优化】优先加载自定义 Word 模板，降级使用默认模板
        DocxRenderer docxRenderer = DocxRenderer.builder(options).build();
        WordprocessingMLPackage wordMLPackage;
        try {
            ClassPathResource resource = new ClassPathResource("templates/markdown-template.docx");
            try (InputStream is = resource.getInputStream()) {
                wordMLPackage = WordprocessingMLPackage.load(is);
                log.info("成功加载自定义 Word 模板: templates/markdown-template.docx");
            }
        } catch (Exception e) {
            log.warn("无法加载自定义 Word 模板，降级使用默认模板", e);
            wordMLPackage = DocxRenderer.getDefaultTemplate(options);
        }

        // 渲染并保存
        docxRenderer.render(flexmarkDocument, wordMLPackage);
        wordMLPackage.save(new File(filePath));

        log.info("Word文件已生成（Flexmark DocxRenderer + docx4j）: {}", filePath);
        return filePath;
    }

    // ==================== 全站Markdown打包导出 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String exportAllArticlesAsMarkdownZip(Long userId) {
        // 1. 查询用户所有文章
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Article::getUserId, userId)
                .eq(Article::getDeleted, 0)
                .orderByDesc(Article::getCreateTime);
        List<Article> articles = articleMapper.selectList(queryWrapper);

        if (articles.isEmpty()) {
            throw new RuntimeException("没有可导出的文章");
        }

        // 2. 创建备份记录
        BackupRecord record = createBackupRecord(userId, "MANUAL", "MARKDOWN_ZIP", "PROCESSING");

        try {
            // 3. 生成ZIP文件
            String filePath = generateMarkdownZip(articles, userId);
            record.setFilePath(filePath);

            // 4. 获取文件大小
            File file = new File(filePath);
            record.setFileSize(file.length());
            record.setArticleCount(articles.size());
            record.setStatus("SUCCESS");

            log.info("全站Markdown导出成功, userId={}, filePath={}, articleCount={}",
                    userId, filePath, articles.size());
            return filePath;
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            log.error("全站Markdown导出失败, userId={}", userId, e);
            throw new RuntimeException("全站导出失败: " + e.getMessage(), e);
        } finally {
            backupRecordMapper.updateById(record);
        }
    }

    /**
     * 生成Markdown文件的ZIP包
     */
    private String generateMarkdownZip(List<Article> articles, Long userId) throws Exception {
        // 确保导出目录存在
        String userDir = exportDir + "/markdown-zip/" + userId;
        Files.createDirectories(Paths.get(userDir));

        // 生成文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "all_articles_" + timestamp + ".zip";
        String filePath = userDir + "/" + fileName;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
            for (Article article : articles) {
                // 构建Markdown内容（包含元数据头）
                StringBuilder mdContent = new StringBuilder();
                mdContent.append("---\n");
                mdContent.append("title: \"").append(escapeYaml(article.getTitle())).append("\"\n");
                mdContent.append("id: ").append(article.getId()).append("\n");
                mdContent.append("status: ").append(article.getStatus()).append("\n");
                mdContent.append("created: ").append(article.getCreateTime()).append("\n");
                mdContent.append("updated: ").append(article.getUpdateTime()).append("\n");
                if (article.getSummary() != null && !article.getSummary().isEmpty()) {
                    mdContent.append("summary: \"").append(escapeYaml(article.getSummary())).append("\"\n");
                }
                mdContent.append("---\n\n");
                mdContent.append("# ").append(article.getTitle()).append("\n\n");
                mdContent.append(article.getContent());

                // 安全文件名
                String safeTitle = sanitizeFileName(article.getTitle());
                if (safeTitle.length() > 100) {
                    safeTitle = safeTitle.substring(0, 100);
                }
                String entryName = safeTitle + ".md";

                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(System.currentTimeMillis());
                zos.putNextEntry(entry);
                zos.write(mdContent.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }

        log.info("ZIP文件已生成: {}, 包含{}篇文章", filePath, articles.size());
        return filePath;
    }

    /**
     * 转义YAML中的特殊字符
     */
    private String escapeYaml(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // ==================== 备份记录管理 ====================

    @Override
    public List<BackupRecord> getExportRecords(Long userId) {
        LambdaQueryWrapper<BackupRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BackupRecord::getUserId, userId)
                .orderByDesc(BackupRecord::getCreateTime);
        return backupRecordMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExportRecord(Long recordId, Long userId) {
        BackupRecord record = backupRecordMapper.selectById(recordId);
        if (record == null) {
            throw new RuntimeException("导出记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new PermissionDeniedException("您没有权限删除此记录");
        }

        // 删除物理文件
        if (record.getFilePath() != null) {
            try {
                File file = new File(record.getFilePath());
                if (file.exists()) {
                    boolean deleted = file.delete();
                    log.info("删除导出文件: {}, result={}", record.getFilePath(), deleted);
                }
            } catch (Exception e) {
                log.warn("删除导出文件失败: {}", record.getFilePath(), e);
            }
        }

        // 删除数据库记录
        backupRecordMapper.deleteById(recordId);
    }

    // ==================== 内部方法 ====================

    /**
     * 校验文章权限并返回文章
     * 规则：
     * 1. 文章作者本人可以导出
     * 2. 非作者导出时，需要检查 allowExport 是否为 1（允许导出）
     */
    private Article validateArticlePermission(Long articleId, Long userId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }
        // 如果是文章作者本人，直接允许导出
        if (article.getUserId().equals(userId)) {
            return article;
        }
        // 非作者导出时，检查作者是否开启了允许导出
        if (article.getAllowExport() == null || article.getAllowExport() != 1) {
            throw new PermissionDeniedException("作者已禁止导出此文章");
        }
        return article;
    }

    /**
     * 创建备份记录
     */
    private BackupRecord createBackupRecord(Long userId, String backupType, String format, String status) {
        BackupRecord record = BackupRecord.builder()
                .userId(userId)
                .backupType(backupType)
                .format(format)
                .status(status)
                .build();
        backupRecordMapper.insert(record);
        return record;
    }

    /**
     * 清理文件名中的非法字符
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "";
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
