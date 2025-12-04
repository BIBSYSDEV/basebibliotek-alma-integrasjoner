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

    private static final String FAILED_TO_SERIALIZE_USER = "Failed to serialize user with primary id: {} because of {}";
    private static final String ID_IS_MISSING = "PrimaryId IS MISSING";

    public static Optional<SerializedUser> serializeUser(User user) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JAXB.marshal(user, outputStream);
            var serializedXml = outputStream.toString(StandardCharsets.UTF_8);
            var serializedUser = new SerializedUser(user.getPrimaryId(), serializedXml);
            return Optional.of(serializedUser);
        } catch (Exception e) {
            logger.error(FAILED_TO_SERIALIZE_USER, getPrimaryIdIfPresent(user), e.getMessage());
            return Optional.empty();
        }
    }

    private static String getPrimaryIdIfPresent(User user) {
        return Optional.ofNullable(user)
                   .map(User::getPrimaryId)
                   .orElse(ID_IS_MISSING);
    }

}
