package io.simpleide.ui.treefile;

public enum TypeFile {

  JAVA("java"),
  GROOVY("groovy"),
  TXT("txt"),
  KOTLIN("kotlin"),
  BINARY("binary");

  private String name;

  private TypeFile(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}
