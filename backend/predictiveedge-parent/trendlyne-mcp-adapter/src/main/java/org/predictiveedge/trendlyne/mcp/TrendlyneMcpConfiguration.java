package org.predictiveedge.trendlyne.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TrendlyneMcpProperties.class)
public class TrendlyneMcpConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "predictiveedge.trendlyne.mcp", name = "enabled", havingValue = "true")
    TrendlyneMcpGateway trendlyneMcpGateway(TrendlyneMcpProperties properties) {
        var operations = new SdkTrendlyneMcpOperations(
                properties.requiredUrl(), properties.requiredRequestTimeout());
        return new TrendlyneMcpGateway(operations);
    }
}
