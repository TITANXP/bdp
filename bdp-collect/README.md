# bdp-collect  

## 1 部署流程  

   bdp-collect是一个基于Apache Camek的Java应用，编译打包后是一个zip包，对接上游系统bdp-metric的后台数据库，将数据提取出来并提交给下游的Kafka队列。  

   ### 创建Kafka topic  

```shell
# 分区数量partitions需要根据集群计算资源来调整,如果是7个节点的集群可以设为12
# --replication-factor备份数，7个节点时可以设为3
kafka-topics.sh \
  --zookeeper 192.168.170.129:2181 \
  --create \
  --topic cpu.usage \
  --partitions 1 \
  --replication-factor 1

kafka-topics.sh \
  --zookeeper 192.168.170.129 \
  --describe \
  --topic cpu.usage

kafka-topics.sh \
  --zookeeper 192.168.170.129 \
  --create \
  --topic mem.used \
  --partitions 1 \
  --replication-factor 1

kafka-topics.sh \
  -zookeeper 192.168.170.129 \
  --describe \
  --topic mem.used

kafka-topics.sh \
  -zookeeper 192.168.170.129 \
  --create \
  --topic alert \
  --partitions 1 \
  --replication-factor 1

kafka-topics.sh \
  --zookeeper 192.168.170.129 \
  --describe \
  --topic alert
```

   ### 启动Kafka  

```shell
bin/kafka-server-start.sh -daemon config/server.properties
```

### 启动bdp-collect   

```shell
bin/bdp-collect.sh start
```

如果想要输出日志

```shell
bin/bdp-collect.sh restart-with-logging
```

### 使用bdp-metric项目生成实时数据  

```shell
bdp-metric.sh start
```

### 查看是否正常工作  

```shell
kafka-console-consumer.sh --zookeeper localhost:2181 --from-beginning --topic alert
```



## 2 介绍  

### camel  

   数据采集的技术选型主要是看其支持的数据源种类和协议是否丰富，对接与开发是否便捷。目前业界较为主流的数据采集工具有Flume、Logstash、Kafka Connect。  

   Camel主要应用于企业应用集成领域，也被一些系统作为ESB（企业服务总线）使用，其作用是在应用系统林立的企业IT环境中扮演“万向接头”的角色，让数据和信息在各种不同的系统间平滑地交换和流转，经过多年的积累，Camel已经支持接近200种协议或数据源，并且可完全基于配置实现。  

   我们希望项目未来能够对接非常多的数据源，同时尽可能地基于配置去集成数据源并采集数据，避免编写大量的代码，Camel很好的满足了这些需求。当然，作为一个非大数据组件，对于Camel的性能和吞吐量要有清醒的认识，这个问题可以通过对数据源进行分组、使用多个Camel实例分区采集数据来解决。   

**Camel的特性**：  

- 丰富的组件，用于对接各种协议和数据源；
- 内置EIP模式，可以应对常见的消息处理需求；
- Processor机制和Bean的良好集成，可以轻松的实现自定义逻辑。   

### 实时采集   

   对于一个关系型数据库来说，如果要做到实时的数据采集，必须让数据供给以Push的模式工作，也就是说，当在数据库中生成数据时主动将数据推送给收集端，要做到这一点只能在数据库层面上实现，一般有两种方案：  

- 使用数据库触发器和存储过程 
- 使用特定数据库产品的同步机制  

   第二种显然更加优雅和简洁，一些主流的数据库都有相关的产品，如Oracle有OGG，MySQL有Canal，但并不是所有数据库都提供这种机制，并且这种方式实现起来也复杂。  
   
   如果系统对实时性的要求不是很高，达到近似实时即可，则可以使用Pull模式，以比较密集的周期在数据库上抓取数据，此项目中采用的就是这种方式。

### 基本的数据采集（cpu.usage)  

   cpu.usage被设计为数据采集环境最优、供给即时并稳定的场景。

```xml
<!-- 收集cpu.usage数据 -->
<route id="cpuCollectingJob">
    <!-- 由定时器cpuCollectingTimer触发，5s一次 -->
    <from uri="timer:cpuCollectingTimer?period={{job.cpu.period}}"/>

    <!-- 把timer生成的时间戳转换格式 -->
    <to uri="bean:dateFormatter?method=format(${header.firedTime})"/>

    <log message="Job name: cpuCollectingJob, Start Time: ${in.body}"/>

    <!-- 将dataFormatter输出的格式化后的时间写到消息的Header里，
         因为dataFormatter的返回结果会被Camel写到消息的body里，下游的SQL组件需要这个时间参数去查询数据库，但是SQL组件只会按照以下两种方式从消息里查找参数：
            - 如果消息的body是一个java.util.Map,则Camel会试图从这个Map中查找；
            - 从消息的Header里查找
          为了使SQL组件能够准确地接收从dateFormatter传来的时间戳，所以设置到了Header里  -->
    <setHeader headerName="timestamp">
        <simple>${in.body}</simple>
    </setHeader>

    <!-- 查询过去5s的数据 -->
    <to uri="sql:{{job.cpu.sql}}"/>

    <log message="SQL Returned Results: ${in.body}"/>

    <!-- SQL组件的查询结果是List<Map<String,Object>>, 需要对这个数据结构进行整理，再发送给kafka
        整理的第一项工作就是将消息数组切分成单一的消息，使用split标记即可实现 -->
    <split>
        <!-- 把消息体中单一的cpu.usage记录取出，交给marshal进行JSON解析 -->
        <simple>${in.body}</simple>

        <log message="Split Message: ${in.body}"/>
        <marshal>
            <json library="Jackson"/>
        </marshal>

        <!-- 正常情况下，可以直接将这个JSON推送给kafka，但是后面流处理环节需要对cpu.usage和mem.used进行不同的计算，
            所以需要标记是哪种metric
            利用kafka消息的key来指明一个metric的类型，同时要考虑到kafka的消息是按照key进行散列的，所以需要保证消息在kafka上均匀分布，
            一个简单的做法是生成一个随机数（小于100即可）后缀，同时在前缀和后缀之间加上分隔符|，便于后续流计算解析 -->
        <setHeader headerName="kafka.KEY">
            <simple>{{kafka.prefix.cpu.usage}}|${random(100)}</simple>
        </setHeader>

        <to uri="kafka:{{kafka.topic.cpuUsage}}"/>
    </split>

</route>
```

cpu.usage采集作业的起点是一个定时器：cpuCollectingTimer。定时器是一个Camel组件，他以指定的周期触发，把触发的时间写入一个消息容器供下游使用。这个消息容器再Camel中叫Exchange，是专门用于信息交换的数据结构，Exchange会在整个router过程中持续存在，为不同组件相互痛惜提供支持。

![](https://raw.githubusercontent.com/TITANXP/pic/master/img/Exchange.png)

- In Message：代表输入数据，代表一个请求消息，这是一个非空属性。每个Message的数据结构会细分为Header、Attachment、和Body三部分。

- Out Message：代表一条响应数据，它是一个可选属性，只有当Exchange的MEP（Message Exchange Patterns，有两种取值：InOnly和InOut）是InOut时，才会有Out Message。

- Properties：和Message的Header在结构上是一致的，但是伴随整个Exchange存在，所以通常用来存放全局变量，而Message的Header只属于某个特定的Message。  

### 应对采集作业超时（alert）  

  数据采集需要对接外部系统，收影响的因素较多，需要应对一些由外部系统引起的复杂问题，cpu.usage采集的预设条件是数据库性能良好，每次发起查询，都能在定时器的一个周期内返回结果。但在实际环境中，很多业务系统的数据库会出现延迟响应问题，一旦查询响应超时，数据采集工作就会收到影响，严重时会造成数据丢失。  

   假设采集alert的作业依然像cpu.usage一样，每5s从数据库抓取一批数据，假定当前时间是00:00:08，正在执行的作业在00:00:00就已经启动了，但由于目标数据库响应超时，SQL执行了8s才返回，当前时间已经晚于预定的启动时间00:00:05，所以下一个job会立即执行，启动时间是00:00:08，这样推送给SQL的时间参数就是00:00:08，查询数据的时间窗口就是[00:00:03, 00:00:08)，位于时间窗口[00:00:00, 00:00:03)的数据就被遗漏了。  

   将作业的执行变成异步非阻塞即可解决这个问题，但这会带来另一个问题，无法保证数据按生成的时间有序，而确保数据按照生成的时间有序对此项目来说非常重要。特别是在后续的流处理环节，会有一系列基于时间窗口的计算，虽然可以在流处理时再对数据进行排序，但流处理组件无法判断某个时间窗口内的数据是否都已就绪，所以这是一个需要在数据采集阶段解决的问题。  

   所以，需要解决两个问题：  

- 确保每个时间窗口内的数据都能被采集到。  
- 数据采集作业必须是串行的，只有这样才能保证在流处理时拿到的都是已经完全就绪的数据。  

   方案：将这个Job切分为两个子Job，第一个job负责制定周期性的执行计划，即周期性的生成时间窗口参数；第二个job负责读取时间窗口参数并执行查询，这部分工作并不是周期性的，而是只要有时间参数生成就立即执行，如果执行超时，在超时期间需要缓存第一个job生成的时间参数，而当所有的查询都及时完成、没有待执行的查询时，第二个job需要等待新的查询参数到达。这实际上是一个生产者-消费者模型，在这个模型里，第三个参与者——仓库或者说传送带，起到了关键的调节作用，一个现成的实现就是JDK中的BlockingQueue。  
   
   - 第一个job由定时器周期性触发，每次触发时会把当前时间放入一个BlockingQueue的队尾；
   - 第二个job循环执行，每次从BlockingQueue队头取出时间参数，组装SQL并执行，当队列为空时，由BlockingQueue来阻塞当前线程，等待时间参数进入队列。；
   - 当第二个job执行完一次时，如果队列中还有时间参数，会继续执行上一个步骤，出现此类情况说明前一次的执行超时了。

```xml
<!-- 收集alert数据 -->
<!-- 应对采集作业超时的情况 -->
<route id="alertSchedulingJob">
    <from uri="timer:alertSchedulingTimer?period={{job.alert.period}}&amp;delay=6s"/>
    <to uri="bean:dateFormatter?method=format(${header.firedTime})"/>
    <log message="Job Name: alertScheduleJob, Schedule Time: ${in.body}"/>
    <!-- 将定时器生成的时间参数放入阻塞队列 -->
    <to uri="bean:alertDateParamQueue?method=put(${in.body})"/>
</route>
<route id="alertExcutingJob">
    <from uri="timer:alertExecutingTimer?delay=-1"/>
    <!-- 从BlockingQueue队头取出时间参数 -->
    <to uri="bean:alertDateParamQueue?method=take()"/>
    <log message="Job Name: alertExecutingJob, Executing Time: ${in.body}"/>
    <setHeader headerName="timestamp">
        <simple>${in.body}</simple>
    </setHeader>
    <!-- 添加10s内的随机延时，用来模拟后面的SQL组件在目标数据库上响应延时的场景
        注意：这时为了模拟而添加的，实际项目中没有理由这样做 -->
    <delay>
        <simple>${random(10000)}</simple>
    </delay>

    <!-- 组装SQL并执行 -->
    <to uri="sql:{{job.alert.sql}}"/>
    <split>
        <simple>${body}</simple>
        <marshal>
            <json library="Jackson" />
        </marshal>
        <setHeader headerName="kafka.KEY">
            <simple>{{kafka.prefix.alert}}|${random(100)}</simple>
        </setHeader>
        <to uri="kafka:{{kafka.topic.alert}}"/>
    </split>
</route>
```

### 应对数据延迟就绪  

   很多时候外围系统会因为种种原因导致数据迟迟不能在数据库层面落地，这会让数据采集工作变得非常被动。以此项目为例，增量采集是以时间戳为依据的每个批次采集过去5s新生成的数据，如果数据从业务系统的应用服务器生成，到写入数据库的时间差超过了5s，那么就会错过采集的时间窗口，这一问题不是上面的方案可以解决的，这已经完全不在数据采集组件的控制范围之内了。  

   策略：如果数据即时就绪，我们要保证能够及时的捕获；如果数据延迟就绪，要保证至少不会丢掉它。  

   可以把同一个数据源的数据采集分成两个波次，第一波次的采集紧紧贴近当前时间，并且保持极高的频率，要保证最早最快的采集到当前的最新数据；第二波次采集的是过去某个时间区间上的数据，时间偏移可能在十几秒到几分钟不等，这取决于目标数据源的延迟程度，第二波次是一个“补录”操作，用于采集在第一波此进行时还未在数据库中就绪的数据，对于某些数据延迟较大的系统，甚至可以添加第三波次作为最后的托底操作，它的时间偏移会更大，目的是最后一次补全数据，保证数据的完整性。  

   为了模拟数据延迟就绪，在bdp-metric生成mem.used时，我们特意将插入数据库中的时间戳从当前时间向前偏移了不超过60s的随机值，这段代码在bdp-metric.sh的genOnlineMemUsed函数中。

```shell
MEM_USED_MAX_LANDING_SECONDS=60 # mem.used数据向前偏移的最大时间（秒）
actualTime=$((curTime-$RANDOM%$MEM_USED_MAX_LANDING_SECONDS))
timestamp=$(date -d @$actualTime +'%F %T')
```

mem.used的采集作业配置

```xml
<!-- 收集mem.used数据 -->
<!-- 应对数据延迟就绪 和 采集作业超时的情况 -->
<!-- 波次1 -->
<route id="memWave1SchedulingJob">
    <from uri="timer:memWave1SchedulingTimer?period={{job.mem.wave1.period}}&amp;delay=2s"/>
    <to uri="bean:dateFormatter?method=format(${header.firedTime})"/>
    <log message="Job Name: memWave1SchedulingJob, Scheduled Time: ${in.body}"/>
    <to uri="bean:memWave1DateParamQueue?method=put(${in.body})"/>
</route>
<route id="memWave1ExcutingJob">
    <from uri="timer:memExcutingTimer?delay=-1"/>
    <to uri="bean:memWave1DateParamQueue?method=take()"/>
    <log message="Job Name: memWave1ExecutingJob, Executing Time: ${in.body}"/>
    <setHeader headerName="timestamp">
        <simple>${in.body}</simple>
    </setHeader>
    <to uri="sql:{{job.mem.sql}}"/>
    <split>
        <simple>${body}</simple>
        <marshal>
            <json library="Jackson"/>
        </marshal>
        <setHeader headerName="kafka.KEY">
            <simple>{{kafka.prefix.mem.used}}|${random(100)}</simple>
        </setHeader>
        <to uri="kafka:{{kafka.topic.memUsed}}"/>
    </split>
</route>
<!-- 波次2 -->
<route id="memWave2SchedulingJob">
    <from uri="timer:memWave2SchedulingTimer?period={{job.mem.wave2.period}}&amp;delay=4s"/>
    <setHeader headerName="offset">
        <simple>{{job.mem.wave2.offset}}</simple>
    </setHeader>
    <!-- 将时间向前偏移job.mem.wave2.offset秒 -->
    <process ref="dateShiftProcessor"/>
    <to uri="bean:dateFormatter?method=format(${header.shiftedTime})"/>
    <log message="Job Name: memWave2SchedulingJob, Scheduled Time: ${in.body}"/>
    <to uri="bean:memWave2DateParamQueue?method=put(${in.body})"/>
</route>
<route id="memWave2ExcutingJob">
    <from uri="timer:memExcutingTimer?delay=-1"/>
    <to uri="bean:memWave2DateParamQueue?method=take()"/>
    <log message="Job Name: memWave2ExecutingJob, Executing Time: ${in.body}"/>
    <setHeader headerName="timestamp">
        <simple>${in.body}</simple>
    </setHeader>
    <to uri="sql:{{job.mem.sql}}"/>
    <split>
        <simple>${body}</simple>
        <marshal>
            <json library="Jackson"/>
        </marshal>
        <setHeader headerName="kafka.KEY">
            <simple>{{kafka.prefix.mem.used}}|${random(100)}</simple>
        </setHeader>
        <to uri="kafka:{{kafka.topic.memUsed}}"/>
    </split>
</route>
```

   这种多波次采集的方式会导致出现重复数据，因此需要进行去重操作。我们把这项工作交给了流处理组件，利用Spark Streaming的Watemark机制和dropDuplicates操作可以将指定时间范围内的重复数据移除。  

## 3 遇到的问题  

- #### 启动bdp-collect项目时，kafka报如下错误：  

```
[2020-08-13 20:07:55,743] ERROR Closing socket for /192.168.170.129 because of error (kafka.network.Processor)
kafka.common.KafkaException: Wrong request type 18
	at kafka.api.RequestKeys$.deserializerForKey(RequestKeys.scala:64)
	at kafka.network.RequestChannel$Request.<init>(RequestChannel.scala:50)
	at kafka.network.Processor.read(SocketServer.scala:450)
	at kafka.network.Processor.run(SocketServer.scala:340)
	at java.lang.Thread.run(Thread.java:748)
```

**原因**：pom文件中kafka的版本号和服务器上的kafka版本不同

**解决方法**：修改pom文件中的kafka版本

```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka_2.11</artifactId>
    <version>0.8.2.1</version>
    <exclusions>
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-log4j12</artifactId>
        </exclusion>
        <exclusion>
            <groupId>log4j</groupId>
            <artifactId>log4j</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>0.8.2.1</version>
</dependency>
```

服务器上的kafka：

```
kafka_2.10-0.8.2.1
```

2.10时编译kafka的scala的版本