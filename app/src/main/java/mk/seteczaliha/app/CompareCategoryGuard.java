package mk.seteczaliha.app;

import java.util.Locale;
import java.util.Map;

/**
 * Identify a product family conservatively using the verified product title
 * and, when available, category-specific named specifications from SETEC.
 * No family is inferred solely from generic capacity, power or brand.
 */
final class CompareCategoryGuard {
    private CompareCategoryGuard(){}

    enum Kind {
        WASHER("Машини за перење"),
        WASHER_DRYER("Машини за перење и сушење"),
        DRYER("Машини за сушење"),
        DISHWASHER("Машини за садови"),
        REFRIGERATOR("Фрижидери / замрзнувачи"),
        TV("Телевизори"),
        MONITOR("Монитори"),
        LAPTOP("Лаптопи"),
        PHONE("Телефони"),
        AIR_CONDITIONER("Клими"),
        HOOD("Аспиратори"),
        OVEN("Шпорети / рерни"),
        TOASTER("Тостери"),
        VACUUM("Правосмукалки"),
        MICROWAVE("Микробранови печки"),
        PRINTER("Принтери"),
        SPEAKER("Звучници"),
        ROUTER("Рутери"),
        HAIR_DRYER("Фенови за коса"),
        UNKNOWN("Непотврдена категорија");
        final String label;
        Kind(String label){this.label=label;}
    }

    private static boolean contains(String source,String...keys){
        for(String key:keys)if(source.contains(key))return true;
        return false;
    }

    /** Exact category words in the title take precedence over generic specs. */
    static Kind guess(String model){
        String n=String.valueOf(model).toUpperCase(Locale.ROOT)
            .replaceAll("\\s+"," ").trim();
        if(contains(n,"WASHER DRYER","WASHING DRYING","МАШИНА ЗА ПЕРЕЊЕ И СУШЕЊЕ",
            "КОМБИНИРАНА МАШИНА ЗА ПЕРЕЊЕ"))return Kind.WASHER_DRYER;
        if(contains(n,"МАШИНА ЗА СУШЕЊЕ","СУШАРА","TUMBLE DRYER","CLOTHES DRYER"))return Kind.DRYER;
        if(contains(n,"МАШИНА ЗА САДОВИ","МИЕЊЕ САДОВИ","DISHWASHER"))return Kind.DISHWASHER;
        if(contains(n,"МАШИНА ЗА ПЕРЕЊЕ","МАШИНА ЗА АЛИШТА","ПЕРАЛНА","ПРАЛНА",
            "WASHING MACHINE","WASHER","TWM 10","MF110W"))return Kind.WASHER;
        if(contains(n,"ФРИЖИДЕР","ЛАДИЛНИК","ЗАМРЗНУВАЧ","REFRIGERATOR",
            "FREEZER","FRIDGE"))return Kind.REFRIGERATOR;
        if(contains(n,"МОНИТОР","MONITOR"))return Kind.MONITOR;
        if(contains(n,"ТЕЛЕВИЗОР","TELEVISION","SMART TV"," QLED"," OLED",
            " LED TV"," ТВ "))return Kind.TV;
        if(contains(n,"ЛАПТОП","NOTEBOOK","LAPTOP","MACBOOK",
            "IDEAPAD","VIVOBOOK","THINKPAD","ZENBOOK","ASPIRE",
            "PAVILION","INSPIRON"))return Kind.LAPTOP;
        if(contains(n,"СМАРТФОН","МОБИЛЕН ТЕЛЕФОН","IPHONE","SMARTPHONE",
            "GALAXY S","REDMI NOTE"))return Kind.PHONE;
        if(contains(n,"КЛИМА","AIR CONDITIONER","SPLIT SYSTEM"))return Kind.AIR_CONDITIONER;
        if(contains(n,"АСПИРАТОР","COOKER HOOD","RANGE HOOD"))return Kind.HOOD;
        if(contains(n,"МИКРОБРАНОВА","MICROWAVE"))return Kind.MICROWAVE;
        if(contains(n,"ПРАВОСМУКАЛКА","VACUUM CLEANER","РОБОТ ПРАВОСМУКАЛКА"))return Kind.VACUUM;
        if(contains(n,"ШПОРЕТ","РЕРНА","OVEN","COOKER"))return Kind.OVEN;
        if(contains(n,"ТОСТЕР","TOASTER"))return Kind.TOASTER;
        if(contains(n,"ФЕН ЗА КОСА","HAIR DRYER","HAIRDRYER"))return Kind.HAIR_DRYER;
        if(contains(n,"ПРИНТЕР","PRINTER","MULTIFUNCTION PRINTER"))return Kind.PRINTER;
        if(contains(n,"РУТЕР","ROUTER"))return Kind.ROUTER;
        if(contains(n,"ЗВУЧНИК","SPEAKER","SOUNDBAR"))return Kind.SPEAKER;
        return Kind.UNKNOWN;
    }

    static Kind guess(String model,Map<String,String> facts){
        Kind named=guess(model);
        if(named!=Kind.UNKNOWN)return named;
        if(facts==null||facts.isEmpty())return Kind.UNKNOWN;
        // Distinctive labels can identify a family; generic RAM, power or capacity cannot.
        if(facts.containsKey("Капацитет на перење")&&facts.containsKey("Капацитет на сушење"))
            return Kind.WASHER_DRYER;
        if(facts.containsKey("Капацитет на перење")||facts.containsKey("Центрифуга"))
            return Kind.WASHER;
        if(facts.containsKey("Капацитет на сушење"))return Kind.DRYER;
        if(facts.containsKey("No Frost"))return Kind.REFRIGERATOR;
        if(facts.containsKey("Капацитет BTU")||facts.containsKey("SEER")||
            facts.containsKey("SCOP"))return Kind.AIR_CONDITIONER;
        if(facts.containsKey("Проток на воздух")&&facts.containsKey("Филтер"))
            return Kind.HOOD;
        if(facts.containsKey("Дијагонала")&&facts.containsKey("Smart TV"))return Kind.TV;
        return Kind.UNKNOWN;
    }

    static boolean canCompare(String left,String right){
        Kind a=guess(left),b=guess(right);
        return a==Kind.UNKNOWN||b==Kind.UNKNOWN||a==b;
    }

    static boolean canCompare(String left,Map<String,String> lf,
                              String right,Map<String,String> rf){
        Kind a=guess(left,lf),b=guess(right,rf);
        return a!=Kind.UNKNOWN&&b!=Kind.UNKNOWN&&a==b
            || a==Kind.UNKNOWN&&b==Kind.UNKNOWN;
    }

    static String incompatibility(String left,String right){
        return incompatibility(left,null,right,null);
    }
    static String incompatibility(String left,Map<String,String> lf,
                                  String right,Map<String,String> rf){
        Kind a=guess(left,lf),b=guess(right,rf);
        if(a!=Kind.UNKNOWN&&b!=Kind.UNKNOWN&&a!=b)
            return "Различни категории: "+a.label+" и "+b.label+
                ". Техничка споредба не е дозволена за различни типови производи.";
        if(a==Kind.UNKNOWN ^ b==Kind.UNKNOWN)
            return "Категоријата на еден производ не е потврдена. "+
                "Нема сигурна основа за споредба на техничките спецификации.";
        return "";
    }
}
