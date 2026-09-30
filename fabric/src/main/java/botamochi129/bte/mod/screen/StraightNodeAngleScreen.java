package botamochi129.bte.mod.screen;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.RailAccessor;
import botamochi129.bte.mod.packet.PacketRememberRailSpeedLimit;
import botamochi129.bte.mod.packet.PacketSetCant;
import botamochi129.bte.mod.packet.PacketUpdateStraightNodeAngle;
import botamochi129.bte.mod.rail.CantProfile;
import botamochi129.bte.mod.rail.RailBuilder;
import botamochi129.bte.mod.registry.BTERegistryClient;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TwoPositionsBase;
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

    /** 「MTR の既定値に従う」を表す内部値。GUI 上は空欄で表現する。 */
    private static final long SPEED_UNSET = -1L;
    private static final long SPEED_MAX = 10_000L;

    /** ウィジェット1行の高さ。 */
    private static final int ROW_H = 18;

    /**
     * 制限速度入力行の {@code init2()} 内での縦位置 (基準 cy からの相対値)。
     * レイアウト: railY = cy + 4 / radiusY = railY + ROW_H + 4 / speedY = radiusY + ROW_H + 4。
     */
    private static final int SPEED_ROW_OFFSET_Y = 4 + (ROW_H + 4) * 2;

    /** 制限速度行の次に並ぶカント行の {@code init2()} 内での縦位置 (基準 cy からの相対値)。 */
    private static final int CANT_ROW_OFFSET_Y = SPEED_ROW_OFFSET_Y + ROW_H + 4;

    /** ノードオフセット見出しの縦位置。カント行の追加に合わせて下げる。 */
    private static final int OFFSET_LABEL_OFFSET_Y = 72;

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

    private TextFieldWidgetExtension textFieldSpeed;
    private ButtonWidgetExtension btnSpeedMinus10, btnSpeedMinus1, btnSpeedPlus1, btnSpeedPlus10;
    private ButtonWidgetExtension btnSpeedDefault;

    /** 選択中レールの制限速度 (km/h)。-1 は「MTR 既定値」。 */
    private long speedLimitKmh = SPEED_UNSET;

    private TextFieldWidgetExtension textFieldCantStart, textFieldCantMiddle, textFieldCantEnd;
    private ButtonWidgetExtension btnCantClear;

    /** 選択中レールのカント（度）。このノードから見た向きの値。 */
    private float cantStartDeg = 0.0F, cantMiddleDeg = 0.0F, cantEndDeg = 0.0F;

    private double offsetX = 0.0, offsetY = 0.0, offsetZ = 0.0;
    private boolean sliderModeX = true, sliderModeY = true, sliderModeZ = true;
    private SliderWidgetExtension sliderX, sliderY, sliderZ;
    private TextFieldWidgetExtension textFieldX, textFieldY, textFieldZ;
    private ButtonWidgetExtension btnModeX, btnModeY, btnModeZ;

    private static final double SIMPLE_MAX_ANGLE = 180.0;
    private static final double SIMPLE_MIN_ANGLE = 0.0;
    private static final double EXACT_MAX_ANGLE = 180.0;
    private static final double EXACT_MIN_ANGLE = -180.0;

    private boolean isExactMode = false;

    // ── Loader 差異吸収用ヘルパー ────────────────────────────────────

    /**
     * ボタン生成。MTR マッピングの PressAction を使用して両プラットフォームで統一。
     * ラベルは MTR マッピングの MutableText 型を使用。
     */
    private ButtonWidgetExtension createButton(int x, int y, int width, int height, MutableText label, Runnable action) {
        return new ButtonWidgetExtension(x, y, width, height, label, new PressAction() {
            @Override
            public void onPress2(ButtonWidget btn) {
                action.run();
            }
        });
    }

    /**
     * スライダーの匿名クラスで applyValue2() を実装。
     */
    private SliderWidgetExtension createSlider(int x, int y, int width, int height, String initialMessage,
            java.util.function.DoubleConsumer onApply) {
        return new SliderWidgetExtension(x, y, width, height, initialMessage) {
            @Override
            public void applyValue2() {
                onApply.accept(this.getValueMapped());
            }
            @Override
            protected void updateMessage2() {}
        };
    }

    /**
     * 翻訳可能なラベルを MutableText として生成（Loader 差異吸収）。
     */
    private MutableText label(String key) {
        return TextHelper.translatable(key);
    }

    /**
     * リテラル文字列ラベルを MutableText として生成（Loader 差異吸収）。
     */
    private MutableText literal(String text) {
        return TextHelper.literal(text);
    }

    public StraightNodeAngleScreen(BlockPos blockPos, World world, BlockPos targetPos) {
        // ★ 修正: タイトルを言語キーから取得
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
        int rowH = ROW_H;

        connectedRails.clear();
        connectedTargetPositions.clear();
        try {
            Data data = MinecraftClientData.getInstance();
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
            final MinecraftClient client = MinecraftClient.getInstance();
            org.mtr.mapping.holder.ClientPlayerEntity player = client.getPlayerMapped();
            if (player != null && !connectedTargetPositions.isEmpty()) {
                float yaw = 0;
                if (client != null) {
                    yaw = player.getYaw(client.getTickDelta());
                }
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

        slider = createSlider(cx - w / 2, cy - 40, w - 24, rowH, String.format("%.1f°", initialDisplay), val -> {
            double min = isExactMode ? EXACT_MIN_ANGLE : SIMPLE_MIN_ANGLE;
            double max = isExactMode ? EXACT_MAX_ANGLE : SIMPLE_MAX_ANGLE;

            double newUIAngle = min + (val * (max - min));
            double newInternalAngle = resolveInternalAngle(newUIAngle);

            if (!isBound || newInternalAngle != currentAngle) {
                isBound = true;
                currentAngle = newInternalAngle;

                double displayAngle = isExactMode ? toExactUI(newInternalAngle) : toSimpleUI(newInternalAngle);
                slider.setMessage2(Text.of(String.format("%.1f°", displayAngle)));
                textField.setText2(String.format("%.1f", displayAngle));

                updateUIState();
                apply();
            }
        });
        slider.setValueMapped(getSliderValueFromAngle(initialDisplay));
        slider.setActiveMapped(true);
        addChild(new ClickableWidget(slider));

        textField = new TextFieldWidgetExtension(cx - w / 2, cy - 40, w - 24, rowH,
                isBound ? String.format("%.1f", initialDisplay) : "0.0",
                7, TextCase.DEFAULT, null, null);
        textField.setChangedListener2(this::onTextChanged);
        addChild(new ClickableWidget(textField));

        btnMode = createButton(cx + w / 2 - 22, cy - 40, 20, rowH, literal("⇄"), this::switchMode);
        addChild(new ClickableWidget(btnMode));

        int railSelectY = cy - 18;
        btnPrevRail = createButton(cx - w / 2, railSelectY, 20, rowH, literal("<"), () -> selectRail(selectedRailIndex - 1));
        btnNextRail = createButton(cx + w / 2 - 20, railSelectY, 20, rowH, literal(">"), () -> selectRail(selectedRailIndex + 1));
        addChild(new ClickableWidget(btnPrevRail));
        addChild(new ClickableWidget(btnNextRail));

        int railY = cy + 4;
        int btnW = w / 3;

        btnShape = createButton(cx - w / 2, railY, btnW, rowH, literal(""), () -> {
            currentShape = currentShape == Rail.Shape.QUADRATIC ? Rail.Shape.TWO_RADII : Rail.Shape.QUADRATIC;
            updateRailProperties(currentRadius, true);
        });
        addChild(new ClickableWidget(btnShape));

        btnStyle = createButton(cx - w / 2 + btnW, railY, btnW, rowH, label("gui.mtr.rail_styles"), () -> {
            if (!connectedRails.isEmpty()) {
                MinecraftClient.getInstance().openScreen(new Screen(RailStyleSelectorScreen.create(connectedRails.get(selectedRailIndex))));
            }
        });
        addChild(new ClickableWidget(btnStyle));

        btnStyleFlip = createButton(cx - w / 2 + btnW * 2, railY, btnW, rowH, label("gui.mtr.flip_styles"), this::flipStyles);
        addChild(new ClickableWidget(btnStyleFlip));

        int radiusY = railY + rowH + 4;
        int radiusBtnW = 24;
        int textFieldW = w - radiusBtnW * 6 - 6;

        textFieldRadius = new TextFieldWidgetExtension(cx - w / 2, radiusY, textFieldW, rowH, 256, TextCase.DEFAULT, "[^\\d\\.]", "0");
        addChild(new ClickableWidget(textFieldRadius));

        btnMinus10 = createButton(cx - w / 2 + textFieldW + 2, radiusY, radiusBtnW, rowH, literal("-10"), () -> updateRailProperties(currentRadius - 10, true));
        btnMinus1 = createButton(cx - w / 2 + textFieldW + 2 + radiusBtnW, radiusY, radiusBtnW, rowH, literal("-1"), () -> updateRailProperties(currentRadius - 1, true));
        btnMinus01 = createButton(cx - w / 2 + textFieldW + 2 + radiusBtnW * 2, radiusY, radiusBtnW, rowH, literal("-.1"), () -> updateRailProperties(currentRadius - 0.1, true));

        btnPlus01 = createButton(cx - w / 2 + textFieldW + 2 + radiusBtnW * 3, radiusY, radiusBtnW, rowH, literal("+.1"), () -> updateRailProperties(currentRadius + 0.1, true));
        btnPlus1 = createButton(cx - w / 2 + textFieldW + 2 + radiusBtnW * 4, radiusY, radiusBtnW, rowH, literal("+1"), () -> updateRailProperties(currentRadius + 1, true));
        btnPlus10 = createButton(cx - w / 2 + textFieldW + 2 + radiusBtnW * 5, radiusY, radiusBtnW, rowH, literal("+10"), () -> updateRailProperties(currentRadius + 10, true));

        addChild(new ClickableWidget(btnMinus10)); addChild(new ClickableWidget(btnMinus1)); addChild(new ClickableWidget(btnMinus01));
        addChild(new ClickableWidget(btnPlus01)); addChild(new ClickableWidget(btnPlus1)); addChild(new ClickableWidget(btnPlus10));

        textFieldRadius.setChangedListener2(text -> {
            try {
                double newRadius = Double.parseDouble(text);
                if (Math.abs(newRadius - currentRadius) > 0.001) updateRailProperties(newRadius, true);
            } catch (Exception ignored) {}
        });

        final int speedY = radiusY + rowH + 4;
        final int speedBtnW = 24;
        final int speedDefaultW = 46;
        final int speedFieldW = w - speedBtnW * 4 - speedDefaultW - 8;

        textFieldSpeed = new TextFieldWidgetExtension(cx - w / 2, speedY, speedFieldW, rowH, 6,
                TextCase.DEFAULT, "[^\\d\\-]", "");
        addChild(new ClickableWidget(textFieldSpeed));

        btnSpeedMinus10 = createButton(cx - w / 2 + speedFieldW + 2, speedY, speedBtnW, rowH, literal("-10"), () -> stepSpeedLimit(-10));
        btnSpeedMinus1 = createButton(cx - w / 2 + speedFieldW + 2 + speedBtnW, speedY, speedBtnW, rowH, literal("-1"), () -> stepSpeedLimit(-1));
        btnSpeedPlus1 = createButton(cx - w / 2 + speedFieldW + 2 + speedBtnW * 2, speedY, speedBtnW, rowH, literal("+1"), () -> stepSpeedLimit(1));
        btnSpeedPlus10 = createButton(cx - w / 2 + speedFieldW + 2 + speedBtnW * 3, speedY, speedBtnW, rowH, literal("+10"), () -> stepSpeedLimit(10));

        btnSpeedDefault = createButton(cx + w / 2 - speedDefaultW, speedY, speedDefaultW, rowH, label("gui.bte.angle_screen.speed_default"), this::resetSpeedLimit);

        addChild(new ClickableWidget(btnSpeedMinus10));
        addChild(new ClickableWidget(btnSpeedMinus1));
        addChild(new ClickableWidget(btnSpeedPlus1));
        addChild(new ClickableWidget(btnSpeedPlus10));
        addChild(new ClickableWidget(btnSpeedDefault));

        textFieldSpeed.setChangedListener2(this::onSpeedTextChanged);
        // ウィジェット生成前に読み込んだ現在値をここで初めて反映する
        updateSpeedLimitUI();

        final int cantY = speedY + rowH + 4;
        final int cantClearW = 46;
        final int cantFieldW = Math.max(24, (w - cantClearW - 4) / 3);

        textFieldCantStart = new TextFieldWidgetExtension(cx - w / 2, cantY, cantFieldW, rowH, 6,
                TextCase.DEFAULT, "[^\\d\\-]", "");
        textFieldCantMiddle = new TextFieldWidgetExtension(cx - w / 2 + cantFieldW + 2, cantY, cantFieldW, rowH, 6,
                TextCase.DEFAULT, "[^\\d\\-]", "");
        textFieldCantEnd = new TextFieldWidgetExtension(cx - w / 2 + (cantFieldW + 2) * 2, cantY, cantFieldW, rowH, 6,
                TextCase.DEFAULT, "[^\\d\\-]", "");
        btnCantClear = createButton(cx + w / 2 - cantClearW, cantY, cantClearW, rowH, label("gui.bte.angle_screen.cant_clear"), this::resetCant);

        addChild(new ClickableWidget(textFieldCantStart));
        addChild(new ClickableWidget(textFieldCantMiddle));
        addChild(new ClickableWidget(textFieldCantEnd));
        addChild(new ClickableWidget(btnCantClear));

        textFieldCantStart.setChangedListener2(text -> onCantChanged());
        textFieldCantMiddle.setChangedListener2(text -> onCantChanged());
        textFieldCantEnd.setChangedListener2(text -> onCantChanged());
        updateCantUI();

        int offY = hasRails ? cantY + rowH + 10 : railSelectY;
        int mainW = w - 24;

        setupOffsetUI(cx, w, mainW, offY, rowH, 0);
        setupOffsetUI(cx, w, mainW, offY + rowH + 2, rowH, 1);
        setupOffsetUI(cx, w, mainW, offY + (rowH + 2) * 2, rowH, 2);

        int bottomY = offY + (rowH + 2) * 3 + 6;

        btnReturn = createButton(cx - w / 2, bottomY, 20, rowH, literal("X"), this::onClose2);
        addChild(new ClickableWidget(btnReturn));

        // ★ 修正: Unbind ボタンのラベルを言語キー化
        btnUnbind = createButton(cx - w / 2 + 24, bottomY, 60, rowH, label("gui.bte.angle_screen.unbind"), this::unbind);
        btnUnbind.setActiveMapped(isBound);
        addChild(new ClickableWidget(btnUnbind));

        // ★ 修正: チェックボックスのラベルを言語キー化
        chkExactMode = new CheckboxWidgetExtension(cx - w / 2 + 90, bottomY, 200, rowH, TextHelper.translatable("gui.bte.angle_screen.exact_angle").getString(), isExactMode, isChecked -> {
            isExactMode = isChecked; updateModeUI();
        });
        addChild(new ClickableWidget(chkExactMode));

        applyModeVisibility();
        updateModeUI();
        updateRailProperties(currentRadius, false);
        updateOffsetUI();
        updateRailSelectionUI();
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
        SliderWidgetExtension s = createSlider(cx - w / 2, y, mainW, rowH, "", val -> {
            val = val * 2.0 - 1.0;
            val = Math.round(val / 0.05) * 0.05;
            double currentVal = (axis == 0) ? offsetX : (axis == 1) ? offsetY : offsetZ;
            if (Math.abs(val - currentVal) > 0.0001) updateOffset(axis, val);
        });

        TextFieldWidgetExtension t = new TextFieldWidgetExtension(cx - w / 2, y, mainW, rowH, 10, TextCase.DEFAULT, "[^\\d\\.\\-]", "0");
        t.setChangedListener2(text -> {
            try {
                double val = Double.parseDouble(text);
                val = Math.max(-1.0, Math.min(1.0, val));
                double currentVal = (axis == 0) ? offsetX : (axis == 1) ? offsetY : offsetZ;
                if (Math.abs(val - currentVal) > 0.0001) updateOffset(axis, val);
            } catch (Exception ignored) {}
        });

        ButtonWidgetExtension b = createButton(cx + w / 2 - 22, y, 20, rowH, literal("⇄"), () -> {
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
            final long[] limits = RailBuilder.getSpeedLimitKmh(selectedRail);
            // 端点ごとに異なる値は本 GUI では表現しないため、端点1の値を表示する
            speedLimitKmh = limits[0] < 0 ? SPEED_UNSET : limits[0];
            loadCantFromBE(selectedRail);
        } else {
            currentShape = Rail.Shape.QUADRATIC;
            currentRadius = 0.0;
            maxRadius = 100.0;
            speedLimitKmh = SPEED_UNSET;
            cantStartDeg = cantMiddleDeg = cantEndDeg = 0.0F;
        }
        updateSpeedLimitUI();
        updateCantUI();
    }

    /**
     * 保存済みカントを GUI へ反映する。保存は正規方向なので、
     * このノードが正規始点でない場合は反転して見せる。
     */
    private void loadCantFromBE(Rail rail) {
        cantStartDeg = cantMiddleDeg = cantEndDeg = 0.0F;
        final StraightNodeBlockEntity be = getBE();
        if (be == null) return;

        // まず rail.getHexId() で試す（クライアント側の Rail オブジェクトが持つ hexId）
        CantProfile stored = be.getCant(rail.getHexId());

        // 見つからない場合、正規 hexId（端点座標でソートして生成）でフォールバックする
        // サーバー側は正規 hexId で保存している可能性があるため
        if (stored == null && ((Object) rail) instanceof RailAccessor accessor) {
            final Position p1 = accessor.bte$getPosition1();
            final Position p2 = accessor.bte$getPosition2();
            if (p1 != null && p2 != null) {
                final String canonicalHexId = TwoPositionsBase.getHexId(p1, p2);
                if (canonicalHexId != null && !canonicalHexId.equals(rail.getHexId())) {
                    stored = be.getCant(canonicalHexId);
                }
            }
        }

        if (stored == null) return;

        final CantProfile view = isCanonicalStartFor(rail) ? stored : stored.reversed();
        cantStartDeg = view.startDeg;
        cantMiddleDeg = view.middleDeg;
        cantEndDeg = view.endDeg;
    }

    /** 制限速度の入力欄・ボタンの表示を現在の {@link #speedLimitKmh} に合わせる。 */
    private void updateSpeedLimitUI() {
        if (textFieldSpeed == null) return;
        final String text = speedLimitKmh < 0 ? "" : String.valueOf(speedLimitKmh);
        if (!text.equals(textFieldSpeed.getText2())) {
            textFieldSpeed.setText2(text);
        }
        final boolean hasRails = !connectedRails.isEmpty();
        btnSpeedDefault.setActiveMapped(hasRails && speedLimitKmh >= 0);
        btnSpeedMinus10.setActiveMapped(hasRails && canStepSpeed(-10));
        btnSpeedMinus1.setActiveMapped(hasRails && canStepSpeed(-1));
        btnSpeedPlus1.setActiveMapped(hasRails && canStepSpeed(1));
        btnSpeedPlus10.setActiveMapped(hasRails && canStepSpeed(10));
    }

    /** 現在値から delta だけ動かした値が 0〜10000 km/h の範囲に収まるか。 */
    private boolean canStepSpeed(long delta) {
        final long base = speedLimitKmh < 0 ? 0L : speedLimitKmh;
        final long next = base + delta;
        return next >= 0L && next <= SPEED_MAX;
    }

    private void stepSpeedLimit(long delta) {
        if (connectedRails.isEmpty()) return;
        final long base = speedLimitKmh < 0 ? 0L : speedLimitKmh;
        applySpeedLimit(base + delta);
    }

    private void onSpeedTextChanged(String text) {
        if (text == null) return;
        final String trimmed = text.trim();
        // 空欄は「MTR の既定値に従う」を意味する
        if (trimmed.isEmpty()) {
            if (speedLimitKmh != SPEED_UNSET) applySpeedLimit(SPEED_UNSET);
            return;
        }
        try {
            final long value = Long.parseLong(trimmed);
            if (value < 0L || value > SPEED_MAX) {
                textFieldSpeed.setEditableColor2(0xFFFF0000);
                return;
            }
            if (value != speedLimitKmh) {
                applySpeedLimit(value);
                textFieldSpeed.setEditableColor2(0xFFFFFFFF);
            }
        } catch (NumberFormatException e) {
            textFieldSpeed.setEditableColor2(0xFFFF0000);
        }
    }

    /** 選択中レールの制限速度を MTR 標準のデータ更新経路で変更する。 */
    private void applySpeedLimit(long valueKmh) {
        if (connectedRails.isEmpty()) return;
        final Rail oldRail = connectedRails.get(selectedRailIndex);
        final String hexId = oldRail.getHexId();
        final StraightNodeBlockEntity be = getBE();

        long target = valueKmh;
        if (target < 0L) {
            // 既定値に戻す: 変更前に退避しておいた MTR 既定値を復元する
            final long[] original = (be == null) ? null : be.getOriginalSpeedLimit(hexId);
            if (original == null) {
                // 退避情報が無い (他クライアントが変更した等) 場合は現在の値に留める
                updateSpeedLimitUI();
                return;
            }
            target = original[0];
        }

        final Rail newRail = RailBuilder.withSpeedLimitKmh(oldRail, target, target);
        if (newRail == null) {
            updateSpeedLimitUI();
            return;
        }

        // 既定値の退避は「変更を MTR に送る前」に完了させる。
        // 1 回目だけ現在の値 (= MTR 既定値) を採取し、2 回目以降は既記録を温存する。
        if (valueKmh >= 0) {
            if (be == null || be.getOriginalSpeedLimit(hexId) == null) {
                final long[] current = RailBuilder.getSpeedLimitKmh(oldRail);
                BTERegistryClient.sendPacketToServer(new PacketRememberRailSpeedLimit(
                        blockPos, hexId, current[0], current[1], false));
            }
        } else {
            BTERegistryClient.sendPacketToServer(new PacketRememberRailSpeedLimit(
                    blockPos, hexId, 0L, 0L, true));
            if (be != null) be.forgetOriginalSpeedLimit(hexId);
        }

        final UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getInstance());
        request.addRail(newRail);
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));

        speedLimitKmh = valueKmh;
        updateSpeedLimitUI();
    }

    private void resetSpeedLimit() {
        applySpeedLimit(SPEED_UNSET);
    }

    // ── カント UI ─────────────────────────────────────────────────────

    private void updateCantUI() {
        if (textFieldCantStart == null) return;
        setCantText(textFieldCantStart, cantStartDeg);
        setCantText(textFieldCantMiddle, cantMiddleDeg);
        setCantText(textFieldCantEnd, cantEndDeg);
        if (btnCantClear != null) {
            btnCantClear.setActiveMapped(hasRails
                    && (cantStartDeg != 0.0F || cantMiddleDeg != 0.0F || cantEndDeg != 0.0F));
        }
    }

    private static void setCantText(TextFieldWidgetExtension field, float value) {
        final String text = formatCant(value);
        if (!text.equals(field.getText2())) {
            field.setText2(text);
        }
    }

    private static String formatCant(float value) {
        if (value == Math.round(value)) return String.valueOf((int) value);
        return String.valueOf(value);
    }

    private void onCantChanged() {
        if (connectedRails.isEmpty()) return;
        final float start = parseCant(textFieldCantStart);
        final float middle = parseCant(textFieldCantMiddle);
        final float end = parseCant(textFieldCantEnd);
        if (start == cantStartDeg && middle == cantMiddleDeg && end == cantEndDeg) return;
        applyCant(start, middle, end);
    }

    private static float parseCant(TextFieldWidgetExtension field) {
        if (field == null) return 0.0F;
        final String text = field.getText2().trim();
        // 入力途中（空欄・"-" のみ）は 0 として扱い、確定を待つ
        if (text.isEmpty() || text.equals("-")) return 0.0F;
        try {
            return CantProfile.clamp(Float.parseFloat(text));
        } catch (NumberFormatException e) {
            return 0.0F;
        }
    }

    private void resetCant() {
        applyCant(0.0F, 0.0F, 0.0F);
    }

    /**
     * カントを適用する。値は「このノードから見た向き」で受け取り、保存はサーバー側で
     * レールの正規方向へ正規化する。
     */
    private void applyCant(float start, float middle, float end) {
        if (connectedRails.isEmpty()) return;
        final Rail rail = connectedRails.get(selectedRailIndex);
        final boolean canonicalStart = isCanonicalStartFor(rail);

        // サーバー往復を待たずプレビューできるよう、ローカル BE と索引へ即時反映する
        final StraightNodeBlockEntity be = getBE();
        if (be != null) {
            final CantProfile profile = canonicalStart
                    ? new CantProfile(start, middle, end)
                    : new CantProfile(start, middle, end).reversed();
            // 正規 hexId でも保存しておく（クライアント側の rail.getHexId() とサーバー側が異なる場合のフォールバック用）
            if (((Object) rail) instanceof RailAccessor accessor) {
                final Position p1 = accessor.bte$getPosition1();
                final Position p2 = accessor.bte$getPosition2();
                if (p1 != null && p2 != null) {
                    final String canonicalHexId = TwoPositionsBase.getHexId(p1, p2);
                    if (canonicalHexId != null && !canonicalHexId.equals(rail.getHexId())) {
                        be.setCant(canonicalHexId, profile);
                    }
                }
            }
            be.setCant(rail.getHexId(), profile);
        }

        BTERegistryClient.sendPacketToServer(new PacketSetCant(
                blockPos, rail.getHexId(), start, middle, end, canonicalStart));

        cantStartDeg = start;
        cantMiddleDeg = middle;
        cantEndDeg = end;
        updateCantUI();
    }

    /**
     * このノードがレールの正規方向（{@code Rail#getHexId()} が決める端点順）の始点か。
     * 正規 id を {@code (self, other)} の順で作って一致すれば self が始点である。
     */
    private boolean isCanonicalStartFor(Rail rail) {
        if (!(((Object) rail) instanceof RailAccessor accessor)) return true;
        final Position p1 = accessor.bte$getPosition1();
        final Position p2 = accessor.bte$getPosition2();
        if (p1 == null || p2 == null) return true;

        final Position self = Init.blockPosToPosition(blockPos);
        final Position other = samePosition(self, p1) ? p2 : p1;
        final String canonical = TwoPositionsBase.getHexId(self, other);
        return canonical != null && canonical.equals(rail.getHexId());
    }

    private static boolean samePosition(Position a, Position b) {
        return a != null && b != null
                && a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
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
        textFieldSpeed.setVisibleMapped(visible);
        btnSpeedMinus10.setVisibleMapped(visible);
        btnSpeedMinus1.setVisibleMapped(visible);
        btnSpeedPlus1.setVisibleMapped(visible);
        btnSpeedPlus10.setVisibleMapped(visible);
        btnSpeedDefault.setVisibleMapped(visible);
        textFieldCantStart.setVisibleMapped(visible);
        textFieldCantMiddle.setVisibleMapped(visible);
        textFieldCantEnd.setVisibleMapped(visible);
        btnCantClear.setVisibleMapped(visible);
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
        // ★ 修正: チェックボックスの横のテキストも言語キー化
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
        BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, UNBOUND_SENTINEL, offsetX, offsetY, offsetZ));
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

        // ★ 修正: String.valueOf() ではなく .getString() を使用して翻訳済み文字列を取得する
        // String.valueOf(Text) は Text.toString() を呼び出してしまい、
        // 翻訳済み文字列ではなく「言語キーそのもの」や「オブジェクトのハッシュ値」が表示される原因になります。

        graphicsHolder.drawCenteredText(TextHelper.translatable("gui.bte.angle_screen.title").getString(), cx, cy - 70, 0xFFFFFF);

        Text hint = Text.cast(isExactMode ? TextHelper.translatable("gui.bte.angle_screen.hint_exact") : TextHelper.translatable("gui.bte.angle_screen.hint_simple"));
        graphicsHolder.drawCenteredText(hint.getString(), cx, cy - 60, 0xAAAAAA);

        Text status;
        int statusColor;
        if (isConnected) { status = Text.cast(TextHelper.translatable("gui.bte.angle_screen.status_connected")); statusColor = 0x55FF55; }
        else if (isBound) { status = Text.cast(TextHelper.translatable("gui.bte.angle_screen.status_bound")); statusColor = 0xFFFF55; }
        else { status = Text.cast(TextHelper.translatable("gui.bte.angle_screen.status_unbound")); statusColor = 0xFF5555; }
        graphicsHolder.drawCenteredText(status.getString(), cx, cy - 50, statusColor);

        double displayAngle = isBound ? (isExactMode ? toExactUI(currentAngle) : toSimpleUI(currentAngle)) : 0.0;
        Text angleText = Text.cast(isBound ? TextHelper.translatable("gui.bte.angle_screen.angle_value", displayAngle) : TextHelper.translatable("gui.bte.angle_screen.angle_unbound"));
        graphicsHolder.drawCenteredText(angleText.getString(), cx, cy - 40, 0xFFFFFF);

        if (hasRails) {
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.rail_properties").getString(), cx - w / 2, cy - 22, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());
            Position targetPos = connectedTargetPositions.get(selectedRailIndex);
            BlockPos otherPos = Init.positionToBlockPos(targetPos);
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.rail_info", selectedRailIndex + 1, connectedRails.size(), otherPos.getX(), otherPos.getY(), otherPos.getZ()).getString(), cx - w / 2 + 24, cy - 18 + 4, 0xAAAAAA, false, GraphicsHolder.getDefaultLight());
        } else {
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.no_connected_rails").getString(), cx - w / 2 + 24, cy - 18 + 4, 0xFF5555, false, GraphicsHolder.getDefaultLight());
        }

        final int speedLabelY = cy + SPEED_ROW_OFFSET_Y - 10;
        graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.speed_limit").getString(),
                cx - w / 2, speedLabelY, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());

        if (hasRails) {
            final int cantLabelY = cy + CANT_ROW_OFFSET_Y - 10;
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.cant").getString(),
                    cx - w / 2, cantLabelY, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());
            // 正規方向に対する start / middle / end を入力欄の上に添える
            final int cantLabelX = cx - w / 2 + 24;
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.cant_start").getString(),
                    cantLabelX, cantLabelY, 0xAAAAAA, false, GraphicsHolder.getDefaultLight());
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.cant_middle").getString(),
                    cantLabelX + 40, cantLabelY, 0xAAAAAA, false, GraphicsHolder.getDefaultLight());
            graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.cant_end").getString(),
                    cantLabelX + 80, cantLabelY, 0xAAAAAA, false, GraphicsHolder.getDefaultLight());
        }

        int offLabelY = hasRails ? (cy + OFFSET_LABEL_OFFSET_Y) : (cy - 18 + 4);
        graphicsHolder.drawText(TextHelper.translatable("gui.bte.angle_screen.node_offset").getString(), cx - w / 2, offLabelY - 4, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());
    }

    private void apply() {
        BTERegistryClient.sendPacketToServer(new PacketUpdateStraightNodeAngle(blockPos, currentAngle, offsetX, offsetY, offsetZ));
    }

    @Override
    public boolean isPauseScreen2() {
        return false;
    }

    private List<BlockPos> findConnectedNodePositions() {
        List<BlockPos> result = new ArrayList<>();
        try {
            Data data = MinecraftClientData.getInstance();
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
            Data data = MinecraftClientData.getInstance();
            if (data == null) return result;
            Position currentPos = Init.blockPosToPosition(this.blockPos);
            if (currentPos == null) return result;
            Map<Position, Rail> connectedMap = data.positionsToRail.get(currentPos);
            if (connectedMap != null) result.addAll(connectedMap.values());
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }
}