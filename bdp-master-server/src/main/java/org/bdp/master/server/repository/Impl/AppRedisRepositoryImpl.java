package org.bdp.master.server.repository.Impl;

import com.alibaba.fastjson.JSON;
import org.bdp.master.server.domain.App;
import org.bdp.master.server.repository.AppRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

// 导入这个类的静态方法、静态变量
import static org.bdp.master.server.Constants.*;

@Repository
public class AppRedisRepositoryImpl implements AppRedisRepository {

    public static Logger logger = LoggerFactory.getLogger(AppRedisRepositoryImpl.class);

    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    /*--------------------------------------------    初始化    ---------------------------------------------*/

    @Autowired
    public AppRedisRepositoryImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    private void init(){
        valueOperations = stringRedisTemplate.opsForValue();
    }

    /*--------------------------------------------    构造Redis的key    ---------------------------------------------*/

    // app:{id}
    private String buildRecKey(Long id){
        return new StringBuilder(APP_KEYSPACE).append(":").append(id).toString();
    }

    private String buildRecKey(App app){
        return buildRecKey(app.getId());
    }

    // i_app:{appName}
    private String buildAppNameIdxKey(String appName){
        return new StringBuilder(INDEX_PREFIX).append(APP_KEYSPACE).append(":").append(appName).toString();
    }

    private String buildAppNameIdxKey(App app){
        return buildAppNameIdxKey(app.getName());
    }

    /*--------------------------------------------    CRUD    ---------------------------------------------*/

    @Override
    public App findOne(Long id) {
        return JSON.parseObject(valueOperations.get(buildRecKey(id)), App.class);
    }

    @Override
    public App findOne(String name) {
        String recKey = valueOperations.get(buildAppNameIdxKey(name));
        return JSON.parseObject(valueOperations.get(recKey), App.class);
    }

    @Override
    public List<App> findAll() {
        return stringRedisTemplate.execute(new RedisCallback<List<App>>() {
            @Override
            public List<App> doInRedis(RedisConnection redisConnection) throws DataAccessException {
                List<App> apps = new ArrayList<>();
                Cursor<byte[]> cursor = redisConnection.scan(
                        new ScanOptions.ScanOptionsBuilder().match(APP_KEY_PATTERN).count(Integer.MAX_VALUE).build()
                );
                while(cursor.hasNext()){
                    App app = JSON.parseObject(new String(redisConnection.get(cursor.next())), App.class);
                    apps.add(app);
                }
                return apps;
            }
        });
    }

    @Override
    public void save(App app) {
        String recKey = buildRecKey(app);
        String hostnameIdxKey = buildAppNameIdxKey(app);
        valueOperations.set(recKey, JSON.toJSONString(app));
        valueOperations.set(hostnameIdxKey, recKey);
    }

    @Override
    public void delete(App app) {
        String recKey = buildRecKey(app);
        String hostnameIdxKey = buildAppNameIdxKey(app);
        stringRedisTemplate.delete(recKey);
        stringRedisTemplate.delete(hostnameIdxKey);
    }
}
