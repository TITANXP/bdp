package org.bdp.master.server.repository;

import org.bdp.master.server.domain.MetricThreshold;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * metricThreshold
 */
public interface MetricThresholdRepository extends PagingAndSortingRepository<MetricThreshold, Long> {
}
