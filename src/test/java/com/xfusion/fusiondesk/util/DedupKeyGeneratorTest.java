package com.xfusion.fusiondesk.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DedupKeyGeneratorTest {
    /** 验证 Unicode NFKC、空白折叠和大小写标准化具有确定性。 */
    @Test
    void normalize_shouldFoldUnicodeWhitespaceAndCase() {
        assertEquals(
                DedupKeyGenerator.normalize("  ＶＰＮ   Error "),
                DedupKeyGenerator.normalize("vpn error"));
    }
}
