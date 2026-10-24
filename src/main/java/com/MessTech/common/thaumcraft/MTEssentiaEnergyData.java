package com.MessTech.common.thaumcraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.MessTech.init.MessTech;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import thaumcraft.api.aspects.Aspect;

/**
 * The essentia fuel table of the Large Essentia Generator (ESSENTIA.json): every aspect carries a fuel value, a
 * category
 * (which upgrade item unlocks it and which auxiliary fluid boosts it) and a fluid consumption coefficient.
 *
 * <p>
 * The category index is the bit installed by the matching {@code MTEssentiaUpgradeItem} meta: 0 = NORMAL is on by
 * default, 1..9 are AIR, THERMAL, UNSTABLE, VICTUS, TAINTED, MECHANICS, SPRITE, RADIATION, ELECTRIC.
 */
public final class MTEssentiaEnergyData {

    private static final String DATA_PATH = "assets/messtech/data/essentia.json";

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_AIR = 1;
    public static final int TYPE_THERMAL = 2;
    public static final int TYPE_UNSTABLE = 3;
    public static final int TYPE_VICTUS = 4;
    public static final int TYPE_TAINTED = 5;
    public static final int TYPE_MECHANIC = 6;
    public static final int TYPE_SPRITE = 7;
    public static final int TYPE_RADIATION = 8;
    public static final int TYPE_ELECTRIC = 9;
    /** No category: the aspect cannot be burnt. */
    public static final int TYPE_INVALID = -1;

    private static final Map<String, FuelData> FUEL_DATA = new HashMap<>();
    private static boolean loaded;

    private MTEssentiaEnergyData() {}

    private static final class FuelData {

        private final int fuelValue;
        private final int type;
        private final float consumeCeo;

        private FuelData(int fuelValue, int type, float consumeCeo) {
            this.fuelValue = fuelValue;
            this.type = type;
            this.consumeCeo = consumeCeo;
        }
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        String data = readData();
        if (data == null) {
            MessTech.MT_LOG.error("Essentia fuel table {} is missing", DATA_PATH);
            return;
        }
        try {
            JsonObject root = new JsonParser().parse(data)
                .getAsJsonObject();
            JsonArray array = root.get("Essentia")
                .getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                String name = entry.get("name")
                    .getAsString();
                Aspect aspect = Aspect.getAspect(name.toLowerCase());
                if (aspect == null) continue;
                FUEL_DATA.put(
                    aspect.getTag(),
                    new FuelData(
                        entry.get("fuelValue")
                            .getAsInt(),
                        typeFromName(
                            entry.get("category")
                                .getAsString()),
                        entry.get("consumeCeo")
                            .getAsFloat()));
            }
        } catch (RuntimeException e) {
            MessTech.MT_LOG.error("Failed to read the essentia fuel table {}", DATA_PATH, e);
        }
    }

    private static String readData() {
        java.io.InputStream stream = MTEssentiaEnergyData.class.getResourceAsStream("/" + DATA_PATH);
        if (stream == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static int typeFromName(String category) {
        return switch (category) {
            case "NORMAL" -> TYPE_NORMAL;
            case "AIR" -> TYPE_AIR;
            case "THERMAL" -> TYPE_THERMAL;
            case "UNSTABLE" -> TYPE_UNSTABLE;
            case "VICTUS" -> TYPE_VICTUS;
            case "TAINTED" -> TYPE_TAINTED;
            case "MECHANICS" -> TYPE_MECHANIC;
            case "SPRITE" -> TYPE_SPRITE;
            case "RADIATION" -> TYPE_RADIATION;
            case "ELECTRIC" -> TYPE_ELECTRIC;
            default -> TYPE_INVALID;
        };
    }

    private static FuelData data(Aspect aspect) {
        load();
        return aspect == null ? null : FUEL_DATA.get(aspect.getTag());
    }

    /** @return The category index of the aspect, {@link #TYPE_INVALID} when it cannot be burnt. */
    public static int getAspectTypeIndex(Aspect aspect) {
        FuelData data = data(aspect);
        return data == null ? TYPE_INVALID : data.type;
    }

    /** @return Base EU per essentia unit, before StableValue and coefficient. */
    public static long getAspectFuelValue(Aspect aspect) {
        FuelData data = data(aspect);
        return data == null ? 0 : data.fuelValue;
    }

    /** @return Litres of auxiliary fluid consumed per essentia unit. */
    public static float getAspectCeo(Aspect aspect) {
        FuelData data = data(aspect);
        return data == null ? 0 : data.consumeCeo;
    }
}
