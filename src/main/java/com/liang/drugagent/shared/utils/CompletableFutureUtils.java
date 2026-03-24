package com.liang.drugagent.shared.utils;

import java.util.concurrent.TimeoutException;

/**
 * CompletableFuture 工具类，提供带超时控制的异步执行能力。
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public class CompletableFutureUtils {

    /**
     * 使用指定超时时间执行任务。
     *
     * @param task      要执行的任务
     * @param timeoutMs 超时时间（毫秒）
     * @param <T>       返回值类型
     * @return 任务结果
     * @throws TimeoutException 如果任务执行超时
     */
    public static <T> T executeWithTimeout(java.util.concurrent.Callable<T> task, long timeoutMs) throws TimeoutException {
        ThreadCompletableFuture<T> future = new ThreadCompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        thread.start();

        try {
            return future.get(timeoutMs);
        } catch (java.util.concurrent.TimeoutException e) {
            thread.interrupt();
            throw new TimeoutException("Task execution timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 简单 Future 实现，用于超时控制。
     */
    private static class ThreadCompletableFuture<T> {
        private T result;
        private Exception exception;
        private boolean done = false;

        public synchronized T get(long timeoutMs) throws Exception {
            long start = System.currentTimeMillis();
            while (!done) {
                long remaining = timeoutMs - (System.currentTimeMillis() - start);
                if (remaining <= 0) {
                    throw new java.util.concurrent.TimeoutException();
                }
                wait(remaining);
            }
            if (exception != null) {
                throw exception;
            }
            return result;
        }

        public synchronized void complete(T result) {
            this.result = result;
            this.done = true;
            notifyAll();
        }

        public synchronized void completeExceptionally(Exception e) {
            this.exception = e;
            this.done = true;
            notifyAll();
        }
    }
}
