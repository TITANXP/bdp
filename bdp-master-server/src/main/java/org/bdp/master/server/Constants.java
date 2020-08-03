package org.bdp.master.server;

/**
 * 定义常量
 * 用import static org.bdp.master.server.Constants.*;即可使用
 */
public interface Constants {
    // app
    String APP_KEYSPACE = "app";
    String APP_KEY_PATTERN = "app:*";
    // server
    String SERVER_KEYSPACE = "server";
    String SERVER_KEY_PATTERN = "server:*";
    // metric_index
    String METRIC_INDEX_KEYSPACE = "metric_index";
    String METRIC_INDEX_KEY_PATTERN = "metric_index:*";
    // alert_index
    String ALERT_INDEX_KEYSPACE = "alert_index";
    String ALERT_INDEX_KEY_PATTERN = "alert_index:*";

    String INDEX_PREFIX = "i_";
    String JOIN_PREFIX = "x_";
}
