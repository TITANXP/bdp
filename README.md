## 整个工程使用Maven构建，由9个子项目组成：

| 项目名称     | 项目介绍               | 详细介绍                                                     | 技术框架               |
| --------------------------- | ---------------------- | ------------------------------------------------------------ | ---------------------- |
| bdp-metric | 模拟数据源生产仿真数据 | 模拟外部数据源，主要用于生成dummy metric数据，然后写入MySQL，该项目主要由shell脚本组成。 | Shell |
| bdp-import        | 批量数据导入           | 负责从外部数据源以批量的方式采集数据。                       | Sqoop                  |
| bdp-collect       | 实时数据采集           |                                                              | Camel、Kafka           |
| bdp-dwh           | 构建数据仓库           | 负责构建数据仓库，是批处理的核心项目。                       | Spark SQL              |
| bdp-master-server | 主数据管理             | 主数据系统的服务器端，负责维护主数据，它有两个存储介质：MySQL、Redis，前者用于主数据的持久化存储，后者作为Cache为实时流处理提供主数据查询服务。 | Spring Boot、Redis     |
| bdp-master-client | 主数据读写客户端       | 专门为读取主数据开发的客户端程序，从bdp-master-server维护的Redis上读取数据，供流处理项目bdp-stream使用 | Scala、Redis           |
| bdp-stream        | 实时流计算             | 负责实时流处理，是实时处理的核心项目。                       | Spark Streaming、HBase |
| bdp-workflow      | 作业调度               | 负责所有批处理的作业编排和调度                               | Oozie                  |
| bdp-parent        | 管理依赖               | 负责统一维护以上所有项目的依赖类库和插件版本，这是Maven项目中常见的做法。 |                        |



## 项目架构  

![](https://raw.githubusercontent.com/TITANXP/pic/master/img/%E5%B9%B3%E5%8F%B0%E6%9E%B6%E6%9E%84.png)

## 项目服务器配置

<table>
    <tr>
        <td rowspan="2">项目</td>
        <td rowspan="2">配置项</td>
        <td rowspan="2">配置项说明</td>
        <td colspan="2">Maven Profile</td>
    </tr>
    <tr>
        <td>standalone</td>
        <td>cluster</td>
    </tr>
    <tr>
    	<td rowspan="2">bdp-metric</td>
        <td>app.host</td>
        <td>部署该应用程序的远程服务器</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster</td>
    </tr>
    <tr>
    	<td>db.host</td>
        <td>bdp_metric的数据库服务器</td>
        <td>node1.cluster</td>
        <td>loadbalancer1.cluster</td>
    </tr>
    <tr>
    	<td rowspan="3">bdp-import</td>
        <td>app.host</td>
        <td>部署该应用程序的远程服务器</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster</td>
    </tr>
    <tr>
    	<td>bdp.metric.db.host</td>
        <td>bdp_metric的数据库服务器</td>
        <td>node1.cluster</td>
        <td>loadbalancer1.cluster</td>
    </tr>
    <tr>
    	<td>bdp.master.db.host</td>
        <td>bdp_master的数据库服务器</td>
        <td>node1.cluster</td>
        <td>loadbalancer1.cluster</td>
    </tr>
    <tr>
    	<td rowspan="3">bdp-collect</td>
        <td>app.host</td>
        <td>部署该应用程序的远程服务器</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster</td>
    </tr>
    <tr>
    	<td>bdp.metric.db.host</td>
        <td>bdp_metric的数据库服务器</td>
        <td>node1.cluster</td>
        <td>loadbalancer1.cluster</td>
    </tr>
    <tr>
    	<td>kafka.brokers</td>
        <td>kafka的broker服务器列表</td>
        <td>node1.cluster:6667</td>
        <td>worker1.cluster:6667,<br> worker2.cluster:6667,<br> worker3.cluster:6667</td>
    </tr>
    <tr>
    	<td rowspan="3">bdp-dwh</td>
        <td>app.host</td>
        <td>部署该应用程序的远程服务器</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster</td>
    </tr>
    <tr>
    	<td>bdp.metric.db.host</td>
        <td>bdp_metric的数据库服务器</td>
        <td>node1.cluster</td>
        <td>loadbalancer1.cluster</td>
    </tr>
    <tr>
    	<td>bdp.master.db.host</td>
        <td>bdp_master的数据库服务器</td>
        <td>node1.cluster</td>
        <td>loadbalancer1.cluster</td>
    </tr>
    <tr>
    	<td rowspan="2">bdp-master-server</td>
        <td>app.host</td>
        <td>部署该应用程序的远程服务器</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster</td>
    </tr>
    <tr>
    	<td>bdp.master.db.host</td>
        <td>bdp_master的数据库服务器</td>
        <td>node1.cluster</td>
        <td>loadbalancer1.cluster</td>
    </tr>
    <tr>
    	<td rowspan="1">bdp-master-client</td>
        <td>redis.host</td>
        <td>Redis服务器</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster</td>
    </tr>
    <tr>
    	<td rowspan="4">bdp-stream</td>
        <td>app.host</td>
        <td>部署该应用程序的远程服务器</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster</td>
    </tr>
    <tr>
    	<td>app.cluster.bodes</td>
        <td>Spark Driver+Executor的服务器列表</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster,<br> worker1.cluster,<br> worker2.cluster,<br> worker3.cluster</td>
    </tr>
    <tr>
    	<td>hbase.zkQuorum</td>
        <td>hbase的zkQuorum服务器列表</td>
        <td>node1.cluster:2181</td>
        <td>master1.cluster:2181,<br> master2.cluster:2181,<br> utility1.cluster:2181</td>
    </tr>
    <tr>
    	<td>kafka.brokers</td>
        <td>kafka的broker服务器列表</td>
        <td>node1.cluster:6667</td>
        <td>worker1.cluster:6667,<br> worker2.cluster:6667,<br> worker3.cluster:6667</td>
    </tr>
    <tr>
    	<td rowspan="4">bdp-workflow</td>
        <td>app.host</td>
        <td>部署该应用程序的远程服务器</td>
        <td>node1.cluster</td>
        <td>gateway1.cluster</td>
    </tr>
    <tr>
    	<td>cluster.namenode</td>
        <td>Namenode服务器</td>
        <td>node1.cluster</td>
        <td>nameservice1.cluster</td>
    </tr>
    <tr>
    	<td>cluster.resourcemanager</td>
        <td>resourcemanager服务器</td>
        <td>node1.cluster</td>
        <td>master1.cluster</td>
    </tr>
    <tr>
    	<td>cluster.oozie.host</td>
        <td>Oozie服务器</td>
        <td>node1.cluster</td>
        <td>utility1.cluster</td>
    </tr>
</table>


