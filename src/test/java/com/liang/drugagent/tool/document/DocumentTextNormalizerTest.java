package com.liang.drugagent.tool.document;

import com.liang.drugagent.tool.document.support.DocumentTextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentTextNormalizer 单元测试。
 *
 * <p>验证文本清洗能力：
 * <ol>
 *   <li>合并多个连续空白</li>
 *   <li>合并多个连续换行（最多保留两个）</li>
 *   <li>清理特殊空白字符</li>
 * </ol>
 */
class DocumentTextNormalizerTest {

    private DocumentTextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new DocumentTextNormalizer();
    }

    @Test
    void should合并多个连续空白() {
        String input = "这是    多个   空格";
        String result = normalizer.normalize(input);
        assertEquals("这是 多个 空格", result);
    }

    @Test
    void should合并多个连续空白_含制表符() {
        String input = "字段1\t\t字段2";
        String result = normalizer.normalize(input);
        assertEquals("字段1 字段2", result);
    }

    @Test
    void should合并多个连续换行_最多保留两个() {
        String input = "第一段\n\n\n\n第二段";
        String result = normalizer.normalize(input);
        assertEquals("第一段\n\n第二段", result);
    }

    @Test
    void should合并多个连续换行_保留原有单个换行() {
        String input = "第一段\n第二段";
        String result = normalizer.normalize(input);
        assertEquals("第一段\n第二段", result);
    }

    @Test
    void should合并多个连续换行_保留两个换行() {
        String input = "第一段\n\n第二段";
        String result = normalizer.normalize(input);
        assertEquals("第一段\n\n第二段", result);
    }

    @Test
    void should清理特殊空白字符_不间断空格() {
        // \u00A0 是 non-breaking space
        String input = "Hello\u00A0World";
        String result = normalizer.normalize(input);
        assertEquals("Hello World", result);
    }

    @Test
    void should清理特殊空白字符_中文全角空格() {
        // \u3000 是中文全角空格
        String input = "你好\u3000世界";
        String result = normalizer.normalize(input);
        assertEquals("你好 世界", result);
    }

    @Test
    void should清理特殊空白字符_蒙古空格等() {
        // \u2000-\u200B 是各种窄空格
        String input = "test\u2000test\u2003test";
        String result = normalizer.normalize(input);
        assertEquals("test test test", result);
    }

    @Test
    void should统一换行符_RWindows风格() {
        String input = "line1\r\nline2\r\nline3";
        String result = normalizer.normalize(input);
        assertTrue(result.contains("\n"));
        assertFalse(result.contains("\r"));
    }

    @Test
    void should统一换行符_旧Mac风格() {
        String input = "line1\rline2\rline3";
        String result = normalizer.normalize(input);
        assertTrue(result.contains("\n"));
        assertFalse(result.contains("\r"));
    }

    @Test
    void should去掉首尾空白() {
        String input = "  \n  实际内容  \n  ";
        String result = normalizer.normalize(input);
        assertEquals("实际内容", result);
    }

    @Test
    void should处理空字符串() {
        String input = "";
        String result = normalizer.normalize(input);
        assertEquals("", result);
    }

    @Test
    void should处理空白字符串() {
        String input = "   \n\t\n  ";
        String result = normalizer.normalize(input);
        assertEquals("", result);
    }

    @Test
    void should处理null输入() {
        String result = normalizer.normalize(null);
        assertEquals("", result);
    }

    @Test
    void should组合场景_复杂文本() {
        String input = "标题\n\n\t\t  内容区   域\n\n\n\n第二段     开始";
        String result = normalizer.normalize(input);
        // 特殊空白字符先被替换为空格，然后多空格合并，多换行被合并为两个
        // 注意：\n\n 后面的空格是 \t\t 被替换后的结果
        assertEquals("标题\n\n 内容区 域\n\n第二段 开始", result);
    }
}
