package com.cloudpulse.repo;

import com.cloudpulse.domain.MetricSample;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MetricSampleRepository extends JpaRepository<MetricSample, Long> {
    List<MetricSample> findByServiceIdAndSampledAtAfterOrderBySampledAtAsc(Long serviceId, Instant since);
    @Modifying
    @Transactional
    @Query("delete from MetricSample m where m.sampledAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
