package com.hybrizat.crndisplaynext.display;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;
import java.util.Arrays;

/**
 * The three side-display modes for JRE side destination boards (E233 style).
 *
 * Mode NEXT_STOP (left-right split, 2-row height):
 *   ┌──────┬─────────────────┐
 *   │[種別]│ [行先]           │
 *   │ 色块 │ 次は [次の駅]    │
 *   └──────┴─────────────────┘
 *   Left: category name with color background (spans full height)
 *   Right-top: destination
 *   Right-bottom: "次は ○○"
 *
 * Mode DEST (single row):
 *   [種別] [行先]
 *
 * Mode LINE (single row):
 *   [種別] [線路名]
 */
public enum EJRESideMode implements ITranslatableEnum {
    NEXT_STOP((byte)0, "next_stop"),
    DEST     ((byte)1, "dest"),
    LINE     ((byte)2, "line");

    private final byte id; private final String name;
    EJRESideMode(byte id, String name) { this.id = id; this.name = name; }
    public byte getId() { return id; }
    public static EJRESideMode getById(int id) {
        return Arrays.stream(values()).filter(x -> x.id == (byte)id).findFirst().orElse(NEXT_STOP);
    }
    @Override public String getSerializedName() { return name; }
    @Override public Data getTranslationData() {
        return new Data(CRNDisplayNextMod.MOD_ID, "jre_side_mode", name);
    }
}
