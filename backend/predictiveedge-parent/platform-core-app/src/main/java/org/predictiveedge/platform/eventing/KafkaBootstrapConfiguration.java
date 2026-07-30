package org.predictiveedge.platform.eventing;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PredictiveEdgeKafkaProperties.class)
public class KafkaBootstrapConfiguration {
    @Bean
    @ConditionalOnProperty(
            prefix = "predictiveedge.kafka",
            name = "provision-topics",
            havingValue = "true")
    KafkaAdmin.NewTopics predictiveEdgeTopics(PredictiveEdgeKafkaProperties properties) {
        return new KafkaAdmin.NewTopics(buildTopics(properties));
    }

    NewTopic[] buildTopics(PredictiveEdgeKafkaProperties properties) {
        return PlatformKafkaTopics.ALL.stream()
                .map(name -> TopicBuilder.name(name)
                        .partitions(properties.getPartitions())
                        .replicas(properties.getReplicationFactor())
                        .build())
                .toArray(NewTopic[]::new);
    }
}
