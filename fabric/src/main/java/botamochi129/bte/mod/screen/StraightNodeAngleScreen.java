package botamochi129.bte.mod.screen;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.packet.PacketUpdateStraightNodeAngle;
import botamochi129.bte.mod.registry.BTERegistryClient;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.SliderWidgetExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.tool.TextCase;

public class StraightNodeAngleScreen extends ScreenExtension {
    private final BlockPos blockPos;
    private final World world;

    private boolean isBound;
    private boolean isConnected;
    private double currentAngle;

    // UI Components
    private ButtonWidgetExtension btnReturn;
    private ButtonWidgetExtension btnMode;
    private ButtonWidgetExtension btnUnbind;

    private SliderWidgetExtension slider;
    private TextFieldWidgetExtension textField;
    private boolean sliderMode = true; // デフォルトはスライダーモード

    private static final double MIN_ANGLE = 0.0;
    private static final double MAX_ANGLE = 179.9;
    private static final int SLIDER_STEPS = 1800; // 0.1度刻み

    public StraightNodeAngleScreen(BlockPos blockPos, World world) {
        super("Straight Node Angle");
        this.blockPos = blockPos;
        this.world = world;

        StraightNodeBlockEntity be = getBE();
        if (be != null) {
            this.isBound = be.isBound();
            this.currentAngle = be.isBound() ? clampNormalize(be.getAngleDegrees()) : 0.0;
            this.isConnected = be.isConnected();
        } else {
            this.isBound = false;
            this.currentAngle = 0.0;
            this.isConnected = false;
        }
    }

    @Override
    protected void init2() {
        super.init2();
        int cx = getWidthMapped() / 2;
        int cy = getHeightMapped() / 2;
        int w = Math.min(getWidthMapped() - 40, 380);

        // 1. 戻るボタン (左上)
        btnReturn = new ButtonWidgetExtension(20, 20, 20, 20, "X", btn -> onClose2());
        addChild(new ClickableWidget(btnReturn));

        // 2. モード切り替えボタン (⇄)
        btnMode = new ButtonWidgetExtension(cx + w / 2 - 40, cy + 60, 40, 20, "⇄", btn -> switchMode());
        addChild(new ClickableWidget(btnMode));

        // 3. Unbind ボタン (左下)
        btnUnbind = new ButtonWidgetExtension(cx - w / 2, cy + 60, 80, 20, "Unbind", btn -> unbind());
        // ANTE準拠: Bound かつ 未接続(Unconnected) の場合のみアクティブ
        btnUnbind.setActiveMapped(isBound && !isConnected);
        addChild(new ClickableWidget(btnUnbind));

        // 4. スライダー (モード切り替え対応)
        // 値は 0.0 ~ 1.0 で管理し、内部で 0 ~ 179.9 に変換する
        slider = new SliderWidgetExtension(cx - w / 2, cy + 20, w, 20, "", currentAngle / 180.0, (value) -> {
            double newAngle = clampNormalize(value * 180.0);
            if (newAngle != currentAngle) {
                currentAngle = newAngle;
                textField.setText2(String.format("%.1f", currentAngle));
                apply(); // 即座にサーバーへ送信 (ANTEの bindAngle に相当)
            }
        });
        addChild(new ClickableWidget(slider));

        // 5. テキストフィールド (モード切り替え対応)
        textField = new TextFieldWidgetExtension(cx - w / 2, cy + 20, w, 20,
                String.format("%.1f", currentAngle), 6, TextCase.DEFAULT, null, null);
        textField.setChangedListener2(this::onTextChanged);
        // 数値と小数点のみ許可
        textField.setFilter2(text -> text.matches("\\d*(\\.\\d*)?"));
        addChild(new ClickableWidget(textField));

        // 初期表示状態の適用
        applyModeVisibility();
    }

    private void switchMode() {
        sliderMode = !sliderMode;
        applyModeVisibility();
    }

    private void applyModeVisibility() {
        slider.setVisibleMapped(sliderMode);
        textField.setVisible2(!sliderMode);
        btnMode.setMessage2(Text.of(sliderMode ? "⇄" : "📝")); // モードアイコン切り替え
    }

    private void onTextChanged(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            double value = Double.parseDouble(text);
            double clamped = clampNormalize(value);
            if (clamped != currentAngle) {
                currentAngle = clamped;
                slider.setValue2(clamped / 180.0);
                apply(); // 即座にサーバーへ送信
                textField.setTextColor2(0xFFFFFFFF); // 正常時は白
            }
        } catch (NumberFormatException e) {
            textField.setTextColor2(0xFFFF0000); // 無効な入力中は赤
        }
    }

    private void unbind() {
        // ANTE準拠: Unbind時は特殊な値を送信して状態をリセット
        BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, -114514.0));
        this.onClose2();
    }

    private void apply() {
        BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, currentAngle));
    }

    private static double clampNormalize(double angle) {
        angle = angle % 180.0;
        if (angle < 0.0) angle += 180.0;
        if (angle > MAX_ANGLE) angle = MAX_ANGLE;
        return angle;
    }

    private StraightNodeBlockEntity getBE() {
        org.mtr.mapping.holder.BlockEntity raw = world.getBlockEntity(blockPos);
        if (raw != null && raw.data instanceof StraightNodeBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);
        super.render(graphicsHolder, mouseX, mouseY, delta);

        int cx = getWidthMapped() / 2;
        int cy = getHeightMapped() / 2;

        // タイトル
        graphicsHolder.drawCenteredText("Straight Node Configuration", cx, cy - 40, 0xFFFFFF);

        // ステータス表示 (ANTE風の色分け)
        String status;
        int statusColor;
        if (isConnected) {
            status = "Status: Connected (Locked)";
            statusColor = 0x55FF55; // 緑
        } else if (isBound) {
            status = "Status: Bound (Adjustable)";
            statusColor = 0xFFFF55; // 黄
        } else {
            status = "Status: Unbound (Use Node Modifier)";
            statusColor = 0xFF5555; // 赤
        }
        graphicsHolder.drawCenteredText(status, cx, cy - 25, statusColor);

        // 現在の角度の大きな表示
        graphicsHolder.drawCenteredText(String.format("%.1f\u00B0", currentAngle), cx, cy, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen2() {
        return false;
    }
}