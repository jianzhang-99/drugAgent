/**
 * PDF 导出工具
 * 将 DOM 元素导出为 PDF 文件
 */

import html2canvas from 'html2canvas';
import jsPDF from 'jspdf';

/**
 * 将 DOM 元素导出为 PDF
 * @param element 要导出的 DOM 元素
 * @param filename PDF 文件名（不含扩展名）
 */
export async function exportElementToPdf(element: HTMLElement, filename: string): Promise<void> {
  if (!element) {
    throw new Error('要导出的 DOM 元素不存在');
  }

  try {
    // 使用 html2canvas 将 DOM 转为图片
    const canvas = await html2canvas(element, {
      scale: 2, // 提高清晰度
      useCORS: true, // 允许跨域图片
      logging: false, // 关闭日志
      backgroundColor: '#ffffff', // 白色背景
    });

    // 计算 PDF 尺寸
    const imgWidth = 210; // A4 宽度 (mm)
    const pageHeight = 297; // A4 高度 (mm)
    const imgHeight = (canvas.height * imgWidth) / canvas.width;

    // 创建 PDF
    const pdf = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: 'a4',
    });

    // 如果内容超过一页，按比例分割
    let heightLeft = imgHeight;
    let position = 0;

    // 添加第一页
    pdf.addImage(
      canvas.toDataURL('image/jpeg', 0.98),
      'JPEG',
      0,
      position,
      imgWidth,
      imgHeight,
      undefined,
      'FAST'
    );
    heightLeft -= pageHeight;

    // 如果内容超过一页，添加更多页
    while (heightLeft > 0) {
      position = heightLeft - imgHeight;
      pdf.addPage();
      pdf.addImage(
        canvas.toDataURL('image/jpeg', 0.98),
        'JPEG',
        0,
        position,
        imgWidth,
        imgHeight,
        undefined,
        'FAST'
      );
      heightLeft -= pageHeight;
    }

    // 下载 PDF
    pdf.save(`${filename}.pdf`);
  } catch (error) {
    console.error('导出 PDF 失败:', error);
    throw new Error('导出 PDF 失败，请稍后重试');
  }
}

/**
 * 将 Markdown 内容导出为 PDF
 * 由于后端返回的是 Markdown，本函数先将 Markdown 渲染为 HTML，再导出 PDF
 * @param markdownContent Markdown 文本内容
 * @param filename PDF 文件名（不含扩展名）
 */
export async function exportMarkdownToPdf(markdownContent: string, filename: string): Promise<void> {
  // 创建一个临时容器来渲染 Markdown
  const container = document.createElement('div');
  container.style.width = '210mm';
  container.style.padding = '20mm';
  container.style.backgroundColor = '#ffffff';
  container.style.fontFamily = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
  container.style.fontSize = '12px';
  container.style.lineHeight = '1.6';
  container.style.color = '#333333';

  // 简单的 Markdown 转 HTML 渲染
  container.innerHTML = renderMarkdownToHtml(markdownContent);

  // 添加到 body（必须添加才能正常渲染）
  container.style.position = 'absolute';
  container.style.left = '-9999px';
  container.style.top = '0';
  document.body.appendChild(container);

  try {
    await exportElementToPdf(container, filename);
  } finally {
    // 清理临时容器
    document.body.removeChild(container);
  }
}

/**
 * 简单的 Markdown 转 HTML 渲染
 * 支持标题、表格、粗体、斜体、列表、代码块等基础语法
 */
function renderMarkdownToHtml(markdown: string): string {
  if (!markdown) return '';

  let html = markdown;

  // 转义 HTML 特殊字符（但保留换行）
  html = html.replace(/&/g, '&amp;')
             .replace(/</g, '&lt;')
             .replace(/>/g, '&gt;');

  // 代码块（必须在行内代码之前处理）
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_, __, code) => {
    return `<pre style="background: #f4f4f4; padding: 12px; border-radius: 4px; overflow-x: auto; font-family: monospace; font-size: 10px;"><code>${code.trim()}</code></pre>`;
  });

  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code style="background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: monospace; font-size: 11px;">$1</code>');

  // 标题
  html = html.replace(/^### (.+)$/gm, '<h3 style="font-size: 16px; font-weight: bold; margin: 16px 0 8px; color: #1a1a1a;">$1</h3>');
  html = html.replace(/^## (.+)$/gm, '<h2 style="font-size: 18px; font-weight: bold; margin: 20px 0 10px; color: #1a1a1a; border-bottom: 1px solid #eee; padding-bottom: 8px;">$1</h2>');
  html = html.replace(/^# (.+)$/gm, '<h1 style="font-size: 22px; font-weight: bold; margin: 24px 0 12px; color: #1a1a1a;">$1</h1>');

  // 粗体和斜体
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');

  // 表格
  html = html.replace(/^\|(.+)\|$/gm, (match) => {
    const cells = match.slice(1, -1).split('|').map((cell: string) => cell.trim());
    return `<tr>${cells.map((cell: string) => `<td style="border: 1px solid #ddd; padding: 6px 10px;">${cell}</td>`).join('')}</tr>`;
  });
  // 简单处理：把连续的表格行包起来
  // 注意：这里简化处理，完整表格解析较复杂

  // 引用块
  html = html.replace(/^> (.+)$/gm, '<blockquote style="border-left: 4px solid #ddd; margin: 12px 0; padding: 8px 16px; color: #666; background: #f9f9f9;">$1</blockquote>');

  // 无序列表
  html = html.replace(/^- (.+)$/gm, '<li style="margin: 4px 0;">$1</li>');
  html = html.replace(/(<li[^>]*>.*<\/li>\n?)+/g, (match) => `<ul style="margin: 8px 0; padding-left: 24px;">${match}</ul>`);

  // 有序列表
  html = html.replace(/^\d+\. (.+)$/gm, '<li style="margin: 4px 0;">$1</li>');

  // 水平线
  html = html.replace(/^---$/gm, '<hr style="border: none; border-top: 1px solid #ddd; margin: 16px 0;">');

  // 段落（双换行）
  html = html.replace(/\n\n/g, '</p><p style="margin: 12px 0;">');

  // 单换行转 <br>
  html = html.replace(/\n/g, '<br/>');

  // 包裹在 p 标签中
  if (!html.startsWith('<')) {
    html = `<p style="margin: 12px 0;">${html}</p>`;
  }

  return html;
}
