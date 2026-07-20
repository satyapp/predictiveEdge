package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZerodhaLoginFlowTest {
    @Test
    void createsTheOfficialVersionThreeLoginUrl() {
        assertThat(ZerodhaLoginFlow.loginUri("test key").toString())
                .isEqualTo("https://kite.zerodha.com/connect/login?v=3&api_key=test+key");
    }

    @Test
    void signsTokenExchangeUsingTheDocumentedChecksumOrder() {
        assertThat(ZerodhaLoginFlow.tokenChecksum("api-key", "request-token", "api-secret"))
                .isEqualTo("d93f7cb933c3518b3a5f87fa0b49ff6bc71de987dc59bc8015b296920b762fd0");
    }
}
