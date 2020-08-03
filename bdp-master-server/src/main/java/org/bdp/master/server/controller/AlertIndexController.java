package org.bdp.master.server.controller;

import org.bdp.master.server.domain.AlertIndex;
import org.bdp.master.server.service.AlertIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
public class AlertIndexController {

    public static Logger logger = LoggerFactory.getLogger(AlertIndexController.class);

    @Autowired
    private AlertIndexService alertIndexService;

    // 获取指定ID的AlertIndex
    @RequestMapping(method = RequestMethod.GET, path = "/alertIndex/{id}")
    public AlertIndex find(@PathVariable Long id){
        return alertIndexService.findOne(id);
    }

    // 根据name查找AlertIndex
    @RequestMapping(method = RequestMethod.GET, path = "/alertIndex")
    public AlertIndex find(@RequestParam("name") String name){
        return alertIndexService.findOne(name);
    }

    // 获取所有的AlertIndex
    @RequestMapping(method = RequestMethod.GET, path = "/alertIndexes")
    public List<AlertIndex> findAll(HttpServletRequest request){
        return alertIndexService.findAll();
    }

    // 新建一个AlertIndex
    @RequestMapping(method = RequestMethod.POST, path = "/alertIndex")
    public void save(@RequestBody AlertIndex alertIndex){
        alertIndexService.save(alertIndex);
    }

    // 删除指定ID的AlertIndex
    @RequestMapping(method = RequestMethod.DELETE, path = "/alertIndex/{id}")
    public void delete(@PathVariable Long id){
        alertIndexService.delete(id);
    }
}
