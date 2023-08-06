package com.github.daodao.tojson.model;

import com.github.daodao.tojson.annotation.ToJson;

/**
 * @author DaoDao
 */
@ToJson
public class Dog {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
