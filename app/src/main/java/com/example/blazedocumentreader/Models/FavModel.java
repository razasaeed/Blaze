package com.example.blazedocumentreader.Models;

public class FavModel {

    private int id;
    private String type, name;

    public FavModel() {
    }

    public FavModel(int id, String type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
