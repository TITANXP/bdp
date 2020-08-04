package org.bdp.master.server.service.impl;

import org.bdp.master.server.domain.Server;
import org.bdp.master.server.repository.ServerJpaRepository;
import org.bdp.master.server.repository.ServerRedisRepository;
import org.bdp.master.server.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Transactional
public class ServerServiceImpl implements ServerService {

    private static Logger logger = LoggerFactory.getLogger(ServerService.class);

    private final ServerJpaRepository serverJpaRepository;

    private final ServerRedisRepository serverRedisRepository;

    @SuppressWarnings("unused")
    public ServerServiceImpl(ServerJpaRepository serverJpaRepository, ServerRedisRepository serverRedisRepository) {
        this.serverJpaRepository = serverJpaRepository;
        this.serverRedisRepository = serverRedisRepository;
    }

    @Override
    public Server findOne(Long id) {
        return serverRedisRepository.findOne(id);
    }

    @Override
    public Server findOne(String hostname) {
        return serverRedisRepository.findOne(hostname);
    }

    @Override
    public List<Server> findAll() {
        return serverRedisRepository.findAll();
    }

    @Override
    public void save(Server server) {
        logger.debug(server.getHostname());
        Server savedServer = serverJpaRepository.save(server);
        serverRedisRepository.save(savedServer);
    }

    @Override
    public void delete(Long id) {
        Server server = findOne(id);
        serverJpaRepository.delete(server.getId());
        serverRedisRepository.delete(server);
    }

    // 从Mysql bdp_master.server读取所有数据，加载到redis
    @Override
    public void loadAll() {
        serverJpaRepository.findAll().forEach(
                server -> serverRedisRepository.save(server)
        );
    }
}
