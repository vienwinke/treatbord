package com.treatbord.module.file.service;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.config.service.AppConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文件上传安全链测试：扩展名白名单 + magic bytes + 大小限制。
 * 用 Mockito 伪造运行期配置（app_config），不依赖数据库。
 */
class FileValidationServiceTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] WEBP_HEAD = {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

    private FileValidationService service;

    @BeforeEach
    void setUp() {
        AppConfigService config = mock(AppConfigService.class);
        when(config.getInt(eq("file.max.size.mb"), anyInt())).thenReturn(5);
        when(config.get(eq("file.allowed.ext"), anyString())).thenReturn("jpg,png,webp");
        service = new FileValidationService(config);
    }

    private MockMultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, null, content);
    }

    private byte[] withHead(byte[] head, int totalSize) {
        byte[] data = new byte[totalSize];
        System.arraycopy(head, 0, data, 0, head.length);
        return data;
    }

    @Test
    @DisplayName("真实 PNG/JPG/WEBP 通过并返回规范化扩展名")
    void realImagesPass() {
        assertEquals("png", service.validate(file("photo.png", withHead(PNG_HEAD, 128))));
        assertEquals("jpg", service.validate(file("photo.JPG", withHead(JPG_HEAD, 128))));
        assertEquals("webp", service.validate(file("photo.webp", withHead(WEBP_HEAD, 128))));
    }

    @Test
    @DisplayName("伪装扩展名（内容不是图片）被拒绝 4001")
    void fakeExtensionRejected() {
        byte[] exeHead = {'M', 'Z', (byte) 0x90, 0x00};
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(file("evil.png", withHead(exeHead, 128))));
        assertEquals(ResultCode.FILE_TYPE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非白名单扩展名被拒绝 4001")
    void extensionNotAllowed() {
        byte[] gifHead = {'G', 'I', 'F', '8', '9', 'a'};
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(file("a.gif", withHead(gifHead, 128))));
        assertEquals(ResultCode.FILE_TYPE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("超过大小上限被拒绝 4002")
    void tooLargeRejected() {
        byte[] big = new byte[6 * 1024 * 1024];
        System.arraycopy(PNG_HEAD, 0, big, 0, PNG_HEAD.length);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(file("big.png", big)));
        assertEquals(ResultCode.FILE_TOO_LARGE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("空文件被拒绝 400")
    void emptyRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(file("empty.png", new byte[0])));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("文件头不足签名长度时拒绝（防截断绕过）")
    void truncatedContentRejected() {
        byte[] tooShort = Arrays.copyOf(PNG_HEAD, 4);
        assertThrows(BusinessException.class, () -> service.validate(file("short.png", tooShort)));
    }
}
