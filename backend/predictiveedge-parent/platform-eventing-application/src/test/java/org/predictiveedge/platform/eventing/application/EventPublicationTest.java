package org.predictiveedge.platform.eventing.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventPublicationTest {
    @Test
    void rejectsUnsafeTopicNamesBeforeTheyReachKafka() {
        assertThatThrownBy(() -> new EventPublication("invalid topic", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
        assertThatThrownBy(() -> new EventPublication("..", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
