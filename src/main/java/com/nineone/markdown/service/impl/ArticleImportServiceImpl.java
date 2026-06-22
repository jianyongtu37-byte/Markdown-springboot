package com.nineone.markdown.service.impl;

import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.Image;
import com.nineone.markdown.enums.ArticleStatusEnum;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.mapper.CategoryMapper;
import com.nineone.markdown.service.ArticleImportService;
import com.nineone.markdown.service.ArticleService;
import com.nineone.markdown.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleImportServiceImpl implements ArticleImportService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("md", "markdown", "txt", "pdf", "docx");

    private final ArticleService articleService;
    private final CategoryMapper categoryMapper;
    private final ImageService imageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> importFromFiles(MultipartFile[] files, Long userId, Long categoryId) {
        if (categoryId != null && categoryMapper.selectById(categoryId) == null) {
            throw new BizException("指定的分类不存在");
        }

        List<Long> articleIds = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                String originalFilename = file.getOriginalFilename();

                // 文件类型校验
                String extension = getExtension(originalFilename);
                if (!ALLOWED_EXTENSIONS.contains(extension)) {
                    throw new BizException("不支持的文件类型: " + extension + "，仅支持 md/txt/pdf/docx 文件");
                }

                // 文件大小校验
                if (file.getSize() > MAX_FILE_SIZE) {
                    throw new BizException("文件过大: " + originalFilename + "，最大支持 10MB");
                }

                if (file.isEmpty()) {
                    throw new BizException("文件为空: " + originalFilename);
                }

                // 根据文件类型提取内容（含图片）
                String content;
                switch (extension) {
                    case "pdf" -> content = extractPdfContent(file.getBytes(), userId);
                    case "docx" -> content = extractDocxContent(file.getBytes(), userId);
                    default -> content = new String(file.getBytes(), StandardCharsets.UTF_8);
                }

                if (content.isBlank()) {
                    throw new BizException("文件内容为空: " + originalFilename);
                }

                String title = originalFilename != null
                        ? originalFilename.replaceAll("\\.(?i)(md|markdown|txt|pdf|docx)$", "")
                        : "导入文章";

                Article article = Article.builder()
                        .userId(userId)
                        .categoryId(categoryId)
                        .title(title)
                        .content(content)
                        .status(ArticleStatusEnum.DRAFT)
                        .allowExport(1)
                        .build();

                Long id = articleService.createArticle(article, null);
                articleIds.add(id);
                log.info("文件导入成功, articleId={}, filename={}", id, originalFilename);
            } catch (IOException e) {
                log.error("文件读取失败: {}", file.getOriginalFilename(), e);
                throw new BizException("文件读取失败: " + file.getOriginalFilename());
            }
        }
        return articleIds;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 从 PDF 提取文本 + 图片，图片保存到 ImageService 并在文本中插入 markdown 引用
     */
    private String extractPdfContent(byte[] bytes, Long userId) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            StringBuilder sb = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();

            for (int i = 0; i < totalPages; i++) {
                if (i > 0) sb.append("\n\n");

                // 提取当前页文本
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(document).trim();
                if (!pageText.isEmpty()) {
                    sb.append(pageText);
                }

                // 提取当前页嵌入的图片
                PDPage page = document.getPage(i);
                PDResources resources = page.getResources();
                if (resources != null) {
                    for (var name : resources.getXObjectNames()) {
                        PDXObject xobj = resources.getXObject(name);
                        if (xobj instanceof PDImageXObject image) {
                            String url = saveBufferedImage(image.getImage(), "png", userId);
                            if (url != null) {
                                sb.append("\n\n![](").append(url).append(")");
                            }
                        }
                    }
                }

                // 如果当前页没有嵌入图片，整页渲染为图片
                boolean hasEmbeddedImage = false;
                if (resources != null) {
                    for (var name : resources.getXObjectNames()) {
                        try {
                            if (resources.getXObject(name) instanceof PDImageXObject) {
                                hasEmbeddedImage = true;
                                break;
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
                if (!hasEmbeddedImage) {
                    BufferedImage rendered = renderer.renderImageWithDPI(i, 150);
                    String url = saveBufferedImage(rendered, "png", userId);
                    if (url != null) {
                        sb.append("\n\n![](").append(url).append(")");
                    }
                }
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("PDF解析失败", e);
            throw new BizException("PDF文件解析失败，请检查文件是否损坏");
        }
    }

    /**
     * 从 Word 提取文本 + 图片，按文档顺序插入
     */
    private String extractDocxContent(byte[] bytes, Long userId) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    // 收集段落中的图片
                    for (XWPFRun run : paragraph.getRuns()) {
                        for (XWPFPicture pic : run.getEmbeddedPictures()) {
                            byte[] picData = pic.getPictureData().getData();
                            String ext = pic.getPictureData().suggestFileExtension();
                            String url = saveImage(picData, ext != null ? ext : "png", userId);
                            if (url != null) {
                                sb.append("\n\n![](").append(url).append(")");
                            }
                        }
                    }

                    // 段落文本
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        // 根据段落样式添加 Markdown 标题前缀
                        String styleId = paragraph.getStyleID();
                        int headingLevel = getHeadingLevel(styleId, paragraph);
                        if (headingLevel > 0) {
                            sb.append("\n\n").append("#".repeat(headingLevel)).append(" ").append(text);
                        } else {
                            sb.append("\n\n").append(text);
                        }
                    }
                } else if (element instanceof XWPFTable table) {
                    // 简单处理表格：转为文本
                    sb.append("\n\n");
                    List<XWPFTableRow> rows = table.getRows();
                    for (int r = 0; r < rows.size(); r++) {
                        XWPFTableRow row = rows.get(r);
                        sb.append("| ");
                        for (XWPFTableCell cell : row.getTableCells()) {
                            sb.append(cell.getText()).append(" | ");
                        }
                        sb.append("\n");
                        if (r == 0) {
                            sb.append("| ").append("--- | ".repeat(row.getTableCells().size())).append("\n");
                        }
                    }
                }
            }

            return sb.toString().trim();
        } catch (IOException e) {
            log.error("DOCX解析失败", e);
            throw new BizException("Word文件解析失败，请检查文件是否损坏");
        }
    }

    private int getHeadingLevel(String styleId, XWPFParagraph paragraph) {
        if (styleId == null) return 0;
        if (styleId.matches("(?i)heading[1-9]")) {
            return Character.getNumericValue(styleId.charAt(styleId.length() - 1));
        }
        // 尝试从段落的格式判断
        if (paragraph.getCTP() != null && paragraph.getCTP().getPPr() != null) {
            var outlineLvl = paragraph.getCTP().getPPr().getOutlineLvl();
            if (outlineLvl != null && outlineLvl.getVal().intValue() < 6) {
                return outlineLvl.getVal().intValue() + 1;
            }
        }
        return 0;
    }

    /**
     * 保存图片字节数组到 ImageService，返回访问 URL
     */
    private String saveImage(byte[] data, String extension, Long userId) {
        String ext = extension.startsWith(".") ? extension : "." + extension;
        String contentType = switch (ext.toLowerCase()) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            default -> "image/png";
        };
        String filename = UUID.randomUUID() + ext;
        try {
            Image image = imageService.uploadImageBytes(data, filename, contentType, userId);
            return imageService.getImageUrl(image.getId());
        } catch (Exception e) {
            log.warn("导入图片保存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存 BufferedImage 到 ImageService，返回访问 URL
     */
    private String saveBufferedImage(BufferedImage image, String format, Long userId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, format, baos);
            byte[] data = baos.toByteArray();
            return saveImage(data, format, userId);
        } catch (IOException e) {
            log.warn("图片转换失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importFromUrl(String url, Long userId, Long categoryId) {
        // SSRF 防护：校验 URL 协议和主机
        validateUrl(url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .followRedirects(false)
                    .get();

            String title = doc.title();
            if (title == null || title.isBlank()) {
                title = "导入文章 - " + url;
            }

            String htmlContent = doc.body() != null ? doc.body().html() : doc.html();
            String content = "> 本文从 [" + url + "](" + url + ") 导入\n\n" + htmlContent;

            Article article = Article.builder()
                    .userId(userId)
                    .categoryId(categoryId)
                    .title(title)
                    .content(content)
                    .status(ArticleStatusEnum.DRAFT)
                    .allowExport(1)
                    .build();

            Long id = articleService.createArticle(article, null);
            log.info("URL导入成功, articleId={}, url={}", id, url);
            return id;
        } catch (IOException e) {
            log.error("URL抓取失败: {}", url, e);
            throw new BizException("URL抓取失败，请检查链接是否可访问");
        }
    }

    /**
     * SSRF 防护：校验 URL 协议和主机，禁止访问内网地址
     */
    private void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new BizException("仅支持 http 和 https 协议的 URL");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new BizException("URL 格式无效，缺少主机名");
            }

            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()) {
                throw new BizException("不允许访问内网地址");
            }

            // 额外检查特殊地址
            byte[] addr = address.getAddress();
            if (addr.length == 4) {
                int first = addr[0] & 0xFF;
                int second = addr[1] & 0xFF;
                // 0.0.0.0/8, 127.0.0.0/8, 169.254.0.0/16
                if (first == 0 || first == 127 || (first == 169 && second == 254)) {
                    throw new BizException("不允许访问保留地址");
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("URL 格式无效");
        }
    }
}
