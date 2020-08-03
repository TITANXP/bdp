# 创建bdp_master数据库和表
DROP DATABASE IF EXISTS bdp_master;

CREATE DATABASE IF NOT EXISTS bdp_master DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci;

# 下面建表语句是，hibernate根据domain下实体类的注解自动生成的
USE bdp_master;

DROP TABLE IF EXISTS app;
CREATE TABLE app (
    id BIGINT NOT NULL auto_increment,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR ( 255 ) NOT NULL,
    NAME VARCHAR ( 255 ) NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version VARCHAR ( 255 ) NOT NULL,
    PRIMARY KEY ( id )
);

DROP TABLE IF EXISTS server;
CREATE TABLE SERVER (
    id BIGINT NOT NULL auto_increment,
    app_id BIGINT NOT NULL,
    cpu_cores INTEGER NOT NULL,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    hostname VARCHAR ( 255 ) NOT NULL,
    memory INTEGER NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY ( id )
);

DROP TABLE IF EXISTS metric_threshold;
CREATE TABLE metric_threshold (
    server_id BIGINT NOT NULL,
    amber_threshold INTEGER,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    red_threshold INTEGER,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    metric_name VARCHAR ( 255 ) NOT NULL,
    PRIMARY KEY ( server_id, metric_name )
);

DROP TABLE IF EXISTS metric_index;
CREATE TABLE metric_index (
    id BIGINT NOT NULL auto_increment,
    category VARCHAR ( 255 ) NOT NULL,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR ( 255 ) NOT NULL,
    NAME VARCHAR ( 255 ) NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY ( id )
);

DROP TABLE IF EXISTS alert_index;
CREATE TABLE alert_index (
    id BIGINT NOT NULL auto_increment,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    NAME VARCHAR ( 255 ) NOT NULL,
    severity INTEGER NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY ( id )
);

