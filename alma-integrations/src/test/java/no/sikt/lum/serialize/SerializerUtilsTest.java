package no.sikt.lum.serialize;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import no.sikt.alma.user.generated.User;
import org.junit.jupiter.api.Test;

class SerializerUtilsTest {

    @Test
    void shouldIgnoreUsersThatCannotBeSerialized() {
        var result = SerializerUtils.serializeUser(null);

        assertThat(result.isEmpty(), equalTo(true));
    }

    @Test
    void shouldSerializeUserWithValidData() {
        var user = new User();
        user.setPrimaryId("1234");
        user.setFirstName("John");
        user.setLastName("Doe");

        var result = SerializerUtils.serializeUser(user);

        assertThat(result.isPresent(), equalTo(true));
        assertThat(result.get().primaryId(), equalTo("1234"));
        assertThat(result.get().serializedXml(), containsString("<first_name>John</first_name>"));
        assertThat(result.get().serializedXml(), containsString("<last_name>Doe</last_name>"));
    }

}