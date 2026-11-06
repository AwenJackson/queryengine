package dev.queryengine.spi;

/** 单条 INSERT 的影响行数和驱动返回的生成键（可能为 null）。 */
public record InsertResult(int affected, Object generatedKey) {
}
