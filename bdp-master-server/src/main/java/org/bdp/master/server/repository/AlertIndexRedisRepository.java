package org.bdp.master.server.repository;

import org.bdp.master.server.domain.AlertIndex;

import java.util.List;

/**
 * 对MySQL的alert_index进行操作
 * key:
 *  alert_index:{id}
 *  i_alert_index:{alertName} （根据AlertIndex的属性alertName存储上面主数据的key）
 */
public interface AlertIndexRedisRepository {

    AlertIndex findOne(Long id);

    AlertIndex findOne(String name);

    List<AlertIndex> findAll();

    void save(AlertIndex alertIndex);

    void delete(AlertIndex alertIndex);

}
