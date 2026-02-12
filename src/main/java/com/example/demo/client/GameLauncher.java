package com.example.demo.client;

public class GameLauncher {
    public static void main(String[] args) {
        // Gọi hàm main của PCGameClient từ đây để tránh lỗi thiếu thư viện
        PCGameClient.main(args);
    }
}