package im.tny.segvault.disturbances.database;

import androidx.room.TypeConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

public class Converters {
    private static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
    }

    @TypeConverter
    public static HashMap<String, String> stringMapFromJSON(String value) {
        TypeReference<HashMap<String, String>> typeRef
                = new TypeReference<HashMap<String, String>>() {
        };
        try {
            return mapper.readValue(value, typeRef);
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    @TypeConverter
    public static String stringMapToJSON(HashMap<String, String> map) {
        try {
            return mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }

    @TypeConverter
    public static float[] floatArrayFromJSON(String value) {
        TypeReference<float[]> typeRef
                = new TypeReference<float[]>() {
        };
        try {
            return mapper.readValue(value, typeRef);
        } catch (IOException e) {
            e.printStackTrace();
            return new float[0];
        }
    }

    @TypeConverter
    public static String floatArrayToJSON(float[] array) {
        try {
            return mapper.writeValueAsString(array);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }

    @TypeConverter
    public static int[] intArrayFromJSON(String value) {
        TypeReference<int[]> typeRef
                = new TypeReference<int[]>() {
        };
        try {
            return mapper.readValue(value, typeRef);
        } catch (IOException e) {
            e.printStackTrace();
            return new int[0];
        }
    }

    @TypeConverter
    public static String intArrayToJSON(int[] array) {
        try {
            return mapper.writeValueAsString(array);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }

    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}
