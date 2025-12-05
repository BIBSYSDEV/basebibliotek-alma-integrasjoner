package no.sikt.lum.serialize;

import jakarta.xml.bind.JAXB;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import no.sikt.alma.user.generated.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerializerUtils {

    private static final Logger logger = LoggerFactory.getLogger(SerializerUtils.class);

    private static final String USER_OR_PRIMARY_ID_IS_NULL = "User is null or primaryId is null";
    private static final String FAILED_TO_SERIALIZE_USER = "Failed to serialize user with primary id: {} because of {}";

    public static Optional<SerializedUser> serializeUser(User user) {
        if (user == null || user.getPrimaryId() == null) {
            logger.error(USER_OR_PRIMARY_ID_IS_NULL);
            return Optional.empty();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JAXB.marshal(user, outputStream);
            var serializedXml = outputStream.toString(StandardCharsets.UTF_8);
            var serializedUser = new SerializedUser(user.getPrimaryId(), serializedXml);
            return Optional.of(serializedUser);
        } catch (Exception e) {
            logger.error(FAILED_TO_SERIALIZE_USER, user.getPrimaryId(), e.getMessage());
            return Optional.empty();
        }
    }

}
