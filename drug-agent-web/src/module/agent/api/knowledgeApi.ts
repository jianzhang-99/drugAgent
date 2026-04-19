/**
 * Knowledge / RAG 知识库 API 层
 * 调用后端接口：/api/knowledge/*, /api/oss/*
 */

import request from '@/utils/request';
import type { ApiResponse } from '../types/agent';

// ==================== 文件类型常量 ====================

/** 文件类型枚举（与后端 OssFile.fileType 一致） */
export const FILE_TYPE = {
  /** 对话附件：仅上传到 COS，不进向量库 */
  ATTACHMENT: 1,
  /** RAG 知识库：上传到 COS 并入库向量库 */
  KNOWLEDGE: 2,
} as const;
export type FileType = (typeof FILE_TYPE)[keyof typeof FILE_TYPE];

// ==================== 知识库文件列表类型 ====================

/** OSS 文件元信息 */
export interface OssFile {
  id: string;
  fileName: string;
  fileSuffix: string;
  fileSize: number;
  ossUrl: string;
  fileType: number;
  sessionId?: string;
  uploadStatus: number;
  createdAt: string;
  updatedAt: string;
}

/** OSS 上传响应 */
export interface OssUploadResp {
  id: string;
  ossUrl: string;
  etag: string;
  bucket: string;
  fileName: string;
  fileSize: number;
}

/** 文件列表响应 */
export interface FileListResp {
  list: OssFile[];
  total: number;
  page: number;
  pageSize: number;
}

// ==================== 知识入库类型 ====================

/** 知识入库请求 */
export interface KnowledgeIngestReq {
  title?: string;
  content: string;
  scene?: string;
  subScene?: string;
  docType?: string;
  orgId: string;
  version?: string;
}

/** 知识入库响应 */
export interface KnowledgeIngestResp {
  sourceId: string;
  message: string;
}

// ==================== 知识问答类型 ====================

/** 知识问答请求 */
export interface KnowledgeAskReq {
  question: string;
  orgId: string;
  scene?: string;
  subScene?: string;
  docType?: string;
  topK?: number;
  sessionId?: string;
}

/** 引用片段 */
export interface KnowledgeCitation {
  sourceId: string;
  sourceTitle: string;
  chunkId: string;
  snippet: string;
  score: number | null;
  sectionTitle?: string;
  pageNo?: number;
}

/** 知识问答响应 */
export interface KnowledgeAskResp {
  answer: string | null;
  decision: string | null;
  reason: string | null;
  riskLevel: string | null;
  citations: KnowledgeCitation[] | null;
}

// ==================== API 函数 ====================

/**
 * 上传文件到腾讯云 COS
 * @param file 上传的文件
 * @param fileType 1-对话附件，2-RAG知识库
 * @param sessionId 关联的会话ID（对话附件场景使用）
 */
export function uploadOssFile(
  file: File,
  fileType: FileType = FILE_TYPE.ATTACHMENT,
  sessionId?: string
) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('fileType', String(fileType));
  if (sessionId) {
    formData.append('sessionId', sessionId);
  }
  return request.post<ApiResponse<OssUploadResp>>('/api/oss/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 获取 OSS 文件列表
 * @param fileType 文件类型：1-对话附件，2-RAG知识库
 * @param sessionId 关联的会话ID（可选，不传则返回所有）
 */
export function getOssFiles(fileType?: FileType, sessionId?: string) {
  return request.get<ApiResponse<FileListResp>>('/api/oss/files', {
    params: {
      ...(fileType !== undefined && { fileType }),
      ...(sessionId && { sessionId }),
      page: 1,
      pageSize: 200,
    },
  });
}

/**
 * 删除 OSS 文件（仅删除 COS 对象和 OSS 记录）
 * @param id OSS 文件记录 ID
 */
export function deleteOssFile(id: string) {
  return request.delete<ApiResponse<null>>(`/api/oss/files/${id}`);
}

/**
 * 上传文件到 COS（知识库类型）并触发 RAG 入库
 * 完整流程：上传到 COS → 入库向量库 → 建立 OSS 与 sourceId 关联
 *
 * @param file 上传的文件
 * @param orgId 组织ID
 * @param scene 业务场景
 * @param subScene 子场景
 * @param docType 文档类型
 */
export function uploadKnowledgeFile(
  file: File,
  _orgId?: string,
  _scene?: string,
  _subScene?: string,
  _docType?: string
) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('fileType', String(FILE_TYPE.KNOWLEDGE));
  return request.post<ApiResponse<OssUploadResp>>('/api/oss/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(async (uploadRes) => {
    if (uploadRes.data.code !== 200 && uploadRes.data.code !== 0) {
      throw new Error(uploadRes.data.message || '上传失败');
    }
    return uploadRes;
  });
}

/**
 * 触发 RAG 入库（将 COS 中的文件提取文本、向量化存入知识库）
 * 必须在 uploadKnowledgeFile 之后调用
 *
 * @param ossUrl COS 对象路径
 * @param ossId OSS 文件记录 ID（用于建立关联）
 * @param title 文档标题
 * @param orgId 组织ID
 * @param scene 业务场景
 */
export function ingestFromOss(
  ossUrl: string,
  ossId: string,
  title: string,
  orgId: string,
  scene?: string
) {
  return request.post<ApiResponse<KnowledgeIngestResp>>('/api/knowledge/ingest/oss', null, {
    params: {
      ossUrl,
      ossId,
      title,
      orgId,
      scene: scene || '',
    },
  });
}

/**
 * 删除知识库文件（同时删除 COS、OSS 记录、向量库 chunks）
 * @param ossFileId OSS 文件记录 ID
 */
export function deleteKnowledgeFile(ossFileId: string) {
  return request.delete<ApiResponse<null>>(`/api/knowledge/files/${ossFileId}`);
}

/**
 * 清空所有知识库文件
 * 同时删除：向量库chunks + rag_file记录 + COS文件
 */
export function batchDeleteKnowledgeByOrgId() {
  return request.delete<ApiResponse<{
    message: string;
    deletedRagFileCount: number;
    deletedChunkCount: number;
    deletedCosCount: number;
  }>>(`/api/knowledge/cleanup/all`);
}

/**
 * 获取知识库文件列表（fileType=2）
 */
export function getKnowledgeFiles(_orgId?: string) {
  return getOssFiles(FILE_TYPE.KNOWLEDGE);
}

/**
 * 获取对话上下文文件列表（fileType=1）
 * @param sessionId 关联的会话ID
 */
export function getContextFiles(sessionId: string) {
  return getOssFiles(FILE_TYPE.ATTACHMENT, sessionId);
}

/**
 * 文本直接入库
 * POST /api/knowledge/ingest/text
 */
export function ingestText(req: KnowledgeIngestReq) {
  return request.post<ApiResponse<KnowledgeIngestResp>>('/api/knowledge/ingest/text', req);
}

/**
 * 知识问答（检索 + LLM 生成回答）
 * POST /api/knowledge/ask
 */
export function askKnowledge(req: KnowledgeAskReq) {
  return request.post<ApiResponse<KnowledgeAskResp>>('/api/knowledge/ask', req);
}

/**
 * 知识检索（仅向量检索，不生成回答）
 * POST /api/knowledge/search
 */
export function searchKnowledge(req: KnowledgeAskReq) {
  return request.post<ApiResponse<KnowledgeAskResp>>('/api/knowledge/search', req);
}

/**
 * 手动持久化向量库
 * POST /api/knowledge/persist
 */
export function persistVectorStore() {
  return request.post<ApiResponse<null>>('/api/knowledge/persist');
}
