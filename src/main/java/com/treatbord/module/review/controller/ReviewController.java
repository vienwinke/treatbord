package com.treatbord.module.review.controller;

import com.treatbord.common.Result;
import com.treatbord.module.review.dto.ReviewRequest;
import com.treatbord.module.review.service.ReviewService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核接口（docs/API_DESIGN.md §6）。
 */
@RestController
@RequestMapping("/api/claims/{id}/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 6.1 审核凭证 */
    @PostMapping
    public Result<Void> review(@PathVariable Long id,
                               @Valid @RequestBody ReviewRequest req,
                               HttpServletRequest httpReq) {
        reviewService.review(id, UserContext.userId(), req.getAction(), req.getNote(), httpReq);
        return Result.ok();
    }
}