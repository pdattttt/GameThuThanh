package com.myteam.rtsgame;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class Enemy {
    public Vector2 position;
    public Vector2 target;
    private final Texture texture;
    public float speed = 100f;

    // Danh sách các điểm cua (Waypoints) đọc từ Tiled
    private final Array<Vector2> waypoints;
    private int currentWaypointIndex = 0; // Đang đi tới điểm thứ mấy
    private boolean reachedBase = false;

    // Khởi tạo quái vật, truyền vào danh sách đường đi
    public Enemy(Array<Vector2> waypoints, Texture texture) {
        this.waypoints = waypoints;
        this.texture = texture;

        // Đặt vị trí xuất phát là điểm đầu tiên của đường vẽ
        this.position = new Vector2(waypoints.get(0));

        // Mục tiêu đầu tiên là điểm thứ 2
        this.target = new Vector2(waypoints.get(1));
        this.currentWaypointIndex = 1;
    }

    public void update(float deltaTime) {
        if (reachedBase) return;

        // Nếu chưa tới điểm cua thì tiếp tục đi
        if (position.dst(target) > 2f) {
            Vector2 direction = new Vector2(target).sub(position).nor();
            position.mulAdd(direction, speed * deltaTime);
        } else {
            // Đã tới điểm cua -> Khóa đúng vị trí
            position.set(target);

            // Chuyển sang điểm cua tiếp theo
            currentWaypointIndex++;
            if (currentWaypointIndex < waypoints.size) {
                target.set(waypoints.get(currentWaypointIndex));
            } else {
                reachedBase = true;
                System.out.println("Quái vật đã vào Nhà Chính!");
            }
        }
    }

    public void render(SpriteBatch batch) {
        // Trừ đi 32 (nửa kích thước ô) để quái đứng ngay chính giữa đường
        batch.draw(texture, position.x - 32, position.y - 32, 64, 64);
    }
}
