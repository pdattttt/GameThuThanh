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
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.math.Polyline;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class GameScreen extends ScreenAdapter {
    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private OrthographicCamera camera;
    private SpriteBatch batch; // Dùng để vẽ nông dân
    private final float unitWidth = 128;
    private final float unitHeight = 128;
    private Enemy testEnemy;
    private Texture enemyTexture;
    private Array<Vector2> enemyPath;
    // Bản đồ logic (Ví dụ 6 dòng x 10 cột)
    // Bạn có thể sửa các số 1 để tạo đường đi ngoằn ngoèo tùy ý!

    // Các biến camera

    private final float cameraSpeed = 400f; // Tăng tốc độ lên chút cho mượt
    private float mapWidth, mapHeight;

    // Các biến nông dân (Test)
    private Texture peasantImg;
    private final float peasantX = 300;
    private final float peasantY = 300;

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
            System.out.println("LỖI LOAD MAP CHI TIẾT LÀ:");
            e.printStackTrace();
            Gdx.app.exit();
            return; // Lệnh này cực kỳ quan trọng: Bắt game dừng ngay tại đây, không chạy xuống dưới nữa!
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
        enemyPath = new Array<>();
        try {
            // Lấy layer Logic và đường Path1
            MapLayer logicLayer = map.getLayers().get("logic");
            MapObject pathObject = logicLayer.getObjects().get("Path1");

            // Lấy ra danh sách các điểm tọa độ
            Polyline polyline = ((PolylineMapObject) pathObject).getPolyline();
            float[] vertices = polyline.getTransformedVertices();

            // Cứ 2 số (x, y) ghép lại thành 1 tọa độ Vector2
            for (int i = 0; i < vertices.length; i += 2) {
                enemyPath.add(new Vector2(vertices[i], vertices[i + 1]));
            }
            System.out.println("Đọc đường đi thành công! Có " + enemyPath.size + " khúc cua.");
        } catch (Exception e) {
            System.out.println("Lỗi: Không tìm thấy layer Logic hoặc đường Path1!");
        }

        // 3. Khởi tạo con quái test
        batch = new SpriteBatch();
        enemyTexture = new Texture("maps/peasant.png");
        if (enemyPath.size > 0) {
            testEnemy = new Enemy(enemyPath, enemyTexture);
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
        if (testEnemy != null) {
            testEnemy.update(delta);
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (testEnemy != null) {
            testEnemy.render(batch);
        }
        batch.end();
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
