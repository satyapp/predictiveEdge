package org.predictiveedge.platform.eventing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("predictiveedge.kafka")
public class PredictiveEdgeKafkaProperties {
    private boolean provisionTopics;
    private int partitions = 3;
    private short replicationFactor = 1;

    public boolean isProvisionTopics() {
        return provisionTopics;
    }

    public void setProvisionTopics(boolean provisionTopics) {
        this.provisionTopics = provisionTopics;
    }

    public int getPartitions() {
        return partitions;
    }

    public void setPartitions(int partitions) {
        if (partitions < 1) {
            throw new IllegalArgumentException("predictiveedge.kafka.partitions must be at least 1");
        }
        this.partitions = partitions;
    }

    public short getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(short replicationFactor) {
        if (replicationFactor < 1) {
            throw new IllegalArgumentException(
                    "predictiveedge.kafka.replication-factor must be at least 1");
        }
        this.replicationFactor = replicationFactor;
    }
}
