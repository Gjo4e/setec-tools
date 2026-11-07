package mk.seteczaliha.app;

import java.util.Locale;

/**
 * Conservative product-type check. A washing machine must never be matched
 * against a dryer, a refrigerator or another unrelated device. Unknown
 * categories are left unknown rather than guessed from unrelated values.
 */
final class CompareCategoryGuard {
    private CompareCategoryGuard(){}

    enum Kind {
        WASHER("Машина за перење"),
        DRYER("Машина за сушење"),
        DISHWASHER("Машина за садови"),
        REFRIGERATOR("Фрижидер / замрзнувач"),
        TV("Телевизор"),
        MONITOR("Монитор"),
        LAPTOP("Лаптоп"),
        PHONE("Телефон"),
        AIR_CONDITIONER("Клима"),
        HOOD("Аспиратор"),
        OVEN("Шпорет / рерна"),
        TOASTER("Тостер"),
        UNKNOWN("Непозната категорија");
        final String label;
        Kind(String label){this.label=label;}
    }

    private static boolean contains(String source,String...keys){
        for(String key:keys)if(source.contains(key))return true;
        return false;
    }
    static Kind guess(String model){
        String n=String.valueOf(model).toUpperCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
        if(contains(n,"МАШИНА ЗА СУШЕЊЕ","СУШАРА","TUMBLE DRYER","CLOTHES DRYER"))return Kind.DRYER;
        if(contains(n,"МАШИНА ЗА САДОВИ","МИЕЊЕ САДОВИ","DISHWASHER"))return Kind.DISHWASHER;
        if(contains(n,"МАШИНА ЗА ПЕРЕЊЕ","МАШИНА ЗА АЛИШТА","ПЕРАЛНА","ПРАЛНА","WASHING MACHINE","WASHER","TWM 10","MF110W"))return Kind.WASHER;
        if(contains(n,"ФРИЖИДЕР","ЛАДИЛНИК","ЗАМРЗНУВАЧ","REFRIGERATOR","FREEZER","FRIDGE"))return Kind.REFRIGERATOR;
        if(contains(n,"МОНИТОР","MONITOR"))return Kind.MONITOR;
        if(contains(n,"ТЕЛЕВИЗОР","TELEVISION","SMART TV"," QLED"," OLED"," LED TV"," ТВ "))return Kind.TV;
        if(contains(n,"ЛАПТОП","NOTEBOOK","LAPTOP","MACBOOK","IDEAPAD","VIVOBOOK","THINKPAD","ZENBOOK","ASPIRE","PAVILION","INSPIRON"))return Kind.LAPTOP;
        if(contains(n,"СМАРТФОН","МОБИЛЕН ТЕЛЕФОН","IPHONE","SMARTPHONE","GALAXY S","REDMI NOTE"))return Kind.PHONE;
        if(contains(n,"КЛИМА","AIR CONDITIONER","SPLIT SYSTEM"))return Kind.AIR_CONDITIONER;
        if(contains(n,"АСПИРАТОР","COOKER HOOD","RANGE HOOD"))return Kind.HOOD;
        if(contains(n,"ШПОРЕТ","РЕРНА","OVEN","COOKER"))return Kind.OVEN;
        if(contains(n,"ТОСТЕР","TOASTER"))return Kind.TOASTER;
        return Kind.UNKNOWN;
    }
    static boolean canCompare(String left,String right){
        Kind a=guess(left),b=guess(right);
        return a==Kind.UNKNOWN||b==Kind.UNKNOWN||a==b;
    }
    static String incompatibility(String left,String right){
        Kind a=guess(left),b=guess(right);
        return a!=Kind.UNKNOWN&&b!=Kind.UNKNOWN&&a!=b
            ?"Различни типови: "+a.label+" и "+b.label+". Споредбата на технички спецификации е исклучена за да не се мешаат неисти карактеристики."
            :"";
    }
}
