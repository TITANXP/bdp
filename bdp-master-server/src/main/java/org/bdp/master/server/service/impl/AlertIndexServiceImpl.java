package org.bdp.master.server.service.impl;

import org.bdp.master.server.domain.AlertIndex;
import org.bdp.master.server.repository.AlertIndexJpaRepository;
import org.bdp.master.server.repository.AlertIndexRedisRepository;
import org.bdp.master.server.service.AlertIndexService;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.List;

@Component("alertService")
@Transactional
public class AlertIndexServiceImpl implements AlertIndexService {

    private final AlertIndexJpaRepository alertIndexJpaRepository;

    private final AlertIndexRedisRepository alertIndexRedisRepository;

    public AlertIndexServiceImpl(AlertIndexJpaRepository alertIndexJpaRepository, AlertIndexRedisRepository alertIndexRedisRepository) {
        this.alertIndexJpaRepository = alertIndexJpaRepository;
        this.alertIndexRedisRepository = alertIndexRedisRepository;
    }

    @Override
    public AlertIndex findOne(Long id) {
        return alertIndexRedisRepository.findOne(id);
    }

    @Override
    public AlertIndex findOne(String alertName) {
        return alertIndexRedisRepository.findOne(alertName);
    }

    @Override
    public List<AlertIndex> findAll() {
        return alertIndexRedisRepository.findAll();
    }

    @Override
    public void save(AlertIndex alertIndex) {
        alertIndexJpaRepository.save(alertIndex);
        alertIndexRedisRepository.save(alertIndex);
    }

    @Override
    public void delete(Long id) {
        AlertIndex alertIndex = findOne(id);
        alertIndexJpaRepository.delete(alertIndex);
        alertIndexRedisRepository.delete(alertIndex);
    }

    // 从Mysql bdp_master.alert_index读取所有数据，加载到redis
    @Override
    public void loadAll() {
        alertIndexJpaRepository.findAll().forEach(
                alertIndex -> alertIndexRedisRepository.save(alertIndex)
        );
    }
}
