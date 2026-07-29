package botamochi129.bte.mod.screen;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.packet.PacketUpdateStraightNodeAngle;
import botamochi129.bte.mod.registry.BTERegistryClient;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.mod.Init;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.CheckboxWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.SliderWidgetExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.tool.TextCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StraightNodeAngleScreen extends ScreenExtension {

    private static final double UNBOUND_SENTINEL = -129129.0D;

    private final BlockPos blockPos;
    private final World world;

    private boolean isBound;
    private boolean isConnected;
    private double currentAngle;

    private ButtonWidgetExtension btnReturn;
    private ButtonWidgetExtension btnMode;
    private ButtonWidgetExtension btnUnbind;
    private CheckboxWidgetExtension chkExactMode;

    private SliderWidgetExtension slider;
    private TextFieldWidgetExtension textField;
    private boolean sliderMode = true;

    private static final double SIMPLE_MAX_ANGLE = 180.0;
    private static final double SIMPLE_MIN_ANGLE = 0.0;
    private static final double EXACT_MAX_ANGLE = 180.0;
    private static final double EXACT_MIN_ANGLE = -180.0;

    private boolean isExactMode = false;

    public StraightNodeAngleScreen(BlockPos blockPos, World world) {
        super("Straight Node Angle");
        this.blockPos = blockPos;
        this.world = world;

        StraightNodeBlockEntity be = getBE();
        if (be != null) {
            this.isBound = be.isBound();
            this.currentAngle = be.isBound() ? be.getAngleDegrees() : UNBOUND_SENTINEL;
            this.isConnected = be.isConnected();
        } else {
            this.isBound = false;
            this.currentAngle = UNBOUND_SENTINEL;
            this.isConnected = false;
        }
    }

    @Override
    protected void init2() {
        super.init2();
        int cx = getWidthMapped() / 2;
        int cy = getHeightMapped() / 2;
        int w = Math.min(getWidthMapped() - 40, 380);

        double initialDisplay = getInitialDisplayAngle();

        btnReturn = new ButtonWidgetExtension(20, 20, 20, 20, "X", btn -> onClose2());
        addChild(new ClickableWidget(btnReturn));

        btnMode = new ButtonWidgetExtension(cx + w / 2 - 40, cy + 80, 40, 20, "⇄", btn -> switchMode());
        addChild(new ClickableWidget(btnMode));

        btnUnbind = new ButtonWidgetExtension(cx - w / 2, cy + 80, 80, 20, "Unbind", btn -> unbind());
        btnUnbind.setActiveMapped(isBound);
        addChild(new ClickableWidget(btnUnbind));

        chkExactMode = new CheckboxWidgetExtension(
                cx - w / 2 + 90, cy + 85, 200, 20,
                "Exact Angle",
                isExactMode,
                isChecked -> {
                    isExactMode = isChecked;
                    updateModeUI();
                }
        );
        addChild(new ClickableWidget(chkExactMode));

        // 【修正】初期メッセージとして角度を表示
        slider = new SliderWidgetExtension(cx - w / 2, cy + 20, w, 20, String.format("%.1f°", initialDisplay)) {
            @Override
            public void applyValue2() {
                double val = this.getValueMapped();
                double min = isExactMode ? EXACT_MIN_ANGLE : SIMPLE_MIN_ANGLE;
                double max = isExactMode ? EXACT_MAX_ANGLE : SIMPLE_MAX_ANGLE;

                double newUIAngle = min + (val * (max - min));
                double newInternalAngle = resolveInternalAngle(newUIAngle);

                if (!isBound || newInternalAngle != currentAngle) {
                    isBound = true;
                    currentAngle = newInternalAngle;

                    // 【修正】スライダー操作中にボタン上へ角度をリアルタイム表示
                    this.setMessage2(Text.of(String.format("%.1f°", newUIAngle)));

                    // テキストフィールド側も内部状態として同期
                    textField.setText2(String.format("%.1f", newUIAngle));
                    updateUIState();
                    apply();
                }
            }

            @Override
            protected void updateMessage2() {
            }
        };
        slider.setActiveMapped(true);
        addChild(new ClickableWidget(slider));

        textField = new TextFieldWidgetExtension(cx - w / 2, cy + 20, w, 20,
                isBound ? String.format("%.1f", initialDisplay) : "0.0",
                7, TextCase.DEFAULT, null, null);
        textField.setChangedListener2(this::onTextChanged);
        addChild(new ClickableWidget(textField));

        applyModeVisibility();
        updateModeUI();
    }

    private void switchMode() {
        sliderMode = !sliderMode;
        applyModeVisibility();
    }

    private void applyModeVisibility() {
        slider.setVisibleMapped(sliderMode);
        textField.setVisible2(!sliderMode);
        btnMode.setMessage2(Text.of(sliderMode ? "⇄" : "📝"));
        chkExactMode.setMessage2(Text.of(
                isExactMode ? "Exact Angle (-180° to 180°)" : "Simple Angle (0° to 180°)"
        ));
    }

    private void updateModeUI() {
        double currentDisplay = isBound ? getInitialDisplayAngle() : 0.0;
        slider.setValueMapped(getSliderValueFromAngle(currentDisplay));

        // 【修正】モード切り替え時などもスライダーの表示を更新
        slider.setMessage2(Text.of(String.format("%.1f°", currentDisplay)));

        if (sliderMode) {
            textField.setText2(String.format("%.1f", currentDisplay));
        }
        applyModeVisibility();
    }

    private double getInitialDisplayAngle() {
        if (!isBound) return 0.0;
        return isExactMode ? toExactUI(currentAngle) : toSimpleUI(currentAngle);
    }

    // 【修正】接続先に対する相対的な曲がり具合 (0〜180度) を返す
    private double toSimpleUI(double internalAngle) {
        List<BlockPos> connectedPositions = findConnectedNodePositions();
        if (connectedPositions.isEmpty()) return 0.0;

        // 簡易モードでは、代表として最初の接続先を基準にする
        BlockPos target = connectedPositions.get(0);
        double geoAngle = Math.toDegrees(Math.atan2(
                target.getZ() - this.blockPos.getZ(),
                target.getX() - this.blockPos.getX()
        ));
        geoAngle = normalize360(geoAngle);

        // 内部角度と幾何学的な方向との差を計算 (0〜180度の範囲に収める)
        double diff = Math.abs(internalAngle - geoAngle) % 360.0;
        if (diff > 180.0) diff = 360.0 - diff;

        return diff;
    }

    private static double toExactUI(double internalAngle) {
        double angle = internalAngle % 360.0;
        if (angle > 180.0) angle -= 360.0;
        return angle;
    }

    private double getSliderValueFromAngle(double angle) {
        double min = isExactMode ? EXACT_MIN_ANGLE : SIMPLE_MIN_ANGLE;
        double max = isExactMode ? EXACT_MAX_ANGLE : SIMPLE_MAX_ANGLE;
        return (angle - min) / (max - min);
    }

    private void onTextChanged(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            double uiValue = Double.parseDouble(text);
            double min = isExactMode ? EXACT_MIN_ANGLE : SIMPLE_MIN_ANGLE;
            double max = isExactMode ? EXACT_MAX_ANGLE : SIMPLE_MAX_ANGLE;

            double clampedUI = Math.max(min, Math.min(max, uiValue));
            double newInternalAngle = resolveInternalAngle(clampedUI);

            if (!isBound || newInternalAngle != currentAngle) {
                isBound = true;
                currentAngle = newInternalAngle;
                slider.setValueMapped(getSliderValueFromAngle(clampedUI));

                // 【修正】テキスト入力時にもスライダー表示を更新
                slider.setMessage2(Text.of(String.format("%.1f°", clampedUI)));
                textField.setText2(String.format("%.1f", clampedUI));

                updateUIState();
                apply();
                textField.setEditableColor2(0xFFFFFFFF);
            }
        } catch (NumberFormatException e) {
            textField.setEditableColor2(0xFFFF0000);
        }
    }

    private void unbind() {
        this.currentAngle = UNBOUND_SENTINEL;
        this.isBound = false;
        updateUIState();
        textField.setText2("Unbound");
        slider.setMessage2(Text.of("0.0°"));
        BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, UNBOUND_SENTINEL));
    }

    private void apply() {
        BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, currentAngle));
    }

    private void updateUIState() {
        if (btnUnbind != null) btnUnbind.setActiveMapped(isBound);
    }

    private double resolveInternalAngle(double uiAngle) {
        if (isExactMode) {
            return toInternalFromExact(uiAngle);
        } else {
            return resolveSimpleAngle(uiAngle);
        }
    }

    private double resolveSimpleAngle(double simpleUIAngle) {
        List<BlockPos> connectedPositions = findConnectedNodePositions();
        if (connectedPositions.isEmpty()) {
            return simpleUIAngle;
        }

        double baseAngle = isBound ? currentAngle : 0.0;
        double bestCand = simpleUIAngle;
        double minTotalDiff = Double.MAX_VALUE;

        for (BlockPos connectedPos : connectedPositions) {
            double geoAngle = Math.toDegrees(Math.atan2(
                    connectedPos.getZ() - this.blockPos.getZ(),
                    connectedPos.getX() - this.blockPos.getX()
            ));
            geoAngle = normalize360(geoAngle);

            double cand1 = normalize360(geoAngle + simpleUIAngle);
            double cand2 = normalize360(geoAngle - simpleUIAngle);

            double diff1 = getAngleDifference(baseAngle, cand1);
            double diff2 = getAngleDifference(baseAngle, cand2);

            double geoDiff1 = getAngleDifference(geoAngle, cand1);
            double geoDiff2 = getAngleDifference(geoAngle, cand2);

            double score1 = diff1 + (geoDiff1 > 90.0 ? 180.0 : 0.0);
            double score2 = diff2 + (geoDiff2 > 90.0 ? 180.0 : 0.0);

            double bestForThisConnection = (score1 <= score2) ? cand1 : cand2;
            double totalDiff = getAngleDifference(baseAngle, bestForThisConnection);

            if (totalDiff < minTotalDiff) {
                minTotalDiff = totalDiff;
                bestCand = bestForThisConnection;
            }
        }

        return bestCand;
    }

    private double getAngleDifference(double a1, double a2) {
        double diff = Math.abs(a1 - a2) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private static double toInternalFromExact(double exactUI) {
        double angle = exactUI % 360.0;
        if (angle < 0.0) angle += 360.0;
        return angle;
    }

    private StraightNodeBlockEntity getBE() {
        BlockEntity raw = world.getBlockEntity(blockPos);
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

        graphicsHolder.drawCenteredText("Straight Node Configuration", cx, cy - 40, 0xFFFFFF);

        String hint = isExactMode
                ? "Exact: -180°(Right) to 180°(Left), 0°=Straight"
                : "Simple: 0°(Straight) to 180°(U-Turn), Auto-direction";
        graphicsHolder.drawCenteredText(hint, cx, cy - 28, 0xAAAAAA);

        String status;
        int statusColor;
        if (isConnected) {
            status = "Status: Connected";
            statusColor = 0x55FF55;
        } else if (isBound) {
            status = "Status: Bound";
            statusColor = 0xFFFF55;
        } else {
            status = "Status: Unbound";
            statusColor = 0xFF5555;
        }
        graphicsHolder.drawCenteredText(status, cx, cy - 15, statusColor);
    }

    @Override
    public boolean isPauseScreen2() {
        return false;
    }

    private List<BlockPos> findConnectedNodePositions() {
        List<BlockPos> result = new ArrayList<>();
        try {
            Data data = LoaderImpl.getDataForWorld(world);
            if (data == null) return result;

            Position currentPos = Init.blockPosToPosition(this.blockPos);
            if (currentPos == null) return result;

            Map<Position, Rail> connectedMap = data.positionsToRail.get(currentPos);
            if (connectedMap != null) {
                for (Position targetPos : connectedMap.keySet()) {
                    result.add(Init.positionToBlockPos(targetPos));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static double normalize360(double angle) {
        angle = angle % 360.0;
        if (angle < 0.0) angle += 360.0;
        return angle;
    }
}