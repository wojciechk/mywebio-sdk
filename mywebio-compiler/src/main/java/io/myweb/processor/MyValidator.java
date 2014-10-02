package io.myweb.processor;

import android.content.Context;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import io.myweb.http.Method;
import io.myweb.http.Request;
import io.myweb.processor.model.ParsedParam;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.size;

public class MyValidator extends AnnotationMessagerAware {

    private final static String TAB = "    ";
    private static final String CONSOLE_COLOR_ORANGE = "\u001B[33m";
    private static final String CONSOLE_COLOR_RESET = "\u001B[0m";
    private static final String CONSOLE_COLOR_RED = "\u001B[31m";

	private static final String[] TYPE_NAMES = {
			String.class.getName(), "int", "long", "double", "float", "boolean",
			Integer.class.getName(), Long.class.getName(), Float.class.getName(),
			Double.class.getName(), Boolean.class.getName(), JSONObject.class.getName(),
			JSONArray.class.getName(), Object.class.getName() // the Object is for json values
	};

    public MyValidator(Messager messager) {
		super(messager);
	}

	public static boolean isTypeNameAllowed(String typeName) {
		for (String t: TYPE_NAMES) {
			if (t.equals(typeName)) return true;
		}
		return false;
	}

	public void validateAnnotation(Method httpMethod, String destMethodRetType, String destMethod, List<ParsedParam> params, String httpUri, ExecutableElement ee, AnnotationMirror am) {
		int paramsInAnnotation = StringUtils.countMatches(httpUri, ":");
		paramsInAnnotation += StringUtils.countMatches(httpUri, "*");
		int paramsInMethod = size(filter(params, new Predicate<ParsedParam>() {
			@Override
			public boolean apply(ParsedParam param) {
				return isTypeNameAllowed(param.getTypeName());
			}
		}));
		if (paramsInAnnotation != paramsInMethod) {
			getMessager().printMessage(Diagnostic.Kind.ERROR, "This annotation requires different set of parameters for the method (details below)", ee, am);
			String errorMsg = buildErrorMsg(httpMethod, httpUri, destMethodRetType, destMethod, params);
			getMessager().printMessage(Diagnostic.Kind.ERROR, errorMsg);
			throw new RuntimeException();
		}
	}

	private String buildErrorMsg(Method httpMethod, String httpUri, String destMethodRetType, String destMethod, List<ParsedParam> params) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		appendAnnotation(sb, httpMethod, httpUri);
		appendAnnotationUnderline(sb, httpMethod, httpUri, params);
		appendMethod(sb, destMethodRetType, destMethod, params);
		appendMethodUnderline(sb, destMethodRetType, destMethod, httpUri, params);
		return sb.toString();
	}

	private void appendMethod(StringBuilder sb, String destMethodRetType, String destMethod, List<ParsedParam> params) {
		sb.append(CONSOLE_COLOR_ORANGE);
		sb.append(TAB);
		sb.append("public ");
		sb.append(simpleTypeName(destMethodRetType)).append(" ");
		sb.append(destMethod);
		sb.append("(");
		appendMethodParams(sb, params);
		sb.append(")");
		sb.append(CONSOLE_COLOR_RESET);
        sb.append("\n");
	}

	private String simpleTypeName(String destMethodRetType) {
		int lastDotIndex = destMethodRetType.lastIndexOf(".");
		if (lastDotIndex > 0) {
			return destMethodRetType.substring(lastDotIndex + 1);
		} else {
			return destMethodRetType;
		}
	}

	private void appendMethodParams(StringBuilder sb, List<ParsedParam> params) {
		Collection<String> typesAndNames = transform(params, new Function<ParsedParam, String>() {
			@Override
			public String apply(ParsedParam pe) {
				return simpleTypeName(pe.getTypeName()) + " " + pe.getName();
			}
		});
		String paramsStr = Joiner.on(", ").join(typesAndNames);
		sb.append(paramsStr);
	}

	private void appendAnnotation(StringBuilder sb, Method httpMethod, String httpUri) {
		sb.append(CONSOLE_COLOR_ORANGE);
		sb.append(TAB).append("@").append(httpMethod.toString());
		sb.append("(\"").append(httpUri).append("\")");
		sb.append(CONSOLE_COLOR_RESET);
        sb.append("\n");
	}

	private void appendAnnotationUnderline(StringBuilder sb, Method httpMethod, String httpUri, List<ParsedParam> params) {
		int beginOffset = 5 + httpMethod.toString().length() + 2;  // TAB @NAME ("
		appendSpaces(sb, beginOffset);
		String[] uriSplit = httpUri.split("/");
		for (String pathElem : uriSplit) {
			if (pathElem.startsWith(":") || pathElem.startsWith("*")) {
				String paramName = pathElem.substring(1);
				if (isPathParamCorrect(paramName, params)) {
					appendSpaces(sb, 2 + paramName.length()); // NAME + "/:"
				} else {
					appendUnderlineChars(sb, 1 + paramName.length()); // NAME + "/:"
					appendSpaces(sb, 1); // NAME + "/:"
				}
			} else {
				appendSpaces(sb, 1 + pathElem.length());
			}
		}
        sb.append("\n");
	}

	private void appendMethodUnderline(StringBuilder sb, String destMethodRetType, String destMethod, String httpUri, List<ParsedParam> params) {
		int beginOffset  = 4 + "public ".length() + simpleTypeName(destMethodRetType).length() + 1 + destMethod.length() + 1;
		appendSpaces(sb, beginOffset);
		for (ParsedParam param : params) {
			if (isMethodParamCorrect(httpUri, param)) {
				int offset = simpleTypeName(param.getTypeName()).length() + 1; // TYPE AND SPACE
				offset += param.getName().length() + 2;
				appendSpaces(sb, offset); // NAME COMA SPACE
			} else {
				int offset = simpleTypeName(param.getTypeName()).length() + 1; // TYPE AND SPACE
				offset += param.getName().length();
				appendUnderlineChars(sb, offset); // NAME COMA
				appendSpaces(sb, 2);   // COMA SPACE
			}
		}
	}

	private boolean isMethodParamCorrect(String httpUri, ParsedParam param) {
		String[] pathElems = httpUri.split("/");
		boolean methodParamCorrect = false;
		for (String pathElem : pathElems) {
			if (pathElem.startsWith(":") || pathElem.startsWith("*")) {
				String paramName = pathElem.substring(1);
				if (paramName.equals(param.getName())) {
					methodParamCorrect = true;
				}
			}
		}
		// TODO warn if more then one Context or Request
        if (Context.class.getName().equals(param.getTypeName()) ||
		        Request.class.getName().equals(param.getTypeName())) {
			methodParamCorrect = true;
		}
		return methodParamCorrect;
	}

	private void appendUnderlineChars(StringBuilder sb, int count) {
		sb.append(CONSOLE_COLOR_RED);
		for (int i = 0; i < count; i++) {
			sb.append("^");
		}
		sb.append(CONSOLE_COLOR_RESET);
	}

	private void appendSpaces(StringBuilder sb, int count) {
		for (int i = 0; i < count; i++) {
			sb.append(" ");
		}
	}

	private boolean isPathParamCorrect(String pathParam, List<ParsedParam> params) {
		for (ParsedParam param : params) {
			if (param.getName().equals(pathParam)) {
				return true;
			}
		}
		return false;
	}
}