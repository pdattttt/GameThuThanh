package com.myteam.rtsgame.Network;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.myteam.rtsgame.Main;
import com.myteam.rtsgame.GameScreen; // Import màn hình game của bạn
import com.badlogic.gdx.Gdx;
import com.myteam.rtsgame.Network.Network;

import java.io.IOException;

public class RtsClient {
    private Client client;
    private Main mainGame; // Tham chiếu để chuyển màn hình

    public RtsClient(Main game) {
        this.mainGame = game;
        client = new Client();
        Network.register(client);
        client.start();

        client.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof Network.StartGameMessage) {
                    // QUAN TRỌNG: LibGDX không cho phép thay đổi UI từ luồng mạng (Network Thread)
                    // Phải dùng Gdx.app.postRunnable để quay về luồng chính
                    Gdx.app.postRunnable(() -> {
                        System.out.println("Server bảo bắt đầu game!");
                        mainGame.setScreen(new GameScreen()); // Chuyển sang màn hình chơi!
                    });
                }
            }
        });
    }

    public void connect(String ip) {
        try {
            client.connect(5000, ip, Network.TCP_PORT, Network.UDP_PORT);

            // Gửi lời chào
            Network.LoginRequest req = new Network.LoginRequest();
            req.name = "Player Client";
            client.sendTCP(req);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Hàm gửi lệnh di chuyển (sẽ dùng sau này)
    public void sendMoveCommand(int unitId, float x, float y) {
        // ...
    }
}
