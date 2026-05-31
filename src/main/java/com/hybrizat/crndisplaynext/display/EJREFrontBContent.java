package com.hybrizat.crndisplaynext.display;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;
import java.util.Arrays;

/**
 * Controls what content is shown in the B panel of the JRE front destination display.
 *   DESTINATION : 行先 (e.g. "Tokyo")
 *   LINE_NAME   : 線路名 (e.g. "Chuo Line Rapid")
 */
public enum EJREFrontBContent implements ITranslatableEnum {
    DESTINATION((byte)0, "destination"),
    LINE_NAME  ((byte)1, "line_name");

    private final byte id; private final String name;
    EJREFrontBContent(byte id, String name) { this.id = id; this.name = name; }
    public byte getId() { return id; }
    public static EJREFrontBContent getById(int id) {
        return Arrays.stream(values()).filter(x -> x.id == (byte)id).findFirst().orElse(DESTINATION);
    }
    @Override public String getSerializedName() { return name; }
    @Override public Data getTranslationData() {
        return new Data(CRNDisplayNextMod.MOD_ID, "jre_front_b_content", name);
    }
}
