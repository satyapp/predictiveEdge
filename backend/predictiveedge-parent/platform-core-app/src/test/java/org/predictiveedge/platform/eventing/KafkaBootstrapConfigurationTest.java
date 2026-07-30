package org.predictiveedge.platform.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class KafkaBootstrapConfigurationTest {
    private final KafkaBootstrapConfiguration configuration = new KafkaBootstrapConfiguration();

    @Test
    void provisionsEveryVersionedPlatformTopicWithConfiguredDurability() {
        var properties = new PredictiveEdgeKafkaProperties();
        properties.setPartitions(6);
        properties.setReplicationFactor((short) 2);

        var topics = configuration.buildTopics(properties);

        assertThat(Arrays.asList(topics))
                .extracting(topic -> topic.name())
                .containsExactlyElementsOf(PlatformKafkaTopics.ALL);
        assertThat(Arrays.asList(topics))
                .allSatisfy(topic -> {
                    assertThat(topic.numPartitions()).isEqualTo(6);
                    assertThat(topic.replicationFactor()).isEqualTo((short) 2);
                });
    }
}
