package com.aireview.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownChunkerTest {
    private final MarkdownChunker chunker = new MarkdownChunker(800, 100);

    @Test
    void splitsByHeading() {
        List<Chunk> chunks = chunker.chunk("## 标题A\n正文A\n## 标题B\n正文B");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo("## 标题A\n正文A");
        assertThat(chunks.get(1).text()).isEqualTo("## 标题B\n正文B");
    }

    @Test
    void keepsFencedCodeBlockAtomic() {
        // 「# 代码内的伪标题」必须留在代码块里，不能被当作标题切开
        List<Chunk> chunks = chunker.chunk("```\n# 代码内的伪标题\nint a = 1;\n```");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("```\n# 代码内的伪标题\nint a = 1;\n```");
    }

    @Test
    void windowSplitsOversizedParagraphWithOverlap() {
        MarkdownChunker small = new MarkdownChunker(10, 2);
        List<Chunk> chunks = small.chunk("0123456789abcdefghij");

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text()).isEqualTo("0123456789");
        assertThat(chunks.get(1).text()).isEqualTo("89abcdefgh");
        assertThat(chunks.get(2).text()).isEqualTo("ghij");
        // 重叠：第二个窗口起点在第一个窗口之内
        assertThat(chunks.get(1).start()).isLessThan(chunks.get(0).end());
    }

    @Test
    void recordsAccuratePositionsForEveryChunk() {
        String content = "# A\n正文\n# B\n正文";
        List<Chunk> chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).start()).isZero();
        // 每个切片的 [start, end) 精确指向其文本，且相邻切片不重叠、顺序递增
        int previousEnd = 0;
        for (Chunk chunk : chunks) {
            assertThat(chunk.text()).isEqualTo(content.substring(chunk.start(), chunk.end()));
            assertThat(chunk.start()).isGreaterThanOrEqualTo(previousEnd);
            previousEnd = chunk.end();
        }
    }

    @Test
    void normalizesLineEndingsAndCollapsesBlankLines() {
        List<Chunk> chunks = chunker.chunk("第一段\r\n\r\n\r\n\r\n第二段");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("第一段\n\n第二段");
    }

    @Test
    void emptyAndNullYieldNoChunks() {
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   \n\n  ")).isEmpty();
    }

    @Test
    void rejectsInvalidWindowParameters() {
        assertThatThrownBy(() -> new MarkdownChunker(0, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarkdownChunker(10, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
