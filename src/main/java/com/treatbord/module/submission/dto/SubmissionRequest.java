package com.treatbord.module.submission.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交凭证请求（docs/API_DESIGN.md §5.1）。
 */
@Data
public class SubmissionRequest {

    /** 文字凭证 ≤2000 */
    @Size(max = 2000, message = "凭证内容不能超过2000字")
    private String content;

    /** 已上传的凭证图 file.id 列表 */
    private List<Long> fileIds;
}