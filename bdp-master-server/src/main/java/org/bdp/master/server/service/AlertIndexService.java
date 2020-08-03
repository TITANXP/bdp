package org.bdp.master.server.service;

import org.bdp.master.server.domain.AlertIndex;

import java.util.List;

public interface AlertIndexService {

    AlertIndex findOne(Long id);

    AlertIndex findOne(String alertName);

    List<AlertIndex> findAll();

    void save(AlertIndex alertIndex);

    void delete(Long id);

    void loadAll();

}
