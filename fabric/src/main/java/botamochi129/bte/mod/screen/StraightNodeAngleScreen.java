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
    private static final int SQUARE_SIZE = 18;
    private static final int TEXT_PADDING = 2;
    private static final int TEXT_FIELD_PADDING = 2;

    private final BlockPos blockPos;
    private final World world;
    private final BlockPos targetBlockPos;

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

    private List<Rail> connectedRails = new ArrayList<>();
    private int selectedRailIndex = 0;
    private ButtonWidgetExtension btnPrevRail, btnNextRail;
    private List<Position> connectedTargetPositions = new ArrayList<>();
    private boolean hasRails = false;

    private Rail.Shape currentShape = Rail.Shape.QUADRATIC;
    private double currentRadius = 0.0;
    private double maxRadius = 0.0;

    private ButtonWidgetExtension btnShape;
    private ButtonWidgetExtension btnStyle;
    private ButtonWidgetExtension btnStyleFlip;

    private TextFieldWidgetExtension textFieldRadius;
    private ButtonWidgetExtension btnMinus10, btnMinus1, btnMinus01;
    private ButtonWidgetExtension btnPlus01, btnPlus1, btnPlus10;

    private double offsetX = 0.0, offsetY = 0.0, offsetZ = 0.0;
    private boolean sliderModeX = true, sliderModeY = true, sliderModeZ = true;
    private SliderWidgetExtension sliderX, sliderY, sliderZ;
    private TextFieldWidgetExtension textFieldX, textFieldY, textFieldZ;
    private ButtonWidgetExtension btnModeX, btnModeY, btnModeZ;

    // ★ 追加: カント（ロール）用変数
    private double currentRoll = 0.0;
    private boolean sliderModeRoll = true;
    private SliderWidgetExtension sliderRoll;
    private TextFieldWidgetExtension textFieldRoll;
    private ButtonWidgetExtension btnModeRoll;

    private static final double SIMPLE_MAX_ANGLE = 180.0;
    private static final double SIMPLE_MIN_ANGLE = 0.0;
    private static final double EXACT_MAX_ANGLE = 180.0;
    private static final double EXACT_MIN_ANGLE = -180.0;

    private boolean isExactMode = false;

    public StraightNodeAngleScreen(BlockPos blockPos, World world, BlockPos targetPos) {
        super(TextHelper.translatable("gui.bte.angle_screen.title").getString());
        this.blockPos = blockPos;
        this.world = world;
        this.targetBlockPos = targetPos;

        StraightNodeBlockEntity be = getBE();
        if (be != null) {
            this.isBound = be.isBound();
            this.currentAngle = be.isBound() ? be.getAngleDegrees() : UNBOUND_SENTINEL;
            this.isConnected = be.isConnected();

            this.offsetX = be.getOffsetX();
            this.offsetY = be.getOffsetY();
            this.offsetZ = be.getOffsetZ();

            this.currentRoll = be.getRollDegrees(); // ★ 追加
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
        int w = Math.min(getWidthMapped() - 40, 360);
        int rowH = 18;

        connectedRails.clear();
        connectedTargetPositions.clear();
        try {
            Data data = LoaderImpl.getDataForWorld(world);
            if (data != null) {
                Position currentPos = Init.blockPosToPosition(this.blockPos);
                if (currentPos != null) {
                    Map<Position, Rail> connectedMap = data.positionsToRail.get(currentPos);
                    if (connectedMap != null) {
                        for (Map.Entry<Position, Rail> entry : connectedMap.entrySet()) {
                            connectedTargetPositions.add(entry.getKey());
                            connectedRails.add(entry.getValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        hasRails = !connectedRails.isEmpty();

        boolean foundTarget = false;
        if (this.targetBlockPos != null) {
            Position targetPos = Init.blockPosToPosition(this.targetBlockPos);
            for (int i = 0; i < connectedTargetPositions.size(); i++) {
                if (connectedTargetPositions.get(i).equals(targetPos)) {
                    this.selectedRailIndex = i;
                    foundTarget = true;
                    break;
                }
            }
        }

        if (!foundTarget) {
            org.mtr.mapping.holder.ClientPlayerEntity player = MinecraftClient.getInstance().getPlayerMapped();
            if (player != null && !connectedTargetPositions.isEmpty()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                float yaw = 0;
                yaw = player.getYaw(mc.getTickDelta());
                double radYaw = Math.toRadians(yaw);
                double lookX = -Math.sin(radYaw);
                double lookZ = Math.cos(radYaw);

                Position myPos = Init.blockPosToPosition(this.blockPos);
                double centerX = myPos.getX() + 0.5;
                double centerZ = myPos.getZ() + 0.5;

                double maxDot = -2.0;
                int bestIndex = 0;

                for (int i = 0; i < connectedTargetPositions.size(); i++) {
                    Position target = connectedTargetPositions.get(i);
                    double dx = (target.getX() + 0.5) - centerX;
                    double dz = (target.getZ() + 0.5) - centerZ;

                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0) {
                        dx /= len;
                        dz /= len;
                        double dot = lookX * dx + lookZ * dz;
                        if (dot > maxDot) {
                            maxDot = dot;
                            bestIndex = i;
                        }
                    }
                }
                this.selectedRailIndex = bestIndex;
            }
        }

        updateRailPropsFromConnected();
        double initialDisplay = getInitialDisplayAngle();

        slider = new SliderWidgetExtension(cx - w / 2, cy - 40, w - 24, rowH, String.format("%.1f°", initialDisplay)) {
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

        textField = new TextFieldWidgetExtension(cx - w / 2, cy - 40, w - 24, rowH,
                isBound ? String.format("%.1f", initialDisplay) : "0.0",
                7, TextCase.DEFAULT, null, null);
        textField.setChangedListener2(this::onTextChanged);
        addChild(new ClickableWidget(textField));

        btnMode = new ButtonWidgetExtension(cx + w / 2 - 22, cy - 40, 20, rowH, "⇄", btn -> switchMode());
        addChild(new ClickableWidget(btnMode));

        int railSelectY = cy - 18;
        btnPrevRail = new ButtonWidgetExtension(cx - w / 2, railSelectY, 20, rowH, "<", btn -> selectRail(selectedRailIndex - 1));
        btnNextRail = new ButtonWidgetExtension(cx + w / 2 - 20, railSelectY, 20, rowH, ">", btn -> selectRail(selectedRailIndex + 1));
        addChild(new ClickableWidget(btnPrevRail));
        addChild(new ClickableWidget(btnNextRail));

        int railY = cy + 4;
        int btnW = w / 3;

        btnShape = new ButtonWidgetExtension(cx - w / 2, railY, btnW, rowH, TextHelper.literal(""), btn -> {
            currentShape = currentShape == Rail.Shape.QUADRATIC ? Rail.Shape.TWO_RADII : Rail.Shape.QUADRATIC;
            updateRailProperties(currentRadius, true);
        });
        addChild(new ClickableWidget(btnShape));

        btnStyle = new ButtonWidgetExtension(cx - w / 2 + btnW, railY, btnW, rowH, TranslationProvider.GUI_MTR_RAIL_STYLES.getMutableText(), btn -> {
            if (!connectedRails.isEmpty()) {
                MinecraftClient.getInstance().openScreen(new Screen(RailStyleSelectorScreen.create(connectedRails.get(selectedRailIndex))));
            }
        });
        addChild(new ClickableWidget(btnStyle));

        btnStyleFlip = new ButtonWidgetExtension(cx - w / 2 + btnW * 2, railY, btnW, rowH, TranslationProvider.GUI_MTR_FLIP_STYLES.getMutableText(), btn -> flipStyles());
        addChild(new ClickableWidget(btnStyleFlip));

        int radiusY = railY + rowH + 4;
        int radiusBtnW = 24;
        int textFieldW = w - radiusBtnW * 6 - 6;

        textFieldRadius = new TextFieldWidgetExtension(cx - w / 2, radiusY, textFieldW, rowH, 256, TextCase.DEFAULT, "[^\\d\\.]", "0");
        addChild(new ClickableWidget(textFieldRadius));

        btnMinus10 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + 2, radiusY, radiusBtnW, rowH, TextHelper.literal("-10"), btn -> updateRailProperties(currentRadius - 10, true));
        btnMinus1 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + 2 + radiusBtnW, radiusY, radiusBtnW, rowH, TextHelper.literal("-1"), btn -> updateRailProperties(currentRadius - 1, true));
        btnMinus01 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + 2 + radiusBtnW * 2, radiusY, radiusBtnW, rowH, TextHelper.literal("-.1"), btn -> updateRailProperties(currentRadius - 0.1, true));

        btnPlus01 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + 2 + radiusBtnW * 3, radiusY, radiusBtnW, rowH, TextHelper.literal("+.1"), btn -> updateRailProperties(currentRadius + 0.1, true));
        btnPlus1 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + 2 + radiusBtnW * 4, radiusY, radiusBtnW, rowH, TextHelper.literal("+1"), btn -> updateRailProperties(currentRadius + 1, true));
        btnPlus10 = new ButtonWidgetExtension(cx - w / 2 + textFieldW + 2 + radiusBtnW * 5, radiusY, radiusBtnW, rowH, TextHelper.literal("+10"), btn -> updateRailProperties(currentRadius + 10, true));

        addChild(new ClickableWidget(btnMinus10)); addChild(new ClickableWidget(btnMinus1)); addChild(new ClickableWidget(btnMinus01));
        addChild(new ClickableWidget(btnPlus01)); addChild(new ClickableWidget(btnPlus1)); addChild(new ClickableWidget(btnPlus10));

        textFieldRadius.setChangedListener2(text -> {
            try {
                double newRadius = Double.parseDouble(text);
                if (Math.abs(newRadius - currentRadius) > 0.001) updateRailProperties(newRadius, true);
            } catch (Exception ignored) {}
        });

        int offY = hasRails ? radiusY + rowH + 10 : railSelectY;
        int mainW = w - 24;

        setupOffsetUI(cx, w, mainW, offY, rowH, 0);
        setupOffsetUI(cx, w, mainW, offY + rowH + 2, rowH, 1);
        setupOffsetUI(cx, w, mainW, offY + (rowH + 2) * 2, rowH, 2);

        // ★ 追加: カント（ロール）UIのセットアップ
        setupRollUI(cx, w, mainW, offY + (rowH + 2) * 3, rowH);

        // ★ 修正: ボトムボタンのY座標を1行分下にずらす
        int bottomY = offY + (rowH + 2) * 4 + 6;

        btnReturn = new ButtonWidgetExtension(cx - w / 2, bottomY, 20, rowH, "X", btn -> onClose2());
        addChild(new ClickableWidget(btnReturn));

        btnUnbind = new ButtonWidgetExtension(cx - w / 2 + 24, bottomY, 60, rowH, TextHelper.translatable("gui.bte.angle_screen.unbind"), btn -> unbind());
        btnUnbind.setActiveMapped(isBound);
        addChild(new ClickableWidget(btnUnbind));

        chkExactMode = new CheckboxWidgetExtension(cx - w / 2 + 90, bottomY, 200, rowH, TextHelper.translatable("gui.bte.angle_screen.exact_angle").getString(), isExactMode, isChecked -> {
            isExactMode = isChecked; updateModeUI();
        });
        addChild(new ClickableWidget(chkExactMode));

        applyModeVisibility();
        updateModeUI();
        updateRailProperties(currentRadius, false);
        updateOffsetUI();
        updateRollUI(); // ★ 追加
        updateRailSelectionUI();
    }

    // ★ 追加: カント（ロール）UIのセットアップメソッド
    private void setupRollUI(int cx, int w, int mainW, int y, int rowH) {
        SliderWidgetExtension s = new SliderWidgetExtension(cx - w / 2, y, mainW, rowH, "") {
            @Override
            public void applyValue2() {
                double val = this.getValueMapped() * 180.0 - 90.0; // 0.0〜1.0 -> -90.0〜90.0
                val = Math.round(val * 10.0) / 10.0; // 0.1度単位
                if (Math.abs(val - currentRoll) > 0.0001) updateRoll(val);
            }
            @Override
            protected void updateMessage2() {}
        };

        TextFieldWidgetExtension t = new TextFieldWidgetExtension(cx - w / 2, y, mainW, rowH, 10, TextCase.DEFAULT, "[^\\d\\.\\-]", "0");
        t.setChangedListener2(text -> {
            try {
                double val = Double.parseDouble(text);
                val = Math.max(-90.0, Math.min(90.0, val));
                if (Math.abs(val - currentRoll) > 0.0001) updateRoll(val);
            } catch (Exception ignored) {}
        });

        ButtonWidgetExtension b = new ButtonWidgetExtension(cx + w / 2 - 22, y, 20, rowH, "⇄", btn -> {
            sliderModeRoll = !sliderModeRoll;
            updateRollUI();
        });

        addChild(new ClickableWidget(s));
        addChild(new ClickableWidget(t));
        addChild(new ClickableWidget(b));

        sliderRoll = s;
        textFieldRoll = t;
        btnModeRoll = b;
    }

    private void updateRoll(double val) {
        currentRoll = val;
        updateRollUI();
        apply();
    }

    private void updateRollUI() {
        if (sliderRoll == null) return;
        sliderRoll.setVisibleMapped(sliderModeRoll);
        textFieldRoll.setVisible2(!sliderModeRoll);
        btnModeRoll.setMessage2(Text.of(sliderModeRoll ? "⇄" : "📝"));
        sliderRoll.setValueMapped((currentRoll + 90.0) / 180.0);
        sliderRoll.setMessage2(Text.of(String.format("Cant: %.1f°", currentRoll)));
        String newText = String.format("%.1f", currentRoll);
        if (!newText.equals(textFieldRoll.getText2())) textFieldRoll.setText2(newText);
    }

    private void selectRail(int index) {
        if (connectedRails.isEmpty()) return;
        selectedRailIndex = (index % connectedRails.size() + connectedRails.size()) % connectedRails.size();
        updateRailPropsFromConnected();
        updateRailProperties(currentRadius, false);
        updateRailSelectionUI();
    }

    private void updateRailSelectionUI() {
        boolean hasMultiple = hasRails && connectedRails.size() > 1;
        btnPrevRail.setVisibleMapped(hasMultiple);
        btnNextRail.setVisibleMapped(hasMultiple);
    }

    private void setupOffsetUI(int cx, int w, int mainW, int y, int rowH, int axis) {
        SliderWidgetExtension s = new SliderWidgetExtension(cx - w / 2, y, mainW, rowH, "") {
            @Override
            public void applyValue2() {
                double val = this.getValueMapped() * 2.0 - 1.0;
                val = Math.round(val / 0.05) * 0.05;
                double currentVal = (axis == 0) ? offsetX : (axis == 1) ? offsetY : offsetZ;
                if (Math.abs(val - currentVal) > 0.0001) updateOffset(axis, val);
            }
            @Override
            protected void updateMessage2() {}
        };

        TextFieldWidgetExtension t = new TextFieldWidgetExtension(cx - w / 2, y, mainW, rowH, 10, TextCase.DEFAULT, "[^\\d\\.\\-]", "0");
        t.setChangedListener2(text -> {
            try {
                double val = Double.parseDouble(text);
                val = Math.max(-1.0, Math.min(1.0, val));
                double currentVal = (axis == 0) ? offsetX : (axis == 1) ? offsetY : offsetZ;
                if (Math.abs(val - currentVal) > 0.0001) updateOffset(axis, val);
            } catch (Exception ignored) {}
        });

        ButtonWidgetExtension b = new ButtonWidgetExtension(cx + w / 2 - 22, y, 20, rowH, "⇄", btn -> {
            if (axis == 0) sliderModeX = !sliderModeX;
            else if (axis == 1) sliderModeY = !sliderModeY;
            else sliderModeZ = !sliderModeZ;
            updateOffsetUI();
        });

        addChild(new ClickableWidget(s));
        addChild(new ClickableWidget(t));
        addChild(new ClickableWidget(b));

        if (axis == 0) { sliderX = s; textFieldX = t; btnModeX = b; }
        else if (axis == 1) { sliderY = s; textFieldY = t; btnModeY = b; }
        else { sliderZ = s; textFieldZ = t; btnModeZ = b; }
    }

    private void updateOffset(int axis, double val) {
        if (axis == 0) offsetX = val;
        else if (axis == 1) offsetY = val;
        else offsetZ = val;
        updateOffsetUI();
        apply();
    }

    private void updateOffsetUI() {
        updateSingleOffsetUI(sliderX, textFieldX, btnModeX, sliderModeX, offsetX, "X");
        updateSingleOffsetUI(sliderY, textFieldY, btnModeY, sliderModeY, offsetY, "Y");
        updateSingleOffsetUI(sliderZ, textFieldZ, btnModeZ, sliderModeZ, offsetZ, "Z");
    }

    private void updateSingleOffsetUI(SliderWidgetExtension s, TextFieldWidgetExtension t, ButtonWidgetExtension b, boolean mode, double val, String axis) {
        if (s == null) return;
        s.setVisibleMapped(mode);
        t.setVisible2(!mode);
        b.setMessage2(Text.of(mode ? "⇄" : "📝"));
        s.setValueMapped((val + 1.0) / 2.0);
        s.setMessage2(Text.of(String.format("%s: %.2f", axis, val)));
        String newText = String.format("%.3f", val);
        if (!newText.equals(t.getText2())) t.setText2(newText);
    }

    private void flipStyles() {
        if (connectedRails.isEmpty()) return;
        Rail oldRail = connectedRails.get(selectedRailIndex);

        UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getInstance());
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
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
    }

    private void updateRailPropsFromConnected() {
        if (!connectedRails.isEmpty()) {
            if (selectedRailIndex >= connectedRails.size()) selectedRailIndex = 0;
            Rail selectedRail = connectedRails.get(selectedRailIndex);
            currentShape = selectedRail.railMath.getShape();
            currentRadius = selectedRail.railMath.getVerticalRadius();
            maxRadius = selectedRail.railMath.getMaxVerticalRadius();
        } else {
            currentShape = Rail.Shape.QUADRATIC;
            currentRadius = 0.0;
            maxRadius = 100.0;
        }
    }

    private void updateRailProperties(double newRadius, boolean sendPacket) {
        setRailPropsVisible(hasRails);
        btnShape.setMessage2((currentShape == Rail.Shape.QUADRATIC ? TranslationProvider.GUI_MTR_RAIL_SHAPE_QUADRATIC : TranslationProvider.GUI_MTR_RAIL_SHAPE_TWO_RADII).getText());

        currentRadius = Utilities.clamp(Utilities.round(newRadius, 2), 0, maxRadius);

        String radiusText = String.valueOf(currentRadius);
        if (!textFieldRadius.getText2().equals(radiusText)) {
            textFieldRadius.setText2(radiusText);
        }

        boolean hasRadiusControls = currentShape != Rail.Shape.QUADRATIC && hasRails;
        btnMinus10.setVisibleMapped(hasRadiusControls); btnMinus1.setVisibleMapped(hasRadiusControls); btnMinus01.setVisibleMapped(hasRadiusControls);
        btnPlus01.setVisibleMapped(hasRadiusControls); btnPlus1.setVisibleMapped(hasRadiusControls); btnPlus10.setVisibleMapped(hasRadiusControls);

        btnMinus10.setActiveMapped(currentRadius > 0); btnMinus1.setActiveMapped(currentRadius > 0); btnMinus01.setActiveMapped(currentRadius > 0);
        btnPlus01.setActiveMapped(currentRadius < maxRadius); btnPlus1.setActiveMapped(currentRadius < maxRadius); btnPlus10.setActiveMapped(currentRadius < maxRadius);

        if (sendPacket) applyRailPropertiesToServer();
    }

    private void setRailPropsVisible(boolean visible) {
        btnShape.setVisibleMapped(visible);
        btnStyle.setVisibleMapped(visible);
        btnStyleFlip.setVisibleMapped(visible);
        textFieldRadius.setVisibleMapped(visible);
    }

    private void applyRailPropertiesToServer() {
        if (connectedRails.isEmpty()) return;
        Rail oldRail = connectedRails.get(selectedRailIndex);

        UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getInstance());
        Rail newRail = Rail.copy(oldRail, currentShape, currentRadius);
        request.addRail(newRail);

        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
    }

    private void switchMode() {
        sliderMode = !sliderMode;
        applyModeVisibility();
    }

    private void applyModeVisibility() {
        slider.setVisibleMapped(sliderMode);
        textField.setVisible2(!sliderMode);
        btnMode.setMessage2(Text.of(sliderMode ? "⇄" : "📝"));
        chkExactMode.setMessage2(Text.cast(TextHelper.translatable(isExactMode ? "gui.bte.angle_screen.checkbox_exact" : "gui.bte.angle_screen.checkbox_simple")));
    }

    private void updateModeUI() {
        double currentDisplay = isBound ? getInitialDisplayAngle() : 0.0;
        slider.setValueMapped(getSliderValueFromAngle(currentDisplay));
        double safeDisplayAngle = isBound ? (isExactMode ? toExactUI(currentAngle) : toSimpleUI(currentAngle)) : 0.0;
        slider.setMessage2(Text.of(String.format("%.1f°", safeDisplayAngle)));
        if (sliderMode) textField.setText2(String.format("%.1f", safeDisplayAngle));
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
        textField.setText2(TextHelper.translatable("gui.bte.angle_screen.angle_unbound").getString());
        slider.setMessage2(Text.of("0.0°"));
        slider.setValueMapped(0.0);
        // ★ 修正: rollDegrees を追加
        BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, UNBOUND_SENTINEL, offsetX, offsetY, offsetZ, currentRoll));
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
        if (connectedPositions.isEmpty()) return simpleUIAngle;

        double baseAngle = isBound ? currentAngle : -1;
        double cand1 = normalize360(simpleUIAngle);
        double cand2 = normalize360(simpleUIAngle + 180.0);
        double bestCand = cand1;
        double minScore = Double.MAX_VALUE;

        for (double cand : new double[]{cand1, cand2}) {
            double score = 0;
            for (BlockPos connectedPos : connectedPositions) {
                double geoAngle = Math.toDegrees(Math.atan2(connectedPos.getZ() - this.blockPos.getZ(), connectedPos.getX() - this.blockPos.getX()));
                geoAngle = normalize360(geoAngle);
                double diff = getAngleDifference(cand, geoAngle);
                if (baseAngle >= 0) score += getAngleDifference(cand, baseAngle) * 2.0;
                else if (diff > 90.0) score += 180.0;
                else score += diff;
            }
            if (score < minScore) { minScore = score; bestCand = cand; }
        }
        return bestCand;
    }

    private double getAngleDifference(double a1, double a2) {
        double diff = Math.abs(a1 - a2) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private static double toSimpleUI(double internalAngle) {
        double angle = internalAngle % 180.0;
        if (angle < 0.0) angle += 180.0;
        return angle;
    }

    private static double toExactUI(double internalAngle) {
        double angle = internalAngle % 360.0;
        if (angle > 180.0) angle -= 360.0;
        return angle;
    }

    private static double normalize360(double angle) {
        angle = angle % 360.0;
        if (angle < 0.0) angle += 360.0;
        return angle;
    }

    private StraightNodeBlockEntity getBE() {
        BlockEntity raw = world.getBlockEntity(blockPos);
        if (raw != null && raw.data instanceof StraightNodeBlockEntity be) return be;
        return null;
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);
        super.render(graphicsHolder, mouseX, mouseY, delta);

        int cx = getWidthMapped() / 2;
        int cy = getHeightMapped() / 2;
        int w = Math.min(getWidthMapped() - 40, 360);
        int rowH = 18; // ★ render内でも使えるようにローカル変数化

        graphicsHolder.drawCenteredText(TextHelper.translatable("gui.bte.angle_screen.title").getString(), cx, cy - 70, 0xFFFFFF);

        Text hint = Text.cast(isExactMode ? TextHelper.translatable("gui.bte.angle_screen.hint_exact") : TextHelper.translatable("gui.bte.angle_screen.hint_simple"));
        graphicsHolder.drawCenteredText(hint.getString(), cx, cy - 60, 0xAAAAAA);

        Text status;
        int statusColor;
        if (isConnected) { status = Text.cast(TextHelper.translatable("gui.bte.angle_screen.status_connected")); statusColor = 0x55FF55; }
        else if (isBound) { status = Text.cast(TextHelper.translatable("gui.bte.angle_screen.status_bound")); statusColor = 0xFFFF55; }
        else { status = Text.cast(TextHelper.translatable("gui.bte.angle_screen.status_unbound")); statusColor = 0xFF5555; }
        graphicsHolder.drawCenteredText(status.getString(), cx, cy - 50, statusColor);

        if (hasRails) {
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.rail_properties").getString(), cx - w / 2, cy - 22, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());
            Position targetPos = connectedTargetPositions.get(selectedRailIndex);
            BlockPos otherPos = Init.positionToBlockPos(targetPos);
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.rail_info", selectedRailIndex + 1, connectedRails.size(), otherPos.getX(), otherPos.getY(), otherPos.getZ()).getString(), cx - w / 2 + 24, cy - 18 + 4, 0xAAAAAA, false, GraphicsHolder.getDefaultLight());
        } else {
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.no_connected_rails").getString(), cx - w / 2 + 24, cy - 18 + 4, 0xFF5555, false, GraphicsHolder.getDefaultLight());
        }

        int offLabelY = hasRails ? (cy + 54) : (cy - 18 + 4);
        graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.node_offset").getString(), cx - w / 2, offLabelY - 4, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());

        // ★ 追加: カント（ロール）のラベル描画
        int rollLabelY = offLabelY + (rowH + 2) * 3;
        graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.node_cant", "Cant / Roll").getString(), cx - w / 2, rollLabelY - 4, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());
    }

    private void apply() {
        // ★ 修正: rollDegrees を追加
        BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, currentAngle, offsetX, offsetY, offsetZ, currentRoll));
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
                for (Position targetPos : connectedMap.keySet()) result.add(Init.positionToBlockPos(targetPos));
            }
        } catch (Exception e) { e.printStackTrace(); }
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
            if (connectedMap != null) result.addAll(connectedMap.values());
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }
}