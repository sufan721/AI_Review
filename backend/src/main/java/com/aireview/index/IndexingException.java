package com.aireview.index;

/**
 * 索引流水线内部错误（嵌入或向量库调用失败）。由 {@code IndexingServiceImpl} 捕获后
 * 将资料标记为 FAILED 并记录原因，不向业务层抛回。
 */
public class IndexingException extends RuntimeException {
    public IndexingException(String message) {
        super(message);
    }

    public IndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
