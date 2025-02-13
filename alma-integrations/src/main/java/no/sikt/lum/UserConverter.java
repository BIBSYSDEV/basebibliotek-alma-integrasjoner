package no.sikt.lum;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.Aut;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.user.generated.User;
import no.sikt.alma.user.generated.User.AccountType;
import no.sikt.alma.user.generated.User.Gender;
import no.sikt.alma.user.generated.User.RecordType;
import no.sikt.alma.user.generated.User.Status;
import no.sikt.alma.user.generated.UserIdentifier;
import no.sikt.alma.user.generated.UserIdentifiers;
import no.sikt.alma.user.generated.UserRole;
import no.sikt.alma.user.generated.UserRoles;
import no.sikt.alma.user.generated.UserStatistic;
import no.sikt.alma.user.generated.UserStatistics;
import no.sikt.commons.AlmaObjectConverter;
import no.sikt.commons.HandlerUtils;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public class UserConverter extends AlmaObjectConverter {

    public static final String COUNTRYCODE_NORWAY = "NO";
    private static final Logger logger = LoggerFactory.getLogger(UserConverter.class);
    public static final String COULD_NOT_CONVERT_TO_USER_ERROR_MESSAGE = " Could not convert to user";
    public static final String COULD_NOT_CONVERT_TO_USER_REPORT_MESSAGE = " could not convert to user\n";
    public static final String INST = "inst";
    public static final String BIBLTYPE = "bibltype";
    public static final String PUBLIC = "Public";
    public static final String INACTIVE = "Inactive";
    public static final String ACTIVE = "Active";
    public static final String AMPERSAND = "&";
    public static final String AND_DUTCH = "en";
    public static final String AND_ENGLISH = "and";
    public static final String AND_FINISH = "ja";
    public static final String AND_FRENCH = "et";
    public static final String AND_GERMAN = "und";
    public static final String AND_ITALIAN_PORTUGUESE = "e";
    public static final String AND_NORWEGIAN = "og";
    public static final String AND_POLISH = "i";
    public static final String AND_SPANISH = "y";
    public static final String AND_SWEDISH = "och";
    public static final String COUNTRYCODE_GREATBRITAIN = "GB";
    public static final String COUNTRYCODE_UNITEDSTATES = "US";
    public static final String COUNTRYCODE_CANADA = "CA";
    public static final String COUNTRYCODE_AUSTRALIA = "AU";
    public static final String COUNTRYCODE_IRLAND = "IE";
    public static final String COUNTRYCODE_NEWZEALAND = "NZ";
    public static final String COUNTRYCODE_GERMANY = "DE";
    public static final String COUNTRYCODE_AUSTRIA = "AT";
    public static final String COUNTRYCODE_SWITZERLAND = "CH";
    public static final String COUNTRYCODE_BELGIUM = "BE";
    public static final String COUNTRYCODE_FINLAND = "FI";
    public static final String COUNTRYCODE_ESTLAND = "EE";
    public static final String COUNTRYCODE_NETHERLANDS = "NL";
    public static final String COUNTRYCODE_FRANCE = "FR";
    public static final String COUNTRYCODE_SWEDEN = "SE";
    public static final String COUNTRYCODE_POLAND = "PL";
    public static final String COUNTRYCODE_SPAIN = "ES";
    public static final String COUNTRYCODE_PORTUGAL = "PT";
    public static final String COUNTRYCODE_ITALY = "IT";
    public static final String LANGUAGECODE_BOKMAAL = "nb";
    public static final String LANGUAGECODE_ENGLISH = "en";
    public static final String DEFAULT_LASTNAME_LIB = "Lib";
    public static final String NONE = "NONE";
    public static final String DEFAULT_PATRON_ROLE_200 = "200";
    public static final String PATRON_ROLE = "Patron";
    public static final String STATISTICS_CATEGORY_BIM = "BIM";
    public static final String STATISTICS_TYPE_USER_UPDATE = "USER_UPDATE";
    public static final String STATISTICS_TYPE_BRUKEROPPDATERING = "Brukeroppdatering";
    public static final String EXTERNAL_ID_SIS = "SIS";
    public static final String ACCOUNT_TYPE_EXTERNAL = "EXTERNAL";
    public static final String LIB_USER_PREFIX = "lib";
    public static final String EXTERNAL = "External";
    public static final String UNIV_ID = "UNIV_ID";
    public static final String UNIVERSITY_ID = "University ID";
    public static final Set<String> USER_IDENTIFIER_REALMS = Set.of("@bibsys.no", "@basebibliotek.no");
    private final transient String targetAlmaCode;

    public UserConverter(BaseBibliotek baseBibliotek, String targetAlmaCode) {
        super(baseBibliotek);
        this.targetAlmaCode = targetAlmaCode;
    }

    @Override
    protected void logProblemAndThrowException(Record record) {
        var missingParameters = Objects.nonNull(record.getInst()) ? StringUtils.EMPTY_STRING : INST;
        logger.info(String.format(COULD_NOT_CONVERT_RECORD, missingParameters, toXml(record)));
        throw new RuntimeException(String.format(COULD_NOT_CONVERT_RECORD, missingParameters, toXml(record)));
    }

    public List<User> toUsers(StringBuilder reportStringBuilder) {
        List<User> users = new ArrayList<>();
        baseBibliotek
            .getRecord()
            .forEach(record -> convertRecordToUserWhenConstraintsSatisfied(record, reportStringBuilder)
                .ifPresent(users::add));
        return users;
    }

    private Optional<User> convertRecordToUserWhenConstraintsSatisfied(Record record,
                                                                       StringBuilder reportStringBuilder) {
        try {
            if (satisfiesConstraints(record)) {
                return Optional.of(convertRecordToUser(record));
            } else {
                logProblemAndThrowException(record);
                return Optional.empty();
            }
        } catch (Exception e) {
            //Errors in individual libraries should not cause crash in entire execution.
            logger.info(COULD_NOT_CONVERT_TO_USER_ERROR_MESSAGE, e);
            reportStringBuilder
                .append(baseBibliotek.getRecord().getFirst().getBibnr())
                .append(COULD_NOT_CONVERT_TO_USER_REPORT_MESSAGE);
            return Optional.empty();
        }
    }

    @Override
    protected List<String> findMissingRequiredFields(Record record) {
        final List<String> missingFields = new ArrayList<>();
        if (StringUtils.isEmpty(record.getInst())) {
            missingFields.add(INST);
        }
        if (StringUtils.isEmpty(record.getBibltype())) {
            missingFields.add(BIBLTYPE);
        }
        return missingFields;
    }

    private User convertRecordToUser(Record record) {
        var user = new User();
        user.setRecordType(defaultRecordType());
        user.setStatus(defineUserStatus(record));
        user.setFirstName(extractPrettyLibraryNameWithoutAmpersand(record));
        user.setLastName(DEFAULT_LASTNAME_LIB);
        user.setGender(defaultGender());
        extractPreferredLanguage(record, user);
        user.setUserGroup(UserGroupConverter.extractUserGroup(record));
        user.setUserRoles(defineUserRoles());
        user.setUserStatistics(defaultUserStatistics());
        user.setExternalId(EXTERNAL_ID_SIS);
        user.setAccountType(defaultAccountType());
        user.setPassword(extractPassword(record));
        user.setContactInfo(ContactInfoConverter.extractContactInfo(record));
        user.setPrimaryId(extractPrimaryID(record));
        user.setUserIdentifiers(extractUserIdentifiers(record));
        return user;
    }

    private RecordType defaultRecordType() {
        User.RecordType recordType = new User.RecordType();
        recordType.setValue(PUBLIC.toUpperCase(Locale.ROOT));
        recordType.setDesc(PUBLIC);
        return recordType;
    }

    private Status defineUserStatus(Record record) {
        User.Status status = new User.Status();
        if (Optional.of(PERMANENTLY_CLOSED).equals(Optional.ofNullable(record.getStengt()))) {
            status.setValue(INACTIVE.toUpperCase(Locale.ROOT));
            status.setDesc(INACTIVE);
        } else {
            status.setValue(ACTIVE.toUpperCase(Locale.ROOT));
            status.setDesc(ACTIVE);
        }
        return status;
    }

    @SuppressWarnings("PMD.ImplicitSwitchFallThrough")
    public String extractPrettyLibraryNameWithoutAmpersand(Record record) {
        // replace linefeed with " - "
        String libraryName = record.getInst()
            .replace(LINEFEED, StringUtils.SPACE + HandlerUtils.HYPHEN + StringUtils.SPACE);
        libraryName = StringUtils.removeMultipleWhiteSpaces(libraryName);
        String ampersand = switch (record.getLandkode().toUpperCase(Locale.ROOT)) {
            case COUNTRYCODE_GREATBRITAIN, COUNTRYCODE_UNITEDSTATES, COUNTRYCODE_CANADA, COUNTRYCODE_AUSTRALIA,
                 COUNTRYCODE_IRLAND, COUNTRYCODE_NEWZEALAND -> AND_ENGLISH;
            case COUNTRYCODE_GERMANY, COUNTRYCODE_AUSTRIA, COUNTRYCODE_SWITZERLAND -> AND_GERMAN;
            case COUNTRYCODE_FRANCE, COUNTRYCODE_BELGIUM -> AND_FRENCH;
            case COUNTRYCODE_FINLAND, COUNTRYCODE_ESTLAND -> AND_FINISH;
            case COUNTRYCODE_NETHERLANDS -> AND_DUTCH;
            case COUNTRYCODE_SWEDEN -> AND_SWEDISH;
            case COUNTRYCODE_POLAND -> AND_POLISH;
            case COUNTRYCODE_SPAIN -> AND_SPANISH;
            case COUNTRYCODE_PORTUGAL, COUNTRYCODE_ITALY -> AND_ITALIAN_PORTUGUESE;
            default -> AND_NORWEGIAN;
        };
        libraryName = libraryName.replace(AMPERSAND, ampersand); // replace & with " og "
        return libraryName;
    }

    private Gender defaultGender() {
        User.Gender gender = new User.Gender();
        gender.setValue(NONE);
        return gender;
    }

    private void extractPreferredLanguage(Record record, User user) {
        String landkode = record.getLandkode();
        if (isNotEmpty(landkode)) {
            User.PreferredLanguage lang = new User.PreferredLanguage();
            lang.setValue(COUNTRYCODE_NORWAY.equals(landkode) ? LANGUAGECODE_BOKMAAL : LANGUAGECODE_ENGLISH);
            user.setPreferredLanguage(lang);
        }
    }

    private UserRoles defineUserRoles() {
        UserRole userRole = new UserRole();
        UserRole.RoleType roleType = new UserRole.RoleType();
        roleType.setValue(DEFAULT_PATRON_ROLE_200);
        roleType.setDesc(PATRON_ROLE);
        userRole.setRoleType(roleType);
        UserRole.Scope scope = new UserRole.Scope();
        scope.setValue(INSTITUTION_CODE_PREFIX + targetAlmaCode);
        userRole.setScope(scope);
        UserRoles userRoles = new UserRoles();
        userRoles.getUserRole().add(userRole);
        return userRoles;
    }

    private UserStatistics defaultUserStatistics() {
        String now = getCurrentTime();
        UserStatistic userStatistic = new UserStatistic();
        userStatistic.setStatisticNote(now);
        UserStatistic.StatisticCategory statisticCategory = new UserStatistic.StatisticCategory();
        statisticCategory.setValue(STATISTICS_CATEGORY_BIM);
        statisticCategory.setDesc(STATISTICS_CATEGORY_BIM);
        userStatistic.setStatisticCategory(statisticCategory);
        UserStatistic.CategoryType categoryType = new UserStatistic.CategoryType();
        categoryType.setValue(STATISTICS_TYPE_USER_UPDATE);
        categoryType.setDesc(STATISTICS_TYPE_BRUKEROPPDATERING);
        userStatistic.setCategoryType(categoryType);
        UserStatistics userStatistics = new UserStatistics();
        userStatistics.getUserStatistic().add(userStatistic);
        return userStatistics;
    }

    private String getCurrentTime() {
        SimpleDateFormat patternString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(patternString.toPattern());
        return formatter.format(LocalDateTime.now());
    }

    private AccountType defaultAccountType() {
        // Account Type - mandatory field for ExLibris
        User.AccountType accountType = new User.AccountType();
        accountType.setValue(ACCOUNT_TYPE_EXTERNAL);
        return accountType;
    }

    private String extractPassword(Record record) {
        return Optional.ofNullable(Optional.ofNullable(record.getAut()).orElse(new Aut()).getContent()).orElse(
            StringUtils.EMPTY_STRING);
    }

    private String extractPrimaryID(Record record) {
        return LIB_USER_PREFIX + getLibraryNumber(record);
    }

    private UserIdentifiers extractUserIdentifiers(Record record) {
        var allUserIdentifiers = createAllUserIdentifiers(record);
        var userIdentifiers = new UserIdentifiers();
        userIdentifiers.getUserIdentifier().addAll(allUserIdentifiers);

        return userIdentifiers;
    }

    private List<UserIdentifier> createAllUserIdentifiers(Record record) {
        return USER_IDENTIFIER_REALMS
                   .stream()
                   .map(realm -> createUserIdentifier(record, realm))
                   .collect(Collectors.toList());
    }

    private UserIdentifier createUserIdentifier(Record record, String realm) {
        var userIdentifier = new UserIdentifier();
        userIdentifier.setValue(getLibraryNumber(record) + realm);
        userIdentifier.setStatus(ACTIVE.toUpperCase(Locale.ROOT));
        userIdentifier.setSegmentType(EXTERNAL);

        var value = new UserIdentifier.IdType();
        value.setValue(UNIV_ID);
        value.setDesc(UNIVERSITY_ID);
        userIdentifier.setIdType(value);

        return userIdentifier;
    }

    private String getLibraryNumber(Record record) {
        return record.getBibnr().replaceAll("\\w+-", StringUtils.EMPTY_STRING);
    }
}

