/**
 * PDF 导出工具
 *
 * 核心思路：直接截图已渲染的报告 DOM，而非手动拼接 HTML 模板。
 * 这样保证"导出即所见"，不需要维护两套报告结构。
 */

import html2canvas from 'html2canvas';
import jsPDF from 'jspdf';

/** A4 尺寸常量（mm） */
const A4_WIDTH_MM = 210;
const A4_HEIGHT_MM = 297;

/**
 * 将已渲染的报告内容区域导出为 PDF。
 *
 * 调用方需传入内容容器元素（不含侧边栏）。
 * 函数内部会临时取消 overflow 和高度限制，确保完整内容都能被 html2canvas 捕获，
 * 截图完成后立即恢复原始样式。
 *
 * @param contentEl  报告内容容器（如 .doc-content 的父级可滚动区域）
 * @param filename   导出文件名（不含 .pdf 扩展名）
 */
export async function exportReportToPdf(contentEl: HTMLElement, filename: string): Promise<void> {
  // 找到真正的内容节点：FormalReportDocument 里的 .doc-content
  const docContent = contentEl.querySelector<HTMLElement>('.doc-content') ?? contentEl;

  // 保存需要临时修改的元素及其原始样式，导出结束后全部还原
  const overrides: Array<{ el: HTMLElement; props: Partial<CSSStyleDeclaration> }> = [];

  function tempStyle(el: HTMLElement, props: Record<string, string>) {
    const original: Partial<CSSStyleDeclaration> = {};
    for (const key of Object.keys(props)) {
      original[key as any] = (el.style as any)[key];
      (el.style as any)[key] = props[key];
    }
    overrides.push({ el, props: original });
  }

  function restoreAll() {
    for (const { el, props } of overrides) {
      for (const key of Object.keys(props)) {
        (el.style as any)[key] = (props as any)[key];
      }
    }
  }

  // 取消滚动容器的高度和 overflow 限制，让内容完整展开
  const scrollContainer = contentEl.closest<HTMLElement>('.doc-main') ?? contentEl;
  const layoutContainer = contentEl.closest<HTMLElement>('.formal-document-layout');

  tempStyle(scrollContainer, {
    overflow: 'visible',
    height: 'auto',
    maxHeight: 'none',
  });

  if (layoutContainer) {
    tempStyle(layoutContainer, {
      height: 'auto',
      overflow: 'visible',
    });
  }

  // 隐藏侧边栏，PDF 里不需要目录导航
  const sidebar = layoutContainer?.querySelector<HTMLElement>('.doc-sidebar');
  if (sidebar) {
    tempStyle(sidebar, { display: 'none' });
  }

  try {
    const canvas = await html2canvas(docContent, {
      scale: 2,             // 2× 提高清晰度，中文字符不模糊
      useCORS: true,
      logging: false,
      backgroundColor: '#ffffff',
      // 确保完整捕获，不依赖视口裁剪
      windowWidth: docContent.scrollWidth,
      windowHeight: docContent.scrollHeight,
    });

    const imgData = canvas.toDataURL('image/png'); // PNG 保留文字锐度，优于 JPEG
    const imgWidth = A4_WIDTH_MM;
    const imgHeight = (canvas.height * A4_WIDTH_MM) / canvas.width;

    const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });

    let remainingHeight = imgHeight;
    let pageOffset = 0; // 当前页在完整图片中的起始位置（mm）

    while (remainingHeight > 0) {
      if (pageOffset > 0) {
        pdf.addPage();
      }
      // 将整张图片偏移到当前页对应位置
      pdf.addImage(imgData, 'PNG', 0, -pageOffset, imgWidth, imgHeight);
      pageOffset += A4_HEIGHT_MM;
      remainingHeight -= A4_HEIGHT_MM;
    }

    pdf.save(`${filename}.pdf`);
  } finally {
    restoreAll();
  }
}

/**
 * 将 DOM 元素导出为 PDF（通用版本，保留供其他场景调用）
 */
export async function exportElementToPdf(element: HTMLElement, filename: string): Promise<void> {
  if (!element) {
    throw new Error('要导出的 DOM 元素不存在');
  }

  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    logging: false,
    backgroundColor: '#ffffff',
  });

  const imgData = canvas.toDataURL('image/png');
  const imgWidth = A4_WIDTH_MM;
  const imgHeight = (canvas.height * A4_WIDTH_MM) / canvas.width;

  const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });

  let remainingHeight = imgHeight;
  let pageOffset = 0;

  while (remainingHeight > 0) {
    if (pageOffset > 0) {
      pdf.addPage();
    }
    pdf.addImage(imgData, 'PNG', 0, -pageOffset, imgWidth, imgHeight);
    pageOffset += A4_HEIGHT_MM;
    remainingHeight -= A4_HEIGHT_MM;
  }

  pdf.save(`${filename}.pdf`);
}
