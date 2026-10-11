package com.cloudpulse.repo;

import com.cloudpulse.domain.HealthCheck;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {

    List<HealthCheck> findByServiceIdOrderByCheckedAtDesc(Long serviceId, Pageable pageable);

    List<HealthCheck> findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(Long serviceId, Instant since);

    @Query("select avg(h.responseTimeMs) from HealthCheck h where h.success = true and h.checkedAt > :since")
    Double averageSuccessfulResponseTimeSince(@Param("since") Instant since);

    @Transactional
    @Modifying
    @Query("delete from HealthCheck h where h.checkedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
