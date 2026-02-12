package com.example.demo.controller;

import com.example.demo.model.GameAction;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class GameController {

    // Khi Client gửi vào /app/action
    @MessageMapping("/action")
    // Server sẽ phát loa lại vào /topic/game-progress
    @SendTo("/topic/game-progress")
    public GameAction handleGameAction(GameAction action) {
        System.out.println("Nhận lệnh: " + action.getActionType() + " từ " + action.getPlayerType());
        return action; // Gửi trả lại đúng gói tin đó cho tất cả mọi người
    }
}