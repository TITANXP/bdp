# bdp-dwh  

## 1 部署流程  

1.构建项目

```powershell
build.bat standalone|cluster
```

2.向远程服务器部署项目

```powershell
deploy.bat
```

3.启动hadoop

```shell
start-dfs.sh
start-yarn.sh
```

4.在hive中创建数据仓库各层

```sql
DROP DATABASE IF EXISTS tmp CASCADE;
CREATE DATABASE IF NOT EXISTS tmp LOCATION '/data/tmp';

DROP DATABASE IF EXISTS src CASCADE;
CREATE DATABASE IF NOT EXISTS src LOCATION '/data/src';

DROP DATABASE IF EXISTS dwh CASCADE;
CREATE DATABASE IF NOT EXISTS dwh LOCATION '/data/dwh';

DROP DATABASE IF EXISTS dmt CASCADE;
CREATE DATABASE IF NOT EXISTS dmt LOCATION '/data/dmt';
```

或者

```
bdp-dwh.sh create-database
```

5.在hive建表并导入数据

```shell
bdp-dwh.sh create-all
```


## 2 数据仓库工程结构   

### 2.1 临时数据层（TMP）   

TMP层是为源头数据在大数据平台上落地而开辟的一个区域，在TMP层会建立与源头数据表几乎一致的表结构。外部数据被采集后会首先放置在TMP层对应表上，这种设计有以下几个初衷   

- 将数据采集与后续任何附加的处理进行隔离，让被采集数据以原始形态先落地在大数据平台上，简化采集过程中的业务逻辑。 
- 以TMP层为起点，后续所有业务处理都是在大数据平台上利用大数据技术进行处理的，即统一了技术堆栈，又可以充分发挥大数据平台的优势。
- 对于很多数据采集工具来说，它们落地到HDFS上时，只支持CSV一类的纯文本格式，而大数据平台上的正式表多用Parquet、ORC一类的二进制格式，基于这种现状，TMP层各表都按指定文本格式存储，以便更好地与数据采集工具对接。
- 很多数据采集工具（如Sqoop）都能根据目标数据源的表结构在Hive上直接建立对等的表结构，这会大大减少TMP的开发工作量，因此我们可以不必对TMP层进行手动干预，让采集工具在上面自动建立表的Schema并进行导入。  

项目中，使用Sqoop自动创建表并导入数据到TMP层。工程结构上没有为TMP层单独设计对应的包，原因是TMP层与SRC层关系极为密切，数据被采集到TMP层后，会立即被处理并提升到SRC层，为了削减重复的基础设施代码，简化工程结构，因此把构建TMP层的代码放在了SRC层对应的包里。  

### 2.2 源数据层（SRC）  

   SRC层的定位是，在数据仓库上保存来自数据源头、未经任何修改的原始数据，这些数据保持与源数据格式一致，且一旦在SRC层落地就不再变动。同时，由于SRC层周期性的采集并存储数据源头的数据，所以它能保存目标数据的变更历史，这为后续建立相关数据的缓慢变化维度提供了支持。   

   SRC层的数据都是按照采集日期进行分区的，以每天采集的数据表为例，在每天的某个固定时刻，Sqoop会从源头数据表采集数据，并放置于TMP层的对应表上，TMP层的表是没有分区的，每次Sqoop写数据时都会直接覆盖上次遗留的数据，紧接着，后续的一个作业会将TMP层数据导入SRC层，其间会有一定的数据清洗和转换工作。  

#### 数据模型

   SRC层的数据表基本上会完全参照其在元数据系统中的定义，同时会追加一些类似“导入时间”这样的与导入操作相关的字段。SRC层的数据有一个鲜明的特点，即“数据是不可变的”，每个导入周期内放到SRC层的数据是永远不会被更新的，它代表的是那个周期内的数据表全量或增量的一个快照，是只读的、不可变的。如果因为某些原因需要重新导入数据，则对应时间周期上的导入数据应该被覆盖，这样可以保持和维护SRC层数据的简洁性。

![](https://raw.githubusercontent.com/TITANXP/pic/master/img/bdp-SRC-%E6%95%B0%E6%8D%AE%E6%A8%A1%E5%9E%8B.png)

#### 建表并处理数据   

   SRC层建表有两个环节：  

- 从源头数据库读取表的元数据，并在TMP层自动创建镜像表
- 读取建表脚本，并在SRC层建表

tmp层的表是由Sqoop自动创建的，其字段名与源表一致，类型进行了自动映射，对于表格存储文件的格式设定也是由Sqoop自动生成的。值得一提的是，Sqoop在Hive这一端落地数据时只支持文本格式的存储，也就是说Sqoop向Hive上的表写数据时，Hive表只能声明为文本格式，这也是设立tmp层的原因之一，我们会用tmp层作为接入数据的一个缓冲层，数据表的格式会尽量贴近源表，某些属性设置也会面向采集工具进行适配（如数据表的存储格式），一旦数据进入到src层，会按照大数据环境的标准和规范来管理数据，那么中间具体的差别是什么呢？我们来看一下src层上Server数据是如何存储的。   

首先看一下Server在SRC层的结构，建表脚本（lib/src/bdp-master/sceama/server.sql)

```sql
DROP TABLE IF EXISTS src.bdp_master_server;

CREATE TABLE IF NOT EXISTS src.bdp_master_server(
    id BIGINT,
    app_id BIGINT,
    hostname STRING,
    cpu_cores INT,
    memory INT,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
PARTITIONED BY (update_time STRING)
STORED AS parquet;
```

   SRC层的表使用了Parquet格式进行存储，分区列update_date是从TMP将数据导入到SRC时，将update_time字段转化为date再转化为字符串得到的（使用string的原因是，当前版本spark不支持date做分区列）

SRC层的数据处理有两个环节：

- 从源头数据库导入数据到TMP层
- 从TMP层导入数据到SRC层   

从TMP层导层SRC层（lib/src/bdp-master/action/build-server.sql)

```sql
INSERT OVERWRITE TABLE src.bdp_master_server PARTITION(update_date)
SELECT
    id,
    app_id,
    hostname,
    cpu_cores,
    memory,
    CAST(creation_time AS TIMESTAMP) AS creation_time,
    CAST(update_time AS TIMESTAMP) AS update_time,
    CURRENT_TIMESTAMP AS imported_time,
    CAST(CAST(update_time AS DATE) AS STRING) AS update_date
from
    tmp.bdp_master_server;
```

   Sqoop自动生成TMP层表时会把日期和时间类型映射为string，所以当这些数据进入SRC层时，需要转换为原本的类型。  

   imported_time用来标记数据进入大数据平台的时间，以便后续追踪和统计。虽然严格意义上数据导入时间并不是SQL执行时间，而是Sqoop作业完成时间，但相差不大。  

   将update_time转换为只含日期的字符串是为了作为分区列。   

#### 增量导入与全量导入   

   在SRC层的数据导入中会涉及选择增量导入还是全量导入的问题，两者在处理逻辑和资源负载上有较大差异，需要对每一张表进行梳理并确定。一般原则是：如果原始数据表存在增量导入的条件，一定要优先按增量进行导入和处理，原因：  

- 增量导入可以大大减小源头数据库的负载；
- 增量导入的数据量较全量导入要少很多，可以用较少的资源在较短的时间内完成作业，避免不必要的资源浪费；  
- 
- 由于SRC层会保留每次导入的数据，全量导入会占用大量的存储空间。  

什么是具备“增量导入条件”？：主要看数据表本身是否有标记增量的字段。例如，很多数据库在设计时会依照规范给所有的数据表添加“创建时间”和“更新时间”字段，bdp_master数据库就是按照这样的规范设计的，所以”更新时间“是最好的增量导入的依据字段。  

如果没有”更新时间“，但有自增ID，这时要看记录本身是否是不可变的，如果数据一旦生成就永远不会变更，新增数据都是新生成的，而不是更新的，那么这样的数据表也具备增量导入的条件。自增ID就是增量的标识字段。  

   如果一张表既没有更新标识，有没有增量标识，就很难进行增量导入了，此时只能全量导入处理。  


| 数据表                      | 导入方式 | 源头数据是否会更新   | 是否需要构建缓慢变化维度 |
| --------------------------- | -------- | -------------------- | ------------------------ |
| bdp_master.app              | 增量导入 | 是（版本升级）       | 是                       |
| bdp_master.server           | 增量导入 | 是（硬件升级）       | 是                       |
| bdp_master.metric_threshold | 增量导入 | 是（阈值调整）       | 是                       |
| bdp_master.metric_index     | 增量导入 | 是（命名或描述调整） | 是                       |
| bdp_metric.metric           | 增量导入 | 否                   | 否                       |

#### SRC层的表分区   

##### 增量导入   

   在增量导入的情况下，采集工具会以增量字段为依据，读取某个增量区间（一般是一天）内的数据写入TMP层，在SRC层中，后续作业会继续读取TMP层中的这批增量数据，并写入SRC层，SRC层的表如何分区就变成了一个需要好好把握的问题。  

   对于HIve和Spark SQL，选择一张表的分区列有一个最核心的原则，就是看这个字段（或多个字段）是否总是在查询时用到，如果是，那么选取这个字段（或多个）做分区列的话，会在查询时极大的收窄数据查找区间，提升查询性能。   

   SRC层的定位是有效管理和存储每次增量导入的数据，保持数据的不可变性，维持变更历史，于是一个很直白的方案是，在SRC层的对应表上，以增量采集的那个依据字段作为表的分区字段，与此同时，不管是DWH还是DMT，后续从SRC层的表读取1数据时，也是将某一批的增量数据”merge“到DWH或DMT层的全量表中，这也要求SRC层的表最好按增量导入的字段做分区，以便于筛选数据。  

   所以对于增量导入的数据表，其在SRC层对应表的分区字段几乎无一例外都是增量导入的依据字段。  

##### 全量导入  

   在全量导入的情况下，SRC层也必须要对每个导入批次加以区分，比较合适的做法是以导入日期作为分区列。  

#### SRC层数据归档  

   SRC层中表处理最后需要留意的一点是，大量SRC层的数据表依据增量进行分区会产生很多分区，每个分区又会有很多文件，如果增量数据不多，随着时间的推移，会累积大量小文件，对Hadoop的性能造成影响，所以一定要对数据进行定期归档，归档的方法是按照更粗的粒度合并分区数据，以时间为例，可以由天改为按月、季度或年重新分区，进而将琐碎的小文件合并为大文件。  

## 2.3 明细数据层（DWH）  

   对于每一张数据表而言，SRC层的数据就绪之后，紧接着的工作就是将SRC层的最新数据更新到DWH层，DWH层的数据是数据仓库对每一类业务数据的正式存储，每一张表的字段名和类型都会遵守同一风格和约定，在数据从SRC层进入到DWH层的过程中会完成绝大多数ETL工作，最终在DWH层形成广泛可用的标准数据。   

   在数据分区上，DWH层每个表的分区字段完全依据具体业务而定，与SRC层的规则完全不同。对于那些无明显分区依据的表不会有分区字段。  

#### 数据模型  

![](https://raw.githubusercontent.com/TITANXP/pic/master/img/bdp-DWH-%E6%95%B0%E6%8D%AE%E6%A8%A1%E5%9E%8B.png)  

#### 建表并处理数据  

##### 建表  

   以Server为例（lib/dwh/bdp-master/schema/server.sql）  

```sql
DROP TABLE IF EXISTS dwh.bdp_master_server;

CREATE TABLE IF NOT EXISTS dwh.bdp_master_server(
    id BIGINT,
    app_id BIGINT,
    hostname STRING,
    cpu_cores INT,
    memory INT,
    creation_time TIMESTAMP,
    update_time TIMESTAMP,
    imported_time TIMESTAMP
)
STORED AS parquet;
```

   DWH层的表有别于SRC层的地方是，不再使用update_date作为分区列，因为基于更新时间进行查询或处理服务器的业务数据场景几乎不存在。  

##### 处理数据  

   正常情况下，数据在从SRC层跃迁到DWH层时，会有一系列的数据清洗、校验和转换工作，因为SRC层表的定位是贴近数据源，数据进入SRC层时时保持原始格式的，而进入DWH层时格式需要统一，数据质量需要提升到更高的层级，这中间的工作都是通过DWH层“action”目录中的SQL及UDF完成的。  

######  合并增量数据

   DWH层的一项核心工作是是把SRC层的增量数据合并到DWH层的全量表中，这一工作需要将两部分数据放在一起，找出最新的数据作为返回结果，因为增量数据中会有更新数据，所以当SRC层的增量数据和DWH层的全量数据合并后，同一条业务数据可能会存在两条记录，这就需要SQL在合并的结果集中按照ID进行分组，再按照更新时间进行排序，选出一条最新的记录作为结果。  

   以Server为例（lib/dwh/bdp-master/action/build-server.sql）

```sql
# 便于直接将select的结果覆盖回dwh.bdp_master_server,
# 因为select语句选择的一部分结果集来自dwh.bdp_master_server,如果直接覆盖回dwh.bdp_master_server,在spark-sql中是不允许的
SET spark.sql.hive.convertMetastoreParquet=false;
# 开启后可以使用正则选择字段
SET spark.sql.parser.quotedRegexColumnNames=true;

INSERT OVERWRITE TABLE dwh.bdp_master_server
SELECT
    `(row_num|oc)?+.+`
FROM(
    # 将SRC层的增量数据，与DWH层的全量数据进行合并
    SELECT
        *,
        ROW_NUMBER() OVER( # 基于id进行分组，并在分组内根据更新时间和oc进行降序排列，然后赋予一个序号
            PARTITION BY id # 通过row_number()函数，可以很容易找到合并之后id相同的记录中最新的一天，这条记录的row_num=1
            ORDER BY update_time DESC, oc DESC
        ) AS row_num
    FROM(
        SELECT
            *, 0 AS oc # oc (Ordering Column 排序列)
        FROM
            dwh.bdp_master_server
        UNION ALL
        SELECT
            `(update_date)?+.+`, 1 AS oc # 除update_date外的所有字段,和oc
        FROM
            dwh.bdp_master_server
        WHERE
            update_date >= '@startDate@' AND update_date < '@endDate@'
    ) a
)
WHERE row_num = 1;
```

   DWH层的全量数据oc为0，SRC层增量数据oc为1，因为如果出现数据重复导入的情况，为了避免数据筛选的不确定性，我们永远让SRC层的增量数据拥有更高的被选择权。  

## 2.4 汇总数据层（DMT）  

  数据跃迁到DMT层时，数据结构往往会发生较大的变化，在DMT层之下，数据还是大体以原始形态为蓝本进行组织和处理的，进入DMT层之后，数据将变为以维度模型为导向的格式进行组织了，典型的代表就是事实表、维表、宽表。  

### 数据模型  

   DMT层的数据模型主要以维度模型为主，维度模型与领域模型（使用面向对象思想建立的模型），有很大的不同，我们的维度模型以Metric作为核心式时数据，围绕Metric会有App、Server、Metric类型和时间等多种维度来度量。  

#### DMT层表的命名：  

   事实表添加fact\_前缀，维度表添加dim\_前缀，轻度汇总表添加sum\_前缀，宽表添加wide\_前缀。  

   由于数据仓库会根据需要保存历史数据，所以很多表，特别是那些需要做2型SCD的表，在DMT层都不能再使用原始数据的ID作为唯一主键（同一ID的数据可能会被多次导入数据仓库，ID不再唯一），而是要生成代理主键DWID，所以在fact\_metric表中存在app\_dwid、server\_dwid、metric\_dwid、metric\_threshold\_id、hour\_dwid等多个外键用来关联相关维度表。  

![](https://raw.githubusercontent.com/TITANXP/pic/master/img/DMT%E5%B1%82Metric%E5%AE%9E%E6%97%B6%E6%95%B0%E6%8D%AE%E7%9A%84%E6%98%9F%E5%9E%8B%E6%A8%A1%E5%9E%8B.png)

   除了基于事实数据的星型模型，有时还需要根据业务需求创建一些轻度汇总数据表，经过汇总的事实数据同样需要关联维度表。以Metric数据为例，业务上经常会以单位时间（如小时）内的Metric均值分析和度量服务器的运行状况，此时就出现了汇总数据的需求，即以单位时间内的Metric均值作为汇总表中的一条记录，再关联各种维度表，形成以汇总的事实数据为核心的星型模型。  

![](https://raw.githubusercontent.com/TITANXP/pic/master/img/DMT%E5%B1%82Metric%E5%9D%87%E5%80%BC%E6%95%B0%E6%8D%AE%E7%9A%84%E6%98%9F%E5%9E%8B%E6%A8%A1%E5%9E%8B.png)

   除了汇总表，宽表也是大数据平台上比较流行的一种数据表，宽表，顾名思义，就是字段比较多的表，对于一个星型模型来说，把事实表和它所有的维度进行join，形成的结果集就是一张宽表，宽表对于终端用户来说是最简单、最容易理解的表，使用起来也很方便，但是宽表会冗余大量数据，并不是所有数据都要建宽表，将Metric星型模型展开后可以得到一张大宽表，如图：  

![](https://raw.githubusercontent.com/TITANXP/pic/master/img/DMT%E5%B1%82Metric%E5%AE%BD%E8%A1%A8.png)

#### 构建维度模型   

##### 事实表  

   看一下Metric事实表dmt.fact_metric及其前身dwh.bdp_metric_metric
![](https://raw.githubusercontent.com/TITANXP/pic/master/img/metric%E5%8E%9F%E5%A7%8B%E7%BB%93%E6%9E%84%E4%B8%8E%E4%BA%8B%E5%AE%9E%E8%A1%A8%E5%AF%B9%E6%AF%94.png)

从两张表的前后结构改变可以发现：  

- 右侧事实表中与“实时”相关的字段（如Metric的value、timestamp）从左侧直接平移过来。  

- 右侧事实表中所有与维度有关的都转换成了DMT层对应维度表的对应记录的DWID。例如：左侧的hostname被右侧的server_dwid取代，通过DWID查找dim_server对应的Server信息即可。右侧app_dwid是基于Metric对应的Server关联查询dim_app得到的，这样，将Metric直接关联到App维度上，便于后续分析。  

以一条Metric数据为例：  

DWH层

| id   | name      | hostname | value | timestamp             | imported_time           | creation_date |
| ---- | --------- | -------- | ----- | --------------------- | ----------------------- | ------------- |
| 1    | cpu.usage | svr1001  | 87    | 2018-09-01 10:34:17.0 | 2019-03-13 03:11:12.621 | 2018-09-01    |

DMT层

| id   | app_dwid | server_dwid | metric_index_dwid | hour_dwid  | timestamp             | value | creation_date |
| ---- | -------- | ----------- | ----------------- | ---------- | --------------------- | ----- | ------------- |
| 1    | 1        | 1           | 1                 | 2018090110 | 2018-09-01 10:34:17.0 | 87    | 2018-09-01    |

##### 维度表   

先看Server维度表，  

| dwid | id   | app_id | hostname | cpu_cores | memory | valid_from            | valid_to | eff_flag |
| ---- | ---- | ------ | -------- | --------- | ------ | --------------------- | -------- | -------- |
| 1    | 1    | 1      | svr1001  | 16        | 64000  | 2018-09-01 00:00:00.0 | \<null>  | true     |

这是一张缓慢变化维度表   

此外，在Metric中必然会涉及时间维度，时间在任何系统里都是一个公共维度，这个维度的粒度要根据分析需求来确定，绝大多数系统使用daliy级别的时间粒度，这时可以有名为类似dim_date的维度表。在此项目中，运维人员可能需要看到小时级别的趋势变化，于是把时间粒度下钻到小时级别，取名为dim_hour。不同于那些业务系统的维度数据，时间维度是统一的、标准的，在设计dim_hour的DWID时，最简单的做法就是将年月日时拼成一个长整型。  

dim_hour示例数据：  

| dwid       | db_date    | db_hour               | year | month | day  | hour | quarter | week | day_name | month_name | weekend_flag |
| ---------- | ---------- | --------------------- | ---- | ----- | ---- | ---- | ------- | ---- | -------- | ---------- | ------------ |
| 2018090110 | 2018-09-01 | 2018-09-01 10:00:00.0 | 2018 | 9     | 1    | 10   | 3       | 35   | Saturday | September  | true         |

##### 维度扁平化 与 关联间接维度    

   App维度与其它维度有所差别，原始Metric数据中没有App的信息，App的信息是通过Server关联过去的。   

   App维度涉及维度建模中的一个基本问题：使用星型模型还是雪花模型？在业务数据库上App和Server是一对多的关联关系，在数据仓库的维度模型上，两者又都是维度数据，如果依据雪花模型的建模思想，会位置App和Server之间的关系，让Metric事实数据只关联到Server维度，再通过Server关联到App维度，在查询Metric事实数据与App维度相关的数据时也要相应的通过表关联来实现。  

   但此项目中使用星型模型，星型模型的思想是要对维度进行扁平化处理，带有层级关系的维度会被压平到最细粒度的层级上，这样的话，App的信息会冗余到Server表中，相当于使用Server左关联App。   

   但并不是所有具有关联关系的维度表都应该这样处理，也可以把间接关联维度提升为直接关联维度，而不是融合到单一维度上，因为融合为单一维度会让很多属性在该维度上显得不合时宜，造成理解和使用上的困难。  

   选择哪种方案还是要看业务上这些维度之间的关联关系有多紧密。对于那些几乎总是一起被提及和使用的维度，可以融合到单一维度，对于那些业务意义上相对独立且经常需要被单独提取进行观察的维度，还是保持其独立性，从简介关联提升为直接关联为好。  

   此项目中Server与App维度的处理采用了后者，因为我们认为App和Server是两个在业务层面上相对独立的维度。  

##### 缓慢变化维度（SCD，Slowly Changing Dimension)   

   维度数据和其它数据一样都有可能发生变化，但是维度数据的变化会给观察数据带来影响，一个不得不考虑的问题是，当维度发生变化后，如何维持实时数据与它们的关联关系，是只维护一个最新状态？还是保存每一次的变更历史，让实时数据关联到其在对应时间窗口上那版数据？  

   选择的依据完全取决于业务的需求。  

   例如，此项目中，应用程序App会进行升级，每次升级会带来功能的增强或集群扩容，升级之后的系统会在诸多指标上产生变化，因此涉及应用维度的分析都应该基于其当时版本和规模进行，而不应该总是使用现在的应用状态去度量过去旧版本的情况。  

   系统中的服务器Server也是相同的情形，因为服务器也会存在硬件和操作系统升级的问题，升级后在Metric和Alert上都会有所体现，所有Server也是一种典型的缓慢变化维度。   

   业界对SCD有有公认的几种不同的处理方式，依次取名为0型到7型：  

   - 0型：静态数据，不可变  

   0型SCD其实并不是一种缓慢变化维度，它是为了和其它SCD进行区别而得名的，简单来讲，0型是指永远不会发生改变的维度，是永远静态的，最典型的例子就是时间维度，所有值都是可预期的与不可变的。  

   - 1型：覆盖原有数据  

   1型SCD的策略是直接使用最新的数据覆盖旧数据  

   - 2型：添加新记录  

   如果业务端需要保存变更的历史，那么1型就无法满足需求，这时就需要引入2型SCD，即为每一次变更添加一行新纪录。  

   2型SCD的实现比较复杂，首先同一条业务数据在维度表中因为多次变更而演变成多条数据，原始业务表中的ID不再唯一，此时需要引入代理主键，再者，维度表中的每一条数据都要添加生效的起止时间和标志位，在“merge”新记录时都要更新这些字段。  

   - 3型：添加新属性   

   3型是对2型的一种弱化处理，把增加新的行改为了增加新的属性，但是无法保留版本变更时间等信息。3型SCD常表现为“曾用名”、“曾用地址”之类的属性列，一方面业务人员有使用这些曾用值的需求，另一方面，又不需要跟踪全部历史1，也不用关心在什么时间范围上是曾用值。  

   - 4型  

   Wiki和The Data Warehouse Toolkit一书中给出了两种不同的解释。  

   后者的解释是，对于那些基数巨大的维度而言，任何关联到它的查询都会面临性能挑战，4型SCD将变化频繁的维度属性抽离到一个单独的表中，成为mini维度表，其余相对稳定的属性保存在主维度表中。4型SCD一个非常典型的特点是，为了缩减维度基数，mini维度表通常会将属性值归纳为预定义好的区间，这在一定程度上牺牲了查询该维度的灵活性和自由度，但是换来的是较大的性能提升。  

   - 类型5、6、7是组合使用两种以上基本类型满足特定需求的复合类型。  

##### 2型SCD表  

   2型SCD是最为普遍和使用的SCD类型，以App维度数据为例：

| dwid | id   | name  | description                                 | version | valid_from            | valid_to              | eff_flag |
| ---- | ---- | ----- | ------------------------------------------- | ------- | --------------------- | --------------------- | -------- |
| 2    | 1    | MyCRM | The Customer Relationship Management System | 7.1     | 2018-09-02 00:00:00.0 | \<null>               | true     |
| 1    | 1    | MyCRM | The Customer Relationship Management System | 7.0     | 2018-09-01 00:00:00.0 | 2018-09-01 00:00:00.0 | false    |

   针对id=1的这条数据，有两条历史数据，其中id、name、description、version都来自原始业务表，观察他们的值可以发现，MyCRM系统在2018-9-2进行了升级。如前所述，应用系统的这种变更对关联查询和分析的影响很大，所以数据仓库系统必须使用2型SCD表保存变更历史，为了实现这一目标，一些重要的辅助列是必不可少的：dwid、valid_from、valid_to、eff_flag。

- dwid：代理主键
- valid_from、valid_to、eff_flag：标记该记录的生效起止时间，及是否正在生效。在记录变更时，就会生成一条新的记录，这条记录的valid_from就是数据的更新时间，valid_to为空，eff_flag=true，而旧的记录会被标记为不再生效（eff_flag=false），同时valid_to为新记录的valid_from，这样每一条记录在时间轴上都能串联在一起，这也是2型SCD表被称为“拉链表”的原因。

##### 构建2型SCD表

   构建2型SCD表的核心逻辑是将SRC层的每日新增数据合并到DMT层的全量数据表中，旧的数据作为变更历史保存在DMT层的全量表中，具体地说，这里会有5中情形，每一种情形都需要考虑如何分别操作SRC层的增量数据和DMT层的全量数据。  

- 情形1：SRC层的增量表中有，DMT层的全量表的生效记录中也有，所有字段值完全一致。  

  出现这种情形有两种可能，一种是发生了数据的重复导入，另一种是记录确实发生过变更，但是在同一个采集周期内，改动的值又回到了初值，这样发生变化的只有update_time字段。  

  如何处理？原则上这类数据应该被忽略，因为数据没有发生实质意义上的变更。如何判断是否发生实质意义上的变更?把具有实质意义的字段拼接在一起去hash值，将这个hash值作为一个字段添加到表中。  

- 情形2：SRC层的增量表中有，DMT层的全量表的生效记录中也有，但有值不一致的字段  

  这类数据是标准的"变更数据"，也就是记录前后发生了变化，同样一份数据在SRC层增量表中的这一版是“更新后的数据”，DMT层的是“更新前正在生效的数据”，对两种数据的处理方式是：  

  - SRC层更新后的数据：复制到结果集中，生效日期取SRC层增量表中记录的更新时间，有效标记为true；  
  - DMT层更新前正在生效的数据：复制到结果集中，失效日期取SRC层增量表中记录的更新时间，有效标记为false；  

  在此项目中，我们也认为情形1是一种异常情形，但没有使用hash值来识别这种情形，这样，从逻辑上讲，程序区分不出情形1和2，情形1按照2去处理，当情形1发生时，在2型SCD表中生成的最新一条有效记录和上一个版本的记录数据是完全一致的，并不会出现逻辑上的错误。  

- 情形3：SRC层的增量表中有，DMT层的全量表的生效记录中没有  

  这类是标准的“新增数据”，处理方式：

  - 对于SRC层的新增数据：复制到结果集中，生效日期取SRC层增量表中记录的更新时间，有效标记为true；  

- 情形4：SRC层的增量表中没有，DMT层的全量表的生效记录中有  

  这类是没有发生过任何变更的数据，处理方法：

  - 对于DMT层全量表中的”未变更数据”，复制到结果集中，不做任何修改。  

- 情形5：DMT层全量表中的变更历史数据  

  这部分数据作为历史沉淀数据，不会再发生任何变更了，这类数据也不可能存在于SRC的增量表中，处理方式：  

  - 对于DMT层全量表中的”变更历史记录”，复制到结果集中，不做任何修改。 

|                       | 情形1：SRC层的增量表中有，DMT层的全量表的生效记录中也有，所有字段值完全一致 | 情形2：SRC层的增量表中有，DMT层的全量表的生效记录中也有，但有值不一致的字段 | 情形3：SRC层的增量表中有，DMT层的全量表的生效记录中没有      | 情形4：SRC层的增量表中没有，DMT层的全量表的生效记录中有      | 情形5：DMT层全量表中的变更历史数据                           |
| --------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 针对SRC层增量表的操作 | 可通过全部字段的hash值识别出这一情形，不做任何操作，如果不做识别，就会按照情形2处理 | 操作2：将SRC层增量表中“更新后的数据”复制到结果集中，生效日期取SRC层增量表中记录的更新时间，有效标记为true； | 操作2：将SRC层的”新增数据“：复制到结果集中，生效日期取SRC层增量表中记录的更新时间，有效标记为true； |                                                              |                                                              |
| 针对DMT层全量表的操作 | 可通过全部字段的hash值识别出这一情形，不做任何操作，如果不做识别，就会按照情形2处理 | 操作1.1：将DMT层“更新前正在生效的数据”复制到结果集中，失效日期取SRC层增量表中记录的更新时间，有效标记为false； |                                                              | 操作1.2：对于DMT层全量表中的”未变更数据”，复制到结果集中，不做任何修改。 | 操作1.2：对于DMT层全量表中的”变更历史记录”，复制到结果集中，不做任何修改。 |

##### 生成代理主键dwid  

   代理主键是数据在数据仓库上区别于原始数据ID的唯一主键。  

   在基于传统数据库的数据仓库上，生成代理主键是一项很简单的工作，只需通过数据库的自增主键生成机制就可实现，但是在Hive和Spark SQL为代表的大数据平台上没有这个功能，原因是在分布式平台上，生成唯一主键并不是一件简单的事情，这就需要开发者自己解决这个问题。  

   一般来说，UUID会被视为一种简单的解决方案，但UUID过长，会占用过多的存储空间，通常不建议使用，下面是一种相对可行的方案。  

   思路是查询出已有记录的最大ID，然后在这个最大ID的基础上通过row_number()函数来生成自增ID，再加上最大ID即可。

##### 用SQL实现上面的操作   

   整体上，会把针对DMT层的全量表，和SRC层的增量表的操作分开，然后将两张表的处理结果union，就是最终结果集。

```sql
# 导入数据 dim_app

SET spark.sql.hive.convertMetastoreParquet=false;
SET spark.sql.parser.quotedRegexColumnNames=true;

# 在增量采集模式下，SRC层增量表中的每日分区数据就是增量数据
# 需要在SQL中反复引用这个单日的数据集，并且不需要使用creation_tine、update_time、imported_time这些字段
# 所以定义临时视图将某一天的“更新和新增数据”作为一个独立的数据集使用
-- CREATE OR REPLACE TEMPORARY VIEW updated_and_added_records AS
CREATE OR REPLACE VIEW updated_and_added_records AS
SELECT
    s.`(creation_time|update_time|imported_time)?+.+`
FROM
    src.bdp_master_app s
WHERE
    s.update_date >= '@startDate@' AND s.update_date < '@endDate@';


#整体上把针对DMT层的全量表，和SRC层的增量表的操作分开，然后将两张表的处理结果union，就是最终结果集。
INSERT OVERWRITE TABLE dmt.dim_app
SELECT
    *
FROM(
    # 针对DMT全量表的操作
    #   操作1.1: 将DMT全量表中的“更新前的数据”复制到结果集，失效日期取SRC增量表中记录的更新时间，有效标记位置为"false"
    #   操作1.2: 将DMT全量表中的“变更历史记录”复制到结果集，不做任何修改
    SELECT
        m.`(valid_to|eff_flag)?+.+`,
        #处理失效时间
        CASE WHEN m.eff_flag = true AND u.id IS NOT NULL THEN # 情况2
            u.update_date # 操作1.1
        ELSE #情况 4、5
            m.valid_to # 操作1.2
        END
        AS valid_to,
        #处理标志位
        CASE WHEN m.eff_flag = true AND u.id IS NOT NULL THEN # 情况2
            false # 操作1.1
        ELSE #情况 4、5
            m.eff_flag # 操作1.2
        END
        AS eff_flag
    FROM
        dmt.dim_app m
    LEFT JOIN
        updated_and_added_records u
    ON
        m.id = u.id

    UNION ALL

    # 操作2: 针对SRC增量表(新增和变更数据集)的操作: 将增量数据复制到结果集，生效日期取增量记录里的更新时间，有效标记位置为"true"
    SELECT
        ROW_NUMBER() OVER(ORDER BY 0) + m.max_id AS dwid, # 在最大dwid的基础上累加，从而为新记录生成dwid
        u.`(update_date)?+.+`,
        u.update_date AS valid_from, # 更新日期设为生效日期
        NULL AS valid_to, #失效日期为null
        TRUE AS eff_flag # 生效标志位设为true
    FROM
        updated_and_added_records u
    CROSS JOIN
        # 查出当前最大dwid，没有则取0
        (SELECT COALESCE(MAX(dwid), 0) AS max_id FROM dmt.dim_app) m
)
```

## 3 异常调优

- ##### spark.sql.hive.convertMetastoreParquet

https://cloud.tencent.com/developer/article/1488143

   parquet是一种列式存储格式，可以用于spark-sql 和hive 的存储格式。在spark中，如果使用using parquet的形式创建表，则创建的是spark 的DataSource表；而如果使用stored as parquet则创建的是hive表。   

   spark.sql.hive.convertMetastoreParquet默认设置是true, 它代表使用spark-sql内置的parquet的reader和writer(即进行反序列化和序列化),它具有更好地性能，如果设置为false，则代表使用 Hive的序列化方式。   

   但是有时候当其设置为true时，会出现使用hive查询表有数据，而使用spark查询为空的情况.   

   但是，有些情况下在将spark.sql.hive.convertMetastoreParquet设为false，可能发生以下异常(spark-2.3.2)。  

```
java.lang.ClassCastException: org.apache.hadoop.io.LongWritable cannot be cast to org.apache.hadoop.io.IntWritable
    at org.apache.hadoop.hive.serde2.objectinspector.primitive.WritableIntObjectInspector.get(WritableIntObjectInspector.java:36)
```

   这是因为在其为false时候，是使用hive-metastore使用的元数据进行读取数据，而如果此表是使用spark sql DataSource创建的parquet表，其数据类型可能出现不一致的情况，例如通过metaStore读取到的是IntWritable类型，其创建了一个WritableIntObjectInspector用来解析数据，而实际上value是LongWritable类型，因此出现了类型转换异常。   

   与该参数相关的一个参数是spark.sql.hive.convertMetastoreParquet.mergeSchema, 如果也是true，那么将会尝试合并各个parquet 文件的schema，以使得产生一个兼容所有parquet文件的schema。   

- #### SET spark.sql.parser.quotedRegexColumnNames=true;

  开启后可以使用正则选择字段

```sql
--选择除了rk以外的所有字段
SELECT `(rk)?+.+` FROM pos_rival;
```

## 4 遇到的问题  

- ### yarn上的任务一直处于accepted状态，不running  

**原因**：未开启yarn多线程模式，也就是scheduler为单线程单队列运行  

**解决方法**：配置yarn多线程运行模式  

修改yarn-site.xml文件，添加如下：  

```xml
<!-- 配置yarn多线程运行模式 -->
<!-- 开启公平调度器 -->
<!-- scheduler configuration, for multi-tasks run in queue, avoid mapreduce-run & pyspark ACCEPTED not run problem -->
    <property>
        <name>yarn.resourcemanager.scheduler.class</name>
        <value>org.apache.hadoop.yarn.server.resourcemanager.scheduler.fair.FairScheduler</value>
    </property>
    <property>
        <name>yarn.scheduler.fair.preemption</name>
        <value>true</value>
    </property>
<!-- 下面配置用来设置集群利用率的阀值， 默认值0.8f，最多可以抢占到集群所有资源的80% -->
    <property>
        <name>yarn.scheduler.fair.preemption.cluster-utilization-threshold</name>
        <value>1.0</value>
    </property>
```

https://blog.csdn.net/u010770993/article/details/70312473

- ### 使用spark-sql连接hive时出现javax.jdo.JDOFatalInternalException: Error creating transactional connection  

**原因**：因为使用的是MySQL作为元数据数据库，在启动的时候，需要使用MySQL驱动jar包进行连接  
**解决方法**：指定mysql-connecror jar包

```shell
bin/spark-sql --jars lib/mysql-connector-java-5.1.27-bin.jar 
```

- ### 导入数据后hive中有些字段为null  

原因：tmp层有些表已经在bdp-import中创建过  

解决方法：删除tmp原来已经存在的表   

- ### 使用beeline连接hive时出现 Required field 'client_protocol' is unset! Struct:TOpenSessionReq(client_protocol:null, configuration:{use:database=default})  

**原因**：直接输入beeline，显示

```
Beeline version 1.6.1 by Apache Hive
```

和hive的版本不一致，然后查看beeline命令的位置  

```shell
which beeline
/usr/local/spark-1.6.1-bin-2.5.0-cdh5.3.6/bin/beeline
```

发现使用的是spark中的beeline  

**解决方法**：将hive的环境变量放到spark的后面（从下往上查找）

- ### spark-sql 执行dwh层bdp_master相关表的数据导入sql时Error in query: cannot recognize input near   '\<EOF>' '\<EOF>' '\<EOF>' in subquery source

**原因**：Hive只支持在FROM子句中使用子查询，子查询必须有名字，并且列必须唯一  

**解决方法** ：在每一层 from() 子查询的括号外面都加上别名。

```sql
select * from (select id,devid,job_time from tb_in_base) a;
```

- ### hive版本问题  

  不支持临时视图  
  不支持正则选择字段    

- ### 导入dim_app表后，表中没有数据，控制台没有报错  

原因：由于输入命令随意输入了开始时间 “1111” 和结束时间“2222”，但转换成通过date命令转换成时间后并不是1111年和2222年

所以导致没有在这个时间范围内的数据。

```shell
\# date -d "1111"
2020年 08月 11日 星期二 11:11:00 CST
\# date -d "2222"
2020年 08月 12日 星期三 22:22:00 CST
```

所以导致没有在这个时间范围内的数据。   

- ### 注册UDF时提示FAILED: Class org.bdp.dwh.udf.GenRag not found  

**原因**：打开jar包，发现GenRag.class直接在根目录下，正常情况下应该在包名所对应的文件夹中  

直接使用类命注册，发现注册成功  

```shell
hive (default)> add jar /home/root/bdp-dwh-1.0/jar/bdp-dwh-1.0.jar ;
hive (default)> create temporary function gen_rag as "GenRag";
```
然后发现GenRag.scala虽然放在了包下，但是没有写包名。   

- ### 在SQL中调用UDF注册的函数时 No handler for Hive udf class org.bdp.dwh.udf.GenRag because: No matching method for class org.bdp.dwh.udf.GenRag with (int, int, int)  

**原因**：UDF类中的方法名写的不是evaluate  

- ###hive 字符串转换为timestamp   

使用下面的方法将字符串转换为timestamp后为null  

```sql
SELECT CAST('2020-01-01' AS TIMESTAMP);
```

**解决方法**：使用另一种方法  

```sql
select from_unixtime(unix_timestamp('2020-01-01', 'yyyy-mm-dd'));
```

