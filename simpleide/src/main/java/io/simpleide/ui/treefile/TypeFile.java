package io.simpleide.ui.treefile;

public enum TypeFile {

  JAVA("java"),
  GROOVY("groovy"),
  TXT("txt"),
  KOTLIN("kotlin"),
  JAVASCRIPT("javascript"),
  PYTHON("python"),
  BINARY("binary"),
  CPP("c++"),
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
