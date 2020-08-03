package org.bdp.master.server.service;

import org.bdp.master.server.domain.MetricIndex;

import java.util.List;

public interface MetricIndexService {

    MetricIndex findOne(Long id);

    MetricIndex findOne(String metricName);

    List<MetricIndex> findAll();

    void save(MetricIndex metricIndex);

    void delete(Long id);

    void loadAll();
}
