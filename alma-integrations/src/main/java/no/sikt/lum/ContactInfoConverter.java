package no.sikt.lum;

import static no.sikt.commons.HandlerUtils.HYPHEN;
import static org.apache.commons.lang3.StringUtils.join;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.user.generated.Address;
import no.sikt.alma.user.generated.Address.AddressTypes;
import no.sikt.alma.user.generated.Address.Country;
import no.sikt.alma.user.generated.Addresses;
import no.sikt.alma.user.generated.ContactInfo;
import no.sikt.alma.user.generated.Email;
import no.sikt.alma.user.generated.Email.EmailTypes;
import no.sikt.alma.user.generated.Emails;
import no.sikt.alma.user.generated.Phone;
import no.sikt.alma.user.generated.Phone.PhoneTypes;
import no.sikt.alma.user.generated.Phones;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;

public final class ContactInfoConverter {

    private static final boolean BEST_EMAIL_IS_ALWAYS_PREFERRED = true;
    private static final boolean THERE_IS_ONLY_ONE_PHONE_SO_IT_IS_ALWAYS_PREFERRED = true;
    private static final boolean P_ADDRESS_IS_ALWAYS_PREFERRED = true;
    public static final String WORK = "Work";
    public static final String OFFICE = "Office";
    public static final String COMMA_SEPARATOR = ", ";
    public static final String LOCALES_WITHOUT_ISO_3_COUNTRY_CODES = "Locales without ISO3Country-codes: ";

    static final Map<String, String> twoToThreeMap = new HashMap<>();



    static {
        Locale[] availableLocales = Locale.getAvailableLocales();
        List<String> localesWithoutISO3Country = new ArrayList<>();
        for (Locale locale : availableLocales) {
            try {
                twoToThreeMap.put(locale.getCountry(), locale.getISO3Country());
            } catch (MissingResourceException e) {
                localesWithoutISO3Country.add(locale.toString());
                // ignore, is useless anyway
            }
        }
        System.out.println(LOCALES_WITHOUT_ISO_3_COUNTRY_CODES + join(localesWithoutISO3Country, COMMA_SEPARATOR));
    }

    @JacocoGenerated
    private ContactInfoConverter() {
    }

    public static ContactInfo extractContactInfo(Record record) {
        var contactInfo = new ContactInfo();
        contactInfo.setAddresses(extractAddressesFromRecord(record));
        contactInfo.setPhones(createPhones(record));
        contactInfo.setEmails(createEmails(record));
        return contactInfo;
    }

    private static Emails createEmails(Record record) {
        var emailBest = createEmailBest(record);
        var emailBestAdr = emailBest.isPresent() ?  emailBest.get().getEmailAddress() : StringUtils.EMPTY_STRING;
        var emailRegular = createEmailRegular(record, emailBestAdr);
        var emails = new Emails();
        emailBest.ifPresent(email -> emails.getEmail().add(email));
        emailRegular.ifPresent(email -> emails.getEmail().add(email));
        return emails;
    }

    private static Optional<Email> createEmailRegular(Record record, String emailBestAdr) {
        return Objects.nonNull(record.getEpostAdr())
                   ? Optional.of(createEmail(record.getEpostAdr(), false))
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
        var emailTypes = new EmailTypes();
        Email.EmailTypes.EmailType emailType = new Email.EmailTypes.EmailType();
        emailType.setValue(WORK.toLowerCase(Locale.ROOT));
        emailType.setDesc(WORK);
        emailTypes.getEmailType().add(emailType);
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
        Phone.PhoneTypes.PhoneType phoneType = new Phone.PhoneTypes.PhoneType();
        phoneType.setValue(OFFICE.toLowerCase(Locale.ROOT));
        phoneType.setDesc(OFFICE);
        phoneTypes.getPhoneType().add(phoneType);
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

        final String line5 = landkode.toUpperCase(Locale.ROOT) + HYPHEN + bibnr;
        var address = new Address();
        address.setLine1(adr);
        address.setLine5(line5);
        address.setCity(poststed);
        address.setPostalCode(postnr);
        address.setCountry(createCountry(landkode));
        address.setAddressTypes(createAddressTypes());
        address.setPreferred(isPreferred);
        return address;
    }

    private static AddressTypes createAddressTypes() {
        var addressTypes = new AddressTypes();
        Address.AddressTypes.AddressType addressType = new Address.AddressTypes.AddressType();
        addressType.setValue(WORK.toLowerCase(Locale.ROOT));
        addressType.setDesc(WORK);
        addressTypes.getAddressType().add(addressType);
        return addressTypes;
    }

    private static Country createCountry(String landkode) {
        String land = twoToThreeMap.get(landkode.toUpperCase(Locale.ROOT));
        var country = new Country();
        if (!StringUtils.isEmpty(land)) {
            country.setValue(land.toUpperCase(Locale.ROOT));
        } else {
            country.setValue(landkode.toUpperCase(Locale.ROOT));
        }
        return country;
    }

}