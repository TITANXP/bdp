package org.bdp.master.server.repository;

import org.bdp.master.server.domain.App;

import java.util.List;

/**
 * 对MySQL的app进行操作
 * key:
 *  app:{id}
 *  i_app:{appName} （根据app的属性appName存储上面主数据的key）
 */
public interface AppRedisRepository {
    App findOne(Long id);
    App findOne(String name);
    List<App> findAll();
    void save(App app);
    void delete(App app);
}
