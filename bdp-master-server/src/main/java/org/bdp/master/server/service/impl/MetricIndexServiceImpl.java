package org.bdp.master.server.service.impl;

import org.bdp.master.server.domain.MetricIndex;
import org.bdp.master.server.repository.MetricIndexJpaRepository;
import org.bdp.master.server.repository.MetricIndexRedisRepository;
import org.bdp.master.server.service.MetricIndexService;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.List;

@Component("metricService")
@Transactional
public class MetricIndexServiceImpl implements MetricIndexService {

    private final MetricIndexJpaRepository metricIndexJpaRepository;

    private final MetricIndexRedisRepository metricIndexRedisRepository;

    public MetricIndexServiceImpl(MetricIndexJpaRepository metricIndexJpaRepository, MetricIndexRedisRepository metricIndexRedisRepository) {
        this.metricIndexJpaRepository = metricIndexJpaRepository;
        this.metricIndexRedisRepository = metricIndexRedisRepository;
    }

    @Override
    public MetricIndex findOne(Long id) {
        return metricIndexRedisRepository.findOne(id);
    }

    @Override
    public MetricIndex findOne(String metricName) {
        return metricIndexRedisRepository.findOne(metricName);
    }

    @Override
    public List<MetricIndex> findAll() {
        return metricIndexRedisRepository.findAll();
    }

    @Override
    public void save(MetricIndex metricIndex) {
        metricIndexJpaRepository.save(metricIndex);
        metricIndexRedisRepository.save(metricIndex);
    }

    @Override
    public void delete(Long id) {
        MetricIndex metricIndex = findOne(id);
        metricIndexJpaRepository.delete(metricIndex.getId());
        metricIndexRedisRepository.delete(metricIndex);
    }

    // 从Mysql bdp_master.metric_index读取所有数据，加载到redis
    @Override
    public void loadAll() {
        metricIndexJpaRepository.findAll().forEach(
                metricIndex -> metricIndexRedisRepository.save(metricIndex)
        );
    }
}
