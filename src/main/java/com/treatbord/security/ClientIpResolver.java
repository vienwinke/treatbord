package com.treatbord.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 客户端 IP 解析（**可信代理链**语义）。
 *
 * <p>核心原则：**只有当 TCP 对端（remoteAddr）本身是可信代理时，才采信 X-Forwarded-For**；
 * 且取的是"从右往左、第一个不可信地址"（标准做法，见 Apache RemoteIP / Rails 的 remote_ip）。
 *
 * <p>为什么不能取最左边那个（常见错误写法）：
 * XFF 是**逐跳追加**的，最左侧由**客户端自己填写**。若代理是"追加"而非"覆盖"，
 * 攻击者发送 {@code X-Forwarded-For: 1.2.3.4} → 服务端看到 {@code 1.2.3.4, <真实IP>}，
 * 取最左就会用**攻击者伪造的值**，从而按 IP 限流形同虚设（换个假 IP 就能继续刷）。
 *
 * <p>配置：
 * <ul>
 *   <li>{@code treatbord.security.trusted-proxies}：可信代理网段（逗号分隔 CIDR），
 *       部署在 Nginx 后应设为 Docker 网段，如 {@code 172.16.0.0/12}；默认空 = 不信任任何代理头</li>
 *   <li>{@code treatbord.security.trust-forwarded-for}：历史开关（保留兼容）。
 *       在未配置网段时若为 true，则信任所有对端并使用 XFF 的**最右**值</li>
 * </ul>
 */
@Component
public class ClientIpResolver {

    /** 仅接受 IP 字面量（避免 XFF 里塞主机名触发 DNS 查询） */
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    @Value("${treatbord.security.trusted-proxies:}")
    private String trustedProxiesRaw;

    @Value("${treatbord.security.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    private List<Cidr> trustedRanges;
    private String parsedFrom;

    public String resolve(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        String remote = normalize(req.getRemoteAddr());
        List<Cidr> ranges = ranges();
        boolean peerTrusted = ranges.isEmpty() ? trustForwardedFor : matchesAny(ranges, remote);
        if (!peerTrusted) {
            return remote; // 对端不可信：完全忽略 XFF（防伪造）
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remote;
        }
        String[] hops = xff.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {   // 从右往左：跳过可信代理，取第一个不可信地址
            String hop = normalize(hops[i]);
            if (hop == null || hop.isEmpty()) {
                continue;
            }
            if (ranges.isEmpty() || !matchesAny(ranges, hop)) {
                return hop;
            }
        }
        return remote; // 整条链都是可信代理
    }

    /** 去掉端口（XFF 里可能是 "1.2.3.4:5678"），并过滤非 IP 字面量 */
    private String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("[")) {                       // [::1]:8080
            int end = s.indexOf(']');
            return end > 0 ? s.substring(1, end) : s;
        }
        int colon = s.indexOf(':');
        if (colon > 0 && s.indexOf(':', colon + 1) < 0) { // 只有一个冒号 → IPv4:port
            s = s.substring(0, colon);
        }
        return IP_LITERAL.matcher(s).matches() ? s : null;
    }

    private List<Cidr> ranges() {
        String raw = trustedProxiesRaw == null ? "" : trustedProxiesRaw.trim();
        if (trustedRanges != null && raw.equals(parsedFrom)) {
            return trustedRanges;
        }
        List<Cidr> list = new ArrayList<>();
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            Cidr c = Cidr.parse(s);
            if (c != null) {
                list.add(c);
            }
        }
        trustedRanges = list;
        parsedFrom = raw;
        return list;
    }

    private boolean matchesAny(List<Cidr> ranges, String ip) {
        if (ip == null) {
            return false;
        }
        for (Cidr c : ranges) {
            if (c.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    /** CIDR 网段（IPv4/IPv6 通用，按字节前缀比较） */
    record Cidr(byte[] network, int prefix) {

        static Cidr parse(String text) {
            try {
                String[] parts = text.split("/");
                if (!IP_LITERAL.matcher(parts[0].trim()).matches()) {
                    return null;
                }
                byte[] addr = InetAddress.getByName(parts[0].trim()).getAddress();
                int prefix = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : addr.length * 8;
                if (prefix < 0 || prefix > addr.length * 8) {
                    return null;
                }
                return new Cidr(addr, prefix);
            } catch (Exception e) {
                return null;
            }
        }

        boolean matches(String ip) {
            try {
                if (!IP_LITERAL.matcher(ip).matches()) {
                    return false;
                }
                byte[] other = InetAddress.getByName(ip).getAddress();
                if (other.length != network.length) {
                    return false; // IPv4 与 IPv6 不互相匹配
                }
                int fullBytes = prefix / 8;
                int restBits = prefix % 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (other[i] != network[i]) {
                        return false;
                    }
                }
                if (restBits > 0) {
                    int mask = (0xFF << (8 - restBits)) & 0xFF;
                    if ((other[fullBytes] & mask) != (network[fullBytes] & mask)) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
