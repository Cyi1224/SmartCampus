package com.simon.campus.controller;

import com.simon.campus.common.R;
import com.simon.campus.model.entity.FaqPair;
import com.simon.campus.service.admin.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/faq")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping
    public R<List<FaqPair>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) String priority) {
        return R.ok(faqService.list(keyword, category, enabled, priority));
    }

    @GetMapping("/categories")
    public R<List<String>> categories() {
        return R.ok(faqService.getCategories());
    }

    @GetMapping("/top")
    public R<List<FaqPair>> top(@RequestParam(defaultValue = "10") int n) {
        return R.ok(faqService.getTopFaqs(n));
    }

    @GetMapping("/{id}")
    public R<FaqPair> getById(@PathVariable Long id) {
        return R.ok(faqService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<FaqPair> create(@RequestBody FaqPair faq) {
        return R.ok(faqService.create(faq));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> update(@PathVariable Long id, @RequestBody FaqPair faq) {
        faqService.update(id, faq);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> delete(@PathVariable Long id) {
        faqService.delete(id);
        return R.ok(null);
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> toggle(@PathVariable Long id) {
        faqService.toggleEnabled(id);
        return R.ok(null);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> batchImport(@RequestBody List<FaqPair> faqs) {
        faqService.batchImport(faqs);
        return R.ok(null);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<List<FaqPair>> export() {
        return R.ok(faqService.exportAll());
    }
}
