package io.simplebytecode.compiler.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.simplebytecode.compiler.classloader.JavaRunner;
import io.simplebytecode.compiler.groovy.GroovyRunner;
import io.simplebytecode.compiler.kotlin.KotlinRunner;
import org.junit.jupiter.api.Test;

class MatchExtensionTest {

  @Test
  void mapsSupportedExtensionsToTheirRunners() {
    assertThat(MatchExtension.getRunnerFromExt("java")).isEqualTo(JavaRunner.class);
    assertThat(MatchExtension.getRunnerFromExt("JAV")).isEqualTo(JavaRunner.class);
    assertThat(MatchExtension.getRunnerFromExt("kt")).isEqualTo(KotlinRunner.class);
    assertThat(MatchExtension.getRunnerFromExt("kts")).isEqualTo(KotlinRunner.class);
    assertThat(MatchExtension.getRunnerFromExt("groovy")).isEqualTo(GroovyRunner.class);
    assertThat(MatchExtension.getRunnerFromExt("gsh")).isEqualTo(GroovyRunner.class);
  }

  @Test
  void returnsNullForUnknownOrNullExtensions() {
    assertThat(MatchExtension.getRunnerFromExt("txt")).isNull();
    assertThat(MatchExtension.getRunnerFromExt(null)).isNull();
  }
}
