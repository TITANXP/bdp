package org.bdp.master.server.repository;

import org.bdp.master.server.domain.AlertIndex;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 对MySQL的alert_index进行操作
 * 表：
 *  alert_index
 */
public interface AlertIndexJpaRepository extends PagingAndSortingRepository<AlertIndex, Long> {
}
