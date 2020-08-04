package org.bdp.master.server.controller;

import org.bdp.master.server.service.AlertIndexService;
import org.bdp.master.server.service.AppService;
import org.bdp.master.server.service.MetricIndexService;
import org.bdp.master.server.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AppStartupListener {

    public static Logger logger = LoggerFactory.getLogger(AppStartupListener.class);

    @Autowired
    private AppService appService;

    @Autowired
    private ServerService serverService;

    @Autowired
    private MetricIndexService metricIndexService;

    @Autowired
    private AlertIndexService alertIndexService;

    // 事件监听器
    @EventListener
    // 在容器初始化完成后执行，将全部数据从MySQL加载到Redis
    public void onApplicationEvent(ContextRefreshedEvent event){
        logger.info("start to load all data into redis");
        appService.loadAll();
        serverService.loadAll();
        metricIndexService.loadAll();
        alertIndexService.loadAll();
        logger.info("loading all data to redis id done!");
    }

}
