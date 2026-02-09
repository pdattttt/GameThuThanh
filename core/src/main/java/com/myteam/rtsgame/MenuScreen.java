package com.myteam.rtsgame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.myteam.rtsgame.Network.RtsServer;

import java.io.IOException;

public class MenuScreen extends ScreenAdapter {
    Main game;
    SpriteBatch batch;
    BitmapFont font;

    // THÊM: Camera và Viewport để chống méo hình
    OrthographicCamera camera;
    Viewport viewport;

    public MenuScreen(Main game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(2); // Phóng to chữ

        // 1. Setup Camera
        camera = new OrthographicCamera();

        // 2. Setup Viewport (Quan trọng)
        // Ta quy định màn hình ảo là 800x600.
        // Dù màn hình thật to bao nhiêu, game sẽ tự co giãn để vừa khít 800x600 này.
        viewport = new FitViewport(800, 600, camera);
        viewport.apply();

        // Đưa camera vào giữa màn hình ảo
        camera.position.set(camera.viewportWidth / 2, camera.viewportHeight / 2, 0);
    }

    @Override
    public void render(float delta) {
        // Xóa màn hình
        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Update Camera
        camera.update();

        // QUAN TRỌNG: Báo cho batch biết cần vẽ theo tỷ lệ của camera này
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        // Giờ ta vẽ theo tọa độ chuẩn 800x600
        // (0,0) là góc dưới trái. (800, 600) là góc trên phải.

        font.draw(batch, "GAME RTS LAN - 2 PLAYER", 200, 500);
        font.draw(batch, "Nhan phim [H] de lam HOST (May chu)", 200, 400);
        font.draw(batch, "Nhan phim [C] de lam CLIENT (May khach)", 200, 350);
        font.draw(batch, "Trang thai: Dang cho...", 200, 250);

        batch.end();

        // Xử lý Input (Giữ nguyên như cũ)
        handleInput();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            try {
                if (game.server == null) game.server = new RtsServer();
                // Delay nhẹ để server kịp mở cổng
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                        Gdx.app.postRunnable(() -> {
                            // Sửa lại chỗ này dùng IP localhost cho chuẩn
                            try { game.client.connect("127.0.0.1"); } catch(Exception e){}
                        });
                    } catch (InterruptedException e) {}
                }).start();
            } catch (IOException e) { e.printStackTrace(); }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            // Sửa lại IP nếu muốn test 2 máy
            game.client.connect("127.0.0.1");
        }
    }

    // CỰC KỲ QUAN TRỌNG: Khi người chơi kéo dãn cửa sổ
    @Override
    public void resize(int width, int height) {
        // Cập nhật lại viewport để không bị méo hình
        viewport.update(width, height, true); // true để căn giữa camera
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
