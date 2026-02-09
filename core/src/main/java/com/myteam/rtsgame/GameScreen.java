package com.myteam.rtsgame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;

public class GameScreen extends ScreenAdapter {
    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private OrthographicCamera camera;
    private SpriteBatch batch; // Dùng để vẽ nông dân
    private float unitWidth = 128;
    private float unitHeight = 128;
    // Các biến camera

    private float cameraSpeed = 400f; // Tăng tốc độ lên chút cho mượt
    private float mapWidth, mapHeight;

    // Các biến nông dân (Test)
    private Texture peasantImg;
    private float peasantX = 300, peasantY = 300;

    @Override
    public void show() {

        // 1. Setup Camera TRƯỚC (Quan trọng: Phải new trước khi set position)
        camera = new OrthographicCamera();
        // Chỉnh zoom phù hợp với map 64x64 (nhìn rộng 1280x720)
        camera.setToOrtho(false, 1280, 720);

        // 2. Load bản đồ
        try {
            map = new TmxMapLoader().load("maps/level1.tmx");
        } catch (Exception e) {
            System.out.println("Lỗi không tìm thấy map! Hãy kiểm tra lại folder assets/maps/");
            Gdx.app.exit();
        }

        // 3. Lấy kích thước map để giới hạn camera
        TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(0);
        mapWidth = layer.getWidth() * layer.getTileWidth();
        mapHeight = layer.getHeight() * layer.getTileHeight();

        // 4. Đặt camera vào giữa map
        camera.position.set(mapWidth / 2, mapHeight / 2, 0);

        // 5. Tạo bộ render map
        renderer = new OrthogonalTiledMapRenderer(map);

        // 6. Khởi tạo Batch và load ảnh nông dân
        batch = new SpriteBatch();
        // LƯU Ý: cần có file peasant.png trong thư mục assets
        // Nếu chưa có, game sẽ báo lỗi.
        try {
            peasantImg = new Texture("maps/peasant.png");

        } catch (Exception e) {
            System.out.println("Chưa có ảnh peasant.png, nông dân sẽ không hiện!");
        }
    }

    @Override
    public void render(float delta) {
        // --- 1. XỬ LÝ INPUT (Di chuyển Camera) ---
        handleInput(delta);

        // --- 2. UPDATE CAMERA ---
        // Giới hạn camera không chạy ra ngoài map (Clamping)
        float camW = camera.viewportWidth / 2f;
        float camH = camera.viewportHeight / 2f;

        // Chỉ giới hạn nếu map lớn hơn màn hình
        if (mapWidth > camera.viewportWidth) {
            camera.position.x = MathUtils.clamp(camera.position.x, camW, mapWidth - camW);
        }
        if (mapHeight > camera.viewportHeight) {
            camera.position.y = MathUtils.clamp(camera.position.y, camH, mapHeight - camH);
        }
        camera.update();

        // --- 3. VẼ (RENDER) ---
        // Xóa màn hình
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Vẽ Map trước
        renderer.setView(camera);
        renderer.render();

        // Vẽ Nông dân sau (đè lên map)
        if (peasantImg != null) {
            batch.setProjectionMatrix(camera.combined); // Đồng bộ batch với camera
            batch.begin();
            batch.draw(peasantImg, peasantX, peasantY, unitWidth, unitHeight);
            batch.end();
        }
    }

    private void handleInput(float dt) {
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.position.x -= cameraSpeed * dt;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.position.x += cameraSpeed * dt;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.position.y += cameraSpeed * dt;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.position.y -= cameraSpeed * dt;
        }
    }

    @Override
    public void dispose() {
        map.dispose();
        renderer.dispose();
        if (batch != null) batch.dispose();
        if (peasantImg != null) peasantImg.dispose();
    }
}
