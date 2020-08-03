package org.bdp.master.server.domain;

import javassist.tools.rmi.AppletServer;
import org.bdp.master.server.service.AppService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name="app")
public class App implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "version", nullable = false)
    private String version;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="creation_time", insertable = false, updatable = false, columnDefinition = "TIMESTAMP default CURRENT_TIMESTAMP")
    private Date creationTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "update_time", insertable = false, columnDefinition = "TIMESTAMP default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP")
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Date getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Date creationTime) {
        this.creationTime = creationTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
