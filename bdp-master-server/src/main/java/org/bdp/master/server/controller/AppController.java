package org.bdp.master.server.controller;

import org.bdp.master.server.domain.App;
import org.bdp.master.server.service.AppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.PathParam;
import java.util.List;

@RestController
public class AppController {

    public static Logger logger = LoggerFactory.getLogger(AppController.class);

    @Autowired
    private AppService appService;

    // 获取指定ID的App
    @RequestMapping(method = RequestMethod.GET, path = "/app/{id}")
    public App find(@PathVariable Long id){
        return appService.findOne(id);
    }

    // 根据name查找App
    @RequestMapping(method = RequestMethod.GET, path = "/app")
    public App find(@RequestParam("name") String appName){
        return appService.findOne(appName);
    }

    // 获取所有的App
    @RequestMapping(method = RequestMethod.GET, path = "/apps")
    public List<App> findAll(HttpServletRequest request){
        return appService.findAll();
    }

    // 新建一个App
    @RequestMapping(method = RequestMethod.POST, path = "/app")
    public void save(@RequestBody App app){
        appService.save(app);
    }

    // 删除指定ID的App
    @RequestMapping(method = RequestMethod.DELETE, path = "/app/{id}")
    public void delete(@PathVariable Long id){
        appService.delete(id);
    }


}
