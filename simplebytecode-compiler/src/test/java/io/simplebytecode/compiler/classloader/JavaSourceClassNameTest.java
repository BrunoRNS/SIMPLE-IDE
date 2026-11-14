package io.simplebytecode.compiler.classloader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavaSourceClassNameTest {

  @Test
  void findsThePublicMainClassAndPackage() {
    assertThat(
            JavaSourceClassName.findMainClass(
                """
            package example;
            public final class Main {
                public static void main(String[] args) {}
            }
            """))
        .isEqualTo("example.Main");
  }

  @Test
  void ignoresCommentsAndRejectsSourcesWithoutPublicClasses() {
    assertThat(
            JavaSourceClassName.findMainClass(
                """
            // public class Fake {}
            /* public class AlsoFake {} */
            class PackagePrivate {}
            """))
        .isNull();
  }
}
