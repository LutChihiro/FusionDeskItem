package com.xfusion.fusiondesk.util;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class DedupKeyGeneratorTest{@Test void normalize_shouldFoldUnicodeWhitespaceAndCase(){assertEquals(DedupKeyGenerator.normalize("  ＶＰＮ   Error "),DedupKeyGenerator.normalize("vpn error"));}}
