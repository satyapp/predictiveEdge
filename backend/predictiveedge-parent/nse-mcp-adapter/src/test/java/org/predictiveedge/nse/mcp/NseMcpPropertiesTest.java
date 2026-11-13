package org.predictiveedge.nse.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;

class NseMcpPropertiesTest {
    @Test
    void usesTheOfficialNseEndpointsByDefault() {
        var properties = new NseMcpProperties();

        assertThat(properties.requiredUrl(NseMcpServer.BHAVCOPY))
                .isEqualTo(URI.create("https://mcp.nseindia.in/bhavcopy/cm/mcp"));
        assertThat(properties.requiredUrl(NseMcpServer.CM_MARKET))
                .isEqualTo(URI.create("https://mcp.nseindia.in/cmmkt/mcp"));
    }

    @Test
    void rejectsNonHttpEndpointOverrides() {
        var properties = new NseMcpProperties();
        properties.setBhavcopyUrl(URI.create("file:///tmp/mcp"));

        assertThatThrownBy(() -> properties.requiredUrl(NseMcpServer.BHAVCOPY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bhavcopy-url");
    }
}
