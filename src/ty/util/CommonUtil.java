package ty.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author	: y0ng94
 * @version	: 1.0
 */
public class CommonUtil {

	public static List<String> getListDiff(List<String> list1, List<String> list2) {
		return list1.stream().filter(item -> list2.stream().noneMatch(Predicate.isEqual(item))).collect(Collectors.toList());
	}

	public static Map<String, Object> getMapDiff(Map<String, Object> map1, Map<String, Object> map2) {
		Map<String, Object> retMap = new HashMap<>(map1);
		
		for (Map.Entry<String,Object> entry : map1.entrySet()) {
			if (entry.getValue().equals(map2.get(entry.getKey())))
				retMap.remove(entry.getKey());
		}
		
		return retMap;
	}
	
	public static double getTimeElapsed(long time) {
		return (double)(System.currentTimeMillis() - time)/(1000.0);
	}

	public static double getDivision(int numerator, int denominator) {
		if (numerator == 0) { return 0.00; }
		return (((double)numerator / (double)denominator) );
	}

	public static double getDivision(int numerator, double denominator) {
		if (numerator == 0) { return 0.00; }
		return (((double)numerator / denominator) );
	}
	
	public static double getPercent(int numerator, int denominator) {
		if (numerator == 0) { return 0.00; }
		return (((double)numerator * 100.0 / (double)denominator) );
	}

	public static int getRandom(int min, int max) {
		return new Random().nextInt(max + 1 - min) + min;
	}
	

	public static String makeBackFileName(String fileName, String postFix) {
		if (fileName.indexOf("\\.") == -1 && fileName.indexOf(".") == -1)
			return fileName + postFix;
		else {
			int index = fileName.lastIndexOf("\\.");
			if (index == -1) { index = fileName.lastIndexOf("."); }
			return fileName.substring(0, index) + postFix + fileName.substring(index);
		}
	}
}
