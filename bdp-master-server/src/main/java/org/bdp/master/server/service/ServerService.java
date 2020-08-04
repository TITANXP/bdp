package org.bdp.master.server.service;

import org.bdp.master.server.domain.Server;

import java.util.List;

public interface ServerService {

    Server findOne(Long id);

    Server findOne(String hostname);

    List<Server> findAll();

    void save(Server server);

    void delete(Long id);

    void loadAll();

}
