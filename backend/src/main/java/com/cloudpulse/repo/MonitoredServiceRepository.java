package com.cloudpulse.repo;

import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.domain.ServiceStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    List<MonitoredService> findByEnabledTrue();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByEnabledTrue();

    long countByEnabledTrueAndStatus(ServiceStatus status);
}
