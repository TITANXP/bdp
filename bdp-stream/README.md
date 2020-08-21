# bdp-collect  

## 1 部署流程  

### 构建项目  

```shell
build.bat standalone | cluster  
```



### 部署项目   

```shell
deploy.bat  
```



###  启动Hadoop  

```shell
start-dfs.sh  
start-yarn.sh     
```



### 启动saprk  

```shell
sbin/start-all.sh  
```



### 启动zookeeper  

```shell
bin/zkServer.sh start  
```



### 启动kafka  

```shell
kafka-server-start.sh -daemon ../config/server.properties  
```



### 启动HBase  

```shell
bin/start-hbase.sh   
```



### 启动redis  

```bash
redis-server "D:\Program Files\Redis-x64-5.0.9\redis.windows.conf"
```

### 启动bdp-master-server项目 ，向redis加载数据  

```shell
bdp-master-server.sh start
```

### 启动bdp-metric项目  

```shell
bdp-metric.sh start  
```



### 启动bdp-collect项目  

```shell
bdp-collect.sh start
```

  

### 启动bdp-stream项目   

```shell
bdp-stream.sh start  
```





## 2 实时计算需求分析  

### 2.1 基于时间窗口的聚合运算   

   针对Metric数据，每5s计算一次过去1min内的平均值，并与规定的阈值进行比较，如果超过了阈值就要生成相应等级的预警。   

### 2.2 有状态的流处理  

   针对Alert数据，需要实时捕获从监控端发出的Alert消息，并根据这种Alert消息的严重级别发出相应的告警，不同于Metric数据，Alert是一种有状态的数据，当一个incident发生时，会生成一个对应的Alert消息，它的status字段是OPEN，表示这一incident已发生但未被修复，直到之后的某个时刻，当这个incident被修复时，会发生一个与前面OPEN的Alert对应的消息，但status字段为CLOSED，业务要求我们能保持住这个OPEN的Alert，并持续的生成告警信息。  

   Metric和Alert的原生数据都会被写入HBase，同时他们经过聚合之后生成的服务器状态信息也会被写入HBase，服务器的状态信息用如下类来描述： 

```scala
case class ServerState (serverId: Long, timestamp: Long, srcType: String, severity: Int)
```

   serverId是目标服务器的ID，timestamp是在进行聚合运算时取得的当前系统时间，然后“round”成5s的区间值（即，它的秒数总是0、5、10.。。。等5的倍数），这样处理的原因是便于后续能把基于Metric和Alert生成的ServerState按时间进行关联，以便对一个Server的健康状况进行整体的评估。srcType标记这个ServerState从何种数据而来（cpu_usage, mem_used, alert）。  

   系统要将三类数据持久化到Hbase中：  
   - 原生的Metric数据（metric）  
   <table>
       <tr>
        	<td rowspan="2">ROWKEY(metricId)</td>
        	<td colspan="4">Column Family:f</td>
    	</tr>
    	<tr>
        	<td >name</td>
        	<td >hostname</td>
        	<td >timestamp</td>
        	<td >value</td>
    	</tr>
    	<tr>
            <td >1</td>
        	<td >cpu.usage</td>
        	<td >svr1001</td>
        	<td >1586228828</td>
        	<td >99</td>
    	</tr>
   </table>


   - 原生的Alert数据（alert）  
   <table>
       <tr>
        	<td rowspan="2">ROWKEY(alertId)</td>
        	<td colspan="4">Column Family:f</td>
    	</tr>
    	<tr>
        	<td >message</td>
        	<td >hostname</td>
        	<td >timestamp</td>
        	<td >status</td>
    	</tr>
    	<tr>
            <td >1</td>
        	<td >free space warning(mb) for host disk</td>
        	<td >svr1001</td>
        	<td >1586228828</td>
        	<td >OPEN</td>
    	</tr>
   </table>

   - 经过聚合之后生成的ServerState数据（server_state）  

   <table>
       <tr>
        	<td rowspan="2">ROWKEY(serverId+timestamp)</td>
        	<td colspan="2">Column Family:f</td>
    	</tr>
    	<tr>
        	<td >srcType</td>
        	<td >severity</td>
    	</tr>
    	<tr>
            <td>11586228828</td>
            <td >cpu_usage</td>
        	<td >2</td>
    	</tr>
   </table>

   直接用Metric和Alert原来存储于关系型数据库中的ID作为rowkey，而server_state的rowkey由serverId和timestamp两个Long型的数字拼接而来，让serverid

在前‘timestamp在后有助于规避热点问题。

## 3 流计算工程结构  
![项目流计算工程结构](https://raw.githubusercontent.com/TITANXP/pic/master/img/%E5%8E%9F%E5%9E%8B%E9%A1%B9%E7%9B%AE%E6%B5%81%E8%AE%A1%E7%AE%97%E5%B7%A5%E7%A8%8B%E7%BB%93%E6%9E%84.png)

   每一个模块都代表着一类组件，映射到工程上就是一个Package  

**Stream**：系统中的每一条流都会封装在一个类中，我们把这些类统一按“XxxStream”的形式命名，放在Stream包，Stream类里出现的大都是于Spark Streaming相关的API，在涉及实际的业务处理时，会调用相应的Service方法。这种设计反映了我们对流计算的一个基本认识，那就是流计算中的API是一个“门面（Facade）”，厚重的业务处理不应该在这些API上直接编写，而应该封装到Service，这与Web应用中的Action和Service的关系极为类似。

**Service**：与业务相关的处理逻辑会封装到Service类里，这是很传统的做法，如果在项目中深度应用了领域驱动设计，那么绝大部分业务逻辑已经自然委派到了领域对象的方法上，此时的Service会变成很薄的一层封装。有个值得一提的细节，我们把所有Service都做成了Scala中的object，也就是单态的，这样做的原因是让所有的Executor节点在本地加载全局唯一的Service实例，避免Service实例从Driver端到Executor端做无谓的序列化与反序列化操作。

**Restful API Client / Repository**：这一层主要是为Service提供数据读写服务。一般的流计算程序在运行中需要对两类数据进行读写：一类是流计算需要依赖的主数据，另一类是流计算的处理结果。对于这些数据，我们可以利用Repository直接从数据库进行读写，如果平台有多个组件都需要使用主数据，则建议建立统一的主数据和配置信息读写组件，如果是这样，则专属于流处理的Repository将不复存在。

**Model**：领域模型涉及的实体和值对象都会放在这个包里，业务处理和分析的逻辑会按照面向对象的设计理念分散到领域对象的业务方法上，如果建立了统一的主数据和配置信息读写组件，则Model也不复存在。

**DTO**：流计算中的DTO并不是为传输领域对象而设计的，它是外部采集的原生数据经过结构化处理之后在流上的数据对象。  



## 4 实现  

### 从kafka读取数据  

```scala
// 从kafka读取数据
sparkSession
  .readStream
  .format("kafka")
  .option("kafka.bootstrap.servers", KAFKA_BROKER_LIST)
  .option("subscribe", s"$TOPIC_CPU_USAGE, $TOPIC_MEM_USED, $TOPIC_ALERT") //订阅的topic
  .option("startingOffsets", "latest") // latest:从最晚进入队列中的消息开始; earliest: 从队列中最早的消息开始
  .load()
  .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
  .createTempView("trunk")
```

   从kafka中取出的数据有固定的格式，如下表所示：

| Column        | Type   |
| ------------- | ------ |
| key           | binary |
| value         | binary |
| topic         | string |
| partition     | int    |
| offset        | long   |
| timestamp     | long   |
| timestampType | int    |

   其中最常用到的是key和value，使用  .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")把key和value提取出来并转化为string类型。

   由于在后续的操作中会从一个流的某个中间状态开始岔开两条分支去做不同的处理，一个分支去持久化原生数据，另一个分支去做聚合运算，如果在DStream上，可以使用cache方法来实现这一需求，但是在Spark2.3.0的Structured Streaming API上，实现同一效果的唯一可行方法是使用createTempView来创建一个临时视图，然后在支流上操作这个视图。  

### 将数据写入HBase  

   当kafka数据引入Spark Streaming后，程序会根据消息的key过滤出cpu_usage、mem_used、alert三类数据，并形成三条支流，其中cpu_usage、mem_used的处理逻辑是一样的，因此使用了同一个Stream类MetricStream处理，alert数据需要维护状态，使用AlertStream处理。  

#### 创建HBase客户端  

   mutator是HBase客户端批量写入数据的接口类，是的，我们使用的是异步批量写入，而非同步逐条插入，因为Spark Streaming本身就是以micro-batch模式工作的，同步逐条插入的意义并不大，而因异步批量写入产生的延迟很小，可以忽略不计，反而可以获得更好的性能。   

   mutator实例是由一个HBase的Connection实例创建的，由于创建并维持一个Connection会消耗较多的资源，通常一个应用只会维持一个Connection。在Spark这种分布式计算环境下，我们通过HbaseClient这个object来**创建并维持一个Connection实例**，这样在Spark的每个Executor上只会有一个Connection实例

```scala
package org.bdp.stream.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{BufferedMutator, BufferedMutatorParams, Connection, ConnectionFactory, RetriesExhaustedWithDetailsException}

import scala.collection.JavaConverters.asScalaBufferConverter
import org.bdp.stream.Constants._

// 在Spark的每个Executor上 创建并维持一个Connection实例， 每个partition对应一个mutator
object HBaseClient extends LazyLogging {

  private val connection = createConnection()

  private val mutatorParams = createMutatorParams()

  // 为每个表创建mutatorParams
  private def createMutatorParams(): Map[String, BufferedMutatorParams] = {
    Map[String, BufferedMutatorParams] (
      METRIC_TABLE_NAME -> createMutatorParams(METRIC_TABLE_NAME),
      ALERT_TABLE_NAME -> createMutatorParams(ALERT_TABLE_NAME),
      SERVER_STATE_TABLE_NAME -> createMutatorParams(SERVER_STATE_TABLE_NAME)
    )
  }

  // 根据表名创建mutatorParams
  private def createMutatorParams(tableName: String): BufferedMutatorParams = {
    val listener = new BufferedMutator.ExceptionListener {
      override def onException(e: RetriesExhaustedWithDetailsException, bufferedMutator: BufferedMutator): Unit = {
        for (cause: Throwable <- e.getCauses.asScala) {
          for (cause: Throwable <- e.getCauses.asScala) {
            logger.error(s"HBase put operation failed! the error message is: ${cause.getMessage}")
            cause.printStackTrace()
          }
          throw e
        }
      }
    }
    new BufferedMutatorParams(TableName.valueOf(tableName)).listener(listener)
  }

  // 创建connection
  private def createConnection(): Connection = {
    try {
      val conf = HBaseConfiguration.create()
      conf.addResource("hbase-site.xml")
      ConnectionFactory.createConnection(conf)
    } catch {
      case e: Throwable =>
        logger.error(s"HBase create connection operation failed! the error message is: ${e.getMessage}")
        throw e
    }
  }

  // 根据表名创建并返回对应的mutator
  def mutator(tableName: String): BufferedMutator = {
    try {
      connection.getBufferedMutator(mutatorParams(tableName))
    } catch {
      case e: Exception =>
        logger.error(s"HBase get mutator operation failed! the error message is: ${e.getMessage}")
        throw e
    }
  }
}
```

#### 将Spark Streaming中的数据持久化到HBase   

较为普遍的做法有以下几种：  

- 如果程序是基于RDD编程并且是批处理的，则可以使用RDD的saveAsHadoopFile或saveAsNewAPIHadoopFile方法将RDD直接保存为HFile格式，由于HFile是HBase的物理存储格式，所以这种数据写入方式的性能是最高的。  

- 如果是流计算程序，可以使用HBase的Client写入数据，这又会根据程序依赖的是RDD 还是 DataFrame/DataSet分为两种情况：  
   - RDD：应该在RDD的foreachPartition方法中获取HBase连接并批量写入，然后释放资源；  
   
```scala
     rdd.foreachRDD {
       rdd =>
           rdd.foreachPartition {
               records => {
                   //获取HBase Connection，创建Table和BufferedMutator
                   //将records转换成puts，写入HBase，关闭Table和BufferedMutator
                   ...
               }
           }
     }
```

   - DataFrame/DataSet：应该在自定义的ForeachWriter中调用Hbase Client完成数据的写入，此项目即时采用的这种方案。   

- 使用第三方的connector。目前可用的第三方connector有由cloudera支持的SparkOnHBase，和由Hotronworks支持的Apache Spark-Apache HBase Connector（shc）。   

### Metric的处理  

   当main方法过滤出cpu\_usage、mem\_used的Dataset后，会把它传给MetricStream的restream方法，这个方法打包了两个基本操作：  

```scala
def restream(metric: String)(implicit sparkSession: SparkSession): Unit = {
  // 将原生的Metric数据持久化到HBase
  persist(metric)
  // 对Metric数据进行评估并生成ServerState
  evaluate(metric)
}
```

####  MetricStream的persist方法

```scala
def persist(metric: String)(implicit sparkSession: SparkSession): Unit = {
  sparkSession.sparkContext.setLocalProperty("spark.scheduler.pool", s"pool_evaluate_$metric")// 为这些短作业引入独立的scheduler pool
  sparkSession
    .sql(s"SELECT * FROM $metric").as[Metric]
    .writeStream
    .outputMode("update")
    .foreach(MetricWriter())
    .queryName(s"persist_$metric")
    .start()
}
```

    outputMode("update")声明使用update模式来写数据，Structured Streaming支持三种输出模式：

- Append模式：每次添加新的行，只适用于那些一旦产生结果就永远不会修改的情形，所以它能保证每一行数据只被写入一次。由于Append模式要求数据已经完全就绪且不会再被修改，所以Spark总是要等到一个非常确定的时刻才会将数据输出，也就是Append模式下数据输出在时效性上会差一些。

- Complete模式：整张结果表在每次触发时都会全量输出！这显然是要支撑那些针对数据全集进行的计算，如聚合。

- Update模式：某种意义上是和Append针锋相对的一种模式，它输出上次tigger之后发生了”更新“的数据，”更新“包含新生成数据和发生了变化的数据，因此同一条记录在update模式下是有可能被多次输出的，每次都是当前更新后的最新状态。Update模式是时效性最好的，数据输出的延迟是最小的。对于像HBase这样的数据库来说，其更新数据的方式是将新写入的数据作为一个新版本追加到对应的cell上，因此这种模式可以很好的工作在HBase上。

foreach(MetricWriter())是注册自定义Sink的地方，实际的持久化操作就发生在MetricWriter中。在Structured Streaming中，自定义的Sink是通过实现一个抽象类ForeachWriter完成的。

```scala
/**
 * 实现自定义的sink
 * 需要继承ForeachWriter，并实现三个方法
 */

// 持久化Metric到HBase
case class MetricWriter() extends ForeachWriter[Metric] with LazyLogging {
  // HBase客户端批量写入数据的接口类
  private var mutator: BufferedMutator = _
  
  /**
   * 获取相应的资源，建立于sink的连接
   *  open方法中有一个参数partitionId，这说明Structured Streaming的设计在暗示开发者是否要以partition为单位创建Connection
   *  这与在RDD中推荐在foreachPartition中创建数据库连接的思想是一致的
   *  具体到HBase，我们选择的做法是，每个Executor维持一个Connection，每个partition对应一个mutator，这样的映射关系是比较合理的
   */
  override def open(partitionId: Long, version: Long): Boolean = {
    try {
      mutator = HBaseClient.mutator(METRIC_TABLE_NAME)
      logger.debug(s"Opening HBase connection & mutator for table [ $METRIC_TABLE_NAME (partitionId=$partitionId) ] is done!")
      true
    } catch {
      case e: Throwable =>
        logger.error(s"Opening HBase mutator for table [ $METRIC_TABLE_NAME (partitionId=$partitionId) ] is failed! the error message is: ${e.getMessage}")
        throw e
        false
    }
  }

  /*
   *  完成数据的写入
   *    通过MetricAssembler将Metric数据转换为HBase接受的put格式数据
   *    然后通过mutator.mutate(put)将put实例加入到Mutator中
   */
  override def process(metric: Metric): Unit = {
    val put = MetricAssembler.assemble(metric)
    mutator.mutate(put)
  }

  // 释放资源
  override def close(errorOrNull: Throwable): Unit = {
    try {
      mutator.close()
      logger.debug(s"Closing HBase connection & mutator for table [ $METRIC_TABLE_NAME ] is done!")
    } catch {
      case e: Throwable =>
        logger.error(s"Closing HBase mutator for table [ $METRIC_TABLE_NAME ] is failed! the error message is: ${e.getMessage}")
        throw e
    }
  }

}
```

#### MetricStream的evaluate方法 （基于时间窗口的聚合运算）

```scala
def evaluate(metric: String)(implicit sparkSession: SparkSession): Unit = {
  import sparkSession.implicits._
  sparkSession.sparkContext.setLocalProperty("spark.scheduler.pool", s"pool_persist_$metric")
  sparkSession
    .sql(s"SELECT * FROM $metric").as[Metric]
    .withWatermark("timestamp", METRIC_WATERMARK) //允许数据的最大延迟，如果系统当前时间和数据的时间戳相差超过阈值，则舍弃这条数据
    .dropDuplicates("id", "timestamp")  // 根据id和timestamp进行去重
    .groupBy($"hostname", window($"timestamp", WINDOW, SLIDE)) // 每5s截取过去60s内的数据，并按服务器进行分组
    .agg(avg($"value") as "avg") // 计算Metric的value字段的平均值，并将结果作为新的列avg
    // 经过groupBy和avg处理后，Dataset的数据格式只剩下hostnaem、avg、window三列
    // 其中window是一个复合结构，包含start和end两个timestamp类型的字段
    // 我们将window.end作为ServerState的时间戳
    .select($"hostname", (unix_timestamp($"window.end") cast "bigint") as "timestamp", $"avg") // 用select取出hostname，avg，window.end封装成一个三元组
    .as[(String, Long, Double)]
    // 去和阈值进行对比,并生成ServerState对象
    .map(MetricService.evaluate(metric, _))
    .writeStream
    .outputMode("update")
    .foreach(ServerStateWriter())
    .queryName(s"evalute_$metric")
    .start
}
```

    dropDuplicates("id", "timestamp")是进行去重，在bdp-collect项目中，为了不遗漏延迟就绪的数据，采用了多波次采集的策略，所以会采集到重复的数据。

   dropDuplicates方法允许设定一到多个列作为去重时的比对列。理论上，只用ID一个字段是能够实现去重的，但是这样性能较差，因为这意味着Spark需要将每一条新记录与全体数据做对比，我们应该时刻清醒的意识到流是没有边界的，因此，”时间“在流处理上是极为重要的一个考量尺度，落实到去重问题上，如果程序使用了Watermark，Spark会强烈推荐我们将事件事件列作为去重时比对列之一，**因为这样可以将数据的对比范围控制在Watermark限定的时间范围内，而不是全体数据，这会大大减少不必要的去重计算。**

MetricService的evaluate方法

```scala
  // 根据metric数据返回ServerState对象
  def evaluate(metric: String, row: (String, Long, Double)): ServerState = {
    val (hostname, timestamp, avg) = row
    // 通过bdp-master-client 取出service
    val service = ServerService.getServerBy(hostname)
    // 根据Metric的名称和hostname查询出这台服务器对应Metric的阈值
    val serverId = service.id
    val amberThreshold = service.metricThresholds(metric.replace("_", ".")).amberThreshold
    val redThreshold = service.metricThresholds(metric.replace("_", ".")).redThreshold
    // 将平均值和阈值进行比较，以确定ServerState的严重等级
    val severity = avg match {
      case avg if avg < amberThreshold => GREEN
      case avg if avg >= redThreshold => RED
      case _ => AMBER
    }
    // 创建ServerState对象并返回
    ServerState(serverId, timestamp, metric, severity.id)
  }
```

### Alert的处理  

#### 自定义状态的流  

   很多情况下，数据本身是有状态的，或者说在描述某种状态。例如，有一个session id串联起来的用户某次登录后发起的一系列请求；再如，此项目中Alert数据有OPEN和CLOSE两种状态。  

   由于数据是有状态的，就要求流处理组件也能相应的维持这种状态，并可以基于状态进行某些分析与处理。前面使用过的watermark其实就是一种状态，Spark需要在流上维持这个状态以便判断数据是否过旧而要舍弃，但是Watermark是由Spark自动管理的，不需要开发人员干预，更多情况下我们需要自己维护某种状态来满足业务需求，为此Structured Streaming提供了对自定义状态的支持，具体说就是两个方法：mapGroupsWithState和flatMapGroupsWithState。它们用于在分组数据上建立并维护一个状态，区别在于前者接收的状态函数返回且只返回一个元素，而后者可以返回0到多个元素。  

   以mapGroupsWithState为例，在它多个重载版本里，下面这个最常用：  

   

```scala
mapGroupsWithState[S: Encoder, U: Encoder] (timeoutConf: GroupStateTimeout)(func: (K, Iterator[V], GroupState[S] => U)) : Dataset[U]  
```

   该方法有两个参数，一个用来指定GroupState的超时策略，另一个用来维持和更新GroupState的函数。以下是对两个参数及GroupState的详细说明。  

   - **ttimeoutConf: GroupStateTimeout**：设定GroupState的超时策略。在很多场景下，当GroupState长时间接收不到新数据时会被认定为超时，这时需要做出一些相应的处理。timeoutConf就是指定基于哪一种时间来判断超时，它有三种取值：无超时（NoTimeout）、基于处理时间的超时（ProcessingTimeTimeout）、基于事件时间的超时（EventTimeTimeout）。如果使用EventTimeTimeout，必须要设定Watermark（实际上，在Spark Structured Streaming中，只要涉及事件时间都必须设定Watermark，因为在设定Watermark时会指定事件时间列，这也是Spark Structured Streaming的API中唯一一处设定”事件时间列“的地方）。超时的阈值是由GroupState#setTimeoutDuration(processing time)或GroupState#setTimeoutTimeTimestamp(event time)两个方法中的任意一个设定的。在流的运行过程中，针对每一个组，只要有一条输入数据，超时时间就会更新，如果在规定的时间内没有接收到任何数据，则被认定为超时，此时GroupState.hasTimedOut的值时true。但是超时发生后负责应对的代码并不会在超时那一刻立即执行，它的执行时间是发生超时后新一批数据到达时，因为只有新数据到达才会驱动watermark的更新和func函数的执行。  
   - **func: (K, Iterator[V], GroupState[S] => U**)：这个函数定义了分组内的数据如何生成或转换成状态信息，它是实现自定义状态业务逻辑的地方。第一个参数是当前分组对应的key，第二个是一个可以迭代当前分组数据的迭代器，用于在方法中迭代数据，最后一个参数是“自定义状态”，它可以是任意类型（类型参数S），只要能进行序列化即可，然后它会被包裹在一个名为GroupState的包裹类中。    
   - **GroupState**：是实际状态信息的一个包裹类，开发人员自定义的状态对象需要放在这个包裹类中，这个类会提供一系列的方法来管控状态对象的生命周期，有如下方法：  
     
         - exists：告知状态对象是否被设置；
         - get：返回状态对象，如果对象不存在，会抛出NoSuchElementException异常，相较而言，getOption方法更加安全和优雅；
         - update(newState: S)：更新现有状态；
         - remove：将现有状态移除；
         - hasTimedOut：判断是否以超时，如果超时则返回true；
         - setTimeoutDuration(...)：设置超时阈值，此方法只适用于processing-time，即只有当timeoutConf被设置为GroupStateTimeout.ProcessingTimeTimeout时，才会使用该方法配置超时的阈值。   
         - setTimeoutTimestamp(...)：设置超时时刻的时间戳，此方法只适用于event-time，即只有当timeoutConf设置为GroupStateTimeout.EventTimeTimeout时，才会使用该方法配置超时时刻的时间戳。  
        
           对于GroupState定义的“超时”和通常的理解不同，一般对超时的理解是，如果一个维持中的状态（如Session）长时间没有收到更新的数据，人们会倾向于认为这个状态已经终结了，应当彻底移除。然而，Spark的“超时”有所不同，Spark认为流既然是没有边界的，那么某个分组（相当于以某个key产生的支流）上的状态也将是“不眠不休”的，即永远不会消亡。所以，当我们在GroupState上检测到超时时，如果使用remove操作移除状态对象，并不意味着当前分组对应的GroupState实例被移除，既然Saprk已经认定数据流是“无止境”的，那么在未来某个时刻可能会有新的数据流入并将它重新激活，所以GroupState上定义的“超时”，并非代表着一种由于流的终结而触发的”绝响“（超时后，这条支流及其状态将不复存在），而只是永不消亡的GroupState实例上的某个中间状态。所以在对数据进行分组时，我们必须要特别注意，选定的分组必须要确保”永远有数据“，否则会产生无数”僵而不死“的GroupState实例。

   回到项目中，Alert是一种有状态的数据，它的处理流程和Metric流基本一致，当main方法过滤出Alert的Dataset后，会把它传给AlertStream#restream方法：

```scala
def restream(implicit sparkSession: SparkSession): Unit = {
  // 将原生的Alert数据持久化到HBase
  persist
  // 对Alert进行评估，评估时会生成并维护Alert状态，评估的结果就是ServerState
  evaluate
}
```

persist的操作与Metric的一致

evaluate

```scala
def evaluate(implicit sparkSession: SparkSession): Unit = {
  import sparkSession.implicits._
  sparkSession.sparkContext.setLocalProperty("spark.scheduler.pool", s"pool_evaluate_alert")
  sparkSession
    .sql(s"SELECT * FROM alert").as[Alert]
    .withWatermark("timestamp", ALERT_WATERMARK)
    //对Alert按服务器进行分组，为面向服务器的状态评估做准备
    .groupByKey(alert => AlertService.getServerId(alert.hostname))
    .mapGroupsWithState(GroupStateTimeout.NoTimeout)(AlertService.updateAlertGroupState)
    .writeStream
    .outputMode("update")
    .foreach(ServerStateWriter())
    .queryName(s"evaluate_alert")
    .start
}
```

   Alert的Watermark达到了24h（86400s），这是因为Alert的timestmap标识的是incident发生的时间，对于一个发生在凌晨1点的incident，即使它在下午5点被修复，对应的CLOSED的Alert消息的timestamp依旧是凌晨1点。为了确保不丢失数据，所以要设置一个足够长的Watermark，假设正常情况下incident都会在24h内修复，我们就可以将Watermark设置为24h。

mapGroupsWithState(GroupStateTimeout.NoTimeout)(AlertService.updateAlertGroupState)：第一个参数配置超时策略，设定的是NoTImeout，因为我们的分组是基于服务器的，并没有Session，也就没有超时这一说法了，但是针对同一台服务器的同一个incident，是有可能会发生Alert消息超时问题的，即在收到一个OPEN消息后，在很长的时间内没有受到对应的CLOSE消息，这一问题在AlertService#updateAlertGroupState方法中解决。

```scala
  /**
   * 更新每个Alert分组的状态信息
   * @param serverId 分组对应的key，因为在AlertStream中是按serverId进行分组的，所以这个就是serverId
   * @param alerts 分组集合的迭代器
   * @param state 分组对应的GroupState实例
   * @return ServerState服务器状态信息
   */
  def updateAlertGroupState(serverId: Long, alerts: Iterator[Alert], state: GroupState[AlertRegistry]): ServerState = {
    // 1. 取出当前分组对应的AlertRegistry
    val alertRegistry = state.getOption.getOrElse(AlertRegistry())
    val now = System.currentTimeMillis()/1000
    // 2. 先清理掉注册表中已经过期，需要被淘汰的数据
    alertRegistry.cleanUp(now: Long)
    // 3. 然后根据新到达的alerts数据更新注册表状态
    alertRegistry.updateWith(alerts)
    state.update(alertRegistry)
    // 4. 汇总注册表信息，生成ServerState
    val severity = alertRegistry.evaluate()
    //  为了在时间戳上和Metric生成的ServerState对齐，所以也将Alert生成的ServerState的时间戳“round”成5s的倍数，以便后续基于时间的关联查询
    val timestamp = (now + 5) / 5 * 5000
    ServerState(serverId, timestamp, ALERT, severity)
  }
```

#### 自定义状态的设计

   Structured Streaming的GroupState是一种开放式的状态自定义机制，因为状态的数据结构与业务逻辑紧密相关，所以不可能通过一种通用的数据结构进行描述，所以当开发人员在使用GroupState时，要好好设计自定义状态的数据结构，使用面向对象的思想合理抽象状态所代表的业务概念，同时要善于通过细粒度的设计将业务逻辑分摊到合适的对象中去，避免在func: (K, Iterator[V], GroupState[S]) => U中堆积大量代码，使程序变得“丑陋”且不易维护。

   维护Alert状态的逻辑是比较复杂的，但是updateAlertGroupState函数保持了轻量和简洁，原因是状态相关的操作大都应该封装到状态对象中，而不应该在状态维护函数中实现。如果状态维护的逻辑非常复杂，在设计状态类时可以考虑设计更细粒度的对象去分摊业务逻辑，这才是遵循良好的面向对象设计思想的最佳实践。

```scala
package org.bdp.stream.model

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable
import org.bdp.master.client.service.AlertIndexService
import scala.math._
import org.bdp.stream.Constants._

/**
 * 自定义状态
 */
case class AlertRegistry () extends LazyLogging {

  // 这个Map存储着各种已知类型的Alert在所有发生过incident的时间点上收到的Alert消息的状态，是“已打开尚未关闭”还是“已打开且已关闭”。
  // 它的key是一个二元组，第一个元素是Alert类型的ID; 第二个元素是UNIX格式的时间戳; 再加上分组对应的key（serverId），
  //  这三个“坐标”可以精确定位一个incident，即哪一台服务器在什么时间报了什么告警。
  // Map的value也是一个二元组，默认初始值都是false，第一个元素标记是否收到了OPEN状态的Alert，第二个标记是否受到CLOSE状态的Alert。
  private var registry = mutable.Map[(Long, Long), (Boolean, Boolean)]()

  // 根据传入的Alert更新注册表
  def updateWith(alerts: Iterator[Alert]): Unit = {
    alerts.foreach {
      alert =>
        // 根据Alert消息中的内容找到这个Alert的类型ID
        val id = AlertIndexService.getAlertIndexBy(alert.message).id
        val timestamp = alert.timestamp.getTime
        val status = alert.status
        val key = (id, timestamp)
        // 如果没有对应的key说明这是第一次在这个服务器的这个时间点上发生这类incident
        val oldValue = registry.getOrElse(key, (false, false))
        val newValue = status match {
          case "OPEN" => (true, oldValue._2)
          case "CLOSE" => (oldValue._1, true)
        }
        // 更新map中对应key的值
        registry.update(key, newValue)
    }
  }

    /**
     *  对当前服务器的健康状况进行评估
     *    逐一迭代Map中的每一个元素，
     *    如果存在以OPEN但未CLOSE的alert，说明当前服务器上的一个incident尚未被修复，此时要获取这个Alert的severity（严重程度）留待后用，
     *    然后继续迭代剩余元素
     *    如果出现第二个未CLOSE的Alert，则将它的severity和前一个进行比较，取severity最大的
     *    迭代结束后，就可以得到当前服务器的最高告警等级，也就是这个服务器的当前健康状态
     */
    def evaluate(): Int = {
      registry.foldLeft(0){
        (severity, entry) =>
          val ((id, _), (open, closed)) = entry
          if(open && !closed){
            max(severity, AlertIndexService.getAlertIndexBy(id).severity)
          } else {
            severity
          }
      }
    }

    // 清理过期的Alert
    def cleanUp(now: Long): Unit = {
      registry.filter{
        case((id, timestamp), _) =>
          logger.debug(s"(CURRENT_TIME-ALERT_TIME-ALERT_TIME_TO_LIVE=" +
            s"($now-$timestamp)-$ALERT_TIME_TO_LIVE = ${(now-timestamp)-ALERT_TIME_TO_LIVE}")
          if(now - timestamp < ALERT_TIME_TO_LIVE) {
            logger.debug(s"($id, $timestamp) is kept in session because it is LIVE.")
            true
          } else {
            logger.debug(s"($id, $timestamp) is removed from session because it is EXPIRED.")
            false
          }
      }
    }
}

```

AlertRegistry的核心实在维护一个Map，它的数据结构如下图

| key                      | value                          |
| ------------------------ | ------------------------------ |
| (alertTypeId, timestamp) | (hasOpenAlert, hasClosedAlert) |
| (1, 1535760000)          | (true, true)                   |
| (2, 1535779300)          | (true, false)                  |
| ...                      | ...                            |

   在updateAlertGroupState方法中，每当有新的Alert消息到达时，函数会把它们传递给AlertRegistry#updateWith方法来更新注册表；当注册表更新后，就要使用evaluate方法对当前服务器的健康状况进行评估；还有一个遗留问题就是数据清理，Structured Streaming 分组对应的状态是不会消亡的，这意味着，AlertRegistry的注册表会随着时间的推移不断膨胀，因此有必要适时的清理已经过期不会再使用的元素。前面在为Alert流设置Watermark时提到过，正常情况下incident都会在24h内修复，相应的，注册表维持数据的最大有效期也是24h，所以提供一个方法cleanUp来清理超过24h的元素，这个方法会在updateAlertGroupState中被调用。


## 5 Struct Streaming性能相关的参数  

### spark.scheduler.mode  

   默认情况下，Spark对于Job的排期策略是FIFO，即spark.scheduler.mode的默认值是FIFO,这一策略的含义是，，先提交的作业会被先执行。但这也不是绝对的，如果当前的Job并没有占用到集群的全部资源（还有空闲的Executors或CPU Core），则Spark会让后续的Job立即执行，这显然是明智的。当然，反方向上的极端情况是，当一个Job很“重”、需要耗用大量资源长时间运行时，后续的Job都会被阻塞。  

   从Spark 0.8开始引入了一种新的作业排期策略FAIR，顾名思义就是让所有的Job能获得相对均等的机会来执行。具体的做法是，将所有Job的task按照一种“round robin（轮询调度）”的方式执行。注意，这里的执行粒度“下放”到了task，并且是跨job的，这样就变成了在job之下按更细的粒度的单位task进行轮询式的执行，宏观上达到了job并行的效果。  

   应该说spark.scheduler.mode是一个面向job级别的配置项，但又不是这么简单，当它是FIFO时，我们可以认为它的作用粒度是job，当它是FAIR时，为了真正能使各个作业得到相等的执行机会，实际上的作业调度已经细化到了task级别，在Spark的源码org.apache.spark.scheduler.Pool#getSortedTaskSetQueue中可以看到：

```scala
override def getSortedTaskSetQueue: ArrayBuffer[TaskSetManager] = {
  val sortedTaskSetQueue = new ArrayBuffer[TaskSetManager]
  val sortedSchedulableQueue =
    schedulableQueue.asScala.toSeq.sortWith(taskSetSchedulingAlgorithm.comparator)
  for (schedulable <- sortedSchedulableQueue) {
    sortedTaskSetQueue ++= schedulable.getSortedTaskSetQueue
  }
  sortedTaskSetQueue
}
```

   在同一个pool中，所有作业的task都会依据配置的spark.scheduler.mode来对task统一进行排序，然后依次提交给Spark Core执行，所以说实际控制的粒度是task，但这种并行并不是在两个作业中频繁地交替执行task（这样做的代价显然是巨大的），从并行作业的Event Timeline上看，实际的运行状况是一个较长的作业在执行期间会允许一到两个短作业“插队”，直到他们执行完毕，在切换回长作业继续执行。

### spark.streaming.concurrentJobs

   顾名思义，这是一个配置作业并行度的参数，这一配置要配合FAIR一起工作，另外，只有当前集群有足够的资源支撑更多的并发作业时，加大concurrentJobs的值才会有明显的效果。注意：即时concurrentJobs=1，如果集群有空闲的计算资源，Spark也会激活新的作业去并行执行。

### scheduler线程池：spark.scheduler.pool

   FAIR给每一个作业提供了均等的执行机会，但是未必能解决这样一类问题：假设在一个Spark Streaming里有一个很重的作业，它有两三百个task，还有几个很小的作业，可能只有几个task，按FAIR轮询调度，每个作业都有均等的机会执行各自的task，这样形成的结果是，在一个相对固定的时间周期内，长作业于众多短作业执行完毕的数量是一样的，比例都是1：1：1……如果我们的需求是让这些短作业执行的频率加快，那么就势必要分配给这些短作业更多的资源，那么此时就需要为这些短作业引入独立的scheduler pool，并配置相应的资源占比，具体的做法是在代码中加入：

```scala
sc.setLocalProperty("spark.scheduler.pool", "mypool")
```

这里的mypool是通过xml配置的一个自定义的scheduler pool。这样，在当前线程提交的作业都会使用这个指定的pool运行作业。在没有引入上述代码时，所有的作业实际上是在共用一个root的pool，整个集群的计算资源是分配给这个root pool的，如果我们设置了spark.scheduler.mode=FAIR，提交的作业中又有几个是很重的，那么在这种模式下短作业的执行时间会被拉长，因为长短作业获得资源的权重是均等的。解决方法是，既然pool是对计算资源的划分，那么我们就可以为不同的作业引入多个独立的pool，然后给这些pool分配相应的权重，让他们来按比例分配整体的计算资源，最后在pool的内部再使用FAIR让其内部的Job均等的执行。

bdp-stream/src/main/resources/conf/fairscheduler.xml

```xml
<?xml version="1.0"?>
<allocations>
    <pool name="pool_persist_cpu_usage">
        <schedulingMode>FAIR</schedulingMode>
        <weight>1</weight>
        <minShare>0</minShare>
    </pool>
    <pool name="pool_persist_mem_used">
        <schedulingMode>FAIR</schedulingMode>
        <weight>1</weight>
        <minShare>0</minShare>
    </pool>
    <pool name="pool_evaluate_cpu_usage">
        <schedulingMode>FAIR</schedulingMode>
        <weight>1</weight>
        <minShare>0</minShare>
    </pool>
    <pool name="pool_evaluate_mem_used">
        <schedulingMode>FAIR</schedulingMode>
        <weight>1</weight>
        <minShare>0</minShare>
    </pool>
    <pool name="pool_persist_alert">
        <schedulingMode>FAIR</schedulingMode>
        <weight>1</weight>
        <minShare>0</minShare>
    </pool>
    <pool name="pool_evaluate_alert">
        <schedulingMode>FAIR</schedulingMode>
        <weight>1</weight>
        <minShare>0</minShare>
    </pool>
</allocations>
```

## 6 遇到的问题  

- #### 运行build.bat时  

```
[ERROR] Failed to execute goal on project bdp-stream: Could not resolve dependencies for project org.bdp:bdp-stream:jar:1.0: Failed to collect dependencies at org.bdp:bdp-master-client:jar:1.0: Failed to read artifact descriptor for org.bdp:bdp-master-client:jar:1.0: Could not find artifact org.bdp:bdp-parent:pom:1.0 in artima (http://repo.artima.com/releases) -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/DependencyResolutionException
```

**原因**：根据错误提示，找不到org.bdp:bdp-parent:pom:1.0，到本地maven仓库目录下查看，发现bdp-parent包下没有bdp-parent的pom文件

**解决方法**：在bdp-parent项目的根目录下执行

```bash
mvn clean install
```

然后再用build.bat构建项目时就会发现已经没有错误了  

- #### Spark版本问题  

因为项目中使用到了Structured Streaming ，所有Spark的版本定为2.3.0。  

在192.169.170.129上直接解压安装的cdh组件中，Spark版本是 spark-1.6.1-bin-2.5.0-cdh5.3.6，
在https://archive.cloudera.com/cdh5/cdh/5/ 中也没有spark2.3.0版本。  

所有使用安装了cm6的192.168.170.171，来安装spark，但是由于cm占用内存太高，所以决定在192.169.170.129安装apache的Spark2.3.0。

下载安装包

```shell
wget http://archive.apache.org/dist/spark/spark-2.3.0/spark-2.3.0-bin-without-hadoop.tgz
```

解压

```shell
tar -zxvf  spark-2.3.0-bin-without-hadoop.tgz -C /usr/local
```

将原来spark中的配置文件复制过去

```shell
cp conf/hive-site.xml ../spark-2.3.0-bin-without-hadoop/conf
cp conf/slaves ../spark-2.3.0-bin-without-hadoop/conf
cp conf/spark-env.sh ../spark-2.3.0-bin-without-hadoop/conf
```

启动失败

```shell
[root@hadoop-master spark-2.3.0-bin-without-hadoop]# sbin/start-all.sh 
starting org.apache.spark.deploy.master.Master, logging to /usr/local/spark-2.3.0-bin-without-hadoop/logs/spark-root-org.apache.spark.deploy.master.Master-1-hadoop-master.out
failed to launch: nice -n 0 /usr/local/spark-2.3.0-bin-without-hadoop/bin/spark-class org.apache.spark.deploy.master.Master --host hadoop-master --port 7077 --webui-port 8180
  Spark Command: /usr/local/jdk1.8.0_191/bin/java -cp /usr/local/spark-2.3.0-bin-without-hadoop/conf/:/usr/local/spark-2.3.0-bin-without-hadoop/jars/*:/usr/local/hadoop-2.5.0-cdh5.3.6/etc/hadoop/ -Xmx1g org.apache.spark.deploy.master.Master --host hadoop-master --port 7077 --webui-port 8180
```

解决方法：

先执行

```shell
hadoop  classpath

/usr/local/hadoop-2.5.0-cdh5.3.6/etc/hadoop:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/common/lib/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/common/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/hdfs:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/hdfs/lib/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/hdfs/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/yarn/lib/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/yarn/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/mapreduce/lib/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/mapreduce/*:/usr/local/hadoop-2.5.0-cdh5.3.6/contrib/capacity-scheduler/*.jar
```

将结果复制

然后修改spark-env.sh

```shell
vim conf/spark-env.sh
```

在里面加入

```shell
SPARK_DIST_CLASSPATH=/usr/local/hadoop-2.5.0-cdh5.3.6/etc/hadoop:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/common/lib/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/common/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/hdfs:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/hdfs/lib/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/hdfs/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/yarn/lib/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/yarn/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/mapreduce/lib/*:/usr/local/hadoop-2.5.0-cdh5.3.6/share/hadoop/mapreduce/*:/usr/local/hadoop-2.5.0-cdh5.3.6/contrib/capacity-scheduler/*.jar
```

- ### 启动项目时，报如下错误（kafka版本问题）   

org.apache.kafka.common.config.ConfigException: Missing required configuration "partition.assignment.strategy" which has no default value.  

```
 .option("kafka.partition.assignment.strategy", "range")    
```


  修改后报如下错误  

```
   java.lang.NoSuchMethodError: org.apache.kafka.clients.consumer.KafkaConsumer.subscribe(Ljava/util/Collection;)V
```
通过查找资料，发现是版本问题  
```xml
<dependency>
            <groupId>org.apache.spark</groupId>
            <artifactId>spark-sql-kafka-0-10_${scala.tools.version}</artifactId>
</dependency>  
```
pom中引用的是0.10版本，但是服务器上的kafka是0.8版本的  

查看maven仓库，发现没有spark-sql-kafka-0-8 ，所以更换kafka版本，顺便删除上面加的kafka.partition.assignment.strategy  

安装0.10.2.0版本的kafka  

```sh
wget http://archive.apache.org/dist/kafka/0.10.2.0/kafka_2.11-0.10.2.0.tgz  
```




- ### 启动项目一段时间后，报如下错误  

```
org.apache.spark.SparkException: Job 0 cancelled because SparkContext was shut down  
```

通过 YARN 查看用户提交应用的日志
http://hadoop-master:19888/jobhistory/logs   

报错 NoSuchMethod，发现是hadoop2.5.0版本太旧，和spark2.3.0不兼容  

**解决方法**：换另一台服务器，使用CDH6.

- ### 项目运行一段时间后停止，查看yarn上的日志，发现如下错误  

```
java.io.IOException: org.apache.zookeeper.KeeperException$ConnectionLossException: KeeperErrorCode = ConnectionLoss for /hbase/meta-region-server  
```

**原因**：根据提示可以看出是连接不上zookeeper，之后发现是因为换了另一个服务器后，没有将项目resources\conf\hbase-site.xml换成新的。  

**解决方法**：从cm上下载HBase的配置，将hbase-site.xml替换即可。  

- ### 运行项目时，spark报如下错误  

```
java.util.NoSuchElementException: head of empty list  
```

**原因**：AlertStream中忘了写   

```scala
implicit val stateEncoder = org.apache.spark.sql.Encoders.kryo[AlertRegistry]
```

参考：https://blog.csdn.net/bluishglc/article/details/81208008   

- ### 项目运行一段时间后报错  
```
redis.clients.jedis.exceptions.JedisDataException: value sent to redis cannot be null
	at redis.clients.util.SafeEncoder.encode(SafeEncoder.java:28)
	at redis.clients.jedis.Connection.sendCommand(Connection.java:115)
	at redis.clients.jedis.Jedis.get(Jedis.java:152)
	at org.bdp.master.client.util.RedisClient$$anonfun$get$1.apply(RedisClient.scala:27)
	at org.bdp.master.client.util.RedisClient$$anonfun$get$1.apply(RedisClient.scala:27)
	at org.bdp.master.client.util.RedisClient$.withClient(RedisClient.scala:15)
	at org.bdp.master.client.util.RedisClient$.get(RedisClient.scala:27)
	at org.bdp.master.client.service.AlertIndexService$.getAlertIndexBy(AlertIndexService.scala:16)
	at org.bdp.stream.model.AlertRegistry$$anonfun$updateWith$1.apply(AlertRegistry.scala:25)
	at org.bdp.stream.model.AlertRegistry$$anonfun$updateWith$1.apply(AlertRegistry.scala:23)
```

**原因**：在bdp-metric的gen-alert.sql中生成alert信息的sql语句中   

insert into `bdp_metric`.`alert` (`message`, `hostname`, `status`, `timestamp`) values ('free space warning (mb) for host disk', 'svr1001', '@status@', '@timestamp@');  

'free space warning (mb) for host disk'的括号前面少了一个空格，导致在Redis中根据message取对应AlertIndex的id时，得到的结果为空，然后会用这个空值去取AlertIndex的元数据。  

**解决方法**：在(mb)前面加一个空格。