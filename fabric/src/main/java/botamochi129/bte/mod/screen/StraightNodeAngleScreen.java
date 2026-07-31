package botamochi129.bte.mod.screen;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.packet.PacketUpdateStraightNodeAngle;
import botamochi129.bte.mod.registry.BTERegistryClient;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.generated.lang.TranslationProvider;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.RailStyleSelectorScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StraightNodeAngleScreen extends ScreenExtension {

    private static final double UNBOUND_SENTINEL = -129129.0D;
    private static final int SQUARE_SIZE = 20;
    private static final int TEXT_PADDING = 4;
    private static final int TEXT_FIELD_PADDING = 4;

    private final BlockPos blockPos;
    private final World world;

    private boolean isBound;
    private boolean isConnected;
    private double currentAngle;

    // 角度調整用UI
    private ButtonWidgetExtension btnReturn;
    private ButtonWidgetExtension btnMode;
    private ButtonWidgetExtension btnUnbind;
    private CheckboxWidgetExtension chkExactMode;
    private SliderWidgetExtension slider;
    private TextFieldWidgetExtension textField;
    private boolean sliderMode = true;

    // レール属性調整用UI
    private Rail.Shape currentShape = Rail.Shape.QUADRATIC;
    private double currentRadius = 0.0;
    private double maxRadius = 0.0;

    private ButtonWidgetExtension btnShape;
    private ButtonWidgetExtension btnStyle;
    private ButtonWidgetExtension btnStyleFlip;

    private TextFieldWidgetExtension textFieldRadius;
    private ButtonWidgetExtension btnMinus10, btnMinus1, btnMinus01;
    private ButtonWidgetExtension btnPlus01, btnPlus1, btnPlus10;

    private static final double SIMPLE_MAX_ANGLE = 180.0;
    private static final double SIMPLE_MIN_ANGLE = 0.0;
    private static final double EXACT_MAX_ANGLE = 180.0;
    private static final double EXACT_MIN_ANGLE = -180.0;

    private boolean isExactMode = false;

    public StraightNodeAngleScreen(BlockPos blockPos, World world) {
        super("Straight Node Configuration");
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
        // 【修正】横幅を少し抑えて、小さな画面でもはみ出ないようにする
        int w = Math.min(getWidthMapped() - 40, 340);

        updateRailPropsFromConnected();
        double initialDisplay = getInitialDisplayAngle();

        // === 1. 基本ボタン (下部に集約) ===
        btnReturn = new ButtonWidgetExtension(20, 20, 20, 20, "X", btn -> onClose2());
        addChild(new ClickableWidget(btnReturn));

        // cy + 85 に配置して縦幅を圧縮
        btnMode = new ButtonWidgetExtension(cx + w / 2 - 40, cy + 85, 40, 20, "⇄", btn -> switchMode());
        addChild(new ClickableWidget(btnMode));

        btnUnbind = new ButtonWidgetExtension(cx - w / 2, cy + 85, 80, 20, TextHelper.literal("Unbind"), btn -> unbind());
        btnUnbind.setActiveMapped(isBound);
        addChild(new ClickableWidget(btnUnbind));

        chkExactMode = new CheckboxWidgetExtension(
                cx - w / 2 + 90, cy + 90, 200, 20,
                "Exact Angle",
                isExactMode,
                isChecked -> {
                    isExactMode = isChecked;
                    updateModeUI();
                }
        );
        addChild(new ClickableWidget(chkExactMode));

        // === 2. 角度調整セクション (上部に配置) ===
        // cy - 10 から開始し、高さを詰める
        slider = new SliderWidgetExtension(cx - w / 2, cy - 10, w, 20, String.format("%.1f°", initialDisplay)) {
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

                    double displayAngle = isExactMode ? toExactUI(newInternalAngle) : toSimpleUI(newInternalAngle);
                    this.setMessage2(Text.of(String.format("%.1f°", displayAngle)));
                    textField.setText2(String.format("%.1f", displayAngle));

                    updateUIState();
                    apply();
                }
            }
            @Override
            protected void updateMessage2() {}
        };
        slider.setValueMapped(getSliderValueFromAngle(initialDisplay));
        slider.setActiveMapped(true);
        addChild(new ClickableWidget(slider));

        textField = new TextFieldWidgetExtension(cx - w / 2, cy - 10, w, 20,
                isBound ? String.format("%.1f", initialDisplay) : "0.0",
                7, TextCase.DEFAULT, null, null);
        textField.setChangedListener2(this::onTextChanged);
        addChild(new ClickableWidget(textField));

        // === 3. レール属性調整セクション (Rail Properties) ===
        int railY = cy + 25; // 角度調整のすぐ下

        int btnW = w / 3;
        // 【修正】MTR標準の翻訳キーを使用
        btnShape = new ButtonWidgetExtension(cx - w / 2, railY, btnW, SQUARE_SIZE, TextHelper.literal(""), btn -> {
            currentShape = currentShape == Rail.Shape.QUADRATIC ? Rail.Shape.TWO_RADII : Rail.Shape.QUADRATIC;
            updateRailProperties(currentRadius, true);
        });
        addChild(new ClickableWidget(btnShape));

        btnStyle = new ButtonWidgetExtension(cx - w / 2 + btnW, railY, btnW, SQUARE_SIZE, TranslationProvider.GUI_MTR_RAIL_STYLES.getMutableText(), btn -> {
            List<Rail> rails = findConnectedRails();
            if (!rails.isEmpty()) {
                MinecraftClient.getInstance().openScreen(new Screen(RailStyleSelectorScreen.create(rails.get(0))));
            }
        });
        addChild(new ClickableWidget(btnStyle));

        btnStyleFlip = new ButtonWidgetExtension(cx - w / 2 + btnW * 2, railY, btnW, SQUARE_SIZE, TranslationProvider.GUI_MTR_FLIP_STYLES.getMutableText(), btn -> {
            flipStyles();
        });
        addChild(new ClickableWidget(btnStyleFlip));

        // Radius 調整
        int radiusY = railY + SQUARE_SIZE + TEXT_FIELD_PADDING; // cy + 49
        int radiusBtnW = SQUARE_SIZE * 2; // 40
        int textFieldW = w - radiusBtnW * 6 - TEXT_FIELD_PADDING; // 340 - 240 - 4 = 96 (十分な幅)

        textFieldRadius = new TextFieldWidgetExtension(cx - w / 2, radiusY, textFieldW, SQUARE_SIZE, 256, TextCase.DEFAULT, "[^\\d\\.]", "0");
        addChild(new ClickableWidget(textFieldRadius));

        btnMinus10 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + TEXT_FIELD_PADDING, radiusY, radiusBtnW, SQUARE_SIZE, TextHelper.literal("-10"), btn -> updateRailProperties(currentRadius - 10, true));
        btnMinus1 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + TEXT_FIELD_PADDING + radiusBtnW, radiusY, radiusBtnW, SQUARE_SIZE, TextHelper.literal("-1"), btn -> updateRailProperties(currentRadius - 1, true));
        btnMinus01 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + TEXT_FIELD_PADDING + radiusBtnW * 2, radiusY, radiusBtnW, SQUARE_SIZE, TextHelper.literal("-0.1"), btn -> updateRailProperties(currentRadius - 0.1, true));

        btnPlus01 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + TEXT_FIELD_PADDING + radiusBtnW * 3, radiusY, radiusBtnW, SQUARE_SIZE, TextHelper.literal("+0.1"), btn -> updateRailProperties(currentRadius + 0.1, true));
        btnPlus1 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + TEXT_FIELD_PADDING + radiusBtnW * 4, radiusY, radiusBtnW, SQUARE_SIZE, TextHelper.literal("+1"), btn -> updateRailProperties(currentRadius + 1, true));
        btnPlus10 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + TEXT_FIELD_PADDING + radiusBtnW * 5, radiusY, radiusBtnW, SQUARE_SIZE, TextHelper.literal("+10"), btn -> updateRailProperties(currentRadius + 10, true));

        addChild(new ClickableWidget(btnMinus10));
        addChild(new ClickableWidget(btnMinus1));
        addChild(new ClickableWidget(btnMinus01));
        addChild(new ClickableWidget(btnPlus01));
        addChild(new ClickableWidget(btnPlus1));
        addChild(new ClickableWidget(btnPlus10));

        textFieldRadius.setChangedListener2(text -> {
            try {
                double newRadius = Double.parseDouble(text);
                if (Math.abs(newRadius - currentRadius) > 0.001) {
                    updateRailProperties(newRadius, true);
                }
            } catch (Exception ignored) {}
        });

        applyModeVisibility();
        updateModeUI();
        updateRailProperties(currentRadius, false);
    }

    private void flipStyles() {
        List<Rail> connectedRails = findConnectedRails();
        if (connectedRails.isEmpty()) return;

        UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getInstance());
        for (Rail oldRail : connectedRails) {
            final ObjectArrayList<String> styles = oldRail.getStyles().stream().map(style -> {
                final boolean isForwards = style.endsWith("_1");
                final boolean isBackwards = style.endsWith("_2");
                if (isForwards || isBackwards) {
                    return style.substring(0, style.length() - 1) + (isForwards ? "2" : "1");
                } else {
                    return style;
                }
            }).collect(Collectors.toCollection(ObjectArrayList::new));

            request.addRail(Rail.copy(oldRail, styles));
        }
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));

        if (isBound) {
            BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, currentAngle));
        }
    }

    private void updateRailPropsFromConnected() {
        List<Rail> connectedRails = findConnectedRails();
        if (!connectedRails.isEmpty()) {
            Rail firstRail = connectedRails.get(0);
            currentShape = firstRail.railMath.getShape();
            currentRadius = firstRail.railMath.getVerticalRadius();
            maxRadius = firstRail.railMath.getMaxVerticalRadius();
        } else {
            currentShape = Rail.Shape.QUADRATIC;
            currentRadius = 0.0;
            maxRadius = 100.0;
        }
    }

    private void updateRailProperties(double newRadius, boolean sendPacket) {
        // 【修正】MTR標準の翻訳キーを使用
        btnShape.setMessage2(Text.cast((currentShape == Rail.Shape.QUADRATIC ? TranslationProvider.GUI_MTR_RAIL_SHAPE_QUADRATIC : TranslationProvider.GUI_MTR_RAIL_SHAPE_TWO_RADII).getMutableText()));

        currentRadius = Utilities.clamp(Utilities.round(newRadius, 2), 0, maxRadius);

        String radiusText = String.valueOf(currentRadius);
        if (!textFieldRadius.getText2().equals(radiusText)) {
            textFieldRadius.setText2(radiusText);
        }

        boolean hasRadiusControls = currentShape != Rail.Shape.QUADRATIC;
        btnMinus10.setVisibleMapped(hasRadiusControls);
        btnMinus1.setVisibleMapped(hasRadiusControls);
        btnMinus01.setVisibleMapped(hasRadiusControls);
        btnPlus01.setVisibleMapped(hasRadiusControls);
        btnPlus1.setVisibleMapped(hasRadiusControls);
        btnPlus10.setVisibleMapped(hasRadiusControls);

        btnMinus10.setActiveMapped(currentRadius > 0);
        btnMinus1.setActiveMapped(currentRadius > 0);
        btnMinus01.setActiveMapped(currentRadius > 0);
        btnPlus01.setActiveMapped(currentRadius < maxRadius);
        btnPlus1.setActiveMapped(currentRadius < maxRadius);
        btnPlus10.setActiveMapped(currentRadius < maxRadius);

        if (sendPacket) {
            applyRailPropertiesToServer();
        }
    }

    private void applyRailPropertiesToServer() {
        List<Rail> connectedRails = findConnectedRails();
        if (connectedRails.isEmpty()) return;

        UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getInstance());
        for (Rail oldRail : connectedRails) {
            Rail newRail = Rail.copy(oldRail, currentShape, currentRadius);
            request.addRail(newRail);
        }

        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));

        if (isBound) {
            BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, currentAngle));
        }
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
                isExactMode ? "Exact Angle (-180° to 180°)" : "Simple Angle (0° to 180°, Auto-select)"
        ));
    }

    private void updateModeUI() {
        double currentDisplay = isBound ? getInitialDisplayAngle() : 0.0;
        slider.setValueMapped(getSliderValueFromAngle(currentDisplay));

        double safeDisplayAngle = isBound ? (isExactMode ? toExactUI(currentAngle) : toSimpleUI(currentAngle)) : 0.0;
        slider.setMessage2(Text.of(String.format("%.1f°", safeDisplayAngle)));

        if (sliderMode) {
            textField.setText2(String.format("%.1f", safeDisplayAngle));
        }
        applyModeVisibility();
    }

    private double getInitialDisplayAngle() {
        if (!isBound) return 0.0;
        return isExactMode ? toExactUI(currentAngle) : toSimpleUI(currentAngle);
    }

    private double getSliderValueFromAngle(double displayAngle) {
        double min = isExactMode ? EXACT_MIN_ANGLE : SIMPLE_MIN_ANGLE;
        double max = isExactMode ? EXACT_MAX_ANGLE : SIMPLE_MAX_ANGLE;
        return (displayAngle - min) / (max - min);
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

                double displayAngle = isExactMode ? toExactUI(newInternalAngle) : toSimpleUI(newInternalAngle);
                String formattedText = String.format("%.1f", displayAngle);

                if (!text.equals(formattedText)) {
                    slider.setValueMapped(getSliderValueFromAngle(displayAngle));
                    slider.setMessage2(Text.of(String.format("%.1f°", displayAngle)));
                    textField.setText2(formattedText);
                }

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
        slider.setValueMapped(0.0);
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
            double angle = uiAngle % 360.0;
            if (angle < 0.0) angle += 360.0;
            return angle;
        } else {
            return resolveSimpleAngle(uiAngle);
        }
    }

    private double resolveSimpleAngle(double simpleUIAngle) {
        List<BlockPos> connectedPositions = findConnectedNodePositions();
        if (connectedPositions.isEmpty()) {
            return simpleUIAngle;
        }

        double baseAngle = isBound ? currentAngle : -1;

        double cand1 = normalize360(simpleUIAngle);
        double cand2 = normalize360(simpleUIAngle + 180.0);

        double bestCand = cand1;
        double minScore = Double.MAX_VALUE;

        for (double cand : new double[]{cand1, cand2}) {
            double score = 0;
            for (BlockPos connectedPos : connectedPositions) {
                double geoAngle = Math.toDegrees(Math.atan2(
                        connectedPos.getZ() - this.blockPos.getZ(),
                        connectedPos.getX() - this.blockPos.getX()
                ));
                geoAngle = normalize360(geoAngle);

                double diff = getAngleDifference(cand, geoAngle);

                if (baseAngle >= 0) {
                    score += getAngleDifference(cand, baseAngle) * 2.0;
                } else {
                    if (diff > 90.0) {
                        score += 180.0;
                    } else {
                        score += diff;
                    }
                }
            }
            if (score < minScore) {
                minScore = score;
                bestCand = cand;
            }
        }

        return bestCand;
    }

    private double getAngleDifference(double a1, double a2) {
        double diff = Math.abs(a1 - a2) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private static double toSimpleUI(double internalAngle) {
        double angle = internalAngle % 180.0;
        if (angle < 0.0) {
            angle += 180.0;
        }
        return angle;
    }

    private static double toExactUI(double internalAngle) {
        double angle = internalAngle % 360.0;
        if (angle > 180.0) {
            angle -= 360.0;
        }
        return angle;
    }

    private static double normalize360(double angle) {
        angle = angle % 360.0;
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
        int w = Math.min(getWidthMapped() - 40, 340);

        // 【修正】Y座標を全体的に上に詰め、はみ出しを防ぐ
        graphicsHolder.drawCenteredText("Straight Node Configuration", cx, cy - 60, 0xFFFFFF);

        String hint = isExactMode
                ? "Exact: -180°(West) to 180°(West), 0°=East"
                : "Simple: 0°(Straight) to 180°(U-Turn), Auto-selects best direction";
        graphicsHolder.drawCenteredText(hint, cx, cy - 48, 0xAAAAAA);

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
        graphicsHolder.drawCenteredText(status, cx, cy - 36, statusColor);

        double displayAngle = isBound ? (isExactMode ? toExactUI(currentAngle) : toSimpleUI(currentAngle)) : 0.0;
        String angleText = isBound
                ? String.format("Angle: %.1f\u00B0", displayAngle)
                : "Angle: Unbound";
        graphicsHolder.drawCenteredText(angleText, cx, cy - 24, 0xFFFFFF);

        // 区切り線
        GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(cx - w / 2, cy + 15, w, 1, 0x88888888);
        guiDrawing.finishDrawingRectangle();

        // Rail Properties ラベル
        graphicsHolder.drawText(TextHelper.literal("Rail Shape, Style & Radius"), cx - w / 2, cy + 20, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());
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

    private List<Rail> findConnectedRails() {
        List<Rail> result = new ArrayList<>();
        try {
            Data data = LoaderImpl.getDataForWorld(world);
            if (data == null) return result;

            Position currentPos = Init.blockPosToPosition(this.blockPos);
            if (currentPos == null) return result;

            Map<Position, Rail> connectedMap = data.positionsToRail.get(currentPos);
            if (connectedMap != null) {
                result.addAll(connectedMap.values());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}