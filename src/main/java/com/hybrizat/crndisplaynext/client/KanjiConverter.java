package com.hybrizat.crndisplaynext.client;

import java.awt.Font;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 简体中文 → 日文汉字 逐字符/词组转换。
 * 若 M+ 等字体能显示原字符（含日文汉字/相同码点），则保留原字符；
 * 否则优先匹配词组表（s2j_phrases.txt），再单字查表（s2j.txt）兜底。
 */
public final class KanjiConverter {

    private static final Map<Character, Character> MAP = new HashMap<>();
    private static final Map<String, String> PHRASES = new HashMap<>();
    private static int maxPhraseLen = 0;

    private KanjiConverter() {}

    /**
     * 转换一段文本。referenceFont 为 null 时跳过字形检查，直接查表。
     */
    public static String toDisplay(String text, Font referenceFont) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length() * 2);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (referenceFont != null && referenceFont.canDisplay(c)) {
                sb.append(c); i++;
                continue;
            }
            // 词组优先（最长匹配）
            int matchedLen = 0;
            String jpTarget = null;
            if (!PHRASES.isEmpty()) {
                for (int len = Math.min(maxPhraseLen, text.length() - i); len >= 2; len--) {
                    String t = PHRASES.get(text.substring(i, i + len));
                    if (t != null) { matchedLen = len; jpTarget = t; break; }
                }
            }
            if (jpTarget != null) {
                for (int k = 0; k < jpTarget.length(); k++) {
                    char mc = jpTarget.charAt(k);
                    if (referenceFont != null && referenceFont.canDisplay(mc)) {
                        sb.append(mc);
                    } else {
                        Character j = MAP.get(mc);
                        sb.append(j != null ? j.charValue() : mc);
                    }
                }
                i += matchedLen;
            } else {
                Character jp = MAP.get(c);
                sb.append(jp != null ? jp.charValue() : c);
                i++;
            }
        }
        return sb.toString();
    }

    /** 仅查表转换（不做字形检查），供预览等场景使用。 */
    public static String convert(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length() * 2);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int matchedLen = 0;
            String jpTarget = null;
            if (!PHRASES.isEmpty()) {
                for (int len = Math.min(maxPhraseLen, text.length() - i); len >= 2; len--) {
                    String t = PHRASES.get(text.substring(i, i + len));
                    if (t != null) { matchedLen = len; jpTarget = t; break; }
                }
            }
            if (jpTarget != null) {
                sb.append(jpTarget);
                i += matchedLen;
            } else {
                Character jp = MAP.get(c);
                sb.append(jp != null ? jp.charValue() : c);
                i++;
            }
        }
        return sb.toString();
    }

    // ── 资源加载 ──

    private static void loadResource() {
        loadSingle("/assets/crndisplaynext/data/s2j.txt");
        loadPhrases("/assets/crndisplaynext/data/s2j_phrases.txt");
    }

    private static void loadSingle(String path) {
        try (InputStream is = KanjiConverter.class.getResourceAsStream(path)) {
            if (is == null) { System.out.println("[KanjiConverter] " + path + " not found"); return; }
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            int n = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() < 2) continue;
                int sp = line.indexOf(' ');
                if (sp <= 0) continue;
                String s = line.substring(0, sp).trim();
                String j = line.substring(sp + 1).trim();
                if (s.length() != 1 || j.length() != 1) continue;
                char sc = s.charAt(0), jc = j.charAt(0);
                if (sc != jc) { MAP.put(sc, jc); n++; }
            }
            System.out.println("[KanjiConverter] loaded " + n + " single mappings");
        } catch (Exception e) {
            System.out.println("[KanjiConverter] " + path + " load failed: " + e);
        }
    }

    private static void loadPhrases(String path) {
        try (InputStream is = KanjiConverter.class.getResourceAsStream(path)) {
            if (is == null) { System.out.println("[KanjiConverter] " + path + " not found"); return; }
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            int n = 0;
            while ((line = br.readLine()) != null) {
                int sp = line.indexOf(' ');
                if (sp <= 0) continue;
                String s = line.substring(0, sp).trim();
                String j = line.substring(sp + 1).trim();
                if (s.length() < 2 || j.length() < 2) continue;
                if (!s.equals(j)) {
                    PHRASES.put(s, j);
                    n++;
                    maxPhraseLen = Math.max(maxPhraseLen, s.length());
                }
            }
            System.out.println("[KanjiConverter] loaded " + n + " phrase mappings (max len " + maxPhraseLen + ")");
        } catch (Exception e) {
            System.out.println("[KanjiConverter] " + path + " load failed: " + e);
        }
    }

    private static void put(String pairs) {
        for (int i = 0; i + 1 < pairs.length(); i += 2) {
            MAP.put(pairs.charAt(i), pairs.charAt(i + 1));
        }
    }

    static {
        loadResource();
        if (MAP.isEmpty()) {
            put("东東铁鉄线線车車门門汉漢语語电電图図关関开開龙竜鸟鳥马馬鱼魚变変声声气気风風云雲华華岛島桥橋港港两両亚亜专専业業义義乐楽乡郷书書买買亏虧传伝伤傷伦倫伪偽佛仏侠侠价価协協单単卖売叶葉号号后後吴呉园園团団场場坛壇坏壊块塊坚堅处处备備复復头頭夹挟奋奮宝宝实実审審导導寿寿将将尝嘗层层属屬师師带帯广広庆慶应応库庫张張弹弾归帰当当录録忆憶惧懼恋恋战戦户户护護担担择択挂掛换換据拠断断昼昼显顕晓暁优優伟偉兑兑兴興养養兽獣冈岡册冊写写冰冰冲冲决決况況冻凍净浄减減凉涼准準击撃刘劉则則刚刚创創别別剑剣剧劇劝勧动動务務劳労势勢区区医医卢盧卫衛卷巻厅庁历暦厌厭参参发発双双围囲圆圆圣聖坠墜报報夸誇夺奪妇婦妈妈宪憲宫宮宽寛富富对対寻尋届届从従德徳无無时时広広駅駅線線鉄鉄島島橋橋浜浜");
        }
    }
}
