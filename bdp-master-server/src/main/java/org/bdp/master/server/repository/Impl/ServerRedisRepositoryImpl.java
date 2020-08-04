package org.bdp.master.server.repository.Impl;

import com.alibaba.fastjson.JSON;
import org.bdp.master.server.domain.Server;
import org.bdp.master.server.repository.ServerRedisRepository;
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

import static org.bdp.master.server.Constants.*;

@Repository
public class ServerRedisRepositoryImpl implements ServerRedisRepository {

    public static Logger logger = LoggerFactory.getLogger(ServerRedisRepositoryImpl.class);

    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    private SetOperations<String, String> setOperations;

    /*--------------------------------------------    初始化    ---------------------------------------------*/

    @Autowired
    public ServerRedisRepositoryImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    private void init(){
        valueOperations = stringRedisTemplate.opsForValue();
        setOperations = stringRedisTemplate.opsForSet();
    }

    /*--------------------------------------------    构造Redis的key    ---------------------------------------------*/

    //server:{Id}
    private String buildRecKey(Long id){
        return new StringBuilder(SERVER_KEY_PATTERN).append(":").append(id).toString();
    }

    private String buildRecKey(Server server){
        return buildRecKey(server.getId());
    }

    //i_server:{serverName}
    private String buildHostnameIdxKey(String serverName){
        return new StringBuilder(INDEX_PREFIX).append(SERVER_KEYSPACE).append(":").append(serverName).toString();
    }

    private String buildHostnameIdxKey(Server server){
        return buildHostnameIdxKey(server.getHostname());
    }

    //x_app:{appId}:server
    private String buildAppServerJoinKey(Server server){
        return new StringBuilder(JOIN_PREFIX).append(APP_KEYSPACE).append(":")
                .append(server.getAppId()).append(":").append(SERVER_KEYSPACE).toString();
    }

    /*--------------------------------------------    CRUD    ---------------------------------------------*/

    @Override
    public Server findOne(Long id) {
        return JSON.parseObject(valueOperations.get(buildRecKey(id)), Server.class);
    }

    @Override
    public Server findOne(String hostname) {
        String recKey = valueOperations.get(buildHostnameIdxKey(hostname));
        return JSON.parseObject(valueOperations.get(recKey), Server.class);
    }

    @Override
    public List<Server> findAll() {
        return stringRedisTemplate.execute(new RedisCallback<List<Server>>() {
            @Override
            public List<Server> doInRedis(RedisConnection redisConnection) throws DataAccessException {
                List<Server> servers = new ArrayList<>();
                Cursor<byte[]> cursor = redisConnection.scan(
                        new ScanOptions.ScanOptionsBuilder().match(SERVER_KEY_PATTERN).count(Integer.MAX_VALUE).build()
                );
                while(cursor.hasNext()){
                    Server server = JSON.parseObject(new String(redisConnection.get(cursor.next())), Server.class);
                    servers.add(server);
                }
                return servers;
            }
        });
    }

    @Override
    public void save(Server server) {
        String recKey = buildRecKey(server);
        String hostnameIdxKey = buildHostnameIdxKey(server);
        String appServerJoinKey = buildAppServerJoinKey(server);
        //set server:{Id} {server}
        valueOperations.set(recKey, JSON.toJSONString(server));
        //set i_server:{serverName} {recKey}
        valueOperations.set(hostnameIdxKey, recKey);
        //set x_app:{appId}:server {serverId} app和server的一对多连接
        setOperations.add(appServerJoinKey, server.getId().toString());
    }

    @Override
    public void delete(Server server) {
        String recKey = buildRecKey(server);
        String hostnameIdxKey = buildHostnameIdxKey(server);
        String appServerJoinKey = buildAppServerJoinKey(server);
        stringRedisTemplate.delete(recKey);
        stringRedisTemplate.delete(hostnameIdxKey);
        setOperations.remove(appServerJoinKey, server.getId().toString());
    }
}
