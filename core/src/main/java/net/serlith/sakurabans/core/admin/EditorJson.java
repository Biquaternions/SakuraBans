package net.serlith.sakurabans.core.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Between JSON trees and the plain trees (maps, lists, scalars) the configuration uses. */
final class EditorJson {

    static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private EditorJson() {
    }

    static JsonElement json(final @Nullable Object plain) {
        return switch (plain) {
            case null -> JsonNull.INSTANCE;
            case JsonElement element -> element;
            case String text -> new JsonPrimitive(text);
            case Boolean flag -> new JsonPrimitive(flag);
            case Number number -> new JsonPrimitive(whole(number));
            case Map<?, ?> map -> {
                final JsonObject object = new JsonObject();
                map.forEach((key, value) -> object.add(String.valueOf(key), json(value)));
                yield object;
            }
            case Iterable<?> list -> {
                final JsonArray array = new JsonArray();
                list.forEach(value -> array.add(json(value)));
                yield array;
            }
            default -> new JsonPrimitive(String.valueOf(plain));
        };
    }

    /** Numbers become a {@code Long} when whole, else a {@code Double}. */
    static @Nullable Object plain(final @Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            final Map<String, @Nullable Object> map = new LinkedHashMap<>();
            element.getAsJsonObject().entrySet().forEach(entry ->
                    map.put(entry.getKey(), plain(entry.getValue())));
            return map;
        }
        if (element.isJsonArray()) {
            final List<@Nullable Object> list = new ArrayList<>();
            element.getAsJsonArray().forEach(value -> list.add(plain(value)));
            return list;
        }
        final JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return whole(primitive.getAsNumber());
        }
        return primitive.getAsString();
    }

    private static Number whole(final Number number) {
        if (number instanceof Long || number instanceof Integer || number instanceof Short
                || number instanceof Byte) {
            return number.longValue();
        }
        final BigDecimal decimal = new BigDecimal(number.toString());
        if (decimal.stripTrailingZeros().scale() <= 0
                && decimal.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
            return decimal.longValueExact();
        }
        return decimal.doubleValue();
    }
}
