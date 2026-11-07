package mk.seteczaliha.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Category-specific comparison criteria. Labels must come from exact SETEC
 * product facts; a profile never creates or infers a specification value.
 */
final class CompareCategoryProfiles {
    private CompareCategoryProfiles(){}

    static List<String> criteria(CompareCategoryGuard.Kind kind){
        if(kind==null)return Collections.emptyList();
        switch(kind){
            case WASHER: return Arrays.asList("Капацитет на перење","Центрифуга",
                "Енергетска класа","Програми","Димензии");
            case DRYER: return Arrays.asList("Капацитет на сушење","Енергетска класа",
                "Програми","Бучава","Димензии");
            case DISHWASHER: return Arrays.asList("Капацитет","Програми",
                "Енергетска класа","Бучава","Димензии");
            case REFRIGERATOR: return Arrays.asList("Капацитет","No Frost",
                "Енергетска класа","Димензии","Бучава");
            case TV: return Arrays.asList("Дијагонала","Резолуција",
                "Освежување","Технологија на екран","Smart TV");
            case MONITOR: return Arrays.asList("Дијагонала","Резолуција",
                "Освежување","Технологија на екран","HDMI");
            case LAPTOP: return Arrays.asList("Процесор","RAM","Диск",
                "Графичка","Екран");
            case PHONE: return Arrays.asList("Екран","RAM","Диск",
                "Батерија","Камера");
            case AIR_CONDITIONER: return Arrays.asList("Капацитет BTU",
                "Ладење","Греење","SEER","SCOP");
            case HOOD: return Arrays.asList("Проток на воздух","Брзини",
                "Бучава","Ширина","Филтер");
            case OVEN: return Arrays.asList("Моќност","Капацитет",
                "Температура","Енергетска класа","Димензии");
            case TOASTER: return Arrays.asList("Моќност","Број на парчиња",
                "Степени на печење","Одмрзнување","Автоматско исклучување");
            case VACUUM: return Arrays.asList("Моќност","Капацитет",
                "Филтер","Бучава","Автономија");
            case MICROWAVE: return Arrays.asList("Моќност","Капацитет",
                "Програми","Тајмер","Димензии");
            case PRINTER: return Arrays.asList("Резолуција","Брзини",
                "Димензии","Wi-Fi","Капацитет");
            case SPEAKER: return Arrays.asList("Моќност","Батерија",
                "Автономија","Димензии","Bluetooth");
            case ROUTER: return Arrays.asList("Wi-Fi","Брзини","Фреквенција",
                "Број на антени","LAN");
            case HAIR_DRYER: return Arrays.asList("Моќност","Брзини",
                "Нивоа на топлина","Јонизатор","Ладен воздух");
            default: return Collections.emptyList();
        }
    }

    static boolean known(CompareCategoryGuard.Kind kind){
        return kind!=null&&kind!=CompareCategoryGuard.Kind.UNKNOWN;
    }
}
