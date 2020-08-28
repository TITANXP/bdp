# bdp-workflow  

## 1. 部署项目  

### 1.1 修改时区  

**修改系统时区**：  

```shell
cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
```

**修改Oozie时区**  

我们进入oozie配置页面，在 ***oozie-site.xml\*** 的 ***Oozie Server\*** 高级配置代码段（安全阀）中添加：
![image-20200823230158214](C:\Users\LiQi\AppData\Roaming\Typora\typora-user-images\image-20200823230158214.png)

**oozie-web控制台配置**  ：

![image-20200823233203910](https://raw.githubusercontent.com/TITANXP/pic/master/img/image-20200823233203910.png)

**修改hue时区**：  

![image-20200823232431264](https://raw.githubusercontent.com/TITANXP/pic/master/img/image-20200823232431264.png)


### 1.2 先清除现有表的数据  

```shell
bdp-dwh-1.0/bin/bdp-dwh.sh truncate-all
```
### 1.3 清除所有的done-flags文件  

   当需要重跑某一天的工作流时，必须要清理当天的done-flags文件，否则作业间的依赖将被打乱，执行过程就会出错。   

```shell
hdfs dfs -rm -r /user/root/done-flags
```
### 1.4 提交所有工作流  

```shell
bdp-worflow-1.0/bin/bdp-workflow.sh submit-all 2018-09-02T00:00+0800 2018-09-03T00:00+0800 
```
   作业提交成功之后，可以通过Hue的管理页面来监控工作流的执行情况。
## 2 需求与概要设计  

   在批处理这条线上，经历了数据采集和数据仓库建设两个阶段，对应的两个子项目是bdp-import，bdp-dwh。由于后者已经继承了前者的全部工作，所以所有的作业都集中在bdp-dwh项目，所有作业接口在bin目录下的shell文件中：  

```
dmt-infra-metric.sh
dmt-master-data.sh
dwh-bdp-master.sh
dwh-bdp-metric.sh
src-bdp-master.sh
src-bdp-metric.sh
```

## 3. 工作流的组织策略  

   通常情况下，当项目进入工作流开发阶段时，所有作业（bdp-dwh的shell文件中暴露出的各种接口）都已基本就绪了。。在设计工作流时，首先要考虑的是依据什么样的策略将这些作业组织在一起。  

### 3.1 从业务角度

   如果从业务的角度切入，我们可以根据业务梳理出若干工作流，然后将作业划分到对应的工作流里，这似乎总是对的，但实际的情况并不会这么简单。   

   例如，某个作业（如某张公共维度表的构建）的结果会被多个工作流使用，则它们的描述文件中都会把这个作业涵盖进去，那么这项作业会在多个工作流中被声明多次，从编码的角度上看这是一种代码冗余，如果这个作业需要改动，开发人员要进行多处修改。另外，对于重复声明的同一个作业，工作流引擎必须确保这个作业在同一个周期内只运行一次，这又会涉及到复杂的依赖管理，配置起来并不简单。  

   还有一种情况，某些数据表可能并不会被现在的上层业务用到，但是从构建数仓体系的角度出发，这些表也都需要被相关作业处理，如果作业是以业务为向导组织的，就很难把它们划归到合适的工作流里。  

   以项目为例，DMT层中各类主题就是以业务导向来划分的，如果按照数据主题从上到下垂直切分，将一个数据主题在DMT层上的表及其依赖的DWH层和SRC层的表的构建作业划分到一起，会得到切分方案，如下图：  

![image-20200821165146470](https://raw.githubusercontent.com/TITANXP/pic/master/img/image-20200821165146470.png)

### 3.2 按数据源  

 图中工作流A涵盖Action 1、2、5、6、9，工作流B涵盖Action 2、3、6、7、10，一个Action可以理解为一张表的构建作业（bdp-dwh中构建某张数据表的命令行）。这种以业务为导向的作业切分方式会从最上层的数据主题开始向下倒退，逐一囊括所有依赖的表，当主题层的某项数据出错时，可以沿着工作流逐一排查各个节点。但是这一方案也有明显的短板，就是Action 2 和 6 被两个工作流重复包含，而Action 4 和 8 又没有被任何工作流包含。  

   除了业务角度，还有一个切入角度，就是根据数仓分层处理，即SRC层先处理，然后是DWH层，最后是DMT层。分层处理的好处是，简化了作业间的依赖，易于配置和管理，缺点是很难从业务角度梳理出作业间的关系。因为作业是按非业务关系组织的，当某个业务功能出现问题时很难从工作流这个层面收窄问题”区间“。  

   以项目为例，总计有3个大的分层，如果按层组织作业，则如下图：  

   ![image-20200821180146154](C:\Users\LiQi\AppData\Roaming\Typora\typora-user-images\image-20200821180146154.png)

   工作流A、B、C分别负责SRC层、DWH层、DMT层的构建，执行顺序为A→B→C。显然这一方案不会有任何重叠或遗漏的Action，并且会大大简化作业间的依赖，因为大多数依赖都是上层数据表对下层数据表的依赖，使用自下而上的分层构建方式可以自然地化解这种依赖。但是这样的切分方式也有不理想的地方，它无法体现业务的边界，这其实是很糟糕的，同一个工作流内的作业应该具有很强的业务内聚性。说直白些就是它们是因为同一个目标才被组织到一起的，如果按照业务边界进行切分能揭示它们之间在业务上的关联关系，便于错误排查与数据核对，而分层构建将丢失这些优势。  

### 3.3 将两种策略结合

   那么有没有更好的方案呢？可以将上面的两个方案融合：  

   ![image-20200821192109588](https://raw.githubusercontent.com/TITANXP/pic/master/img/image-20200821192109588.png)

   这一方案的基本思想是，综合使用按业务（数据主题）和按数据源两种切分方式，在上层（DMT层）按数据主题组织，在下层（SRC、DWH）按数据源组织。在宏观构建顺序上先执行数据源层的作业，再执行数据主题层的作业。其中数据源层的作业会按数据源进行二次切分，每个数据源对应一个工作流，该工作流会涵盖对应数据源上所有数据表从数据采集到SRC层再到DWH层的构建工作，因为SRC和DWH两层本身就是面向数据源设计的，针对每一张数据表在这两层上都有对应的作业，所以以数据表为单位，将TMP→SRC→DWH层的作业组织到一个工作流是面向数据源构建工作流的主要策略。当数据源层的作业执行完成后，也就意味着数据主题层依赖的下层作业都已就绪，就可以启动数据主题相关的作业了，数据主题层的作业往往有很多同层内的横向表间依赖，如，构建事实表之前要确保它所依赖的所有维度表都已构建完成。

   如上图，DWH和SRC层被界定为面向数据源的分层，因此针对数据源1和2各有两个独立的工作流A和B，在数据主题层上，针对数据主题 1 和 2 有两个工作流 C 和 D ，宏观上的工作流执行顺序是A → B → C →  D， 其中A、B之间无依赖，是可以并行的。对于工作流A内部的作业执行顺序，如果Action 1 和 5 对应一张数据表在SRC和DWH两层上的处理，Action 2 和 6 是另一张表在SRC和DWH两层上的处理，那么其执行顺序是Action 1 → Action 5， Acion 2 → Action 6， 这两组之间没有依赖，，也可以并行。   

## 4. 实现工作流  

   Oozie针对作业调度抽象出了三个重要的概念：workflow、coordinator、bundle。workflow是对一个工作流的具体描述，一个工作流由多个action（action是一个可独立完成的操作，执行一条命令、运行一条SQL都可以视为一个action）组成，action之间可以串行也可以并行，工作流包含分支（Fork/Join）及嵌套子工作流（sub-workflow）。workflow只定义了“做什么”，并没有描述“什么时间做”，几乎所有作业都需要周期性的执行，所以还需要一套作业排期机制，这个工作在Oozie中是由coordinator负责的（类似于Linux上的cron），而coordinator也支持corn的周期描述语法，但是coordinator区别于一般的scheduling工具的地方在于，除一般的时间触发条件外，它还支持基于“事件”的触发条件，这一机制非常重要，通过事件可以有效地协调和管理作业间的依赖。  

   当“做什么”和“什么时间做”都描述清楚之后，理论上作业调度的开发基本也就完成了，但是有时我们还需在conndinator的基础上再组装一下，形成一个完整的数据流（Data Pipeline），这一动作是通过bundle完成的。一个bundle通常会包含多个coordinator，这些coordinator在更大尺度的业务流程上有上下游的依赖关系。bundle代表的Data Pipeline和workflow的区别在于，一般Data Pipeline会跨越多个系统，从数据源采集开始，到数仓，最后到达数据展示的终端，而workflow的范围要小些，通常描述一个系统内的数据流转。  

   项目中涉及了4个工作流：build-ds-bdp-master、build-ds-bdp-metric、build-sj-master-data、build-sj-infra-metric，定义这四个工作流的文件分别是：  

```
lib/ds-bdp-master-daily-build/workflow.xml
lib/ds-bdp-metric-daily-build/workflow.xml
lib/sj-infra-metric-daily-build/workflow.xml
lib/sj-master-data-daily-build/workflow.xml
```

## 5. 实现coordinator  

   工作流实现后，进入coordinator的开发。coordinator是用来定义工作流执行周期的，几乎所有工作流都需要周期性的触发执行，最常见的是daily作业，每天0点过后，过去一天的业务数据都已经沉淀到业务数据库，这时就可以启动数据采集和相关处理任务了，类似的还有weekly、monthly、yearly的周期作业。  

```xml
<coordinator-app name="ds-bdp-master-daily-build" frequency="${coord:days(1)}"
                 start="${startTime}" end="${endTime}" timezone="Asia/Shanghai"
                 xmlns="uri:oozie:coordinator:0.1">
    <action>
        <workflow>
            <!-- 指明工作流文件的位置，以便coordinator能找到并加载它。-->
            <app-path>${app.hdfs.home}/lib/ds-bdp-master-daily-build/workflow.xml</app-path>
            <!-- coordinator生成参数，并传递给workflow -->
            <configuration>
                <property>
                    <name>START_TIME</name>
                    <!-- 以触发时刻为基准，向前偏移一天 -->
                    <!-- 例如，作业在2018-09-02T00:00+0800触发，解析出的START_TIME的值将是2018-09-01T00:00+0800 -->
                    <value>${coord:dateOffset(coord:nominalTime(), -1, 'DAY')}</value>
                </property>
                <property>
                    <name>END_TIME</name>
                    <!-- 直接取触发时间，无偏移 -->
                    <value>${coord:dateOffset(coord:nominalTime(), 0, 'DAY')}</value>
                </property>
                <property>
                    <name>DATE_FLAG</name>
                    <!-- 和START_TIME保持一致 -->
                    <!-- 但是考虑到这个时间值会作为文件夹存在于HDFS上，为了避免出现一些无法处理的特殊时间字符，所以对格式进行了处理 -->
                    <value>${coord:formatTime(coord:dateOffset(coord:nominalTime(), -1, 'DAY'), "yyyy-MM-dd")}</value>
                </property>
            </configuration>
        </workflow>
    </action>
</coordinator-app>
```
**\<coordinator-app/>中的各个属性：**  

- name：这个coordinator的名称

- frequency：这个coordinator的触发频率
   ${coord:days(1)}是oozie提供的一种表达式语言，实质是oozie提供的内置函数，除了内置函数外，用户也可以编写自定义函数。  
   ${coord:days(int n)}是一个常用的时间周期表达式，它会返回一个以n天为周期的0点时间。例如，${coord:days(1)}意味着这个coordinator每天都会触发一次，触发时间是每天的0点；${coord:days(2)}就是每两天触发一次，触发事件是每隔一天的0点。既然是周期性的，就会涉及起止时间，即在多长实际范围内周期性的触发工作流，这个时间范围由start=“${startTime}"和end="{endTime}"这两个属性定义，
   
- start, end：每次重新部署或重启coordinator时，起止时间都是不一样的，所以不会在配置文件中给出固定值，而是在每次启动coordinator时在命令行里设定，例如：
  
```shell
   bdp-workflow-1.0/bin/bdp-workflow.sh submit ds-bdp-master-daily-build 2018-09-02T00:00+0800 2018-09-03T00:00+0800
```

   在这个命令行中2018-09-02T00:00+0800将赋值给参数startTime，2018-09-03T00:00+0800将赋值给参数endTIme，这个起止时间限制了工作流只执行一次，即2018-09-02零点那次。一般情况下，coordinator设定的起止时间跨度是非常大的，如10年或更长时间，目的是让工作流持续的运转下去。

- timezone="Asia/Shanghai"：它并不是指定Oozie或这个coordinator遵循哪个时区来解析时间，而是与夏令时有关。Oozie引擎会使用全局唯一的时区设置，其设置项是Oozie配置文件oozie-default.xml中的oozie.processing.timezone。该设置项的默认值是UTC，也就是说Oozie默认使用的是UTC时间，通常我们需要将其修改为本地时区，以中国时区为例，配置如下：

```xml
   <property>
       <name>oozie.processing.timezone</name>
       <value>GMT+0800</value>
   </property>
```

   修改时区配置并重启后，Oozie将按照配置的时区格式检查时间相关的参数，如coordinator中的\${startTime}和\${endTIme}都是东八区的时间格式，即yyyy-MM-ddTHH:mm+0800，否则Oozie会拒绝接收。那么coordinator中的timezone是做什么的呢？原来世界上的一些国家会使用一种夏令时（Daylight Saving Time：DST）的计时方案，夏令时会在天亮的早的夏季 人为的将时间调快一小时，这会导致在夏令时进行时间调整的当天工作流的执行时间发生错误。例如，对于一个daily作业，在夏令时调整时，距上次执行的时间差不再是24小时，而是23h或25h，通过设定timezone，Oozie会根据设定区域是否使用夏令时自动调整，规避可能出现的错误。  

**\<action>**    

   coordinator的\<action/>部分包裹了一个\<workflow/>，这时coordinator的核心逻辑。Oozie coordinator的XML-schema中明确标注：一个coordinator有且只能有一个action，一个action有且只能有一个workflow，所以说coordinator和workflow之间是一一映射的，是为单一工作流指定作业排期的专职组件。     

   通常情况下，设置给coordinator的START_TIME和END_TIME差值是非常大的，往往是数年时间，因为正常来说工作流会无限期的周期性运转下去，上面的命令行示例中时间差只有一天，是出于测试目的。

## 6. 部署与提交工作流

   完成workflow和coordinator的实现工作之后，就可以部署并提交工作流了，Oozie要求工作流的所有文件都要部署在HDFS上，完成此操作的脚本如下：  


```shell
init(){
  # 如果目录已经存在，则删除
  hdfs dfs -test -d ${BDP_WORKFLOW_HDFS_HOME}&&\
  hdfs dfs -rm -r -f -skipTrash ${BDP_WORKFLOW_HDFS_HOME}
  # 创建目录，更改用户权限
  hdfs dfs -mkdir -p ${BDP_WORKFLOW_HDFS_HOME} &&\
  hdfs dfs -chown ${USER_NAME} ${BDP_WORKFLOW_HDFS_HOME}
  # 上传项目文件
  hdfs dfs -put ${BDP_WORKFLOW_LOCAL_HOME}/* ${BDP_WORKFLOW_HDFS_HOME}/
  # 创建done-flags文件夹
  hdfs dfs -mkdir -p ${BDP_WORKFLOW_DONE_FLAGS_HOME}
```
   init函数只在初次部署时执行一次就可以了，一旦工程文件上传到HDFS，就可以提交作业了。Oozie提供了专门的命令行工具来提交并查看作业运行状态。  

   在项目中，使用了oozie job-submit命令来提交作业，，使用它的是bdp-workflow.sh  

```shell
submit() {
  COORD_NAME=$1
  START_TIME=$(date -d "$2" +"%FT%H:%M%z")
  END_TIME=$(date -d "$3" +"%FT%H:%M%z")
  echo "Accept start time : [ ${START_TIME} ]"
  echo "Accept end time : [ ${END_TIME} ]"

  OOZIE_MSG=$(oozie job -submit \
  -Doozie.coord.application.path="${BDP_WORKFLOW_HDFS_HOME}/lib/${COORD_NAME}" \
  -DstartTime="${START_TIME}" \
  -DendTime="${END_TIME}")
  if [ "$?" = "0" ]
  then
    echo "The Coordinator ID: [ ${OOZIE_MSG/job: /} ]"
    echo "submitting job succeded!"
  else
    echo "${OOZIE_MSG}"
    echo "submitting job failed!"
  fi 
}
```

submit函数接收三个参数：

- COORD_NAME：coordinator的名称，即要启动哪个coordinator；
- START_TIME：开始时间，即coordinator配置文件中声明的startTime参数；
- END_TIME：结束时间，即coordinator配置文件中声明的endTime参数。

上述三个参数都以属性的形式传递给了Oozie，Oozie会将这些属性解析出来，以参数的形式传递给coordinator  

```xml
<coordinator-app name="ds-bdp-master-daily-build" frequency="${coord:days(1)}"
                 start="${startTime}" end="${endTime}" timezone="Asia/Shanghai"
                 xmlns="uri:oozie:coordinator:0.1">
```

这里的${startTime}和${endTime}就是在Oozie中声明的参数，在使用Oozie命令行提交作业时，我们以-DstartTime=xxx和-DendTime=xxx的形式传递参数，对应的值赋值给了coordinator配置文件中声明的两个参数。而对于COORD_NAME参数而言，他被用到了oozie.coord.application.path路径上，这是提交所有coordinator必须指定的一个参数，它会指明coordinator在HDFS上的存放路径，Oozie引擎会去这个路径下寻找coordinator.xml。

## 7. 作业间的依赖管理  

### 7.1   依赖管理的分类

   在三种工作流组织策略中，项目使用的是第三种，虽然这种策略可以大大简化作业间的依赖，但规避所有的作业依赖是不可能的，工作流必须有一套针对作业依赖的管理机制。以项目为例，主题相关的作业一定会依赖数据源相关的作业，只有当被依赖的数据源作业执行成功之后，主题作业才开始执行。  

   针对作业依赖，不同的工作流会使用不同的机制进行应对，总体上分为两大类：  

   - **基于作业的依赖** 
      一个作业B要求某些条件必须满足才能执行，而这些条件恰好是作业A的1范畴，也就是说当A执行成功之后，B所需要的条件就满足了，即作业B依赖于作业A，在配置时，我们要显式的指出B对A的依赖。这种配置常出现在Azkaban中，上述逻辑使用Azkaban来配置可以描述为：  

```yaml
nodes：
	- name: JobA
	  type: command
	  config:
	  	command: bash ./write_to_props.sh
	  	
	- name: JobB
	  type: command
	  dependsOn:
	  	- JobA
	  config:
	  	command: echo"This is JobB"
```
   - **基于事件（数据）的依赖**

   每一个作业可以在配置上声明执行它所需要的一些事件，只有这些事件发生后才会触发当前作业的执行，这些事件都是其他作业在执行期间或执行结束后产生的，所以这也是一种间接处理作业依赖的方式，Oozie使用的就是这种方式。在Oozie的配置中经常会声明一系列的input-events和output-event，这些事件都是用来描述某个或某类文件是否已经就绪的。

### 7.2 Oozie的作业依赖管理  

   Oozie是如何基于事件（数据）进行依赖管理的呢？  

   一个基本的逻辑是，如果我们能找到一种方法可以准确的描述什么是数据 并能持续的监控所需数据是否已经就绪，就可以了。要准确的描述依赖的数据要讲清楚两点：  

   - 整个数据集存放在哪里
   - 当前运行周期所依赖的是数据集的哪一部分（如哪一天）数据。

   针对第一个问题Oozie引入了datasets的概念，针对第二点引入了input-events的概念。  

   如下示例，某个daily作业要处理昨天的某类数据，但是它需要用到my_table表中昨天的数据，这样，当前的作业就对my_table表的数据产生了依赖。假设my_tablbe的HDFS存放路径是data/mu_table，它按日期进行了分区，每天会在数据表对应的文件夹下创建一个分区子文件夹，以2018-09-01这一天的数据为例，他们会存放在data/my_table/2018-08-01/这个路径下。  

   在这一场景下，这个daily作业可以通过如下配置来声明它对my_tanble表的依赖 ：  

```xml
<datasets>
    <dataset name="my_table" frequency="${coord:days(1)}"
             initial-instance="2018-09-02T00:00+0800"
             timezone="Asia/Shanghai">
        <uri-template>/data/my_table/${YEAR}-${MONTH}-${DAY}/</uri-template>
    </dataset>
    ...
</datasets>

<input-events>
    <data-in name="my_table_input" dataset="my_table">
        <instance>${coord:current(-1)}</instance>
    </data-in>
    ...
</input-events>
```

   在\<dataset>的配置中，Oozie使用了URI（统一资源标识符）来描述数据集，在绝大多数情况下，它都是一个HDFS路径。上面例子中的/data/my_table/${YEAR}-${MONTH}-${DAY}就是一个HDFS路径模板，很显然，这不是一个单一值，而是使用模式描述的一组数据集，这些数据的总和就是my_table表的全部数据，所以通过\<dataset>解决了第一个问题，告诉了Oozie数据存放在哪里。  
   然后是input-events的配置，使用EL表达式${coord:current(-1)}注明现在需要的是当前日期减1天的时间。假设作业的执行时间是2018-09-02，则表达式解析出的值就是2018-09-01，而解析出的dataset就是/data/my_table/2018-09-01/，也就是说，对于当前这个作业，只有当/data/my_table/2018-09-01/路径下的数据全部就绪了作业才会执行。  

   Oozie如何判定/data/my_table/2018-09-01/路径下的数据已经全部就绪？这个文件夹下可能有多个文件，文件名和文件数量都是不确定的即便只有一个文件，在大数据场景下，这个文件也可能会因为过大而需要很长时间才能完成写入，所以单纯靠检查数据文件本身是很难判定数据是否就绪的，那怎么办呢？Oozie给出了一个很聪明的做法，\<dataset>中的一个非空配置项\<done-flag>，它配置的是一个文件名，如果不显式的进行配置，就会使用默认值\_SUCCESS，那么\<done-flag>是怎么判定数据就绪的呢？这需要作业调度方（Oozie）和数据提供方之间做一个约定，当数据完成写入时，由写入方生成一个标记文件标记写入已完成，只有当作业调度方（Oozie）检测到了这个文件才会认定数据已经就绪。（之所以它的默认值是\_SUCCESS，是因为早期Hadoop的MR作业完成时默认会生成一个名为\_SUCCESS的标记文件，用来表示MR作业已经结束，也就意味着当前目录下的数据已经就绪了，Oozie沿用了这一做法，也包括这个标记文件的默认名，方便与MR作业集成）  

### 7.3 实现  

   项目中的作业依赖管理有两个现实的问题需要解决：  

   - 现在的Spark作业不会像以前MR作业那样自动生成\_SUCCESS文件，我们需要自行生成；
   - 并非所有的数据表都有分区，即使有，也不一定按时间进行分区，所以依靠分区下的\_SUCCESS文件有时是无法帮助工作流引擎判定某周期上依赖的数据是否已经就绪的。  

   针对这两个问题，项目中的解决方案是：  
   - 由Oozie负责生成标记文件。本质上生成标记文件是供作业调度使用的，所以应该由工作流引擎负责。Oozie的HDFS操作中刚好有一个touchz操作，对应的是HDFS上的touchz命令，这个命令专门用来在HDFS上生成一个空文件。所以，当一个被其他作业依赖的数据表完成本周期内的构建时，我们会在工作流配置中加入一个touchz，用来生成一个\_SUCCESS文件。
   
   - 将标记文件\_SUCCESS从原始数据存放的目录中剥离出来，转到一个专门的目录下存放，并让目录结构与作业运行周期相对应，这样就不存在周期生成的标记文件和原始数据存放路径无法一一对应的问题了，从而可以更加自由的使用标记文件。  

   项目bdp-workflow中总计有4条工作流，它们的执行顺序如下：  

![image-20200823101127759](https://raw.githubusercontent.com/TITANXP/pic/master/img/image-20200823101127759.png)

   形成这种执行顺序的原因是，sh-infra-metric-daily-build作业需要用到sj-master-data-daily-build构建的各类主数据作为维度进行参照，同时要用到ds-bdp-metric-daily-build作业收集的Metric数据表构建事实表,而sj-master-data-daily-build中的各类维度数据都来自ds-bdp-master-daily-build从数据源收集的原始数据。  

具体的依赖关系如下：  

![image-20200823103307770](https://raw.githubusercontent.com/TITANXP/pic/master/img/image-20200823103307770.png)

   

![image-20200823104032020](https://raw.githubusercontent.com/TITANXP/pic/master/img/image-20200823104032020.png)

   这些被依赖的数据会由负责构建这些数据的作业在执行完毕时主动”raise event“来告知所有的依赖方，一旦依赖数据已经就绪（也就是所有event已经发生），作业就启动了。  

   我们来看一下Oozie中是如何在配置文件中声明一个作业所依赖的event，又是如何”raise“一个event的。以sh-master-data-daily-build作业为例，在它的coordinator配置中声明了4个dataset及4个input-events，它们是用来声明当前工作流所依赖的DWH层的app、server、metric-index、metric-threshold 4类源数据的：  

```xml
<coordinator-app name="sj-master-data-daily-build" frequency="${coord:days(1)}"
                 start="${startTime}" end="${endTime}" timezone="Asia/Shanghai"
                 xmlns="uri:oozie:coordinator:0.1">

    <!-- 数据集 -->
    <datasets>
        <dataset name="ds-bdp-master-app" frequency="${coord:days(1)}"
                 initial-instance="2018-01-02T00:00+0800" timezone="Asia/Shanghai">
            <uri-template>${app.hdfs.user.home}/done-flags/${YEAR}-${MONTH}-${DAY}/ds-bdp-master/app</uri-template>
        </dataset>
        <dataset name="ds-bdp-mster-server" frequency="${coord:days(1)}"
                 initial-instance="2018-01-02T00:00+0800" timezone="Asia/Shanghai">
            <uri-template>${app.hdfs.user.home}/done-flags/${YEAR}-${MONTH}-${DAY}/ds-bdp-master/server</uri-template>
        </dataset>
        <dataset name="ds-bdp-mster-metric-index" frequency="${coord:days(1)}"
                 initial-instance="2018-01-02T00:00+0800" timezone="Asia/Shanghai">
            <uri-template>${app.hdfs.user.home}/done-flags/${YEAR}-${MONTH}-${DAY}/ds-bdp-master/metric-index</uri-template>
        </dataset>
        <dataset name="ds-bdp-mster-metric-threshold" frequency="${coord:days(1)}"
                 initial-instance="2018-01-02T00:00+0800" timezone="Asia/Shanghai">
            <uri-template>${app.hdfs.user.home}/done-flags/${YEAR}-${MONTH}-${DAY}/ds-bdp-master/metric-threshold</uri-template>
        </dataset>
    </datasets>

    <!-- 依赖的事件 -->
    <input-events>
        <data-in name="ds-bdp-master-app-input" dataset="ds-bdp-master-app">
            <instance>${coord:current(-1)}</instance>
        </data-in>
        <data-in name="ds-bdp-master-server-input" dataset="ds-bdp-master-server">
            <instance>${coord:current(-1)}</instance>
        </data-in>
        <data-in name="ds-bdp-master-metric-index-input" dataset="ds-bdp-master-metric-index">
            <instance>${coord:current(-1)}</instance>
        </data-in>
        <data-in name="ds-bdp-master-metric-threshold-input" dataset="ds-bdp-master-metric-threshold">
            <instance>${coord:current(-1)}</instance>
        </data-in>
    </input-events>

    <action>
        <workflow>
            <app-path>${app.hdfs.home}/lib/sj-master-data-daily-build/workflow.xml</app-path>
            <configuration>
                <property>
                    <name>START_TIME</name>
                    <value>${coord:dateOffset(coord:nominalTime(), -1, 'DAY')}</value>
                </property>
                <property>
                    <name>END_TIME</name>
                    <value>${coord:dateOffset(coord:nominalTime(), 0, 'DAY')}</value>
                </property>
                <property>
                    <name>DATE_FLAG</name>
                    <value>${coord:formatTime(coord:dateOffset(coord:nominalTime(), -1, 'DAY'), "yyyy-MM-dd")}</value>
                </property>
            </configuration>
        </workflow>
    </action>

</coordinator-app>
```

   将标记文件\_SUCCESS从原始数据存放的目录中剥离出来，转到一个专门的目录下存放，并让目录的结构与作业运行周期对应，这里配置的uri-template正是独立存放标记文件的路径模板，这个目录是精心设计过的，它的各组成部分如下图：  

   ![image-20200823181914646](https://raw.githubusercontent.com/TITANXP/pic/master/img/image-20200823181914646.png)

   关于这个目录结构设计的补充说明：  

   - 标记文件使用专职目录done-flags统一存放，避免与存储数据的目录混用；
   - 时间周期子目录排在数据源子目录的上一级，便于对某一天的作业运行状况进行排查；
   - app是一个文件夹，用于存放app作业生成的\_SUCCESS文件。

至此，sj-master-data-daily-build对于dwh层数据的依赖已经描述清楚了，剩下的是由负责构建dwh层数据的作业方标记数据就绪，这一操作发生在负责构建它的工作流ds-bdp-master-daily-build中，以dwh层app为例，具体位于lib/ds-bdp-master-daily-build/sub-workflow/app.xml中：  

```xml
<workflow-app name="build-ds-bdp-master :: app :: data source -> tmp -> src -> dwh" xmlns="uri:oozie:workflow:0.5">

<!-- 导入bdp_master.app 的 tmp -> src -> dwh 层 数据 -->

    <start to="build-src-app"/>

    <!-- app: tmp -> src -->
    <action name="build-src-app">
        <ssh xmlns="uri:oozie:ssh-action:0.1">
            <host>${bdp-dwh.ssh.host}</host>
            <command>${bdp-dwh.app.bin.home}/src-bdp-master.sh</command>
            <args>build-app</args>
            <args>${START_TIME}</args>
            <args>${END_TIME}</args>
            <capture-output/>
        </ssh>
        <ok to="build-dwh-app"/>
        <error to="kill"/>
    </action>

    <!-- app: dwh -->
    <action name="build-dwh-app">
        <ssh xmlns="uri:oozie:ssh-action:0.1">
            <host>${bdp-dwh.ssh.host}</host>
            <command>${bdp-dwh.app.bin.home}/dwh-bdp-master.sh</command>
            <args>build-app</args>
            <args>${START_TIME}</args>
            <args>${END_TIME}</args>
            <capture-output/>
        </ssh>
        <ok to="flag-done"/>
        <error to="kill"/>
    </action>

    <!-- 调用hdfs的文件操作命令 touchz，创建一个名为_SUCCESS的空文件 -->
    <action name="flag-done">
        <fs>
            <!-- DATE_FLAG是执行作业当天的日期, 所以每天的作业都会有唯一的FLAG -->
            <touchz path='hdfs://${cluster.namenode}${app.hdfs.user.home}/done-flags/${DATE_FLAG}/ds-bdp-master/app/_SUCCESS'/>
        </fs>
        <ok to="end"/>
        <error to="kill"/>
    </action>

    <kill name="kill">
        <message>Action failed, error message[${wf:errorMessage(wf:lastErrorNode())}]</message>
    </kill>

    <end name="end"/>

</workflow-app>
```

\<touchz>会在

'hdfs://${cluster.namenode}${app.hdfs.user.home}/done-flags/${DATE_FLAG}/ds-bdp-master/app/_SUCCESS'

下创建\_SUCCESS文件，而这个路径正是sj-master-data-daily-build配置中所声明的依赖的dwh层app数据的路径，至此整个控制闭环就完成了。

四条工作流的完整执行过程

   以2018-09-02的daily作业为例，在2018-09-02 00:00:00这一时刻，sj-infra-metric-daily-build、sj-master-data-daily-build、ds-bdp-master-daily-build、ds-bdp-metric-daily-build 4个作业会同时进入RUNNING状态，因为4个daily作业的coordinator配置的启动时间就是每日0点，实际的执行顺序就是按它们所依赖的数据有序推进的。

   由于sj-infra-metric-daily-build要同时依赖sj-master-data-daily-build生成的dmt.dim_app、dmt.dim_server、dmt.dim_metric_index、dmt.dim_metric_threshold 4张表的数据，以及ds-bdp-metric-daily-build生成的dwh.bdp_metric_metric表的数据，所以 sj-infra-metric-daily-build不会立即执行，而是等待这些表的数据就绪。

   此时的sj-master-data-daily-build 也不能执行，因为它在等待由ds-bdp-master-daily-build生成的dwh.bdp_master_app、dwh.bdp_master_server、dwh.bdp_master_metric_index、dwh.bdp_master_metric_threshold 4 张表的数据。

   没有数据依赖的是ds-bdp-master-daily-build 和 ds-bdp-metric-daily-build 这两个作业，它们会率先执行，当ds-bdp-master-daily-build 执行完成时，会生成4个标记文件，分别标记dwh.bdp_master_app、dwh.bdp_master_server、dwh.bdp_master_metric_index、dwh.bdp_master_metric_threshold 4张表的数据已经就绪， 此时sj-master-data-daily-build所需要的数据就都就绪了，所以它将进入执行阶段，当它执行完毕时又会生成4个标记文件，分别标记dmt.dim_app、dmt.dim_server、dmt.dim_metric_index、dmt.dim_metric_threshold 4 张表的数据已经就绪。

   另一方面，当 ds-bdp-metric-daily-build 执行完成时，会生成 1 个标记文件标记 dwh.bdp_metric_metric 的数据已就绪。

   这样 sj-infra-metric-daily-build 所依赖的 5 张表的数据都已经就绪，它将最后一个启动，在执行结束后也会生成 3 个标记文件，分别标记 dmt.fact_metric、dmt.sum_metric、dmt.wide_metric_avg 三张表的数据已经就绪，生成这三个标记文件主要是为了便于和更高层级（如App层）的作业进行对接，或者为其它下游系统对接工作流做准备。

   所以，在2018-09-02 作业执行期间会陆续生成 12 个SUCCESS文件，Oozie利用这些标记文件轻巧的维护和推进相互依赖的作业有序执行。

## 8 遇到的问题  

- ### Oozie web页面显示Oozie web console is disabled  

**原因**：  

缺少 ExtJS 2.2

**解决方法**：

```shell
wget http://archive.cloudera.com/gplextras/misc/ext-2.2.zip
unzip ext-2.2.zip -d /var/lib/oozie/
```

- ### bin/bdp-workflow.sh submit-all '2020-08-23T00:00+0800' '2021-09-03T00:00+0800' 报错  

```
Error: E1003 : E1003: Invalid coordinator application attributes, parameter [start] = [2020-08-23T00:00+0800] must be Date in UTC format (yyyy-MM-dd'T'HH:mm'Z'). Parsing error java.text.ParseException: Could not parse [2020-08-23T00:00+0800] using [yyyy-MM-dd'T'HH:mm'Z'] mask
```
**解决方法**：修改Oozie时区

- ### Oozie运行任务时报错   

```
org.apache.oozie.action.ActionExecutorException: AUTH_FAILED: Not able to perform operation [ssh -o PasswordAuthentication=no -o KbdInteractiveDevices=no -o StrictHostKeyChecking=no -o ConnectTimeout=20 root@192.168.170.171  mkdir -p oozie-oozi/0000054-200824103035502-oozie-oozi-W/build-src-metric--ssh/ ] | ErrorStream: Permission denied (publickey,gssapi-keyex,gssapi-with-mic,password).
```
**解决方法：**  
```shell
vi /etc/passwd
```
```shell
oozie:x:973:967:Oozie User:/var/lib/oozie:/bin/false
```
改为
```shell
oozie:x:973:967:Oozie User:/var/lib/oozie:/bin/bash 
```
生成秘钥：
```shell
su - oozie
ssh-keygen
```
```shell
su - root 
cat /var/lib/oozie/.ssh/id_rsa.pub >> /root/.ssh/authorized_keys
```
验证免密登录：
```shell
su - oozie 
ssh root@localhost
```

-  ### 执行创建\_SUCCESS文件时报错  

任务  

```xml
<fs xmlns="uri:oozie:workflow:0.5">
  <touchz path="hdfs://192.168.170.71/user/root/done-flags/2020-08-26/ds-bdp-master/app/_SUCCESS" />
  <name-node>192.168.170.71</name-node>
  <configuration />
</fs>
```

**错误1**：

```
org.apache.oozie.action.ActionExecutorException: FS001: Missing scheme in path [192.168.170.71]   
```

**原因**
workflow.xml中的namenode没有加前缀 hdfs://

```xml
    <global>
        <job-tracker>${cluster.resourcemanager}</job-tracker>
        <name-node>${cluster.namenode}</name-node>
    </global>
```

**错误2**：  

org.apache.oozie.action.ActionExecutorException: HadoopAccessorException: E0901: NameNode [192.168.170.71] not allowed, not in Oozie's whitelist. Allowed values are: [cm6:8020]

**原因**  
namenode要使用 主机名:端口（cm6:8020），而不是ip