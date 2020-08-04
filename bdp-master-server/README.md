# bdp-master-server
## 1. 部署项目    
### (1) bdp_master建表  
  - 如果是第一次运行项目，需要进行建表，在application.properties中更改如下配置，hiberbate可以自动建表。  
```properties
spring.jpa.hibernate.ddl-auto=create   
```
  （如果建表有问题，注意看控制台的ERROR）
  - 如果要关闭启动项目时建表，设置为：  
```properties 
spring.jpa.hibernate.ddl-auto=none
```
### (2) 构建项目   
参数控制使用standalone.properties或cluster.properties
```shell
build.bat standalone | cluster
```


### (3) 将项目部署到远程服务器
```shell
deploy.bat
```

   最小化增量部署：不重新部署项目依赖的第三方jar包

```shell
deploy.bat -delta
```



## 2. 原型设计
  此项目为主数据管理系统，负责App、Server、MetricThreshold、MetricIndex、AlertIndex的增删改查。   

  bdp-master-server是一个Java Web应用，使用Spring Boot，存储层面对接一个MySQL数据库bdp_master及一个Redis，数据双写，对外提供Restful API。引入Redis是为流计算提供高性能的主数据读取能力。整个项目是一个典型的Java Web应用，架构上分为Controller、Service、Repository三层，各种实体类对应于各种主数据，使用Hibernate进行ORM处理   

## 3. 遇到的问题  
- ###  Could not resolve placeholder 'spring.redis.host' in value "${spring.redis.host}" 
   这是因为找不到application.properties，可以将application.properties移动到resources文件夹下，或将resources/conf改为resources/config   
   如果要使用IDEA运行项目，需要将resources/conf文件夹下的文件打包后放到taret/classes文件夹下，所以可以在pom.xml文件中做如下配置
   
   ```xml
   <resources>
       <!--  将resources下的conf文件夹打包到target目录下，供打包成zip文件上传到服务器使用  -->
       <resource>
           <directory>src/main/resources</directory>
           <filtering>true</filtering>
           <excludes>
               <exclude>*.bat</exclude>
               <!--<exclude>*.sql</exclude>-->
           </excludes>
       </resource>
       <!--  将resouces/conf下的文件打包到target/classes目录下,供idea运行项目使用  -->
       <resource>
           <directory>src/main/resources/conf</directory>
       </resource>
       <!--  将deploy.bat打包到target目录下  -->
       <resource>
           <directory>src/main/resources</directory>
           <!-- 替换占位符${} -->
           <filtering>true</filtering>
           <includes>
               <include>*.bat</include>
           </includes>
           <targetPath>..</targetPath>
       </resource>
   </resources>
   ```
   
- ### Cannot determine embedded database driver class for database type NONE    
  
  添加依赖

    ```xml
            <dependency>
                <groupId>com.h2database</groupId>
                <artifactId>h2</artifactId>
                <version>RELEASE</version>
                <scope>runtime</scope>
            </dependency>
    ```
- ### 内存不足可能会导致应用连接Redis失败      
- ### 应用启动时Spring Boot会自动执行resources下的schema.sql文件，   
    如果不想执行，可以改成其它名称
- ### 远程服务器无法访问redis   
    #### 更改redis配置文件   
    关闭保护模式
    ```properties
    protected-mode no
    ```
    注释掉ip绑定
    ```properties
    # bind 127.0.0.1
    ```
- ### Redis Windows版本指定配置文件无法启动   
    使用绝对路径
    ``` bash
    redis-server "D:\Program Files\Redis-x64-5.0.9\redis.windows.conf"
    ```


