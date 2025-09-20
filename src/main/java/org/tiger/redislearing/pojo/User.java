package org.tiger.redislearing.pojo;

import lombok.Data;

/**
 * @ClassName User
 * @Description
 * @Author tiger
 * @Date 2025/9/20 17:32
 */
@Data
public class User {
    private String name;
    private int id;
    private String des;

    public User() {}

    public User(String name, int id, String des) {
        this.name = name;
        this.id = id;
        this.des = des;
    }
}
