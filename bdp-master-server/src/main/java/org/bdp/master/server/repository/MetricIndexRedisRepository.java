package org.bdp.master.server.repository;

import org.bdp.master.server.domain.MetricIndex;

import java.util.List;

/**
 * 对MySQL的metric_index进行操作
 * key:
 *  metric_index:{id}
 *  i_metric_index:{metricName} （根据metricIndex的属性metricName存储上面主数据的key）
 */
public interface MetricIndexRedisRepository {

    MetricIndex findOne(Long id);

    MetricIndex findOne(String name);

    List<MetricIndex> findAll();

    void save(MetricIndex metricIndex);

    void delete(MetricIndex metricIndex);

}
