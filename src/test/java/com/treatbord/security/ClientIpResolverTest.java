package com.treatbord.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 客户端 IP 解析（可信代理链）验收：
 * 重点验证「客户端伪造的 X-Forwarded-For 无效」——这是按 IP 限流能否真正生效的前提。
 */
class ClientIpResolverTest {

    private ClientIpResolver resolver(String trustedProxies, boolean legacyTrustFlag) {
        ClientIpResolver r = new ClientIpResolver();
        ReflectionTestUtils.setField(r, "trustedProxiesRaw", trustedProxies);
        ReflectionTestUtils.setField(r, "trustForwardedFor", legacyTrustFlag);
        return r;
    }

    private MockHttpServletRequest req(String remoteAddr, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remoteAddr);
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        return r;
    }

    @Test
    @DisplayName("默认（未配置可信代理）：完全忽略 X-Forwarded-For，防伪造绕过限流")
    void ignoresXffByDefault() {
        ClientIpResolver r = resolver("", false);
        assertEquals("9.9.9.9", r.resolve(req("9.9.9.9", "1.2.3.4")), "应返回 TCP 对端地址");
    }

    @Test
    @DisplayName("对端不在可信网段：即便带了 XFF 也不采信")
    void untrustedPeerIgnoresXff() {
        ClientIpResolver r = resolver("172.16.0.0/12", false);
        assertEquals("8.8.8.8", r.resolve(req("8.8.8.8", "1.2.3.4")));
    }

    @Test
    @DisplayName("对端是可信代理：取 XFF 中的真实客户端")
    void trustedProxyUsesXff() {
        ClientIpResolver r = resolver("172.16.0.0/12,127.0.0.1/32", false);
        assertEquals("1.2.3.4", r.resolve(req("172.18.0.5", "1.2.3.4")));
    }

    @Test
    @DisplayName("★ 客户端伪造 XFF 前缀无效：取【最右】不可信地址，而非最左")
    void clientForgedXffIsIgnored() {
        ClientIpResolver r = resolver("172.16.0.0/12", false);
        // 真实客户端 1.2.3.4，攻击者伪造了 9.9.9.9；Nginx 追加后为 "9.9.9.9, 1.2.3.4"
        assertEquals("1.2.3.4", r.resolve(req("172.18.0.5", "9.9.9.9, 1.2.3.4")),
                "必须取最右侧不可信地址（真实客户端），否则换个假 IP 就能绕过限流");
    }

    @Test
    @DisplayName("多级代理：跳过所有可信代理，取第一个不可信地址")
    void skipsTrustedHops() {
        ClientIpResolver r = resolver("172.16.0.0/12", false);
        assertEquals("1.2.3.4", r.resolve(req("172.18.0.5", "1.2.3.4, 172.18.0.9, 172.18.0.7")));
    }

    @Test
    @DisplayName("整条链都是可信代理：回落到 TCP 对端")
    void allHopsTrustedFallsBackToPeer() {
        ClientIpResolver r = resolver("172.16.0.0/12", false);
        assertEquals("172.18.0.5", r.resolve(req("172.18.0.5", "172.18.0.9, 172.18.0.7")));
    }

    @Test
    @DisplayName("带端口的 XFF 与 IPv6 括号形式都能正确剥离")
    void stripsPortAndBrackets() {
        ClientIpResolver r = resolver("172.16.0.0/12", false);
        assertEquals("1.2.3.4", r.resolve(req("172.18.0.5", "1.2.3.4:5678")));
        ClientIpResolver r6 = resolver("::1/128", false);
        assertEquals("2001:db8::1", r6.resolve(req("::1", "[2001:db8::1]:443")));
    }

    @Test
    @DisplayName("XFF 塞主机名（触发 DNS 的常见攻击面）被忽略")
    void hostnameInXffIsRejected() {
        ClientIpResolver r = resolver("172.16.0.0/12", false);
        assertEquals("172.18.0.5", r.resolve(req("172.18.0.5", "evil.example.com")));
    }

    @Test
    @DisplayName("历史开关（无网段 + trust-forwarded-for=true）：取最右值，而非旧实现的最左值")
    void legacyFlagUsesRightmost() {
        ClientIpResolver r = resolver("", true);
        assertEquals("1.2.3.4", r.resolve(req("10.0.0.1", "9.9.9.9, 1.2.3.4")));
    }

    @Test
    @DisplayName("IPv4 网段不匹配 IPv6 地址")
    void cidrFamilyMismatch() {
        ClientIpResolver r = resolver("172.16.0.0/12", false);
        assertEquals("2001:db8::1", r.resolve(req("2001:db8::1", "1.2.3.4")));
    }
}
