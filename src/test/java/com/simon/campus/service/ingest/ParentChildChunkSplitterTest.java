package com.simon.campus.service.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ParentChildChunkSplitter — 分块逻辑单元测试")
class ParentChildChunkSplitterTest {

    private ParentChildChunkSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new ParentChildChunkSplitter();
    }

    @Test
    @DisplayName("短文本：产生至少一个 Parent 和一个 Child")
    void shortText_producesAtLeastOneParentAndChild() {
        String content = "本校学生手册规定，学生在校期间须遵守学校各项规章制度。" +
            "违反规定者将受到相应处分，严重者可予以开除学籍。" +
            "学生应按时参加课程学习，无故缺席超过三分之一者取消考试资格。";

        var section = new DocumentParser.ParsedSection("第一章 学生纪律", content, 1, 1);
        var result = splitter.split("doc-001", "学生手册", 0, List.of(section));

        assertThat(result.parents()).isNotEmpty();
        assertThat(result.children()).isNotEmpty();
    }

    @Test
    @DisplayName("每个 Child 必须关联到一个 Parent")
    void everyChildLinksToAParent() {
        String content = "一".repeat(600);
        var section = new DocumentParser.ParsedSection("标题", content, 1, 1);
        var result = splitter.split("doc-002", "测试文档", 0, List.of(section));

        var parentIds = result.parents().stream()
            .map(p -> p.getParentId())
            .toList();

        for (var child : result.children()) {
            assertThat(parentIds).contains(child.getParentId());
        }
    }

    @Test
    @DisplayName("Child 内容长度不超过最大阈值")
    void childContentWithinSizeLimit() {
        String longContent = "校园生活规范及管理细则。".repeat(100);
        var section = new DocumentParser.ParsedSection("管理细则", longContent, 1, 1);
        var result = splitter.split("doc-003", "管理手册", 0, List.of(section));

        for (var child : result.children()) {
            assertThat(child.getContent().length())
                .as("Child '%s' 长度超限", child.getChildId())
                .isLessThanOrEqualTo(300); // 允许 overlap 小幅超出理论上限
        }
    }

    @Test
    @DisplayName("多个 Section 都被处理")
    void multipleSectionsAllProcessed() {
        var sections = List.of(
            new DocumentParser.ParsedSection("第一节 入学须知", "入学须知内容。".repeat(40), 1, 1),
            new DocumentParser.ParsedSection("第二节 学籍管理", "学籍管理相关规定。".repeat(40), 5, 1),
            new DocumentParser.ParsedSection("第三节 考核制度", "考核与评价制度说明。".repeat(40), 10, 1)
        );

        var result = splitter.split("doc-004", "学生手册", 0, sections);

        assertThat(result.parents()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.children()).hasSizeGreaterThan(result.parents().size());
    }

    @Test
    @DisplayName("docId 和 docTitle 正确传递到 Parent")
    void docMetadataPropagatedToParent() {
        var section = new DocumentParser.ParsedSection("测试章节", "测试内容。".repeat(50), 1, 1);
        var result = splitter.split("DOC-XYZ-123", "测试文档标题", 1, List.of(section));

        assertThat(result.parents()).allSatisfy(p -> {
            assertThat(p.getDocId()).isEqualTo("DOC-XYZ-123");
            assertThat(p.getDocTitle()).isEqualTo("测试文档标题");
            assertThat(p.getAccessLevel()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("空 Section 列表不抛异常且返回空结果")
    void emptySection_returnsEmptyResult() {
        var result = splitter.split("doc-005", "空文档", 0, List.of());

        assertThat(result.parents()).isEmpty();
        assertThat(result.children()).isEmpty();
    }
}
