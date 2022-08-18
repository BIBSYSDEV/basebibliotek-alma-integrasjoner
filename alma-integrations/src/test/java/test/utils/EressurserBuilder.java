package test.utils;

import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.Map;
import javax.xml.namespace.QName;
import no.nb.basebibliotek.generated.Eressurser;

public class EressurserBuilder {

    private static final Map<String, String> valueMap = new HashMap<>();

    public EressurserBuilder withNncipUri(String value) {
        valueMap.put("nncip_uri", value);
        return this;
    }

    public Eressurser build() {
        final Eressurser eressurser = new Eressurser();

        valueMap.entrySet().stream()
            .map(entry -> new JAXBElement<>(new QName("http://nb.no/BaseBibliotek", entry.getKey()),
                                            String.class, entry.getValue()))
            .forEach(element -> eressurser.getOAIOrSRUOrArielIp().add(element));

        return eressurser;
    }
}
