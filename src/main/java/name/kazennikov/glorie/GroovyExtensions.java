package name.kazennikov.glorie;


import gate.LanguageAnalyser;
import gate.creole.ExecutionException;

import java.util.List;

/**
 * Basic Groovy extension functions
 */
public class GroovyExtensions {

    public static class Global {
    }


    public static class Static {
    }

    public static Object getAt(SymbolSpan span, Object feature) {
        return span.features.get(feature);
    }

    public static void putAt(SymbolSpan span, Object feature, Object value) {
        span.features.put(feature, value);
    }


    public static Object missingProperty(SymbolSpan span, String name) {
        return span.features.get(name);
    }

    public static void missingProperty(SymbolSpan span, String name, Object value) {
        span.features.put(name, value);
    }

    public static String string(FieldedRHSAction self, SymbolSpan span) {
        try {
            return string(self, span.start, span.end);
        } catch(Exception e) {
            return "";
        }
    }

    public static String string(FieldedRHSAction self, List<SymbolSpan> spans) {
        if(spans.isEmpty())
            return "";

        int start = spans.get(0).start;
        int end = spans.get(spans.size() - 1).end;
        try {
            return string(self, start, end);
        } catch(Exception e) {
            return "";
        }
    }

    public static String string(FieldedRHSAction self, SymbolSpan... spans) {
        if(spans.length == 0)
            return "";

        int start = spans[0].start;
        int end = spans[spans.length - 1].end;

        try {
            return string(self, start, end);
        } catch(Exception e) {
            return "";
        }
    }

    public static String string(FieldedRHSAction self, int start, int end) {
        try {
            return self.text.substring(start, end);
        } catch(Exception e) {
            return "";
        }
    }

    public static int length(SymbolSpan span) {
        return span.end - span.start;
    }

    public static int length(List<SymbolSpan> spans) {
        if(spans.isEmpty())
            return 0;

        return spans.get(spans.size() - 1).end - spans.get(0).start;
    }

    public static int length(SymbolSpan... spans) {
        if(spans.length == 0)
            return 0;

        return spans[spans.length - 1].end - spans[0].start;
    }

    public static int parseInt(String s) {
        return Integer.parseInt(s);
    }

    public static float parseFloat(String s) {
        return Float.parseFloat(s);
    }

    public static double parseDouble(String s) {
        return Double.parseDouble(s);
    }

    public static long parseLong(String s) {
        return Long.parseLong(s);
    }

	public static void call(LanguageAnalyser la) throws ExecutionException {
		la.execute();
	}



}

