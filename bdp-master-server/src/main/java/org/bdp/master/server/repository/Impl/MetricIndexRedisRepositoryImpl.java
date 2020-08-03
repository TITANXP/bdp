package org.bdp.master.server.repository.Impl;

import com.alibaba.fastjson.JSON;
import org.bdp.master.server.domain.MetricIndex;
import org.bdp.master.server.repository.MetricIndexRedisRepository;
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
public class MetricIndexRedisRepositoryImpl implements MetricIndexRedisRepository {

    public static Logger logger = LoggerFactory.getLogger(MetricIndexRedisRepositoryImpl.class);

    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    /*--------------------------------------------    初始化    ---------------------------------------------*/

    @Autowired
    public MetricIndexRedisRepositoryImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    private void init(){
        valueOperations = stringRedisTemplate.opsForValue();
    }

    /*--------------------------------------------    构造Redis的key    ---------------------------------------------*/

    // metric_index:{id}
    private String buildRecKey(Long id){
        return new StringBuilder(METRIC_INDEX_KEYSPACE).append(":").append(id).toString();
    }

    private String buildRecKey(MetricIndex metricIndex){
        return buildRecKey(metricIndex.getId());
    }

    // i_metric_index:{metricName}
    private String buildMetricNameIdxKey(String metricName){
        return new StringBuilder(INDEX_PREFIX).append(METRIC_INDEX_KEYSPACE).append(":").append(metricName).toString();
    }

    private String buildMetricNameIdxKey(MetricIndex metricIndex){
        return buildMetricNameIdxKey(metricIndex.getName());
    }

    /*--------------------------------------------    CRUD    ---------------------------------------------*/

    @Override
    public MetricIndex findOne(Long id) {
        return JSON.parseObject(valueOperations.get(buildRecKey(id)), MetricIndex.class);
    }

    @Override
    public MetricIndex findOne(String name) {
        String recKey = valueOperations.get(buildMetricNameIdxKey(name));
        return JSON.parseObject(valueOperations.get(recKey), MetricIndex.class);
    }

    @Override
    public List<MetricIndex> findAll() {
        return stringRedisTemplate.execute(new RedisCallback<List<MetricIndex>>() {
            @Override
            public List<MetricIndex> doInRedis(RedisConnection redisConnection) throws DataAccessException {
                List<MetricIndex> metricIndices = new ArrayList<>();
                Cursor<byte[]> cursor = redisConnection.scan(
                        new ScanOptions.ScanOptionsBuilder().match(METRIC_INDEX_KEY_PATTERN).count(Integer.MAX_VALUE).build()
                );
                while(cursor.hasNext()){
                    MetricIndex metricIndex = JSON.parseObject(new String(redisConnection.get(cursor.next())), MetricIndex.class);
                    metricIndices.add(metricIndex);
                }
                return metricIndices;
            }
        });
    }

    @Override
    public void save(MetricIndex metricIndex) {
        String recKey = buildRecKey(metricIndex.getId());
        String hostnameIdxKey = buildMetricNameIdxKey(metricIndex);
        // 保存元数据 set： {recKey} {metricIndex}
        valueOperations.set(recKey, JSON.toJSONString(metricIndex));
        // 保存元数据的key： set i_metric_index:{name} {recKey}
        valueOperations.set(hostnameIdxKey, recKey);
    }

    @Override
    public void delete(MetricIndex metricIndex) {
        String recKey = buildRecKey(metricIndex);
        String hostnameIdxKey = buildMetricNameIdxKey(metricIndex);
        stringRedisTemplate.delete(recKey);
        stringRedisTemplate.delete(hostnameIdxKey);
    }
}
