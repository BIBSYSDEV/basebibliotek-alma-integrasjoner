package no.sikt.lum;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.Test;

class SensitiveXmlDataRedacterTest {

    @Test
    void shouldRedactSensitiveDataFromXml() {
        var redacter = new SensitiveXmlDataRedacter();

        var xml =
                """
                  <id>12345</id>
                  <name>Library</name>
                  <aut>my-secret-password</aut>
                  <aut encrypted=true>my-secret-password</aut>
                  <password>my-secret-password</password>
                """;

        var secret = "my-secret-password";

        assertThat(xml, containsString(secret));

        var actual = redacter.redact(xml);

        assertThat(actual, containsString("12345"));
        assertThat(actual, containsString("Library"));
        assertThat(actual, containsString("<aut>redacted</aut>"));
        assertThat(actual, containsString("<password>redacted</password>"));
        assertThat(actual, not(containsString(secret)));
    }

}
