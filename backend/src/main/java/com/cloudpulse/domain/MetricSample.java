
package com.cloudpulse.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "metric_sample")
public class MetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "metric_name", nullable = false, length = 150)
    private String metricName;

    @Column(name = "metric_value", nullable = false)
    private double value;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    protected MetricSample() {}

    public MetricSample(Long serviceId, String metricName,
                        double value, Instant sampledAt) {
        this.serviceId = serviceId;
        this.metricName = metricName;
        this.value = value;
        this.sampledAt = sampledAt;
    }

    public Long getId() {
        return id;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public String getMetricName() {
        return metricName;
    }

    public double getValue() {
        return value;
    }

    public Instant getSampledAt() {
        return sampledAt;
    }
}
