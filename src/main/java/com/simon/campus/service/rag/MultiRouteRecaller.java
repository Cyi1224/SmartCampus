package com.simon.campus.service.rag;

import com.simon.campus.model.dto.QueryExpansion;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stage 3: Parallel three-route recall (Dense + BM25 + FAQ), fused via RRF(k=60).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiRouteRecaller {

    private static final int RRF_K = 60;
    private static final int RRF_OUTPUT_TOP_K = 20;

    private final DenseRetriever denseRetriever;
    private final BM25Retriever bm25Retriever;
    private final FaqMatcher faqMatcher;
    private final SystemConfigService configService;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public record RecallResult(
        boolean faqShortCircuited,
        String faqAnswer,
        List<RecallCandidate> candidates
    ) {}

    public RecallResult recall(QueryExpansion expansion, int userAccessLevel) {
        String mainQuery = expansion.getMainQuery();
        int mainTopK = configService.getInt("rag.topk.child", 20);
        int subTopK = configService.getInt("rag.topk.child_sub", 7);

        // FAQ check first (fast path)
        FaqMatcher.FaqMatchResult faqResult = faqMatcher.match(mainQuery);
        log.info("[AGENT_FLOW] step=faq_match query={} shortCircuited={} candidates={}",
            abbreviate(mainQuery), faqResult.shortCircuited(), faqResult.candidates().size());
        if (faqResult.shortCircuited()) {
            log.debug("FAQ short-circuit matched for: {}", mainQuery);
            return new RecallResult(true, faqResult.directAnswer(), Collections.emptyList());
        }

        // Parallel: dense (main + subs) + BM25 + FAQ candidates
        CompletableFuture<List<RecallCandidate>> denseFuture = CompletableFuture.supplyAsync(() -> {
            List<RecallCandidate> all = new ArrayList<>(
                denseRetriever.retrieve(mainQuery, userAccessLevel, mainTopK));
            for (String sub : expansion.getSubQueries()) {
                all.addAll(denseRetriever.retrieve(sub, userAccessLevel, subTopK));
            }
            return all;
        }, executor);

        CompletableFuture<List<RecallCandidate>> bm25Future = CompletableFuture.supplyAsync(() ->
            bm25Retriever.retrieve(mainQuery, userAccessLevel, mainTopK), executor);

        List<RecallCandidate> faqCandidates = faqResult.candidates();

        List<RecallCandidate> denseResults;
        List<RecallCandidate> bm25Results;
        try {
            denseResults = denseFuture.get();
            bm25Results = bm25Future.get();
        } catch (Exception e) {
            log.error("Recall futures failed: {}", e.getMessage());
            denseResults = Collections.emptyList();
            bm25Results = Collections.emptyList();
        }

        // Deduplicate and build ranked lists per source
        List<RecallCandidate> denseDeduped = dedup(denseResults);
        List<RecallCandidate> bm25Deduped  = dedup(bm25Results);

        List<RecallCandidate> merged = rrfMerge(denseDeduped, bm25Deduped, faqCandidates);
        log.info("[AGENT_FLOW] step=multi_recall dense={} bm25={} faqCandidates={} rrfOut={} hits={}",
            denseDeduped.size(), bm25Deduped.size(), faqCandidates.size(), merged.size(), summarizeHits(merged));

        return new RecallResult(false, null, merged);
    }

    /**
     * RRF fusion: each list contributes 1/(k + rank) to the score.
     */
    @SafeVarargs
    private List<RecallCandidate> rrfMerge(List<RecallCandidate>... lists) {
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, RecallCandidate> candidates = new LinkedHashMap<>();

        for (List<RecallCandidate> list : lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                RecallCandidate c = list.get(rank);
                double contrib = 1.0 / (RRF_K + rank + 1);
                rrfScores.merge(c.getChildId(), contrib, Double::sum);
                candidates.putIfAbsent(c.getChildId(), c);
            }
        }

        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(RRF_OUTPUT_TOP_K)
            .map(e -> {
                RecallCandidate c = candidates.get(e.getKey());
                c.setScore(e.getValue());
                return c;
            })
            .toList();
    }

    private List<RecallCandidate> dedup(List<RecallCandidate> list) {
        Map<String, RecallCandidate> seen = new LinkedHashMap<>();
        for (RecallCandidate c : list) {
            seen.putIfAbsent(c.getChildId(), c);
        }
        return new ArrayList<>(seen.values());
    }

    private String summarizeHits(List<RecallCandidate> candidates) {
        return candidates.stream()
            .limit(5)
            .map(c -> c.getDocTitle() + "/" + c.getChildId() + "/" + c.getScore())
            .toList()
            .toString();
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int max = 1000;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...[truncated]";
    }
}
