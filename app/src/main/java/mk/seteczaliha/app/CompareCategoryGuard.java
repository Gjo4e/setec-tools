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
        HEADPHONES("Слушалки"), TABLET("Таблет"), SMARTWATCH("Паметен часовник"),
        VACUUM("Правосмукалка"), COFFEE_MACHINE("Кафемат"), BOILER("Бојлер"),
        COOKTOP("Плотна"), MICROWAVE("Микробранова"), PRINTER("Печатач"),
        ROUTER("Рутер"), CAMERA("Камера"), SPEAKER("Звучник"),
        KEYBOARD("Тастатура"), MOUSE("Глушец"), GAME_CONSOLE("Конзола"),
        AIR_PURIFIER("Прочистувач на воздух"), FAN("Вентилатор"),
        HAIR_DRYER("Фен"), BLENDER("Блендер"), IRON("Пегла"),
        FREEZER("Замрзнувач"),
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
        if(contains(n,"ЗАМРЗНУВАЧ","FREEZER","ЗАМРЗНАТА"))return Kind.FREEZER;
        if(contains(n,"ФРИЖИДЕР","ЛАДИЛНИК","ЗАМРЗНУВАЧ","REFRIGERATOR","FREEZER","FRIDGE"))return Kind.REFRIGERATOR;
        if(contains(n,"СЛУШАЛКИ","HEADPHONES","EARBUDS","EARPHONES","TWS","HEADSET"))return Kind.HEADPHONES;
        if(contains(n,"ТАБЛЕТ","TABLET","IPAD","IDEA TAB"))return Kind.TABLET;
        if(contains(n,"ПАМЕТЕН ЧАСОВНИК","SMARTWATCH","SMART WATCH","GALAXY WATCH","APPLE WATCH"))return Kind.SMARTWATCH;
        if(contains(n,"ПРАВОСМУКАЛКА","VACUUM","ROBOT CLEANER"))return Kind.VACUUM;
        if(contains(n,"КАФЕМАТ","COFFEE MACHINE","ESPRESSO","АПАРАТ ЗА КАФЕ"))return Kind.COFFEE_MACHINE;
        if(contains(n,"БОЈЛЕР","WATER HEATER"))return Kind.BOILER;
        if(contains(n,"ПЛОТНА","HOB","COOKTOP"))return Kind.COOKTOP;
        if(contains(n,"МИКРОБРАНОВА","MICROWAVE"))return Kind.MICROWAVE;
        if(contains(n,"ПЕЧАТАЧ","PRINTER","MULTIFUNCTION PRINTER"))return Kind.PRINTER;
        if(contains(n,"РУТЕР","ROUTER","MESH WIFI"))return Kind.ROUTER;
        if(contains(n,"ФОТОАПАРАТ","WEB CAMERA","WEBCAM","DIGITAL CAMERA"))return Kind.CAMERA;
        if(contains(n,"ЗВУЧНИК","SPEAKER","SOUNDBAR"))return Kind.SPEAKER;
        if(contains(n,"ТАСТАТУРА","KEYBOARD"))return Kind.KEYBOARD;
        if(contains(n,"ГЛУШЕЦ","MOUSE"))return Kind.MOUSE;
        if(contains(n,"КОНЗОЛА","PLAYSTATION","XBOX","NINTENDO SWITCH"))return Kind.GAME_CONSOLE;
        if(contains(n,"ПРОЧИСТУВАЧ НА ВОЗДУХ","AIR PURIFIER"))return Kind.AIR_PURIFIER;
        if(contains(n,"ВЕНТИЛАТОР","FAN"))return Kind.FAN;
        if(contains(n,"ФЕН ЗА КОСА","HAIR DRYER"))return Kind.HAIR_DRYER;
        if(contains(n,"БЛЕНДЕР","BLENDER"))return Kind.BLENDER;
        if(contains(n,"ПЕГЛА","IRON"))return Kind.IRON;
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
