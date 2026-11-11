package io.simpleide.ui.treefile;

public enum TypeFile {

  JAVA("java"),
  CSHARP("csharp"),
  GROOVY("groovy"),
  TXT("txt"),
  KOTLIN("kotlin"),
  JAVASCRIPT("javascript"),
  GO("go"),
  PYTHON("python"),
  BINARY("binary"),
  CPP("c++"),
  ASM("Assembly-65x"),
  C("C");

  private final String name;

  private TypeFile(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}
