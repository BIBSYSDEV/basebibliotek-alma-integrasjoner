package no.sikt.rsp;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.generated.Address;
import no.sikt.alma.generated.Address.AddressTypes;
import no.sikt.alma.generated.Address.Country;
import no.sikt.alma.generated.Addresses;
import no.sikt.alma.generated.ContactInfo;
import no.sikt.alma.generated.Email;
import no.sikt.alma.generated.Email.EmailTypes;
import no.sikt.alma.generated.Emails;
import no.sikt.alma.generated.Phone;
import no.sikt.alma.generated.Phone.PhoneTypes;
import no.sikt.alma.generated.Phones;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;

public final class ContactInfoConverter {

    private static final boolean BEST_EMAIL_IS_ALWAYS_PREFERRED = true;
    private static final boolean THERE_IS_ONLY_ONE_PHONE_SO_IT_IS_ALWAYS_PREFERRED = true;
    private static final boolean P_ADDRESS_IS_ALWAYS_PREFERRED = true;
    private static final List<String> PHONE_TYPES = List.of("claimPhone", "orderPhone", "paymentPhone", "returnsPhone");
    private static final List<String> ADDRESS_TYPES = List.of("billing", "claim", "order", "payment", "returns",
                                                              "shipping");
    private static final List<String> EMAIL_TYPES = List.of("claimMail", "orderMail", "paymentMail", "queries",
                                                            "returnsMail");
    @JacocoGenerated
    private ContactInfoConverter() {
    }

    public static ContactInfo extractContactInfoFromRecord(Record record) {
        var contactInfo = new ContactInfo();
        contactInfo.setAddresses(extractAddressesFromRecord(record));
        contactInfo.setPhones(createPhones(record));
        contactInfo.setEmails(createEmails(record));
        return contactInfo;
    }

    private static Emails createEmails(Record record) {
        var emailBest = createEmailBest(record);
        var emailBestExists = emailBest.isPresent();
        var emailRegular = createEmailRegular(record, emailBestExists);
        var emails = new Emails();
        emailBest.ifPresent(email -> emails.getEmail().add(email));
        emailRegular.ifPresent(email -> emails.getEmail().add(email));
        return emails;
    }

    private static Optional<Email> createEmailRegular(Record record, boolean emailBestExists) {
        return Objects.nonNull(record.getEpostAdr())
                   ? Optional.of(createEmail(record.getEpostAdr(), !emailBestExists))
                   : Optional.empty();
    }

    private static Optional<Email> createEmailBest(Record record) {
        return Objects.nonNull(record.getEpostBest())
                   ? Optional.of(createEmail(record.getEpostBest(), BEST_EMAIL_IS_ALWAYS_PREFERRED))
                   : Optional.empty();
    }

    private static Email createEmail(String emailAddress, boolean isPreferred) {
        var email = new Email();
        email.setEmailAddress(emailAddress);
        email.setPreferred(isPreferred);
        email.setEmailTypes(createEmailTypes());
        return email;
    }

    private static EmailTypes createEmailTypes() {

        var emailTypes = new Email.EmailTypes();
        emailTypes.getEmailType().addAll(EMAIL_TYPES);
        return emailTypes;
    }

    private static Phones createPhones(Record record) {
        var phones = new Phones();
        phones.getPhone().add(createPhone(record));
        return phones;
    }

    private static Phone createPhone(Record record) {
        var phone = new Phone();
        phone.setPhoneNumber(Objects.nonNull(record.getTlf()) ? record.getTlf() : StringUtils.EMPTY_STRING);
        phone.setPreferred(THERE_IS_ONLY_ONE_PHONE_SO_IT_IS_ALWAYS_PREFERRED);
        phone.setPhoneTypes(createPhoneTypes());
        return phone;
    }

    private static PhoneTypes createPhoneTypes() {

        var phoneTypes = new PhoneTypes();
        phoneTypes.getPhoneType().addAll(PHONE_TYPES);
        return phoneTypes;
    }

    private static Addresses extractAddressesFromRecord(Record record) {
        var addresses = new Addresses();
        var postAddress = createPAddress(record);
        var visitationAddress = createVAddress(record, postAddress.isPresent());
        postAddress.ifPresent(address -> addresses.getAddress().add(address));
        visitationAddress.ifPresent(address -> addresses.getAddress().add(address));
        return addresses;
    }

    private static Optional<Address> createPAddress(Record record) {
        return Objects.nonNull(record.getPadr())
                   ? Optional.of(createAddress(record.getPadr(),
                                               record.getBibnr(),
                                               record.getPpoststed(),
                                               record.getPpostnr(),
                                               record.getLandkode(),
                                               P_ADDRESS_IS_ALWAYS_PREFERRED))
                   : Optional.empty();
    }

    private static Optional<Address> createVAddress(Record record, boolean postAddressAlreadyExists) {
        return Objects.nonNull(record.getVadr())
                   ? Optional.of(createAddress(record.getVadr(),
                                               record.getBibnr(),
                                               record.getVpoststed(),
                                               record.getVpostnr(),
                                               record.getLandkode(),
                                               !postAddressAlreadyExists))
                   : Optional.empty();
    }

    private static Address createAddress(String adr,
                                         String bibnr,
                                         String poststed,
                                         String postnr,
                                         String landkode,
                                         boolean isPreferred) {
        var address = new Address();
        address.setLine1(adr);
        address.setLine5(bibnr);
        address.setCity(poststed);
        address.setPostalCode(postnr);
        address.setCountry(createCountry(landkode));
        address.setAddressTypes(createAddressTypes());
        address.setPreferred(isPreferred);
        return address;
    }

    private static AddressTypes createAddressTypes() {
        var addressTypes = new Address.AddressTypes();
        addressTypes.getAddressType().addAll(ADDRESS_TYPES);
        return addressTypes;
    }

    private static Country createCountry(String landkode) {
        var country = new Country();
        if (!StringUtils.isEmpty(landkode)) {
            country.setValue(landkode.toUpperCase(Locale.ROOT));
        }
        return country;
    }
}
