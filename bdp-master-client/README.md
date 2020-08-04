# bdp-master-client   
## 1. 介绍   
   为了便于读取主数据，编写了主数据二点客户端组件bdp-master-client，该组件主要左两件事：
   - 通过Redis的Java客户端连接Redis数据库。
   - 实现所有主数据的实体类，从Redis中取出对应的主数据后，反序列化出对应的实体类供调用端使用。   

   bdp-master-client是以jar包依赖的形式添加到项目中，然后使用service包下的各类Service进行主数据的读取，这些Service都较为简单，大多数都是通过ID或name来获取对应实体对象的，除了因为项目业务较为简单，主要的原因是Redis自身没有二级索引机制，除了以ID作为Primary Key的查询，所有基于其他属性或属性组合的查询都需要手动建立二级索引。在使用Redis时，要尽可能简化对象的查询条件。   

   实际用到bdp-master-client的是bdp-stream子项目。   

## 2.部署流程   

## 3.遇到的问题   

- ### Exception in thread "main" java.util.NoSuchElementException: Either.right.value on Left   
   circe解析JSON失败
   Scala中有Left,Right两个类，继承于Either,主要用途是表示两个可能不同的类型（它们之间没有交集）,Left主要是表示Failure,Right表示有,跟Some类型有点类似。
查看错误信息
    ```scala
    println(decode[Server](json).left.e)
    ```
     DecodingFailure(Attempt to decode value on failed cursor, List(DownField(amberThershold), DownField(cpu.usage), DownField(metricThresholds)))   

     可以看到有问题的三个key metricThresholds -> cpu.usage -> amberThershold是嵌套关系，amberThershold是最里层的key，也就是说解析amberThershold时出了问题，查看对应的样例类MetricThreshold发现变量名和Redis中存储的JSON中的不一样，改正即可。

    ```json
    {
        "appId":1,
        "cpuCores":16,
        "creationTime":1535760000000,
        "hostname":"svr1001",
        "id":1,
        "memory":64000,
        "metricThresholds":{
            "cpu.usage":{
                "amberThreshold":80,
                "creationTime":1535760000000,
                "redThreshold":90,
                "updateTime":1535760000000
            },
            "mem.used":{
                "amberThreshold":5120,
                "creationTime":1535760000000,
                "redThreshold":5760,
                "updateTime":1535760000000
            }
        },
        "updateTime":1535760000000
    }
    ```

