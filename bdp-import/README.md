# bdp-import  
## 部署流程
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

4.在hive中创建数据仓库的tmp层，即数据库tmp

```sql
drop database if exists tmp cascade;
create database if not exists tmp location '/data/tmp';
```
## sqoop
指定按照哪个列进行切分，Sqoop采集作业为多个Map组成的MapReduce作业，么个Map作业抽取一定区间内的数据，这时候需要我们告诉Sqoop按照哪个列划分Map作业抽取的区间，例如，我们这里设定的是ID列，则Sqoop在启动作业前会基于where条件查出目标数据集中最小和最大的ID值，然后基于--num-mappers设定的Map作业数量，均等的划分出每一个Map作业抽取ID的区间，然后并行的去抽取。
- #### 在hive中建表

```shell
sqoop create-hive-table \
-D mepred.job.name="$jobname" \
# 源数据库JDBC配置信息
--connect '${bdp.metric.jdbc.url}' \
--username '${bdp.metric.jdbc.user}' \
--password '${bdp.metric.jdbc.password}' \
# 源数据库表名
--table "$srcTable" \
# 目标表名
--hive-table "$sinkTable" \
# 覆盖现有hive表
--hive-overwrite
```

- #### 向hive表中导入数据

```sh
sqoop import \
-D mapred.job.name="${jobname}" \
-srcTable "$srcTable" \
-sinkTable "$sinkTable" \
--connect '${bdp.metric.jdbc.url}' \
--username '${bdp.metric.jdbc.user}' \
--password '${bdp.metric.jdbc.password}' \
--table "$srcTable" \
--where "timestamp between '$startTime' and '$endTime'" \
--split-by "$splitColumn" \
--hive-import \
--hive-overwrite \
--hive-table "$sinkTable" \
--target-dir "$sinkTablePath" \  # 目标表的HDFS路径
--outdit "/tmp" \   # 生成代码的存放目录，Sqoop会在执行作业期间生成一些代码文件，可以放在/tmp目录下
--delete-target-dir # 如果目标表的HDFS目录已存在则直接删除
```

## 遇到的问题  
- #### Could not create connection to database server.   
  ​    sqoop无法连接MySQL，mysql的版本是8.0.12，而sqoop lib目录下只有mysql-connector-java-5.1.27-bin.jar，将mysql-connector-java-8.0.12.jar放到lib目录下即可。

- #### ERROR tool.CreateHiveTableTool: Encountered IOException running create table job: java.io.IOException: Hive exited with status 1    

  在使用 sqoop create-hive-table 在hive中创建和mysql相同的表结构是，出现了如上错误。

  注意往上看，会有一个其它错误和日志格式类似，不容易发现：

  ```
  FAILED: Execution Error, return code 1 from org.apache.hadoop.hive.ql.exec.DDLTask. MetaException(message:javax.jdo.JDOFatalInternalException: Cannot add `CDS`.`CD_ID` as referenced FK column for `SERDES`，
  ```

  可以看出是元数据的问题，删除mySQL中的metastore后，错误变为：

  ```
  FAILED: Execution Error, return code 1 from org.apache.hadoop.hive.ql.exec.DDLTask. MetaException(message:javax.jdo.JDOFatalInternalException: Cannot add `SDS`.`SD_ID` as referenced FK column for `DBS`
  ```

  查看hive元数据所在的MySQL发现，有另一个hive的metastore，删除这个metastore即可解决；因为hive扫描的是整个MySQL，而不只是元数据库。

         https://blog.csdn.net/ciqingloveless/article/details/94666000   

- ####  The connection property 'zeroDateTimeBehavior' only accepts values of the form: 'exception', 'round' or 'convertToNull'. The value 'CONVERT_TO_NULL' is not in this set.   
  sqoop 使用import将MySQL的数据导入hive是出现如上错误。   

  解决方法:由于MySQL的版本是8.0.12，将mysql-connector-java-8.0.12.jar放入sqoop的lib目录下，并在--connect的jdbc url后面加上
  
  ```
  zeroDateTimeBehavior=CONVERT_TO_NULL
  ```
  
  