package io.simpleide.ui.treefile;

public enum TypeFile {

  JAVA("java"),
  CSHARP("csharp"),
  GROOVY("groovy"),
  TXT("txt"),
  KOTLIN("kotlin"),
  JAVASCRIPT("javascript"),
  GO("go"),
  RUST("rust"),
  PYTHON("python"),
  BINARY("binary"),
  CPP("c++"),
  GO("go"),
  RUST("rust"),
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
