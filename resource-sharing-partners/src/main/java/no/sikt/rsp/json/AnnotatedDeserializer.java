package no.sikt.rsp.json;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnotatedDeserializer<T> implements JsonDeserializer<T> {
    private static final Logger logger = LoggerFactory.getLogger(AnnotatedDeserializer.class);

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        T pojo = new Gson().fromJson(json, typeOfT);

        Field[] fields = pojo.getClass().getDeclaredFields();
        for (Field f : fields) {
            NotNullOrEmpty notNullOrEmpty = f.getAnnotation(NotNullOrEmpty.class);
            if (notNullOrEmpty != null) {
                try {
                    f.setAccessible(true);
                    Object fieldObject = f.get(pojo);
                    if (f.getType().equals(String.class) && StringUtils.isEmpty((String) fieldObject)) {
                        throw new JsonParseException(notNullOrEmpty.message() + ": " + f.getName());
                    } else {
                        if (fieldObject == null) {
                            throw new JsonParseException(notNullOrEmpty.message() + ": " + f.getName());
                        }
                    }
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    // should never happen!
                    logger.error("Unable to inspect class for deserialization!", ex);
                }
            }
        }
        return pojo;
    }
}
