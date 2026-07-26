package top.srcrs.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * bili_ticket 签名的测试。
 *
 * @author srcrs
 * @Time 2026-07-26
 */
class BiliTicketTest {

    @Test
    @DisplayName("HMAC-SHA256 结果是 64 位小写十六进制")
    void hmacMatchesReference() {
        assertEquals("bb79f0d980ffbb51597aa1a3e8b55603025cc1322ac766f4c1a98852e6182514",
                BiliTicket.hmacSha256("ts1700000000", "XgwSnGZ1p"));
    }

    @Test
    @DisplayName("每个字节都补足两位，不会漏掉前导 0")
    void hmacKeepsLeadingZeros() {
        String hex = BiliTicket.hmacSha256("ts1", "XgwSnGZ1p");
        assertEquals(64, hex.length());
        assertEquals(hex.toLowerCase(), hex);
    }
}
