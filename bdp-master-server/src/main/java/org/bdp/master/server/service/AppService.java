package org.bdp.master.server.service;

import org.bdp.master.server.domain.App;

import java.util.List;

public interface AppService {
    App findOne(Long id);

    App findOne(String name);

    void save(App app);

    void delete(Long id);

    List<App> findAll();

    void loadAll();
}
