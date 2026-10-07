package fm.corus.android.domain

object UsernameValidator {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 25

    sealed class Result {
        object Empty : Result()
        // Valid-so-far but under MIN_LENGTH. Distinct from Invalid so the UI can
        // stay neutral while the user is still typing toward 3 chars and only
        // surface the length message on an explicit submit tap.
        object TooShort : Result()
        object Valid : Result()
        data class Invalid(val message: String) : Result()
    }

    // Shared copy for both length failures (too short on submit, too long live)
    // and web's onboarding_username_invalid.
    val lengthRangeMessage: String
        get() = "$MIN_LENGTH-$MAX_LENGTH chars, must include a letter."

    fun clean(input: String): String =
        input.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(MAX_LENGTH)

    fun validate(input: String): Result {
        if (input.isEmpty()) return Result.Empty

        if (!input.all { it.isLetterOrDigit() || it == '_' || it == '.' }) {
            return Result.Invalid("Only letters, numbers, underscores, and periods")
        }

        if (input.contains("..")) {
            return Result.Invalid("Username can't have two periods in a row")
        }

        if (input.startsWith('.')) {
            return Result.Invalid("Username can't start with a period")
        }

        if (input.endsWith('.')) {
            return Result.Invalid("Username can't end with a period")
        }

        if (input.none { it.isLetter() }) {
            return Result.Invalid("Username must contain at least one letter")
        }

        // Length parity with web and the server rule (isValidUsername in
        // firestore.rules): 3 to 25 chars. Checked after the charset/letter
        // rules so length is the last thing standing between a well-formed
        // handle and validity.
        //   - Too long is a hard error shown live (a real limit was overshot);
        //     also the backstop for callers that skip clean() (which caps at 25).
        //   - Too short is NOT an error yet — the user is still typing toward 3
        //     chars, so return TooShort and let the UI stay neutral until submit.
        if (input.length > MAX_LENGTH) {
            return Result.Invalid(lengthRangeMessage)
        }
        if (input.length < MIN_LENGTH) {
            return Result.TooShort
        }

        return Result.Valid
    }

    // Reserved handles strangers can't register (brand, support, system,
    // impersonation-bait). Keep in sync with the iOS/web UsernameValidator
    // RESERVED sets and `reservedUsernames()` in backend/firestore.rules (the
    // server source of truth). Enforced via FirestoreDataSource.checkUsernameAvailable.
    val RESERVED: Set<String> = setOf(
        // Generated from Corus-Web/config/reserved-usernames.json.
        // 50 system handles plus 200 artist identities and exact separator variants.
        "corus", "corusapp", "corusofficial", "corusmedia",
        "corusfm", "corushq", "teamcorus", "corusclub",
        "official", "verified", "corushelp", "help",
        "support", "corussupport", "admin", "administrator",
        "moderator", "mod", "staff", "corusstaff",
        "team", "abuse", "report", "trust",
        "safety", "contact", "info", "feedback",
        "press", "media", "legal", "privacy",
        "security", "billing", "payments", "root",
        "system", "api", "www", "mail",
        "noreply", "notifications", "bot", "corusbot",
        "everyone", "founder", "ceo", "null",
        "anonymous", "user",
        // Artist handles: no prefix or substring matching.
        "billieeilish", "billie.eilish", "billie_eilish", "taylorswift",
        "taylor.swift", "taylor_swift", "arianagrande", "ariana.grande",
        "ariana_grande", "sabrinacarpenter", "sabrina.carpenter", "sabrina_carpenter",
        "oliviarodrigo", "olivia.rodrigo", "olivia_rodrigo", "chappellroan",
        "chappell.roan", "chappell_roan", "dualipa", "dua.lipa",
        "dua_lipa", "ladygaga", "lady.gaga", "lady_gaga",
        "brunomars", "bruno.mars", "bruno_mars", "theweeknd",
        "the.weeknd", "the_weeknd", "badbunny", "bad.bunny",
        "bad_bunny", "justinbieber", "justin.bieber", "justin_bieber",
        "edsheeran", "ed.sheeran", "ed_sheeran", "harrystyles",
        "harry.styles", "harry_styles", "lanadelrey", "lana.del.rey",
        "lana_del_rey", "kendricklamar", "kendrick.lamar", "kendrick_lamar",
        "travisscott", "travis.scott", "travis_scott", "postmalone",
        "post.malone", "post_malone", "nickiminaj", "nicki.minaj",
        "nicki_minaj", "cardib", "cardi.b", "cardi_b",
        "dojacat", "doja.cat", "doja_cat", "megantheestallion",
        "megan.thee.stallion", "megan_thee_stallion", "lilnasx", "lil.nas.x",
        "lil_nas_x", "tylerthecreator", "tyler.the.creator", "tyler_the_creator",
        "frankocean", "frank.ocean", "frank_ocean", "childishgambino",
        "childish.gambino", "childish_gambino", "playboicarti", "playboi.carti",
        "playboi_carti", "asaprocky", "asap.rocky", "asap_rocky",
        "liluzivert", "lil.uzi.vert", "lil_uzi_vert", "lilbaby",
        "lil.baby", "lil_baby", "lildurk", "lil.durk",
        "lil_durk", "lilwayne", "lil.wayne", "lil_wayne",
        "juicewrld", "juice.wrld", "juice_wrld", "popsmoke",
        "pop.smoke", "pop_smoke", "macmiller", "mac.miller",
        "mac_miller", "jcole", "j.cole", "j_cole",
        "kidcudi", "kid.cudi", "kid_cudi", "chancetherapper",
        "chance.the.rapper", "chance_the_rapper", "andersonpaak", "anderson.paak",
        "anderson_paak", "stevelacy", "steve.lacy", "steve_lacy",
        "danielcaesar", "daniel.caesar", "daniel_caesar", "summerwalker",
        "summer.walker", "summer_walker", "brysontiller", "bryson.tiller",
        "bryson_tiller", "partynextdoor", "jheneaiko", "jhene.aiko",
        "jhene_aiko", "snohaalegra", "snoh.aalegra", "snoh_aalegra",
        "kehlani", "tinashe", "victoriamonet", "victoria.monet",
        "victoria_monet", "tatemcrae", "tate.mcrae", "tate_mcrae",
        "gracieabrams", "gracie.abrams", "gracie_abrams", "madisonbeer",
        "madison.beer", "madison_beer", "addisonrae", "addison.rae",
        "addison_rae", "reneerapp", "renee.rapp", "renee_rapp",
        "bensonboone", "benson.boone", "benson_boone", "conangray",
        "conan.gray", "conan_gray", "troyesivan", "troye.sivan",
        "troye_sivan", "charlieputh", "charlie.puth", "charlie_puth",
        "shawnmendes", "shawn.mendes", "shawn_mendes", "camilacabello",
        "camila.cabello", "camila_cabello", "selenagomez", "selena.gomez",
        "selena_gomez", "demilovato", "demi.lovato", "demi_lovato",
        "mileycyrus", "miley.cyrus", "miley_cyrus", "katyperry",
        "katy.perry", "katy_perry", "britneyspears", "britney.spears",
        "britney_spears", "christinaaguilera", "christina.aguilera", "christina_aguilera",
        "justintimberlake", "justin.timberlake", "justin_timberlake", "aliciakeys",
        "alicia.keys", "alicia_keys", "mariahcarey", "mariah.carey",
        "mariah_carey", "whitneyhouston", "whitney.houston", "whitney_houston",
        "celinedion", "celine.dion", "celine_dion", "cyndilauper",
        "cyndi.lauper", "cyndi_lauper", "janetjackson", "janet.jackson",
        "janet_jackson", "michaeljackson", "michael.jackson", "michael_jackson",
        "dianaross", "diana.ross", "diana_ross", "steviewonder",
        "stevie.wonder", "stevie_wonder", "lionelrichie", "lionel.richie",
        "lionel_richie", "tinaturner", "tina.turner", "tina_turner",
        "arethafranklin", "aretha.franklin", "aretha_franklin", "ninasimone",
        "nina.simone", "nina_simone", "laurynhill", "lauryn.hill",
        "lauryn_hill", "erykahbadu", "erykah.badu", "erykah_badu",
        "maryjblige", "mary.j.blige", "mary_j_blige", "missyelliott",
        "missy.elliott", "missy_elliott", "outkast", "wutangclan",
        "wu.tang.clan", "wu_tang_clan", "atribecalledquest", "a.tribe.called.quest",
        "a_tribe_called_quest", "delasoul", "de.la.soul", "de_la_soul",
        "publicenemy", "public.enemy", "public_enemy", "beastieboys",
        "beastie.boys", "beastie_boys", "rundmc", "run.dmc",
        "run_dmc", "snoopdogg", "snoop.dogg", "snoop_dogg",
        "drdre", "dr.dre", "dr_dre", "icecube",
        "ice.cube", "ice_cube", "tupacshakur", "tupac.shakur",
        "tupac_shakur", "thenotoriousbig", "the.notorious.big", "the_notorious_big",
        "jayz", "jay.z", "jay_z", "kanyewest",
        "kanye.west", "kanye_west", "eminem", "50cent",
        "50.cent", "50_cent", "21savage", "21.savage",
        "21_savage", "metroboomin", "metro.boomin", "metro_boomin",
        "dontoliver", "don.toliver", "don_toliver", "roddyricch",
        "roddy.ricch", "roddy_ricch", "polog", "polo.g",
        "polo_g", "centralcee", "central.cee", "central_cee",
        "jbalvin", "j.balvin", "j_balvin", "karolg",
        "karol.g", "karol_g", "rauwalejandro", "rauw.alejandro",
        "rauw_alejandro", "daddyyankee", "daddy.yankee", "daddy_yankee",
        "pesopluma", "peso.pluma", "peso_pluma", "fuerzaregida",
        "fuerza.regida", "fuerza_regida", "grupofrontera", "grupo.frontera",
        "grupo_frontera", "luismiguel", "luis.miguel", "luis_miguel",
        "luisfonsi", "luis.fonsi", "luis_fonsi", "rickymartin",
        "ricky.martin", "ricky_martin", "enriqueiglesias", "enrique.iglesias",
        "enrique_iglesias", "marcanthony", "marc.anthony", "marc_anthony",
        "sebastianyatra", "sebastian.yatra", "sebastian_yatra", "natalialafourcade",
        "natalia.lafourcade", "natalia_lafourcade", "julietavenegas", "julieta.venegas",
        "julieta_venegas", "caetanoveloso", "caetano.veloso", "caetano_veloso",
        "gilbertogil", "gilberto.gil", "gilberto_gil", "chicobuarque",
        "chico.buarque", "chico_buarque", "mariabethania", "maria.bethania",
        "maria_bethania", "marisamonte", "marisa.monte", "marisa_monte",
        "jorgebenjor", "jorge.ben.jor", "jorge_ben_jor", "timmaia",
        "tim.maia", "tim_maia", "galcosta", "gal.costa",
        "gal_costa", "elisregina", "elis.regina", "elis_regina",
        "miltonnascimento", "milton.nascimento", "milton_nascimento", "djavan",
        "ludmilla", "pabllovittar", "pabllo.vittar", "pabllo_vittar",
        "luisasonza", "luisa.sonza", "luisa_sonza", "gloriagroove",
        "gloria.groove", "gloria_groove", "liniker", "emicida",
        "racionaismcs", "racionais.mcs", "racionais_mcs", "charliebrownjr",
        "charlie.brown.jr", "charlie_brown_jr", "legiaourbana", "legiao.urbana",
        "legiao_urbana", "osmutantes", "os.mutantes", "os_mutantes",
        "secosemolhados", "secos.e.molhados", "secos_e_molhados", "novosbaianos",
        "novos.baianos", "novos_baianos", "sepultura", "blackpink",
        "straykids", "stray.kids", "stray_kids", "newjeans",
        "lesserafim", "le.sserafim", "le_sserafim", "tomorrowxtogether",
        "tomorrow.x.together", "tomorrow_x_together", "redvelvet", "red.velvet",
        "red_velvet", "bts", "twentyonepilots", "twenty.one.pilots",
        "twenty_one_pilots", "aespa", "coldplay", "radiohead",
        "thebeatles", "the.beatles", "the_beatles", "therollingstones",
        "the.rolling.stones", "the_rolling_stones", "pinkfloyd", "pink.floyd",
        "pink_floyd", "ledzeppelin", "led.zeppelin", "led_zeppelin",
        "fleetwoodmac", "fleetwood.mac", "fleetwood_mac", "thebeachboys",
        "the.beach.boys", "the_beach_boys", "thedoors", "the.doors",
        "the_doors", "thecure", "the.cure", "the_cure",
        "thesmiths", "the.smiths", "the_smiths", "depechemode",
        "depeche.mode", "depeche_mode", "joydivision", "joy.division",
        "joy_division", "neworder", "new.order", "new_order",
        "talkingheads", "talking.heads", "talking_heads", "davidbowie",
        "david.bowie", "david_bowie", "eltonjohn", "elton.john",
        "elton_john", "billyjoel", "billy.joel", "billy_joel",
        "brucespringsteen", "bruce.springsteen", "bruce_springsteen", "bobdylan",
        "bob.dylan", "bob_dylan", "jonimitchell", "joni.mitchell",
        "joni_mitchell", "leonardcohen", "leonard.cohen", "leonard_cohen",
        "paulmccartney", "paul.mccartney", "paul_mccartney", "johnlennon",
        "john.lennon", "john_lennon", "georgeharrison", "george.harrison",
        "george_harrison", "ringostarr", "ringo.starr", "ringo_starr",
        "katebush", "kate.bush", "kate_bush", "pjharvey",
        "pj.harvey", "pj_harvey", "bjork", "nirvana",
        "foofighters", "foo.fighters", "foo_fighters", "pearljam",
        "pearl.jam", "pearl_jam", "soundgarden", "aliceinchains",
        "alice.in.chains", "alice_in_chains", "redhotchilipeppers", "red.hot.chili.peppers",
        "red_hot_chili_peppers", "greenday", "green.day", "green_day",
        "blink182", "blink.182", "blink_182", "linkinpark",
        "linkin.park", "linkin_park", "mychemicalromance", "my.chemical.romance",
        "my_chemical_romance", "falloutboy", "fall.out.boy", "fall_out_boy",
        "panicatthedisco", "panic.at.the.disco", "panic_at_the_disco", "arcticmonkeys",
        "arctic.monkeys", "arctic_monkeys", "tameimpala", "tame.impala",
        "tame_impala", "thestrokes", "the.strokes", "the_strokes",
        "thekillers", "the.killers", "the_killers", "daftpunk",
        "daft.punk", "daft_punk",
    )

    fun isReserved(input: String): Boolean = input.lowercase() in RESERVED
}
