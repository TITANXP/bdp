package org.bdp.master.server.repository;

import org.bdp.master.server.domain.Server;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 对MySQL的server进行操作
 * 表：
 *  server
 */
public interface ServerJpaRepository extends PagingAndSortingRepository<Server, Long> {

}
