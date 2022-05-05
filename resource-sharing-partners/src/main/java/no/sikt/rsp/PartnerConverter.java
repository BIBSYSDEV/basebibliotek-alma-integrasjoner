package no.sikt.rsp;

import java.util.List;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.generated.Partner;
import nva.commons.core.JacocoGenerated;

public class PartnerConverter {

    @JacocoGenerated
    public PartnerConverter() {
        
    }

    public static List<Partner> convertBasebibliotekToPartners(BaseBibliotek baseBibliotek) {
        return baseBibliotek
                   .getRecord()
                   .stream()
                   .map(PartnerConverter::convertRecordToPartner)
                   .collect(Collectors.toList());
    }

    private static Partner convertRecordToPartner(Record record) {
        var partner = new Partner();
        partner.setContactInfo(ContactInfoConverter.extractContactInfoFromRecord(record));
        return partner;
    }
}
