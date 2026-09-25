package com.aireview.index;

/**
 * 一份资料被清洗后切出的一个切片。
 *
 * @param text  切片文本
 * @param start 在清洗后正文中的起始字符位置
 * @param end   在清洗后正文中的结束字符位置（开区间）
 * @param index 切片序号，从 0 开始
 */
public record Chunk(String text, int start, int end, int index) {
}
