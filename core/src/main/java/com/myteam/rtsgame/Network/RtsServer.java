package com.myteam.rtsgame.Network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import java.io.IOException;

public class RtsServer {
    private Server server;

    public RtsServer() throws IOException {
        server = new Server();
        Network.register(server); // Đăng ký gói tin
        server.bind(Network.TCP_PORT, Network.UDP_PORT);
        server.start();

        server.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof Network.LoginRequest) {
                    System.out.println("Có người kết nối: " + ((Network.LoginRequest) object).name);

                    // Trả lời OK
                    Network.LoginResponse response = new Network.LoginResponse();
                    response.isSuccess = true;
                    connection.sendTCP(response);

                    // Nếu muốn test nhanh: Có người vào là Start Game luôn
                    Network.StartGameMessage start = new Network.StartGameMessage();
                    server.sendToAllTCP(start);
                }
            }
        });
    }
}
