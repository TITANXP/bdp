package org.bdp.master.server.repository;

import org.bdp.master.server.domain.Server;

import java.util.List;

/**
 * 对MySQL的server进行操作
 * key:
 *  server:{Id}
 *  i_server:{serverName} （根据server的属性serverName存储上面主数据的key）
 */
public interface ServerRedisRepository {

    Server findOne(Long id);

    Server findOne(String hostname);

    List<Server> findAll();

    void save(Server server);

    void delete(Server server);

}
