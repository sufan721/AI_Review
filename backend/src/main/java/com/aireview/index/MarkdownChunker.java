package com.aireview.index;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 清洗与切片。纯函数、无 Spring 依赖，便于单测覆盖切片边界。
 *
 * <p>策略：先按标题与段落（含围栏代码块）切分；单段超长时再按固定窗口切分并保留重叠。
 * 每个切片记录在「清洗后正文」中的 UTF-16 字符区间，供引用定位。
 *
 * <p>清洗仅做换行归一化与多余空行折叠，保证切片文本是清洗后正文的连续子串。
 */
public class MarkdownChunker {
    private final int maxSize;
    private final int overlap;

    public MarkdownChunker(int maxSize, int overlap) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize 必须为正数");
        }
        if (overlap < 0 || overlap >= maxSize) {
            throw new IllegalArgumentException("overlap 必须满足 0 <= overlap < maxSize");
        }
        this.maxSize = maxSize;
        this.overlap = overlap;
    }

    public List<Chunk> chunk(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        String content = clean(raw);
        if (content.isEmpty()) {
            return List.of();
        }
        List<Block> blocks = parseBlocks(content);

        List<Chunk> chunks = new ArrayList<>();
        int chunkStart = -1;
        int chunkEnd = -1;
        for (Block block : blocks) {
            if (block.heading) {
                if (chunkStart >= 0) {
                    emit(chunks, content, chunkStart, chunkEnd);
                }
                chunkStart = block.start;
                chunkEnd = block.end;
            } else if (chunkStart < 0) {
                chunkStart = block.start;
                chunkEnd = block.end;
            } else if (block.end - chunkStart <= maxSize) {
                chunkEnd = block.end;
            } else {
                emit(chunks, content, chunkStart, chunkEnd);
                chunkStart = block.start;
                chunkEnd = block.end;
            }
        }
        if (chunkStart >= 0) {
            emit(chunks, content, chunkStart, chunkEnd);
        }
        return chunks;
    }

    private String clean(String raw) {
        String content = raw.replace("\r\n", "\n").replace('\r', '\n');
        content = content.replaceAll("\\n{3,}", "\n\n");
        return content.replaceAll("^\\n+", "").replaceAll("\\n+$", "");
    }

    private List<Block> parseBlocks(String content) {
        List<Block> blocks = new ArrayList<>();
        int length = content.length();
        int pos = 0;
        while (pos < length) {
            int lineEnd = endOfLine(content, pos);
            String trimmed = content.substring(pos, lineEnd).trim();
            if (trimmed.isEmpty()) {
                pos = nextLine(content, lineEnd);
                continue;
            }
            String fence = fenceMarker(trimmed);
            if (fence != null) {
                int end = consumeFence(content, pos, fence);
                blocks.add(new Block(pos, end, false));
                pos = end == length ? length : nextLine(content, end);
                continue;
            }
            if (isHeading(trimmed)) {
                blocks.add(new Block(pos, lineEnd, true));
                pos = nextLine(content, lineEnd);
                continue;
            }
            int start = pos;
            int lastEnd = lineEnd;
            pos = nextLine(content, lineEnd);
            while (pos < length) {
                int nextEnd = endOfLine(content, pos);
                String next = content.substring(pos, nextEnd).trim();
                if (next.isEmpty() || isHeading(next) || fenceMarker(next) != null) {
                    break;
                }
                lastEnd = nextEnd;
                pos = nextLine(content, nextEnd);
            }
            blocks.add(new Block(start, lastEnd, false));
        }
        return blocks;
    }

    /** 从围栏起始行消费到闭合围栏行（含），返回闭合行内容末尾的偏移。 */
    private int consumeFence(String content, int start, String fence) {
        int length = content.length();
        int pos = nextLine(content, endOfLine(content, start));
        while (pos < length) {
            int lineEnd = endOfLine(content, pos);
            String line = content.substring(pos, lineEnd).trim();
            if (isClosingFence(line, fence)) {
                return lineEnd;
            }
            pos = nextLine(content, lineEnd);
        }
        return length;
    }

    private void emit(List<Chunk> chunks, String content, int start, int end) {
        if (end - start <= maxSize) {
            chunks.add(new Chunk(content.substring(start, end), start, end, chunks.size()));
            return;
        }
        int step = maxSize - overlap;
        int pos = start;
        while (pos < end) {
            int windowEnd = Math.min(pos + maxSize, end);
            chunks.add(new Chunk(content.substring(pos, windowEnd), pos, windowEnd, chunks.size()));
            if (windowEnd >= end) {
                break;
            }
            pos += step;
        }
    }

    private int endOfLine(String content, int from) {
        int idx = content.indexOf('\n', from);
        return idx < 0 ? content.length() : idx;
    }

    private int nextLine(String content, int lineEnd) {
        return lineEnd < content.length() ? lineEnd + 1 : lineEnd;
    }

    private static String fenceMarker(String line) {
        if (line.startsWith("```")) {
            return "```";
        }
        if (line.startsWith("~~~")) {
            return "~~~";
        }
        return null;
    }

    private static boolean isClosingFence(String line, String fence) {
        if (!line.startsWith(fence)) {
            return false;
        }
        for (int i = fence.length(); i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != fence.charAt(0) && c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private static boolean isHeading(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6 || level >= line.length()) {
            return false;
        }
        return line.charAt(level) == ' ';
    }

    private static final class Block {
        final int start;
        final int end;
        final boolean heading;

        Block(int start, int end, boolean heading) {
            this.start = start;
            this.end = end;
            this.heading = heading;
        }
    }
}
