package com.myteam.rtsgame;

import com.badlogic.gdx.Game;
import com.myteam.rtsgame.Network.RtsClient;
import com.myteam.rtsgame.Network.RtsServer;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {

    public RtsServer server; // Giữ tham chiếu server
    public RtsClient client; // Giữ tham chiếu client

    @Override
    public void create() {
        client = new RtsClient(this);

        setScreen(new MenuScreen(this));
    }
}
