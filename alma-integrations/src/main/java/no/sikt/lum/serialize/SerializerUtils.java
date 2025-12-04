package no.sikt.lum.serialize;

import jakarta.xml.bind.JAXB;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import no.sikt.alma.user.generated.User;

public class SerializerUtils {

    private static final String FAILED_TO_SERIALIZE_USER = "Failed to serialize user: ";

    public static SerializedUser serializeUser(User user) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JAXB.marshal(user, outputStream);
            var serializedXml = outputStream.toString(StandardCharsets.UTF_8);
            return new SerializedUser(user.getPrimaryId(), serializedXml);
        } catch (IOException e) {
            throw new RuntimeException(FAILED_TO_SERIALIZE_USER + user.getPrimaryId(), e);
        }
    }

}
