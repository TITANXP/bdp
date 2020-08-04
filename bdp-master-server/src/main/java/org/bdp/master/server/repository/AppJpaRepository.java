package org.bdp.master.server.repository;

import org.bdp.master.server.domain.App;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 对MySQL的app进行操作
 * 表：
 *  app
 */
public interface AppJpaRepository extends PagingAndSortingRepository<App, Long> { }
