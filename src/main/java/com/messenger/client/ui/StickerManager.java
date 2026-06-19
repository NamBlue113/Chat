package com.messenger.client.ui;

import java.util.*;

/**
 * Quản lý sticker packs — giống Zalo sticker.
 * Sticker được biểu diễn dưới dạng emoji lớn + text.
 * Có thể nâng cấp lên ảnh PNG sau này.
 */
public class StickerManager {

    /** Một sticker pack */
    public static class StickerPack {
        public final String id;
        public final String name;
        public final String icon;
        public final String[][] stickers;  // [name, emoji/content]
        public boolean favorite;

        public StickerPack(String id, String name, String icon, String[][] stickers) {
            this.id = id; this.name = name; this.icon = icon; this.stickers = stickers;
        }
    }

    private static final List<StickerPack> PACKS = new ArrayList<>();
    private static final Map<String, StickerPack> PACK_MAP = new LinkedHashMap<>();

    static {
        // Pack 1: Cảm xúc cơ bản
        register(new StickerPack("emotion", "C\u1EA3m x\u00FAc", "\uD83D\uDE00", new String[][]{
            {"Like", "\uD83D\uDC4D"}, {"Love", "\u2764\uFE0F\u200D\uD83D\uDD25"},
            {"Haha", "\uD83D\uDE02"}, {"Wow", "\uD83D\uDE2E"},
            {"Sad", "\uD83D\uDE22"}, {"Angry", "\uD83D\uDE21"},
            {"Heart eyes", "\uD83D\uDE0D"}, {"Kiss", "\uD83D\uDE18"},
            {"Cool", "\uD83D\uDE0E"}, {"Party", "\uD83E\uDD73"},
            {"Sleep", "\uD83D\uDE34"}, {"Thinking", "\uD83E\uDD14"},
            {"Clap", "\uD83D\uDC4F"}, {"Fire", "\uD83D\uDD25"},
            {"100", "\uD83D\uDCAF"}, {"OK", "\uD83D\uDC4C"},
        }));

        // Pack 2: Động vật dễ thương
        register(new StickerPack("animals", "\u0110\u1ED9ng v\u1EADt", "\uD83D\uDC36", new String[][]{
            {"Dog", "\uD83D\uDC36"}, {"Cat", "\uD83D\uDC31"},
            {"Bunny", "\uD83D\uDC30"}, {"Bear", "\uD83D\uDC3B"},
            {"Panda", "\uD83D\uDC3C"}, {"Monkey", "\uD83D\uDC35"},
            {"Lion", "\uD83E\uDD81"}, {"Tiger", "\uD83D\uDC2F"},
            {"Frog", "\uD83D\uDC38"}, {"Penguin", "\uD83D\uDC27"},
            {"Chick", "\uD83D\uDC24"}, {"Unicorn", "\uD83E\uDD84"},
            {"Fox", "\uD83E\uDD8A"}, {"Owl", "\uD83E\uDD89"},
            {"Pig", "\uD83D\uDC37"}, {"Cow", "\uD83D\uDC2E"},
        }));

        // Pack 3: Đồ ăn & Đồ uống
        register(new StickerPack("food", "\u0110\u1ED3 \u0103n", "\uD83C\uDF55", new String[][]{
            {"Pizza", "\uD83C\uDF55"}, {"Burger", "\uD83C\uDF54"},
            {"Fries", "\uD83C\uDF5F"}, {"Sushi", "\uD83C\uDF63"},
            {"Ramen", "\uD83C\uDF5C"}, {"Cake", "\uD83C\uDF70"},
            {"Ice cream", "\uD83C\uDF66"}, {"Coffee", "\u2615"},
            {"Beer", "\uD83C\uDF7A"}, {"Wine", "\uD83C\uDF77"},
            {"Donut", "\uD83C\uDF69"}, {"Cookie", "\uD83C\uDF6A"},
            {"Boba tea", "\uD83E\uDDCB"}, {"Lemon", "\uD83C\uDF4B"},
            {"Watermelon", "\uD83C\uDF49"}, {"Strawberry", "\uD83C\uDF53"},
        }));

        // Pack 4: Hoạt động & Thể thao
        register(new StickerPack("activity", "Ho\u1EA1t \u0111\u1ED9ng", "\u26BD", new String[][]{
            {"Soccer", "\u26BD"}, {"Basketball", "\uD83C\uDFC0"},
            {"Tennis", "\uD83C\uDFBE"}, {"Swim", "\uD83C\uDFCA"},
            {"Run", "\uD83C\uDFC3"}, {"Bike", "\uD83D\uDEB4"},
            {"Game", "\uD83C\uDFAE"}, {"Music", "\uD83C\uDFB5"},
            {"Dance", "\uD83D\uDC83"}, {"Art", "\uD83C\uDFA8"},
            {"Travel", "\u2708\uFE0F"}, {"Beach", "\uD83C\uDFD6\uFE0F"},
            {"Camp", "\uD83C\uDFD5"}, {"Workout", "\uD83D\uDCAA"},
            {"Medal", "\uD83C\uDFC5"}, {"Trophy", "\uD83C\uDFC6"},
        }));

        // Pack 5: Tình yêu & Lãng mạn
        register(new StickerPack("love", "T\u00ECnh y\u00EAu", "\uD83D\uDC95", new String[][]{
            {"Love letter", "\uD83D\uDC8C"}, {"Rose", "\uD83C\uDF39"},
            {"Heart", "\u2764\uFE0F"}, {"Two hearts", "\uD83D\uDC95"},
            {"Couple", "\uD83D\uDC6B"}, {"Ring", "\uD83D\uDC8D"},
            {"Gift", "\uD83C\uDF81"}, {"Hug", "\uD83E\uDD17"},
            {"Kiss mark", "\uD83D\uDC8B"}, {"Sparkling", "\u2728"},
            {"Rainbow", "\uD83C\uDF08"}, {"Moon", "\uD83C\uDF19"},
            {"Sun", "\u2600\uFE0F"}, {"Cherry", "\uD83C\uDF52"},
            {"Teddy", "\uD83E\uDDF8"}, {"Butterfly", "\uD83E\uDD8B"},
        }));
    }

    private static void register(StickerPack pack) {
        PACKS.add(pack);
        PACK_MAP.put(pack.id, pack);
    }

    public static List<StickerPack> getPacks() { return PACKS; }
    public static StickerPack getPack(String id) { return PACK_MAP.get(id); }

    /**
     * Tạo nội dung STICKER message: "[STICKER:packId:stickerIndex]"
     */
    public static String encodeSticker(String packId, int index) {
        return "[STICKER:" + packId + ":" + index + "]";
    }

    /**
     * Giải mã STICKER message, trả về emoji để hiển thị.
     * Trả về null nếu không phải sticker.
     */
    public static String decodeSticker(String content) {
        if (content == null || !content.startsWith("[STICKER:")) return null;
        try {
            int end = content.indexOf(']', 9);
            if (end < 0) return null;
            String inner = content.substring(9, end);
            String[] parts = inner.split(":");
            if (parts.length != 2) return null;
            StickerPack pack = PACK_MAP.get(parts[0]);
            if (pack == null) return null;
            int idx = Integer.parseInt(parts[1]);
            if (idx < 0 || idx >= pack.stickers.length) return null;
            return pack.stickers[idx][1];
        } catch (Exception e) { return null; }
    }
}
