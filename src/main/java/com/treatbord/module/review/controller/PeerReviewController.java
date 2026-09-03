package com.treatbord.module.review.controller;

import com.treatbord.common.Result;
import com.treatbord.module.review.dto.ReviewCreateRequest;
import com.treatbord.module.review.service.PeerReviewService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互评接口（docs/API_DESIGN.md §9.1）。
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class PeerReviewController {

    private final PeerReviewService peerReviewService;

    /** 9.1 提交互评 */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ReviewCreateRequest req,
                               HttpServletRequest httpReq) {
        peerReviewService.create(req, UserContext.userId(), httpReq);
        return Result.ok();
    }
}