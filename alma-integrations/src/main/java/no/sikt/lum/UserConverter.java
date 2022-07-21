package no.sikt.lum;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import no.nb.basebibliotek.generated.Aut;
import no.nb.basebibliotek.generated.BaseBibliotek;
import no.nb.basebibliotek.generated.Record;
import no.sikt.alma.user.generated.RsLibraries;
import no.sikt.alma.user.generated.RsLibrary;
import no.sikt.alma.user.generated.User;
import no.sikt.alma.user.generated.User.AccountType;
import no.sikt.alma.user.generated.User.CampusCode;
import no.sikt.alma.user.generated.User.Gender;
import no.sikt.alma.user.generated.User.PreferredLanguage;
import no.sikt.alma.user.generated.User.RecordType;
import no.sikt.alma.user.generated.User.Status;
import no.sikt.alma.user.generated.UserRole;
import no.sikt.alma.user.generated.UserRoles;
import no.sikt.alma.user.generated.UserStatistic;
import no.sikt.alma.user.generated.UserStatistics;
import no.sikt.commons.AlmaConverter;
import no.sikt.commons.HandlerUtils;
import no.sikt.rsp.AlmaCodeProvider;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserConverter extends AlmaConverter {

    public static final String COUNTRYCODE_NORWAY = "NO";
    public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                                                                                   Locale.ROOT);
    private static final Logger logger = LoggerFactory.getLogger(UserConverter.class);
    public static final String INST = "inst";
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
    public static final String COULD_NOT_GENERATE_A_CAMPUS_CODE_FOR = "Could not generate a campus-code for %s";
    public static final String STATISTICS_CATEGORY_BIM = "BIM";
    public static final String STATISTICS_TYPE_USER_UPDATE = "USER_UPDATE";
    public static final String STATISTICS_TYPE_BRUKEROPPDATERING = "Brukeroppdatering";
    public static final String EXTERNAL_ID_SIS = "SIS";
    public static final String ACCOUNT_TYPE_EXTERNAL = "EXTERNAL";
    private final transient String targetAlmaCode;

    public UserConverter(AlmaCodeProvider almaCodeProvider, BaseBibliotek baseBibliotek, String targetAlmaCode) {
        super(almaCodeProvider, baseBibliotek);
        this.targetAlmaCode = targetAlmaCode;
    }

    @Override
    protected void logProblemAndThrowException(Record record) {
        var missingParameters = Objects.nonNull(record.getInst()) ? StringUtils.EMPTY_STRING : INST;
        logger.info(String.format(COULD_NOT_CONVERT_RECORD, missingParameters, toXml(record)));
        throw new RuntimeException(String.format(COULD_NOT_CONVERT_RECORD, missingParameters, toXml(record)));
    }

    public List<User> toUser() {
        return baseBibliotek
            .getRecord()
            .stream()
            .map(this::convertRecordToUserWhenConstraintsSatisfied)
            .collect(Collectors.toList());
    }

    private User convertRecordToUserWhenConstraintsSatisfied(Record record) {
        if (satisfiesConstraints(record)) {
            return convertRecordToUser(record);
        } else {
            logProblemAndThrowException(record);
            return null;
        }
    }

    @Override
    protected List<String> findMissingRequiredFields(Record record) {
        final List<String> missingFields = new ArrayList<>();
        if (StringUtils.isEmpty(record.getInst())) {
            missingFields.add(INST);
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
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(record.getLandkode())) {
            user.setPreferredLanguage(extractPreferredLanguage(record));
        }
        user.setUserGroup(UserGroupConverter.extractUserGroup(record));
        user.setUserRoles(defineUserRoles());
        user.setCampusCode(defineCampusCode());
        if (defineRSLibaries().isPresent()) {
            user.setRsLibraries(defineRSLibaries().get());
        }
        user.setUserStatistics(defaultUserStatistics());
        user.setExternalId(EXTERNAL_ID_SIS);
        user.setAccountType(defaultAccountType());
        user.setPassword(extractPassword(record));
        user.setContactInfo(ContactInfoConverter.extractContactInfo(record));
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
        // replace linefeeds with " - "
        String libraryName = record.getInst()
            .replace(LINEFEED, StringUtils.SPACE + HandlerUtils.HYPHEN + StringUtils.SPACE);
        String ampersand;
        switch (record.getLandkode().toUpperCase(Locale.ROOT)) {
            case COUNTRYCODE_GREATBRITAIN:
            case COUNTRYCODE_UNITEDSTATES:
            case COUNTRYCODE_CANADA:
            case COUNTRYCODE_AUSTRALIA:
            case COUNTRYCODE_IRLAND:
            case COUNTRYCODE_NEWZEALAND:
                ampersand = AND_ENGLISH;
                break;
            case COUNTRYCODE_GERMANY:
            case COUNTRYCODE_AUSTRIA:
            case COUNTRYCODE_SWITZERLAND:
                ampersand = AND_GERMAN;
                break;
            case COUNTRYCODE_FRANCE:
            case COUNTRYCODE_BELGIUM:
                ampersand = AND_FRENCH;
                break;
            case COUNTRYCODE_FINLAND:
            case COUNTRYCODE_ESTLAND:
                ampersand = AND_FINISH;
                break;
            case COUNTRYCODE_NETHERLANDS:
                ampersand = AND_DUTCH;
                break;
            case COUNTRYCODE_SWEDEN:
                ampersand = AND_SWEDISH;
                break;
            case COUNTRYCODE_POLAND:
                ampersand = AND_POLISH;
                break;
            case COUNTRYCODE_SPAIN:
                ampersand = AND_SPANISH;
                break;
            case COUNTRYCODE_PORTUGAL:
            case COUNTRYCODE_ITALY:
                ampersand = AND_ITALIAN_PORTUGUESE;
                break;
            default:
                ampersand = AND_NORWEGIAN;
        }
        libraryName = libraryName.replace(AMPERSAND, ampersand); // replace & with " og "
        return libraryName;
    }

    private Gender defaultGender() {
        User.Gender gender = new User.Gender();
        gender.setValue(NONE);
        return gender;
    }

    private PreferredLanguage extractPreferredLanguage(Record record) {
        User.PreferredLanguage lang = new User.PreferredLanguage();
        lang.setValue(COUNTRYCODE_NORWAY.equals(record.getLandkode()) ? LANGUAGECODE_BOKMAAL : LANGUAGECODE_ENGLISH);
        return lang;
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

    private CampusCode defineCampusCode() {
        // Todo: this might be not precise enough. As it uses the libCode to the alma-instance the libUser is updated to
        // (as defined in the libCodeToAlmaCode mapping config file used in RSP)
        Optional<String> libCode = almaCodeProvider.getLibCode(targetAlmaCode);
        if (libCode.isPresent()) {
            User.CampusCode campusCode = new User.CampusCode();
            campusCode.setValue(libCode.get());
            campusCode.setDesc(libCode.get());
            return campusCode;
        }
        //todo: what to do, when we do not have anything in that file??? @Audun
        throw new RuntimeException(String.format(COULD_NOT_GENERATE_A_CAMPUS_CODE_FOR, targetAlmaCode));
    }


    private Optional<RsLibraries> defineRSLibaries() {
        //Todo: is that sufficient? Same issue as with campusCode @Audun
        Optional<RsLibraries> rsLibraries = Optional.of(new RsLibraries());
        Optional<String> libCode = almaCodeProvider.getLibCode(targetAlmaCode);
        if (libCode.isPresent()) {
            RsLibrary rsLibrary = new RsLibrary();
            RsLibrary.Code rsLCode = new RsLibrary.Code();
            rsLCode.setValue(libCode.get());
            rsLibrary.setCode(rsLCode);
            rsLibraries.get().getRsLibrary().add(rsLibrary);
        }
        return rsLibraries;
    }

    private UserStatistics defaultUserStatistics() {
        String now = SIMPLE_DATE_FORMAT.format(Calendar.getInstance().getTime());
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
}

