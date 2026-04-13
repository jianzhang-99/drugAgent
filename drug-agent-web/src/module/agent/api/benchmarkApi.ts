/**
 * 模型评测 API 层
 * 调用后端接口：/api/benchmark/*
 */

import request from '@/utils/request';
import type { ApiResponse } from '../types/agent';

// ==================== 评测请求/响应类型 ====================

/** 评测请求 */
export interface BenchmarkRunReq {
  modelName?: string;  // 不传则评测所有模型
  prompt: string;
}

/** 单个模型评测结果 */
export interface ModelBenchmarkResp {
  modelName: string;
  provider: string;
  responseTimeMs: number;
  tokensUsed: number;
  success: boolean;
  errorMessage?: string;
  response?: string;  // 模型回复内容
}

/** 历史评测记录项 */
export interface BenchmarkRecord {
  id: number;
  modelName: string;
  provider: string;
  prompt: string;
  responseTimeMs: number;
  tokensUsed: number;
  success: boolean;
  errorMessage?: string;
  benchmarkTime: string;
}

/** 分页结果（MyBatis Plus IPage 结构） */
export interface BenchmarkPageResp {
  records: BenchmarkRecord[];
  total: number;
  size: number;
  current: number;
}

// ==================== API 函数 ====================

/**
 * 运行模型评测（评测所有模型或指定模型）
 * POST /api/benchmark/run-all
 */
export function runBenchmark(req: BenchmarkRunReq) {
  return request.post<ApiResponse<ModelBenchmarkResp[]>>('/api/benchmark/run-all', req);
}

/**
 * 获取评测历史记录（分页）
 * GET /api/benchmark/results
 */
export function getBenchmarkResults(page: number, pageSize: number) {
  return request.get<ApiResponse<BenchmarkPageResp>>('/api/benchmark/results', {
    params: { page, pageSize },
  });
}

/**
 * 获取可用模型列表
 * GET /api/benchmark/models
 */
export function getAvailableModels() {
  return request.get<ApiResponse<any[]>>('/api/benchmark/models');
}
