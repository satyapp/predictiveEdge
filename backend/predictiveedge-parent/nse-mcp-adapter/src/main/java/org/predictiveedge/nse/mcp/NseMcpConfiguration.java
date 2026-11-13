package org.predictiveedge.nse.mcp;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NseMcpProperties.class)
public class NseMcpConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "predictiveedge.nse.mcp", name = "enabled", havingValue = "true")
    NseMcpGateway nseMcpGateway(NseMcpProperties properties) {
        var timeout = properties.requiredRequestTimeout();
        Map<NseMcpServer, NseMcpOperations> operations = Map.of(
                NseMcpServer.BHAVCOPY,
                new SdkNseMcpOperations(properties.requiredUrl(NseMcpServer.BHAVCOPY), timeout),
                NseMcpServer.CM_MARKET,
                new SdkNseMcpOperations(properties.requiredUrl(NseMcpServer.CM_MARKET), timeout));
        return new NseMcpGateway(operations);
    }
}
