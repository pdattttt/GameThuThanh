package com.example.demo.client;

import com.example.demo.model.GameAction;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PCGameClient extends Application {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 750;
    private static final int INITIAL_BASE_HP = 2000;

    // --- CẤU HÌNH ---
    private static final int LEVEL_DURATION_SECONDS = 180;
    private static final long BUILD_COOLDOWN_MS = 1500;

    // --- MÀU SẮC THEME NEON ---
    private static final Color BG_DARK = Color.web("#0b0b1a");
    private static final Color BG_LIGHT = Color.web("#1a1a2e");
    private static final Color NEON_RED = Color.web("#ff2e63");
    private static final Color NEON_GREEN = Color.web("#08d9d6");
    private static final Color NEON_CYAN = Color.web("#00fff5");
    private static final Color NEON_PURPLE = Color.web("#aa2ee6");
    private static final Color NEON_ORANGE = Color.web("#ff9a3c");
    private static final Color ROAD_COLOR = Color.web("#2a2a40");
    private static final Color ROAD_BORDER = Color.web("#00fff5");

    // --- ENUM ---
    enum TowerType {
        ARCHER(180, 25, 0.3, NEON_GREEN, "ARCHER"),
        MAGE(140, 70, 1.2, NEON_PURPLE, "MAGE"),
        BARRACKS(0, 0, 0, NEON_ORANGE, "BARRACKS");
        double range; int damage; double cooldown; Color color; String name;
        TowerType(double r, int d, double c, Color col, String n) {
            this.range=r; this.damage=d; this.cooldown=c; this.color=col; this.name=n;
        }
    }

    enum MonsterType {
        GOBLIN(150, 3.5, 0, 0, NEON_GREEN, 1),
        ORC(500, 1.5, 60, 0, Color.web("#2e7d32"), 2),
        SHAMAN(400, 1.8, 0, 70, NEON_CYAN, 3),
        BOSS(5000, 1.0, 40, 40, NEON_RED, 10);
        int hp; double speed; int armor; int magicResist; Color color; int cooldown;
        MonsterType(int h, double s, int a, int m, Color c, int cd) {
            this.hp=h; this.speed=s; this.armor=a; this.magicResist=m; this.color=c; this.cooldown=cd;
        }
    }

    // --- STATE ---
    private GraphicsContext gc;
    private int baseHp = INITIAL_BASE_HP;
    private int currentLevel = 1;
    private boolean isGameOver = false, isVictory = false, isPaused = false;
    private TowerType selectedTower = TowerType.ARCHER;
    private long lastBuildTime = 0;

    // --- DATA ---
    private List<Point2D> currentPath = new ArrayList<>();
    private List<Monster> monsters = new ArrayList<>();
    private List<Tower> towers = new ArrayList<>();
    private List<Projectile> projectiles = new ArrayList<>();
    private List<Soldier> soldiers = new ArrayList<>();

    // --- LOGIC ---
    private boolean levelStarted = false;
    private long levelStartTime = 0;
    private double animationTime = 0;
    private boolean isOfflineMode = false;
    private long lastAiActionTime = 0;

    // --- NET & UI ---
    private StompSession session;
    private String myRole = "NONE";
    private String message = "Chọn chế độ chơi...";
    private Map<MonsterType, Long> cooldowns = new HashMap<>();

    // UI COMPONENTS
    private StackPane rootPane;
    private HBox topHud;
    private VBox attackerPanel;
    private VBox menuOverlay; // Menu Tạm dừng / Kết thúc
    private Map<MonsterType, Button> attackerButtons = new HashMap<>();
    private ProgressBar buildProgressBar;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        rootPane = new StackPane();
        rootPane.setBackground(new Background(new BackgroundFill(BG_DARK, CornerRadii.EMPTY, Insets.EMPTY)));

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();

        // Xử lý Input
        canvas.setOnMouseClicked(e -> {
            if ("DEFENDER".equals(myRole) && !isGameOver && !isPaused) {
                if (e.getButton() == MouseButton.PRIMARY) handleBuild(e.getX(), e.getY());
                else if (e.getButton() == MouseButton.SECONDARY) switchTower();
            }
        });

        // Phím tắt Pause
        Scene scene = new Scene(rootPane, WIDTH, HEIGHT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && levelStarted && !isGameOver && !isVictory) {
                togglePause();
            }
        });

        rootPane.getChildren().add(canvas);
        setupRoleSelection(); // Màn hình chọn phe ban đầu

        new AnimationTimer() {
            public void handle(long now) {
                if (!isPaused) {
                    animationTime += 0.05;
                    if (!isGameOver && !isVictory) {
                        update(now);
                        if (isOfflineMode) updateAI(now);
                    }
                }
                render(); // Vẽ liên tục dù pause hay không
                updateInterface();
            }
        }.start();

        stage.setTitle("Kingdom Rush: Ultimate Edition");
        stage.setScene(scene);
        stage.show();
    }

    // --- 1. CORE LOGIC ---
    private void startGame(boolean offline, String role) {
        this.isOfflineMode = offline;
        this.myRole = role;

        monsters.clear(); projectiles.clear(); towers.clear(); soldiers.clear(); currentPath.clear();
        baseHp = INITIAL_BASE_HP; isGameOver = false; isVictory = false; isPaused = false;
        currentLevel = 1; // Reset level

        // Xóa các UI cũ
        rootPane.getChildren().removeIf(node -> node instanceof VBox || node instanceof HBox);

        setupGameHUD(); // Hiện HUD

        if (!offline) {
            connect();
            if ("ATTACKER".equals(role)) message = "Chờ Host tạo map...";
            else {
                generateMap();
                new Timer().schedule(new TimerTask() { public void run() { Platform.runLater(()->startLevel()); } }, 1000);
            }
        } else {
            generateMap();
            startLevel();
        }

        if ("ATTACKER".equals(role)) createAttackerPanel();
    }

    // Reset data để chơi lại màn hoặc qua màn mới
    private void resetLevelData() {
        monsters.clear(); projectiles.clear(); soldiers.clear();
        // towers.clear(); // KHÔNG XÓA TRỤ NẾU MUỐN GIỮ LẠI CHO MÀN SAU (Tùy chọn)
        // Nhưng nếu map đổi đường đi thì bắt buộc phải xóa trụ:
        towers.clear();

        isGameOver = false; isVictory = false; isPaused = false;
    }

    private void generateMap() {
        currentPath.clear();
        double startY = ThreadLocalRandom.current().nextDouble(150, HEIGHT-150);
        currentPath.add(new Point2D(0, startY));
        for(int i=1; i<5; i++) {
            double x = (WIDTH/5.0)*i;
            double y = ThreadLocalRandom.current().nextDouble(150, HEIGHT-150);
            currentPath.add(new Point2D(x, y));
        }
        currentPath.add(new Point2D(WIDTH-120, ThreadLocalRandom.current().nextDouble(150, HEIGHT-150)));

        if (!isOfflineMode && "DEFENDER".equals(myRole)) {
            StringBuilder sb = new StringBuilder();
            for(Point2D p : currentPath) sb.append((int)p.getX()).append(":").append((int)p.getY()).append(",");
            sendAction("SYNC_MAP", sb.toString());
        }
    }

    private void startLevel() {
        levelStarted = true;
        levelStartTime = System.currentTimeMillis();
        message = "LEVEL " + currentLevel + " - SURVIVE!";
        if (!isOfflineMode && "DEFENDER".equals(myRole)) sendAction("NEW_LEVEL", String.valueOf(currentLevel));
    }

    // --- 2. UPDATE LOOP ---
    private void update(long now) {
        if (levelStarted) {
            long elapsed = (System.currentTimeMillis() - levelStartTime) / 1000;
            long remaining = LEVEL_DURATION_SECONDS - elapsed;
            if (remaining <= 0 && baseHp > 0) {
                handleVictory(); // Thắng theo thời gian
            }
        }

        // Lính
        for(Soldier s : soldiers) {
            if (s.engaging==null && s.hp<100 && now%20==0) s.hp++;
            if (s.engaging!=null && now-s.lastAtk > 1e9) {
                if(s.engaging.hp>0) s.engaging.takeDamage(15 + (s.level*5), "PHYSICAL"); s.lastAtk=now;
            }
            if (s.engaging!=null && s.engaging.hp<=0) s.engaging=null;
        }
        soldiers.removeIf(s -> s.hp<=0);

        // Quái
        Iterator<Monster> it = monsters.iterator();
        while(it.hasNext()) {
            Monster m = it.next();
            boolean blocked = false;
            for(Soldier s : soldiers) {
                if (s.engaging==null && m.engaging==null && m.dist(s.x,s.y)<30) {
                    s.engaging=m; m.engaging=s; blocked=true; break;
                }
            }
            if (m.engaging!=null) {
                if(m.engaging.hp<=0) m.engaging=null;
                else if(now-m.lastAtk > 1e9) { m.engaging.hp-=25; m.lastAtk=now; }
            } else { moveMonster(m); }
            if(m.finished) { baseHp-=100; it.remove(); if(!isOfflineMode) sendAction("SYNC_HP", baseHp+""); }
        }
        monsters.removeIf(m -> m.hp<=0);

        if(baseHp<=0 && !isGameOver) {
            handleDefeat();
        }

        // Trụ
        projectiles.clear();
        for(Tower t : towers) {
            if(t.type==TowerType.BARRACKS) continue;
            if(t.target==null || t.target.hp<=0 || t.dist(t.target.x, t.target.y)>t.getRange()) t.target=findTarget(t);
            if(t.target!=null && now-t.lastAtk > t.getCooldown()*1e9) {
                projectiles.add(new Projectile(t.x,t.y,t.target.x,t.target.y,t.type.color));
                t.target.takeDamage(t.getDamage(), t.type==TowerType.MAGE?"MAGIC":"PHYSICAL");
                t.lastAtk=now;
            }
        }
    }

    // --- 3. MENUS & UI LOGIC (YÊU CẦU MỚI) ---

    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) showPauseMenu();
        else {
            if (menuOverlay != null) rootPane.getChildren().remove(menuOverlay);
        }
    }

    private void handleVictory() {
        isVictory = true;
        if (!isOfflineMode) sendAction("VICTORY", "");
        Platform.runLater(() -> showEndGameMenu(true));
    }

    private void handleDefeat() {
        isGameOver = true;
        if (!isOfflineMode) sendAction("GAME_OVER", "");
        Platform.runLater(() -> showEndGameMenu(false));
    }

    // MENU TẠM DỪNG (IN-GAME)
    private void showPauseMenu() {
        if (menuOverlay != null) rootPane.getChildren().remove(menuOverlay);

        menuOverlay = new VBox(20);
        menuOverlay.setAlignment(Pos.CENTER);
        menuOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.8);");

        Label lbl = createHUDLabel("PAUSED", NEON_CYAN); lbl.setFont(Font.font("Impact", 60));

        Button btnResume = createStyledButton("RESUME GAME", NEON_GREEN);
        btnResume.setOnAction(e -> togglePause());

        Button btnQuit = createStyledButton("QUIT TO MAIN MENU", NEON_RED);
        btnQuit.setOnAction(e -> returnToMainMenu());

        menuOverlay.getChildren().addAll(lbl, btnResume, btnQuit);
        rootPane.getChildren().add(menuOverlay);
    }

    // MENU KẾT THÚC (CUỐI MÀN)
    private void showEndGameMenu(boolean victory) {
        if (menuOverlay != null) rootPane.getChildren().remove(menuOverlay);

        menuOverlay = new VBox(20);
        menuOverlay.setAlignment(Pos.CENTER);
        menuOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.85);");

        Label lbl = createHUDLabel(victory ? "VICTORY!" : "DEFEAT", victory ? NEON_GREEN : NEON_RED);
        lbl.setFont(Font.font("Impact", 80));

        HBox buttons = new HBox(20);
        buttons.setAlignment(Pos.CENTER);

        // Nút Chơi Lại (Luôn có)
        Button btnReplay = createStyledButton("REPLAY LEVEL", NEON_ORANGE);
        btnReplay.setOnAction(e -> {
            rootPane.getChildren().remove(menuOverlay);
            resetLevelData();
            // Start again same level
            generateMap();
            startLevel();
        });

        // Nút Thoát (Luôn có)
        Button btnQuit = createStyledButton("MAIN MENU", NEON_RED);
        btnQuit.setOnAction(e -> returnToMainMenu());

        // Nút Màn Kế Tiếp (Chỉ khi Thắng)
        if (victory) {
            Button btnNext = createStyledButton("NEXT LEVEL >>", NEON_CYAN);
            btnNext.setOnAction(e -> {
                rootPane.getChildren().remove(menuOverlay);
                currentLevel++;
                resetLevelData();
                generateMap();
                startLevel();
            });
            buttons.getChildren().add(btnNext);
        }

        buttons.getChildren().addAll(btnReplay, btnQuit);
        menuOverlay.getChildren().addAll(lbl, buttons);
        rootPane.getChildren().add(menuOverlay);
    }

    private void returnToMainMenu() {
        // Reset toàn bộ state
        rootPane.getChildren().clear();
        rootPane.getChildren().add(new Canvas(WIDTH, HEIGHT)); // Reset canvas
        gc = ((Canvas)rootPane.getChildren().get(0)).getGraphicsContext2D(); // Reset GC
        // Add lại canvas click listener
        rootPane.getChildren().get(0).setOnMouseClicked(e -> {
            if ("DEFENDER".equals(myRole) && !isGameOver && !isPaused) {
                if (e.getButton() == MouseButton.PRIMARY) handleBuild(e.getX(), e.getY());
                else if (e.getButton() == MouseButton.SECONDARY) switchTower();
            }
        });

        isPaused = false;
        setupRoleSelection(); // Quay về màn hình chọn
    }

    // --- 4. TOWER UPGRADE LOGIC ---
    private void handleBuild(double x, double y) {
        long now = System.currentTimeMillis();
        // Check hồi chiêu
        if (now - lastBuildTime < BUILD_COOLDOWN_MS) { message = "Building Cooldown!"; return; }

        // KIỂM TRA XÂY ĐÈ (COLLISION)
        Tower existingTower = towers.stream().filter(t -> t.dist(x, y) < 40).findFirst().orElse(null);
        // Nếu click gần trụ cũ (bán kính 40)

        if (existingTower != null) {
            // NẾU TRÙNG LOẠI -> NÂNG CẤP
            if (existingTower.type == selectedTower) {
                existingTower.upgrade();
                message = "UPGRADED TO LV." + existingTower.level;
                lastBuildTime = now;
                if(!isOfflineMode) sendAction("UPGRADE", x+","+y); // Cần xử lý bên kia đồng bộ
            } else {
                // NẾU KHÁC LOẠI -> XÓA CŨ, XÂY MỚI (REPLACE)
                towers.remove(existingTower);
                // Xóa lính cũ nếu là nhà lính
                if (existingTower.type == TowerType.BARRACKS) {
                    soldiers.removeIf(s -> s.owner == existingTower);
                }

                // Xây mới
                Tower t = new Tower(selectedTower, x, y);
                towers.add(t);
                if(selectedTower==TowerType.BARRACKS) spawnSoldiers(t);

                message = "REPLACED TOWER!";
                lastBuildTime = now;
                if(!isOfflineMode) sendAction("BUILD", selectedTower.name()+","+x+","+y);
            }
        } else {
            // XÂY MỚI HOÀN TOÀN (CHỖ TRỐNG)
            Tower t = new Tower(selectedTower,x,y);
            towers.add(t);
            if(selectedTower==TowerType.BARRACKS) spawnSoldiers(t);
            lastBuildTime = now;
            if(!isOfflineMode) sendAction("BUILD", selectedTower.name()+","+x+","+y);
        }
    }

    private void spawnSoldiers(Tower t) {
        soldiers.add(new Soldier(t.x+20, t.y, t));
        soldiers.add(new Soldier(t.x-20, t.y, t));
        soldiers.add(new Soldier(t.x, t.y+20, t));
    }

    // --- 5. RENDER ---
    private void render() {
        drawBackgroundGrid();
        if (currentPath.isEmpty()) return;

        // Vẽ đường
        gc.setLineCap(StrokeLineCap.ROUND); gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setEffect(new DropShadow(20, ROAD_BORDER));
        gc.setStroke(ROAD_BORDER); gc.setLineWidth(60); beginPath(); gc.stroke();
        gc.setEffect(null);
        gc.setStroke(ROAD_COLOR); gc.setLineWidth(50); beginPath(); gc.stroke();

        // Vẽ Nhà Chính
        Point2D end = currentPath.get(currentPath.size()-1);
        drawBase(end.getX(), end.getY());

        // Vẽ Trụ
        for(Tower t : towers) {
            // Tầm bắn
            if ("DEFENDER".equals(myRole) && t.type != TowerType.BARRACKS) {
                gc.setStroke(Color.rgb(255, 255, 255, 0.05)); gc.setLineWidth(1);
                gc.strokeOval(t.x-t.getRange(), t.y-t.getRange(), t.getRange()*2, t.getRange()*2);
            }
            drawTower(t);
        }

        // Vẽ Lính
        for(Soldier s : soldiers) {
            drawSoldier(s.x, s.y);
            renderModernBar(s.x, s.y-25, s.hp, 100, 30, NEON_CYAN);
        }

        // Vẽ Quái
        for(Monster m : monsters) {
            drawMonster(m);
            double size = m.type==MonsterType.BOSS?60:40;
            renderModernBar(m.x, m.y - size/2 - 15, m.hp, m.type.hp, size, NEON_RED);
        }

        // Vẽ Đạn
        gc.setEffect(new Glow(1.0)); gc.setLineWidth(3);
        for(Projectile p : projectiles) { gc.setStroke(p.c); gc.strokeLine(p.sx, p.sy, p.ex, p.ey); }
        gc.setEffect(null);

        updateHUD();

        // Vẽ hiệu ứng Pause mờ mờ
        if (isPaused) {
            gc.setFill(Color.rgb(0,0,0,0.5));
            gc.fillRect(0,0,WIDTH,HEIGHT);
        }
    }

    private void drawTower(Tower t) {
        double x = t.x; double y = t.y;
        gc.setEffect(new DropShadow(10, t.type.color.darker()));
        gc.setFill(Color.web("#222")); gc.fillOval(x-25, y-20, 50, 30);
        gc.setFill(t.type.color.darker()); gc.fillRect(x-20, y-25, 40, 15);

        // Vẽ thân trụ
        switch (t.type) {
            case ARCHER:
                gc.setFill(new LinearGradient(0,0,1,0, true, CycleMethod.NO_CYCLE, new Stop(0, t.type.color), new Stop(1, t.type.color.brighter())));
                gc.fillRect(x-10, y-55, 20, 40);
                gc.setStroke(Color.WHITE); gc.setLineWidth(3); gc.strokeArc(x-20, y-65, 40, 30, 0, 180, ArcType.OPEN);
                break;
            case MAGE:
                gc.setFill(t.type.color.darker()); gc.fillPolygon(new double[]{x-15, x+15, x}, new double[]{y-10, y-10, y-60}, 3);
                gc.setEffect(new Glow(0.8)); gc.setFill(t.type.color); gc.fillOval(x-12, y-72 + Math.sin(animationTime*2)*5, 24, 24);
                break;
            case BARRACKS:
                gc.setFill(t.type.color.darker()); gc.fillRect(x-25, y-30, 50, 25);
                gc.setFill(t.type.color); gc.fillArc(x-20, y-45, 40, 30, 0, 180, ArcType.ROUND);
                gc.setFill(Color.BLACK); gc.fillRect(x-8, y-25, 16, 15);
                break;
        }

        // HIỂN THỊ LEVEL TRỤ (YÊU CẦU MỚI)
        gc.setEffect(null);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        gc.fillText("Lv." + t.level, x-12, y+5);
    }

    // --- AI LOGIC (OFFLINE) ---
    private void updateAI(long now) {
        if (!levelStarted) return;
        if ("DEFENDER".equals(myRole)) {
            if (System.currentTimeMillis() - lastAiActionTime > 2000) {
                MonsterType[] types = MonsterType.values();
                MonsterType randomType = types[ThreadLocalRandom.current().nextInt(types.length)];
                if (ThreadLocalRandom.current().nextInt(10) > 8) randomType = MonsterType.BOSS;
                spawnMonster(randomType);
                lastAiActionTime = System.currentTimeMillis();
            }
        } else {
            if (System.currentTimeMillis() - lastAiActionTime > 2500) {
                int pathIndex = ThreadLocalRandom.current().nextInt(currentPath.size() - 1);
                Point2D p1 = currentPath.get(pathIndex); Point2D p2 = currentPath.get(pathIndex + 1);
                double t = ThreadLocalRandom.current().nextDouble();
                double midX = p1.getX() + t * (p2.getX() - p1.getX());
                double midY = p1.getY() + t * (p2.getY() - p1.getY());
                double offset = 80 * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
                TowerType[] types = TowerType.values();
                TowerType randomTower = types[ThreadLocalRandom.current().nextInt(types.length)];
                Tower tow = new Tower(randomTower, midX, midY + offset);
                towers.add(tow);
                if (randomTower == TowerType.BARRACKS) spawnSoldiers(tow);
                lastAiActionTime = System.currentTimeMillis();
            }
        }
    }

    // --- OTHER HELPERS ---
    private void spawnMonster(MonsterType t) {
        long now = System.currentTimeMillis();
        if (isOfflineMode && "DEFENDER".equals(myRole)) { monsters.add(new Monster(t, currentPath)); return; }
        if(now - cooldowns.getOrDefault(t,0L) > t.cooldown*1000L) {
            cooldowns.put(t,now);
            if (isOfflineMode) monsters.add(new Monster(t, currentPath));
            else sendAction("SPAWN", t.name());
        }
    }

    private void switchTower() { selectedTower = selectedTower==TowerType.ARCHER?TowerType.MAGE:selectedTower==TowerType.MAGE?TowerType.BARRACKS:TowerType.ARCHER; }
    private void moveMonster(Monster m) { if(m.pathIdx>=currentPath.size()-1) { m.finished=true; return; } Point2D t = currentPath.get(m.pathIdx+1); double d = m.dist(t.getX(), t.getY()); if(d<=m.type.speed) { m.x=t.getX(); m.y=t.getY(); m.pathIdx++; } else { m.x+=(t.getX()-m.x)/d*m.type.speed; m.y+=(t.getY()-m.y)/d*m.type.speed; } }
    private Monster findTarget(Tower t) { return monsters.stream().filter(m->t.dist(m.x,m.y)<=t.getRange()).min(Comparator.comparingDouble(m->t.dist(m.x,m.y))).orElse(null); }
    private void beginPath() { gc.beginPath(); gc.moveTo(currentPath.get(0).getX(), currentPath.get(0).getY()); for(Point2D p : currentPath) gc.lineTo(p.getX(), p.getY()); }

    // --- UI SETUP ---
    private void updateInterface() {
        if ("ATTACKER".equals(myRole)) {
            long now = System.currentTimeMillis();
            for (MonsterType type : MonsterType.values()) {
                Button btn = attackerButtons.get(type);
                if (btn != null) {
                    long cooldownEnd = cooldowns.getOrDefault(type, 0L) + type.cooldown * 1000L;
                    if (now < cooldownEnd) {
                        btn.setText(type.name() + "\n" + (cooldownEnd - now) / 1000 + "s"); btn.setDisable(true); btn.setOpacity(0.5);
                    } else {
                        btn.setText(type.name() + "\n(" + type.cooldown + "s)"); btn.setDisable(false); btn.setOpacity(1.0);
                    }
                }
            }
        }
        if ("DEFENDER".equals(myRole) && buildProgressBar != null) {
            long now = System.currentTimeMillis();
            buildProgressBar.setProgress(Math.min(1.0, (double)(now - lastBuildTime) / BUILD_COOLDOWN_MS));
        }
    }

    private void setupRoleSelection() {
        VBox menu = new VBox(20); menu.setAlignment(Pos.CENTER);
        menu.setStyle("-fx-background-color: rgba(0,0,0,0.9);");
        Label title = new Label("NEON KINGDOM"); title.setTextFill(NEON_CYAN); title.setFont(Font.font("Impact", 60)); title.setEffect(new DropShadow(20, NEON_CYAN));

        HBox onlineBox = new HBox(20); onlineBox.setAlignment(Pos.CENTER);
        Button btnDef = createStyledButton("ONLINE: DEFENDER", NEON_PURPLE);
        btnDef.setOnAction(e -> startGame(false, "DEFENDER"));
        Button btnAtk = createStyledButton("ONLINE: ATTACKER", NEON_RED);
        btnAtk.setOnAction(e -> startGame(false, "ATTACKER"));
        onlineBox.getChildren().addAll(btnDef, btnAtk);

        HBox offlineBox = new HBox(20); offlineBox.setAlignment(Pos.CENTER);
        Button btnOffDef = createStyledButton("OFFLINE: DEFENDER (vs AI)", NEON_GREEN);
        btnOffDef.setOnAction(e -> startGame(true, "DEFENDER"));
        Button btnOffAtk = createStyledButton("OFFLINE: ATTACKER (vs AI)", NEON_ORANGE);
        btnOffAtk.setOnAction(e -> startGame(true, "ATTACKER"));
        offlineBox.getChildren().addAll(btnOffDef, btnOffAtk);

        menu.getChildren().addAll(title, new Label("--- ONLINE ---"), onlineBox, new Label("--- OFFLINE ---"), offlineBox);
        rootPane.getChildren().add(menu);
    }

    private void setupGameHUD() {
        topHud = new HBox(30); topHud.setPadding(new Insets(15)); topHud.setAlignment(Pos.CENTER);
        topHud.setStyle("-fx-background-color: rgba(20,20,30,0.8); -fx-background-radius: 0 0 20 20;");
        topHud.setMaxHeight(60); StackPane.setAlignment(topHud, Pos.TOP_CENTER);

        // Thêm nút Pause vào HUD
        Button btnPause = createStyledButton("||", Color.YELLOW);
        btnPause.setPrefWidth(40);
        btnPause.setOnAction(e -> togglePause());

        rootPane.getChildren().addAll(topHud, btnPause);
        StackPane.setAlignment(btnPause, Pos.TOP_RIGHT);
        StackPane.setMargin(btnPause, new Insets(10));
    }

    private void updateHUD() {
        if (topHud == null) return; topHud.getChildren().clear();
        long elapsed = (System.currentTimeMillis() - levelStartTime) / 1000;
        long remaining = Math.max(0, LEVEL_DURATION_SECONDS - elapsed);
        String timeStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
        topHud.getChildren().addAll(createHUDLabel("TIME: " + timeStr, remaining < 30 ? NEON_RED : NEON_GREEN), createHUDLabel("CORE: " + baseHp, baseHp<500?NEON_RED:NEON_GREEN));
        if ("DEFENDER".equals(myRole)) {
            Label lblBuild = createHUDLabel("BUILD: " + selectedTower.name, selectedTower.color);
            buildProgressBar = new ProgressBar(1.0); buildProgressBar.setPrefWidth(100); buildProgressBar.setStyle("-fx-accent: " + toHex(NEON_CYAN) + ";");
            topHud.getChildren().addAll(lblBuild, buildProgressBar);
        }
        topHud.getChildren().add(createHUDLabel(isOfflineMode ? "[OFFLINE]" : "[ONLINE]", Color.GRAY));
    }

    private void createAttackerPanel() {
        attackerPanel = new VBox(15); attackerPanel.setPadding(new Insets(20)); attackerPanel.setAlignment(Pos.CENTER_LEFT);
        attackerPanel.setMaxWidth(160); attackerPanel.setStyle("-fx-background-color: rgba(20,20,30,0.9); -fx-background-radius: 0 20 20 0; -fx-border-color: #e94560; -fx-border-width: 0 2 2 0;");
        StackPane.setAlignment(attackerPanel, Pos.CENTER_LEFT);
        Label lbl = new Label("UNITS"); lbl.setTextFill(NEON_RED); lbl.setFont(Font.font("Impact", 20)); attackerPanel.getChildren().add(lbl);
        for(MonsterType t : MonsterType.values()) {
            Button b = createStyledButton(t.name() + "\n(" + t.cooldown + "s)", t.color);
            b.setPrefWidth(120); b.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
            b.setOnAction(e -> spawnMonster(t));
            attackerButtons.put(t, b); attackerPanel.getChildren().add(b);
        }
        rootPane.getChildren().add(attackerPanel);
    }

    // --- DRAW HELPERS ---
    private void drawBackgroundGrid() {
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, BG_DARK), new Stop(1, BG_LIGHT)));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.setStroke(Color.rgb(255,255,255,0.02)); gc.setLineWidth(1);
        for(int i=0;i<WIDTH;i+=40) gc.strokeLine(i,0,i,HEIGHT); for(int i=0;i<HEIGHT;i+=40) gc.strokeLine(0,i,WIDTH,i);
    }
    private void drawBase(double x, double y) {
        gc.setEffect(new DropShadow(15, NEON_CYAN));
        gc.setFill(Color.web("#333")); gc.fillRect(x-50, y-40, 100, 80);
        gc.setFill(new LinearGradient(0,0,0,1, true, CycleMethod.NO_CYCLE, new Stop(0, NEON_CYAN), new Stop(1, Color.TRANSPARENT)));
        gc.fillOval(x-30, y-20, 60, 60);
        gc.save(); gc.translate(x, y+10); gc.rotate(animationTime * 50); gc.setStroke(Color.WHITE); gc.setLineWidth(3); gc.strokeRect(-15, -15, 30, 30); gc.restore(); gc.setEffect(null);
    }
    private void drawSoldier(double x, double y) {
        gc.setFill(NEON_CYAN.darker()); gc.fillArc(x-10, y-15, 20, 20, 0, 180, ArcType.ROUND);
        gc.setFill(NEON_CYAN); gc.fillRect(x-12, y-5, 24, 15);
        gc.setStroke(Color.WHITE); gc.setLineWidth(2); gc.strokeLine(x+5, y-10, x+15, y+5);
    }
    private void drawMonster(Monster m) {
        double x = m.x; double y = m.y; Color c = m.type.color;
        switch (m.type) {
            case GOBLIN: gc.setFill(c); gc.fillOval(x-15, y-15, 30, 30); gc.fillPolygon(new double[]{x-15, x-25, x-15}, new double[]{y-5, y-15, y-25}, 3); gc.fillPolygon(new double[]{x+15, x+25, x+15}, new double[]{y-5, y-15, y-25}, 3); break;
            case ORC: gc.setFill(c); gc.fillRect(x-20, y-25, 40, 50); gc.setFill(Color.GRAY); gc.fillRect(x-25, y-30, 15, 20); gc.fillRect(x+10, y-30, 15, 20); break;
            case SHAMAN: gc.setFill(c.darker()); gc.fillPolygon(new double[]{x-20, x+20, x}, new double[]{y+20, y+20, y-30}, 3); gc.setStroke(c); gc.setLineWidth(3); gc.strokeLine(x+15, y+20, x+25, y-20); gc.setEffect(new Glow(1)); gc.setFill(c.brighter()); gc.fillOval(x+20, y-25, 10, 10); gc.setEffect(null); break;
            case BOSS: gc.setFill(c.darker()); gc.fillOval(x-30, y-40, 60, 80); gc.setFill(Color.BLACK); gc.fillPolygon(new double[]{x-10, x+10, x}, new double[]{y-40, y-40, y-60}, 3); gc.fillPolygon(new double[]{x-30, x-10, x-20}, new double[]{y-20, y-20, y-40}, 3); gc.fillPolygon(new double[]{x+10, x+30, x+20}, new double[]{y-20, y-20, y-40}, 3); gc.setFill(Color.YELLOW); gc.fillOval(x-15, y-20, 10, 5); gc.fillOval(x+5, y-20, 10, 5); break;
        }
    }
    private Button createStyledButton(String text, Color color) {
        Button btn = new Button(text); String hex = toHex(color);
        String style = "-fx-background-color:rgba(0,0,0,0.5);-fx-border-color:"+hex+";-fx-border-width:2;-fx-border-radius:10;-fx-text-fill:white;-fx-font-family:'Consolas';-fx-font-weight:bold;-fx-font-size:13px;-fx-cursor:hand;";
        btn.setStyle(style);
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color:"+hex+";-fx-background-radius:10;-fx-border-color:"+hex+";-fx-text-fill:black;"+style.substring(style.indexOf("-fx-font-family"))));
        btn.setOnMouseExited(e -> btn.setStyle(style)); return btn;
    }
    private Label createHUDLabel(String text, Color color) { Label l = new Label(text); l.setTextFill(color); l.setFont(Font.font("Consolas", FontWeight.BOLD, 16)); l.setEffect(new DropShadow(5, color)); return l; }
    private void renderModernBar(double x, double y, int cur, int max, double w, Color c) { gc.setFill(Color.rgb(0,0,0,0.7)); gc.fillRoundRect(x-w/2, y, w, 5, 2, 2); gc.setFill(c); gc.fillRoundRect(x-w/2, y, w * Math.max(0, (double)cur/max), 5, 2, 2); }
    private String toHex(Color c) { return String.format("#%02X%02X%02X", (int)(c.getRed()*255),(int)(c.getGreen()*255),(int)(c.getBlue()*255)); }

    // --- 6. NETWORK ---
    private void connect() { StandardWebSocketClient c = new StandardWebSocketClient(); WebSocketStompClient sc = new WebSocketStompClient(c); sc.setMessageConverter(new MappingJackson2MessageConverter()); try { sc.connectAsync("ws://localhost:8080/ws-game", new SessionHandler()).get(); } catch (Exception e) { message = "Server Disconnected!"; } }
    private void sendAction(String type, String data) { if(session!=null) session.send("/app/action", new GameAction(myRole, type, data)); }
    private class SessionHandler extends StompSessionHandlerAdapter { public void afterConnected(StompSession s, StompHeaders h) { session = s; session.subscribe("/topic/game-progress", new StompFrameHandler() { public Type getPayloadType(StompHeaders h) { return GameAction.class; } public void handleFrame(StompHeaders h, Object p) { Platform.runLater(() -> processAction((GameAction)p)); } }); } }
    private void processAction(GameAction a) { String[] d = a.getData().split(","); switch(a.getActionType()) { case "SYNC_MAP": currentPath.clear(); monsters.clear(); towers.clear(); projectiles.clear(); soldiers.clear(); for(String s : a.getData().split(",")) { String[] xy = s.split(":"); if(xy.length==2) currentPath.add(new Point2D(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]))); } break; case "NEW_LEVEL": levelStarted = true; levelStartTime = System.currentTimeMillis(); break; case "SPAWN": if(!currentPath.isEmpty()) monsters.add(new Monster(MonsterType.valueOf(a.getData()), currentPath)); break; case "BUILD": TowerType t = TowerType.valueOf(d[0]); double x = Double.parseDouble(d[1]), y = Double.parseDouble(d[2]); towers.add(new Tower(t,x,y)); if(t==TowerType.BARRACKS) { soldiers.add(new Soldier(x+20,y, towers.get(towers.size()-1))); soldiers.add(new Soldier(x-20,y, towers.get(towers.size()-1))); soldiers.add(new Soldier(x,y+20, towers.get(towers.size()-1))); } break; case "SYNC_HP": baseHp = Integer.parseInt(a.getData()); if(baseHp<=0) handleDefeat(); break; case "GAME_OVER": handleDefeat(); break; case "VICTORY": handleVictory(); break; } }

    // --- CLASSES ---
    class Monster { MonsterType type; double x,y; int hp, pathIdx=0; boolean finished=false; long lastAtk=0; Soldier engaging=null; public Monster(MonsterType t, List<Point2D> p) { this.type=t; this.hp=t.hp; this.x=p.get(0).getX(); this.y=p.get(0).getY(); } public double dist(double ox, double oy) { return Math.sqrt(Math.pow(x-ox,2)+Math.pow(y-oy,2)); } public void takeDamage(int d, String type) { double m=1.0; if(type.equals("PHYSICAL")) m=(100-this.type.armor)/100.0; if(type.equals("MAGIC")) m=(100-this.type.magicResist)/100.0; this.hp-=d*m; } }
    class Soldier { double x,y; int hp=100; int level=1; long lastAtk=0; Monster engaging=null; Tower owner; public Soldier(double x, double y, Tower t){this.x=x;this.y=y;this.owner=t;this.level=t.level;} }

    class Tower {
        TowerType type; double x,y; long lastAtk=0; Monster target; int level = 1;
        public Tower(TowerType t, double x, double y){this.type=t;this.x=x;this.y=y;}
        public double dist(double ox, double oy){return Math.sqrt(Math.pow(x-ox,2)+Math.pow(y-oy,2));}
        public void upgrade() { if(level<3) level++; }
        public int getDamage() { return type.damage + (level * 10); } // Lv tăng dame
        public double getRange() { return type.range + (level * 20); } // Lv tăng tầm
        public double getCooldown() { return Math.max(0.1, type.cooldown - (level * 0.1)); } // Lv giảm hồi chiêu
    }
    class Projectile { double sx,sy,ex,ey; Color c; public Projectile(double sx,double sy,double ex,double ey, Color c){this.sx=sx;this.sy=sy;this.ex=ex;this.ey=ey;this.c=c;} }
}