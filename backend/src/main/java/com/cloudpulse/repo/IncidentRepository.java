package com.cloudpulse.repo;

import com.cloudpulse.domain.Incident;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByServiceIdAndResolvedAtIsNull(Long serviceId);

    long countByResolvedAtIsNull();

    List<Incident> findByResolvedAtIsNullOrderByStartedAtDesc(Pageable pageable);

    List<Incident> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<Incident> findByServiceIdOrderByStartedAtDesc(Long serviceId, Pageable pageable);
}
