package com.nineone.markdown.util;

import lombok.extern.slf4j.Slf4j;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart;
import org.docx4j.wml.*;

import java.io.File;
import java.math.BigInteger;

/**
 * Word 模板生成工具
 * <p>
 * 生成一个预定义好样式的 Word 模板文件（markdown-template.docx），
 * 用于 Flexmark DocxRenderer 导出时映射样式，使导出的 Word 文档更加精美。
 * <p>
 * 使用方法：直接运行 main 方法即可生成模板文件到 resources/templates/ 目录下。
 */
@Slf4j
public class WordTemplateGenerator {

    private static final String TEMPLATE_PATH = "src/main/resources/templates/markdown-template.docx";

    public static void main(String[] args) throws Exception {
        generateTemplate();
        log.info("Word 模板文件已生成: {}", new File(TEMPLATE_PATH).getAbsolutePath());
    }

    /**
     * 生成精美的 Word 模板文件
     */
    public static void generateTemplate() throws Exception {
        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.createPackage();
        MainDocumentPart mainDocumentPart = wordMLPackage.getMainDocumentPart();

        // 获取样式定义部分
        StyleDefinitionsPart stylePart = mainDocumentPart.getStyleDefinitionsPart();
        Styles styles = stylePart.getJaxbElement();
        if (styles == null) {
            styles = Context.getWmlObjectFactory().createStyles();
        }

        ObjectFactory factory = Context.getWmlObjectFactory();

        // ========== 1. 定义 Normal（正文）样式 ==========
        addParagraphStyle(factory, styles, "Normal", "Normal", null,
                "Microsoft YaHei", "000000", 21, false, false,
                BigInteger.valueOf(360), BigInteger.valueOf(120)); // 1.5倍行距，段后3磅

        // ========== 2. 定义 Heading 1（标题1）样式 ==========
        addParagraphStyle(factory, styles, "heading 1", "heading 1", "Normal",
                "Microsoft YaHei", "1A1A1A", 36, true, false,
                BigInteger.valueOf(360), BigInteger.valueOf(120));

        // ========== 3. 定义 Heading 2（标题2）样式 ==========
        addParagraphStyle(factory, styles, "heading 2", "heading 2", "Normal",
                "Microsoft YaHei", "2C3E50", 28, true, false,
                BigInteger.valueOf(360), BigInteger.valueOf(120));

        // ========== 4. 定义 Heading 3（标题3）样式 ==========
        addParagraphStyle(factory, styles, "heading 3", "heading 3", "Normal",
                "Microsoft YaHei", "34495E", 24, true, false,
                BigInteger.valueOf(360), BigInteger.valueOf(60));

        // ========== 5. 定义 Heading 4（标题4）样式 ==========
        addParagraphStyle(factory, styles, "heading 4", "heading 4", "Normal",
                "Microsoft YaHei", "555555", 22, true, false,
                BigInteger.valueOf(360), BigInteger.valueOf(60));

        // ========== 6. 定义 Code（代码字符样式） ==========
        addCharacterStyle(factory, styles, "Code", "Code", "DefaultParagraphFont",
                "Consolas", "24292F", 18, false, false);

        // ========== 7. 定义 Quote（引用段落样式） ==========
        addParagraphStyle(factory, styles, "Quote", "Quote", "Normal",
                "Microsoft YaHei", "57606A", 21, false, true,
                BigInteger.valueOf(360), BigInteger.valueOf(120));

        // 添加一些示例内容
        mainDocumentPart.addStyledParagraphOfText("heading 1", "标题示例（Heading 1）");
        mainDocumentPart.addStyledParagraphOfText("Normal", "这是一段正文示例文字，展示了 Normal 样式的效果。Microsoft YaHei 字体，五号字，1.5倍行距。");
        mainDocumentPart.addStyledParagraphOfText("heading 2", "二级标题示例（Heading 2）");
        mainDocumentPart.addStyledParagraphOfText("Normal", "这是二级标题下的正文内容。");
        mainDocumentPart.addStyledParagraphOfText("heading 3", "三级标题示例（Heading 3）");
        mainDocumentPart.addStyledParagraphOfText("Normal", "这是三级标题下的正文内容。");
        mainDocumentPart.addStyledParagraphOfText("Quote", "这是一段引用文字，展示了 Quote 样式的效果。引用通常用于标注重要的引述内容。");
        mainDocumentPart.addStyledParagraphOfText("Normal", "下面是一段代码示例：");

        // 保存模板
        wordMLPackage.save(new File(TEMPLATE_PATH));
    }

    /**
     * 添加段落样式
     */
    private static void addParagraphStyle(ObjectFactory factory, Styles styles,
                                          String styleId, String styleName, String basedOn,
                                          String fontName, String fontColor, int fontSizeHalfPoints,
                                          boolean bold, boolean italic,
                                          BigInteger lineSpacing, BigInteger afterSpacing) {
        Style style = factory.createStyle();
        style.setStyleId(styleId);
        // 使用字符串 "paragraph" 替代 STStyleType.PARAGRAPH
        style.setType("paragraph");

        // 使用 Style.Name 内部类替代 StyleName
        Style.Name name = factory.createStyleName();
        name.setVal(styleName);
        style.setName(name);

        if (basedOn != null) {
            // 使用 Style.BasedOn 内部类替代 StyleBasedOn
            Style.BasedOn sbo = factory.createStyleBasedOn();
            sbo.setVal(basedOn);
            style.setBasedOn(sbo);
        }

        // 段落属性
        PPr ppr = factory.createPPr();
        PPrBase.Spacing spacing = new PPrBase.Spacing();
        spacing.setLine(lineSpacing);
        spacing.setAfter(afterSpacing);
        ppr.setSpacing(spacing);
        style.setPPr(ppr);

        // 字符属性
        RPr rpr = factory.createRPr();
        RFonts fonts = factory.createRFonts();
        fonts.setAscii(fontName);
        fonts.setHAnsi(fontName);
        fonts.setEastAsia(fontName);
        rpr.setRFonts(fonts);

        HpsMeasure sz = factory.createHpsMeasure();
        sz.setVal(BigInteger.valueOf(fontSizeHalfPoints));
        rpr.setSz(sz);
        rpr.setSzCs(sz);

        Color color = factory.createColor();
        color.setVal(fontColor);
        rpr.setColor(color);

        if (bold) {
            BooleanDefaultTrue b = new BooleanDefaultTrue();
            b.setVal(true);
            rpr.setB(b);
        }
        if (italic) {
            BooleanDefaultTrue i = new BooleanDefaultTrue();
            i.setVal(true);
            rpr.setI(i);
        }

        style.setRPr(rpr);
        styles.getStyle().add(style);
    }

    /**
     * 添加字符样式（用于代码等内联样式）
     */
    private static void addCharacterStyle(ObjectFactory factory, Styles styles,
                                          String styleId, String styleName, String basedOn,
                                          String fontName, String fontColor, int fontSizeHalfPoints,
                                          boolean bold, boolean italic) {
        Style style = factory.createStyle();
        style.setStyleId(styleId);
        // 使用字符串 "character" 替代 STStyleType.CHARACTER
        style.setType("character");

        // 使用 Style.Name 内部类
        Style.Name name = factory.createStyleName();
        name.setVal(styleName);
        style.setName(name);

        if (basedOn != null) {
            // 使用 Style.BasedOn 内部类
            Style.BasedOn sbo = factory.createStyleBasedOn();
            sbo.setVal(basedOn);
            style.setBasedOn(sbo);
        }

        // 字符属性
        RPr rpr = factory.createRPr();
        RFonts fonts = factory.createRFonts();
        fonts.setAscii(fontName);
        fonts.setHAnsi(fontName);
        fonts.setEastAsia(fontName);
        rpr.setRFonts(fonts);

        HpsMeasure sz = factory.createHpsMeasure();
        sz.setVal(BigInteger.valueOf(fontSizeHalfPoints));
        rpr.setSz(sz);
        rpr.setSzCs(sz);

        Color color = factory.createColor();
        color.setVal(fontColor);
        rpr.setColor(color);

        if (bold) {
            BooleanDefaultTrue b = new BooleanDefaultTrue();
            b.setVal(true);
            rpr.setB(b);
        }
        if (italic) {
            BooleanDefaultTrue i = new BooleanDefaultTrue();
            i.setVal(true);
            rpr.setI(i);
        }

        style.setRPr(rpr);
        styles.getStyle().add(style);
    }
}
