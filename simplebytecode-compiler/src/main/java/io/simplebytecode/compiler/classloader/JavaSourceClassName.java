package io.simplebytecode.compiler.classloader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavaSourceClassName {

  private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.$]+)\\s*;");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile(
          "\\bpublic\\s+(?:(?:abstract|final|sealed|non-sealed)\\s+)*class\\s+([\\w$]+)");

  private JavaSourceClassName() {}

  static String findMainClass(String source) {
    String withoutComments = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    Matcher classMatcher = PUBLIC_CLASS.matcher(withoutComments);
    if (!classMatcher.find()) {
      return null;
    }

    Matcher packageMatcher = PACKAGE.matcher(withoutComments);
    return packageMatcher.find()
        ? packageMatcher.group(1) + "." + classMatcher.group(1)
        : classMatcher.group(1);
  }
}
