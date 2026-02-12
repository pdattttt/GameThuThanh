package com.example.demo.model;

public class GameAction {
    private String playerType; // "ATTACKER" hoặc "DEFENDER"
    private String actionType; // "SPAWN", "BUILD"...
    private String data;       // "Quái A tại (10,10)"

    // Constructor mặc định (Bắt buộc)
    public GameAction() {
    }

    // Constructor đầy đủ
    public GameAction(String playerType, String actionType, String data) {
        this.playerType = playerType;
        this.actionType = actionType;
        this.data = data;
    }

    // Getter và Setter
    public String getPlayerType() {
        return playerType;
    }

    public void setPlayerType(String playerType) {
        this.playerType = playerType;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}