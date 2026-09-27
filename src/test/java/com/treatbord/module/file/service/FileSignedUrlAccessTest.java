package com.treatbord.module.file.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /files/** 签名访问控制验收（把 signed-url.required 覆盖为 true，模拟生产）：
 * 无签名 / 伪造 / 过期 → 403；正确签名 → 200。
 */
@SpringBootTest(properties = "treatbord.storage.signed-url.required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileSignedUrlAccessTest {

    private static final String KEY = "biz/submission/202609/sigtest0001.png";
    private static final String LOCAL_DIR = "./target/test-uploads";

    @Autowired private MockMvc mockMvc;
    @Autowired private FileUrlSigner signer;

    @BeforeEach
    void prepareFile() throws Exception {
        Path path = Paths.get(LOCAL_DIR, KEY);
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    }

    private String signedQuery() {
        String url = signer.signedUrl(KEY);
        return url.substring(url.indexOf('?') + 1); // exp=..&sig=..
    }

    private String expOf(String query) {
        return query.substring(query.indexOf("exp=") + 4, query.indexOf('&'));
    }

    private String sigOf(String query) {
        return query.substring(query.indexOf("sig=") + 4);
    }

    @Test
    @DisplayName("无签名 → 403")
    void withoutSignatureForbidden() throws Exception {
        mockMvc.perform(get("/files/" + KEY)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("正确签名 → 200")
    void withValidSignatureOk() throws Exception {
        String q = signedQuery();
        mockMvc.perform(get("/files/" + KEY).param("exp", expOf(q)).param("sig", sigOf(q)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("伪造签名 → 403")
    void forgedSignatureForbidden() throws Exception {
        String q = signedQuery();
        mockMvc.perform(get("/files/" + KEY).param("exp", expOf(q)).param("sig", "forged"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("过期签名 → 403")
    void expiredSignatureForbidden() throws Exception {
        long pastExp = System.currentTimeMillis() / 1000 - 10;
        mockMvc.perform(get("/files/" + KEY)
                        .param("exp", String.valueOf(pastExp))
                        .param("sig", signer.sign(KEY, pastExp)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("拿 A 文件的签名去访问 B 文件 → 403（防越权）")
    void signatureCannotBeReusedForAnotherFile() throws Exception {
        String q = signedQuery();
        String otherKey = "biz/submission/202609/sigtest0002.png";
        Path other = Paths.get(LOCAL_DIR, otherKey);
        Files.write(other, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        mockMvc.perform(get("/files/" + otherKey).param("exp", expOf(q)).param("sig", sigOf(q)))
                .andExpect(status().isForbidden());
    }
}
