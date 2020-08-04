# bdp-master-client   
## 1. 介绍   
   为了便于读取主数据，编写了主数据二点客户端组件bdp-master-client，该组件主要左两件事：
   - 通过Redis的Java客户端连接Redis数据库。
   - 实现所有主数据的实体类，从Redis中取出对应的主数据后，反序列化出对应的实体类供调用端使用。   

   bdp-master-client是以jar包依赖的形式添加到项目中，然后使用service包下的各类Service进行主数据的读取，这些Service都较为简单，大多数都是通过ID或name来获取对应实体对象的，除了因为项目业务较为简单，主要的原因是Redis自身没有二级索引机制，除了以ID作为Primary Key的查询，所有基于其他属性或属性组合的查询都需要手动建立二级索引。在使用Redis时，要尽可能简化对象的查询条件。   

   实际用到bdp-master-client的是bdp-stream子项目。