package org.bdp.master.server.repository;

import org.bdp.master.server.domain.MetricIndex;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 对MySQL的metric_index进行操作
 * 表：
 *  metric_index
 */
public interface MetricIndexJpaRepository extends PagingAndSortingRepository<MetricIndex, Long> {
}
