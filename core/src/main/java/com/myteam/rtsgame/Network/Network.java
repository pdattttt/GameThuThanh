package com.myteam.rtsgame.Network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;

public class Network {
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;

    public static void register(EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();

        // Đăng ký các gói tin cơ bản
        kryo.register(LoginRequest.class);
        kryo.register(LoginResponse.class);
        kryo.register(StartGameMessage.class);
        // Sau này sẽ thêm: MoveUnit, BuildStructure, etc.
    }
    // --- CÁC GÓI TIN (PACKETS) ---

    // Client gửi: "Cho tôi vào phòng với tên này"
    public static class LoginRequest {
        public String name;
    }

    // Server trả lời: "OK vào đi" hoặc "Phòng đầy rồi"
    public static class LoginResponse {
        public String status;
        public boolean isSuccess;
    }

    // Server ra lệnh: "Đủ người rồi, vào game thôi!"
    public static class StartGameMessage {
        public int mapId; // Ví dụ: map số 1
    }
}
