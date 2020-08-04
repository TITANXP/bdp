package org.bdp.master.server.controller;


import org.bdp.master.server.domain.Server;
import org.bdp.master.server.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
public class ServerController {

    private static Logger logger = LoggerFactory.getLogger(ServerController.class);

    @Autowired
    private ServerService serverService;

    // 获取指定ID的Server
    @RequestMapping(method = RequestMethod.GET, path = "/server/{id}")
    public Server find(@PathVariable Long id){
        return serverService.findOne(id);
    }

    // 根据hostname查找Server
    @RequestMapping(method = RequestMethod.GET, path = "/server")
    public Server find(@RequestParam("hostname") String hostname){
        return serverService.findOne(hostname);
    }

    // 获取所有的Server
    @RequestMapping(method = RequestMethod.GET, path = "/servers")
    public List<Server> findAll(HttpServletRequest request){
        return serverService.findAll();
    }

    // 新建一个Server
    @RequestMapping(method = RequestMethod.POST, path = "/server")
    public void save(@RequestBody Server server){
        serverService.save(server);
    }

    // 删除指定ID的Server
    @RequestMapping(method = RequestMethod.DELETE, path = "/server/{id}")
    public void delete(@PathVariable Long id){
        serverService.delete(id);
    }
}
