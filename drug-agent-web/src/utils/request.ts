/**
 * 基于 axios 的请求封装
 */
import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { MessagePlugin } from 'tdesign-vue-next';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
});

// 请求拦截器
instance.interceptors.request.use(
  (config) => {
    // 可在此添加 token 等
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
instance.interceptors.response.use(
  (response: AxiosResponse) => {
    return response;
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败';
    MessagePlugin.error(message);
    return Promise.reject(error);
  }
);

export default instance;
