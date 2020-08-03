package org.bdp.master.server.controller;

import org.bdp.master.server.domain.MetricIndex;
import org.bdp.master.server.service.MetricIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
public class MetricIndexController {

    public static Logger logger = LoggerFactory.getLogger(MetricIndexController.class);

    @Autowired
    private MetricIndexService metricIndexService;

    // 获取指定ID的MetricIndex
    @RequestMapping(method = RequestMethod.GET, path = "/metricIndex/{id}")
    public MetricIndex find(@PathVariable Long id){
        return metricIndexService.findOne(id);
    }

    // 根据name查找MetricIndex
    @RequestMapping(method = RequestMethod.GET, path = "/metricIndex")
    public MetricIndex find(@RequestParam("name") String name){
        return metricIndexService.findOne(name);
    }

    // 获取所有的MetricIndex
    @RequestMapping(method = RequestMethod.GET, path = "/metricIndexes")
    public List<MetricIndex> findAll(HttpServletRequest request) {
        return metricIndexService.findAll();
    }

    // 新建一个MetricIndex
    @RequestMapping(method = RequestMethod.POST, path = "/metricIndex")
    public void save(@RequestBody MetricIndex metricIndex){
        metricIndexService.save(metricIndex);
    }

    // 删除指定ID的MetricIndex
    @RequestMapping(method = RequestMethod.DELETE, path = "/metricIndex/{id}")
    public void delete(@PathVariable Long id){
        metricIndexService.delete(id);
    }
}
