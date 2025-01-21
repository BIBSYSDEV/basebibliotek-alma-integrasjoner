package test.utils;

import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.nb.basebibliotek.generated.Adgang;
import no.nb.basebibliotek.generated.BetalMrk;
import no.nb.basebibliotek.generated.FjGen;
import no.nb.basebibliotek.generated.FjMrk;
import no.nb.basebibliotek.generated.FjSpes;
import no.nb.basebibliotek.generated.IndSyst;
import no.nb.basebibliotek.generated.Klass;
import no.nb.basebibliotek.generated.KlassNoter;
import no.nb.basebibliotek.generated.KopGeb;
import no.nb.basebibliotek.generated.KopMrk;
import no.nb.basebibliotek.generated.Merknader;
import no.nb.basebibliotek.generated.Omtale;
import no.nb.basebibliotek.generated.SpesSaml;
import no.unit.nva.language.LanguageConstants;

public final class MerknaderGenerator {

    private MerknaderGenerator() {

    }

    public static Merknader randomMerknader() {
        var merknader = new Merknader();
        merknader.getAdgangOrBetalMrkOrFjGen().addAll(randomAdgangOrBetalMrkOrFjGenList());
        return merknader;
    }

    private static Collection<?> randomAdgangOrBetalMrkOrFjGenList() {
        var maxNumberOfAdgangOrBetalMrkOrFjGen = 12;
        return IntStream.range(0, randomInteger(maxNumberOfAdgangOrBetalMrkOrFjGen) + 1).boxed().map(
            MerknaderGenerator::randomAdgangOrBetalMrkOrFjGen).collect(
            Collectors.toList());
    }

    private static Object randomAdgangOrBetalMrkOrFjGen(int index) {
        return switch (index % 12) {
            case 0 -> randomAdgang();
            case 1 -> randomBetalMrk();
            case 2 -> randomFjGen();
            case 3 -> randomFjMrk();
            case 4 -> randomFjSpes();
            case 5 -> randomIndSyst();
            case 6 -> randomKlass();
            case 7 -> randomKlassNoter();
            case 8 -> randomKopGeb();
            case 9 -> randomKopMrk();
            case 10 -> randomOmtale();
            default -> randomSpesSaml();
        };
    }

    private static Omtale randomOmtale() {
        var omtale = new Omtale();
        omtale.setContent(randomString());
        omtale.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return omtale;
    }

    private static KopMrk randomKopMrk() {
        var kopMrk = new KopMrk();
        kopMrk.setContent(randomString());
        kopMrk.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return kopMrk;
    }

    private static KopGeb randomKopGeb() {
        var kopGeb = new KopGeb();
        kopGeb.setContent(randomString());
        kopGeb.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return kopGeb;
    }

    private static KlassNoter randomKlassNoter() {
        var klassNoter = new KlassNoter();
        klassNoter.setContent(randomString());
        klassNoter.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return klassNoter;
    }

    private static Klass randomKlass() {
        var klass = new Klass();
        klass.setContent(randomString());
        klass.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return klass;
    }

    private static IndSyst randomIndSyst() {
        var indSyst = new IndSyst();
        indSyst.setContent(randomString());
        indSyst.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return indSyst;
    }

    private static FjSpes randomFjSpes() {
        var fjSpes = new FjSpes();
        fjSpes.setContent(randomString());
        fjSpes.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return fjSpes;
    }

    private static FjMrk randomFjMrk() {
        var fjMrk = new FjMrk();
        fjMrk.setContent(randomString());
        fjMrk.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return fjMrk;
    }

    private static FjGen randomFjGen() {
        var fjGen = new FjGen();
        fjGen.setContent(randomString());
        fjGen.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return fjGen;
    }

    private static BetalMrk randomBetalMrk() {
        var betalMrk = new BetalMrk();
        betalMrk.setContent(randomString());
        betalMrk.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return betalMrk;
    }

    private static SpesSaml randomSpesSaml() {
        var spesSaml = new SpesSaml();
        spesSaml.setContent(randomString());
        spesSaml.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return spesSaml;
    }

    private static Adgang randomAdgang() {
        var adgang = new Adgang();
        adgang.setContent(randomString());
        adgang.setLang(randomElement(LanguageConstants.ALL_LANGUAGES).getIso6391Code());
        return adgang;
    }

}
