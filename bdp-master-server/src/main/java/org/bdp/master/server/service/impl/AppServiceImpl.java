package org.bdp.master.server.service.impl;

import org.bdp.master.server.controller.AlertIndexController;
import org.bdp.master.server.controller.AppController;
import org.bdp.master.server.domain.App;
import org.bdp.master.server.repository.AppJpaRepository;
import org.bdp.master.server.repository.AppRedisRepository;
import org.bdp.master.server.service.AppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.List;

@Component
@Transactional
public class AppServiceImpl implements AppService {

    public static Logger logger = LoggerFactory.getLogger(AppController.class);

    private final AppJpaRepository appJpaRepository;

    private final AppRedisRepository appRedisRepository;

    public AppServiceImpl(AppJpaRepository appJpaRepository, AppRedisRepository appRedisRepository) {
        this.appJpaRepository = appJpaRepository;
        this.appRedisRepository = appRedisRepository;
    }

    @Override
    public App findOne(Long id) {
        return appRedisRepository.findOne(id);
    }

    @Override
    public App findOne(String name) {
        return appRedisRepository.findOne(name);
    }

    @Override
    public void save(App app) {
        App savedApp = appJpaRepository.save(app);
        appRedisRepository.save(savedApp);
    }

    @Override
    public void delete(Long id) {
        App app = findOne(id);
        appJpaRepository.delete(app);
        appRedisRepository.delete(app);
    }

    @Override
    public List<App> findAll() {
        return appRedisRepository.findAll();
    }

    // 从Mysql bdp_master.app读取所有数据，加载到redis
    @Override
    public void loadAll() {
        appJpaRepository.findAll().forEach(
                app -> appRedisRepository.save(app)
        );
    }
}
