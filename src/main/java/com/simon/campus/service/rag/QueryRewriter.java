package com.simon.campus.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.common.LlmClient;
import com.simon.campus.model.dto.QueryExpansion;
import com.simon.campus.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stage 2: Expand query into main + sub-queries + keywords.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryRewriter {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final SystemConfigService configService;

    private static final String SYSTEM_PROMPT = """
        你是一个查询改写专家，帮助改善知识库检索效果。
        给定用户问题，输出严格的JSON格式（不要有markdown代码块）：
        {
          "mainQuery": "改写后的完整核心问题",
          "subQueries": ["从不同角度的子问题1", "子问题2"],
          "keywords": ["关键词1", "关键词2", "关键词3"]
        }
        要求：
        - mainQuery 是最重要的独立完整问题
        - subQueries 2-3个，覆盖不同检索角度
        - keywords 3-6个核心词（名词为主）
        只输出JSON，不要有任何其他文字。
        """;

    public QueryExpansion rewrite(String query) {
        try {
            String model = configService.get("models.query-rewriter.model", "qwen-plus");
            double temperature = configService.getDouble("models.query-rewriter.temperature", 0.2);
            int maxTokens = configService.getInt("models.query-rewriter.max-tokens", 512);
            String result = llmClient.chat(model, temperature, maxTokens,
                List.of(LlmClient.systemMsg(SYSTEM_PROMPT), LlmClient.userMsg(query)));

            // Clean potential markdown fences
            String json = result.strip()
                .replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$", "");

            QueryExpansion expansion = objectMapper.readValue(json, QueryExpansion.class);
            log.debug("QueryRewriter expanded '{}' → main='{}', subs={}", query, expansion.getMainQuery(), expansion.getSubQueries());
            return expansion;
        } catch (Exception e) {
            log.warn("QueryRewriter failed for '{}': {}. Using fallback.", query, e.getMessage());
            QueryExpansion fallback = new QueryExpansion();
            fallback.setMainQuery(query);
            fallback.setSubQueries(List.of());
            fallback.setKeywords(List.of());
            return fallback;
        }
    }
}
