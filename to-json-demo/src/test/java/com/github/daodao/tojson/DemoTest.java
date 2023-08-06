package com.github.daodao.tojson;

import com.github.daodao.tojson.model.Cat;
import com.github.daodao.tojson.model.Dog;
import org.junit.jupiter.api.Test;

public class DemoTest {

    @Test
    public void test() {
        String json;

        Cat cat = new Cat();
        cat.setName("喵");
        json = cat.toJson();
        System.out.println(json);

        Dog dog = new Dog();
        dog.setName("汪");
        json = dog.toJson();
        System.out.println(json);

    }

}
